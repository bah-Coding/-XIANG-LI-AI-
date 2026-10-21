# 教学：部署工程化 · 安全加固 · 压测

> 这份文档面向「知道怎么写业务代码，但没做过上线相关的事」的读者。
> 三部分互相独立，可以按需读；每部分最后都有「动手验证」和「自检题」。

---

# 第一部分：部署工程化（Docker + Compose）

## 1.1 先搞清楚三个概念

| 概念 | 一句话解释 | 类比 |
|---|---|---|
| 镜像（Image） | 一个只读的「安装包」，包含程序 + 运行时 + 依赖 | 装系统的 ISO |
| 容器（Container） | 镜像运行起来的实例，有独立进程/网络/文件系统 | 装好的机器 |
| 编排（Compose） | 用 YAML 描述「多个容器怎么一起跑」 | 机架图 |

**为什么要把项目容器化？**

1. **环境一致**：你本地是 JDK 17 + MySQL 8.0.34，服务器可能是别的版本；镜像里写死版本。
2. **交付物明确**：一条 `docker compose up -d` 就能起全套，新人不用配环境。
3. **隔离**：MySQL/Redis/RabbitMQ 不污染宿主机。
4. **可回滚**：镜像有 tag，出问题换回上一个。

**代价**：多一层学习成本、镜像体积、跨容器网络排查稍麻烦。个人项目/小团队，收益通常大于成本。

## 1.2 Dockerfile 逐段讲

文件：`AI-dianping-backend/Dockerfile`

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build     # 构建阶段：带 Maven + JDK
WORKDIR /build
COPY pom.xml .                                  # 先只拷 pom
RUN mvn -B -q dependency:go-offline || true     # 下载依赖（这层会被缓存）
COPY src ./src                                  # 再拷源码
RUN mvn -B -q clean package -DskipTests        # 打包 jar
FROM eclipse-temurin:17-jre                     # 运行阶段：只要 JRE，镜像小很多
COPY --from=build /build/target/*.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
```

**关键点：**

- **多阶段构建**：构建用 `maven:...`（约 700MB），运行只用 JRE（约 200MB）。最终镜像不包含源码和 Maven。
- **层缓存**：`COPY pom.xml` 在前、`COPY src` 在后。只改代码时，依赖下载那层直接命中缓存，构建从几分钟降到几十秒。
- **`-DskipTests`**：测试已经在 CI 跑过；镜像构建里再跑一遍会拖慢。要跑就把这行去掉。
- **`-XX:MaxRAMPercentage=75`**：容器内存限制下，JVM 按比例设堆，避免 OOM kill。

## 1.3 docker-compose.yml 讲解

文件：`docker-compose.yml`（项目根目录）

### 服务之间怎么互相访问？

Compose 会创建一个默认网络，**服务名就是主机名**。所以后端配置里写的是：

```yaml
REDIS_HOST: redis          # 不是 localhost
RABBITMQ_HOST: rabbitmq
DB_URL: jdbc:mysql://mysql:3306/hmdp...
```

这是新手最常犯的错：在容器里写 `localhost`，指向的是容器自己，不是宿主机。

### 依赖顺序与健康检查

```yaml
depends_on:
  mysql:
    condition: service_healthy
```

`depends_on` 只保证「启动顺序」，**不保证服务可用**。所以要给 MySQL/Redis/RabbitMQ 配 `healthcheck`，
后端等到 `service_healthy` 才启动。否则后端会因连不上数据库而崩溃重启好几轮。

### 数据持久化

```yaml
volumes:
  - mysql-data:/var/lib/mysql       # 命名卷：容器删了数据还在
  - ./db/db.sql:/docker-entrypoint-initdb.d/01-db.sql:ro   # 初始化脚本
```

**注意**：`docker-entrypoint-initdb.d` 里的脚本**只在数据目录为空时执行一次**。
如果你改了 `db.sql`，要 `docker compose down -v`（删除卷）才会重新导入——这会清数据，慎用。

### 端口映射

```yaml
ports: ["8082:8081"]   # 宿主机 8082 -> 容器 8081
```

后端只在 compose 网络内被 nginx 访问，对外只暴露 nginx 的 8080。`8082` 是为了本地调试方便。

### 上传目录

```yaml
volumes:
  - ./AI-dianping-frontend/html/hmdp/imgs:/imgs            # 后端写入
  - ./AI-dianping-frontend/html/hmdp/imgs:/usr/share/nginx/html/imgs  # nginx 读取
```

同一个宿主机目录挂到两个容器：后端写、nginx 读。否则上传的图片 nginx 找不到。

## 1.4 怎么用

```bash
cd E:\hmdp-Java
copy .env.docker.example .env      # 修改密码；.env 不进版本库
docker compose up -d --build       # 首次构建 + 启动
docker compose ps                  # 看状态（healthy 才算好）
docker compose logs -f backend     # 看后端日志
docker compose up -d --build       # 改代码后重建
docker compose down                # 停止（保留数据）
docker compose down -v             # 停止并删除数据卷（会清库！）
```

访问 <http://localhost:8080>；RabbitMQ 管理台 <http://localhost:15673>。

## 1.5 常见坑

| 现象 | 原因 | 处理 |
|---|---|---|
| 后端启动即退出 | 数据库还没好就启动 | 确认 healthcheck 配了、`depends_on` 用了 `service_healthy` |
| 容器里连不上 Redis | 配了 `localhost` | 改成服务名 `redis` |
| 改 `db.sql` 没生效 | 初始化脚本只跑一次 | `down -v` 清卷重来 |
| 图片 404 | 只挂给了后端没挂给 nginx | 两边都挂同一个宿主机目录 |
| 端口冲突 | 宿主机已占用 8080 | 改 `ports` 左侧的宿主机端口 |

> ⚠️ 本机没有安装 Docker，所以 compose 文件**未在本机实跑验证**；语法与结构按官方规范编写。
> 在装了 Docker Desktop 的机器或 Linux 服务器上执行 `docker compose config` 可先校验语法。

---

# 第二部分：安全加固

## 2.1 先分清「认证」和「授权」

- **认证（Authentication）**：你是谁？——登录、Token、API Key
- **授权（Authorization）**：你能做什么？——角色、权限、工具白名单

本项目原来的问题：
- `/mcp` 暴露 11 个业务工具（能查订单、创建预约），却在登录拦截器白名单里 → 任何人都能调
- `/actuator/*` 暴露健康状态、JVM/缓存指标 → 信息泄露
- 发验证码接口没有频率限制 → 可以被脚本刷（每条验证码都会写 Redis）

## 2.2 API Key 鉴权（过滤器实现）

文件：`common/ApiKeyAuthFilter.java`

```java
@Order(Ordered.HIGHEST_PRECEDENCE + 10)        // 在 TraceIdFilter 之后、业务拦截器之前
protected boolean shouldNotFilter(...) {       // 只处理这两类路径
    return !(path.startsWith("/mcp") || path.startsWith("/actuator"));
}
// 比较请求头 X-API-Key 与配置 MCP_API_KEY；不一致返回 401 + JSON
```

**为什么用 Filter 而不是 Interceptor？**

- Interceptor 只作用于 Spring MVC 的 Controller，而 `/mcp` 是 Servlet 注册的、`/actuator` 走独立 HandlerMapping，拦截器覆盖不到。
- Filter 在 Servlet 层，任何请求都会经过，覆盖面更全。

**为了教学方便，留了开关**：`MCP_API_KEY` 为空时放行并打 WARN（本地开发不被打断），生产必须配置。

缺点（要能说出来）：
- 共享密钥无法区分调用方、无法细粒度授权、无法自动轮换；
- 更完善的做法：JWT + 网关校验，或给 MCP 做**工具级权限**（哪些工具允许匿名、哪些要用户身份）。

验证：

```powershell
curl.exe -s -o NUL -w "%{http_code}`n" http://127.0.0.1:8081/actuator/health                      # 401
curl.exe -s -o NUL -w "%{http_code}`n" http://127.0.0.1:8081/actuator/health -H "X-API-Key: your-api-key"  # 200
```

## 2.3 验证码限流

文件：`service/impl/UserServiceImpl.java` 的 `sendCode`

**限流算法对比：**

| 算法 | 思路 | 优点 | 缺点 |
|---|---|---|---|
| 固定窗口（本项目） | 每个时间窗计数，超阈值拒绝 | 实现最简单 | 窗口边界可能被"打两倍" |
| 滑动窗口 | 记录每次请求时间戳/用 ZSet | 边界平滑 | 存储/计算成本高 |
| 令牌桶 | 匀速发令牌，桶满即上限 | 允许突发 | 要维护桶状态 |
| 漏桶 | 恒定速率流出 | 输出平滑 | 不允许突发 |

本项目用 Redis `INCR + EXPIRE` 实现固定窗口：

```java
Long count = redis.opsForValue().increment("login:code:limit:" + phone);
if (count != null && count > 1) return Result.fail(TOO_MANY_REQUESTS, "发送过于频繁");
if (count != null && count == 1L) redis.expire(key, 60, SECONDS);   // 只在第一次设置过期
```

**为什么要判断 `count == 1`？** 如果每次请求都 `expire`，窗口会被不断续期，等于永远不过期。

**为什么加 IP 维度？** 只限手机号的话，攻击者换手机号就能继续刷；IP 维度限制总量（20 次/小时）。

**面试可能会追问**：分布式环境下 INCR 本身是原子的，但"INCR + EXPIRE"两步不是原子的；
极端情况下 `INCR` 成功、`EXPIRE` 前宕机，key 会永不过期（内存泄漏）。彻底方案是用 Lua 把两步合并，
或用 `SET key 1 EX 60 NX` + `INCR` 的组合。

## 2.4 还没做的（写简历时别说"完成安全加固"）

- HTTPS（证书、Nginx 配置）
- MCP 工具级授权与调用审计
- 登录失败次数限制 / 账号锁定
- 敏感字段脱敏（手机号打码）
- 依赖漏洞扫描（OWASP Dependency-Check）

---

# 第三部分：压测

## 3.1 先分清几个概念

| 指标 | 含义 | 注意 |
|---|---|---|
| QPS | 每秒完成的请求数 | 与并发数不是一回事 |
| 并发（Concurrency） | 同时在途的请求数 | 受客户端、网络、服务端共同影响 |
| 延迟分位数 P50/P95/P99 | 50%/95%/99% 的请求快于该值 | **P99 才是用户真实感受** |
| 错误率 | 非 2xx 或超时比例 | 压测时最容易忽略 |

**为什么不能只看平均值？** 平均 50ms 可能是 95% 的请求 10ms + 5% 的请求 800ms。
用户遇到的是那 5%，所以要报 P95/P99。

**QPS 和并发的关系**：`QPS ≈ 并发数 / 平均响应时间`。100 并发、50ms 响应 → 理论 2000 QPS。
想提高 QPS，要么降延迟（缓存），要么加实例。

## 3.2 工具怎么选

| 工具 | 优点 | 缺点 |
|---|---|---|
| `ab` | 简单 | 功能弱、只支持 HTTP/1.0 语义 |
| `wrk` | 高性能、支持 Lua 脚本 | Windows 上要自己编译 |
| JMeter | 图形化、场景丰富、报表全 | 重、吃资源、脚本维护麻烦 |
| 自写脚本（本项目） | 零依赖、可解释、能嵌进 CI | 功能有限、客户端可能先成为瓶颈 |

本项目用 PowerShell 7 的 `ForEach-Object -Parallel` + `HttpClient`，理由：本机不用装东西、代码可读、方便演示概念。

## 3.3 脚本讲解（`scripts/loadtest.ps1`）

```powershell
1..$Concurrency | ForEach-Object -Parallel {
    $client = [System.Net.Http.HttpClient]::new()   # 每个并发一个 HttpClient，复用连接
    while ((Get-Date) -lt $end) {
        $sw = [Stopwatch]::StartNew()
        $response = $client.GetAsync($u).GetAwaiter().GetResult()
        $sw.Stop()
        $latencies.Add($sw.Elapsed.TotalMilliseconds)
    }
} -ThrottleLimit $Concurrency
```

- `HttpClient` 复用连接，否则每次 TCP 握手会污染延迟数据；
- 记录**单请求延迟**，最后统一算分位数；
- `-ThrottleLimit` 控制并发；
- 汇总时把各 worker 的延迟列表合并。

## 3.4 本机实测结果（2026-10-21）

环境：Windows 宿主机 + 虚拟机（Redis/MySQL 在宿主机，RabbitMQ 在 VM），Nginx → 后端 8081。

**场景 1：`/api/shop/1`（缓存命中路径：布隆过滤器 → 本地缓存 → Redis）**

| 并发 | 时长 | 总请求 | QPS | P50 | P95 | P99 | 错误率 |
|---|---|---|---|---|---|---|---|
| 20 | 5s | 5,999 | **1,197** | 13.4ms | 21.7ms | 29.6ms | 0% |
| 100 | 10s | 15,358 | **1,529** | 55ms | 75ms | 95.1ms | 0% |

**场景 2：`/api/shop/999`（不存在的 id，布隆过滤器直接拦截）**：50 并发 5s → 1,427 QPS，P99 52ms，0 错误。

**怎么解读：**

- 1,500 QPS 对个人项目来说是「够看」的数字，但**这是本机回环 + 缓存命中**的结果，不能当生产容量；
- 并发 20→100，QPS 只涨 27%，延迟却涨 4 倍 → 说明瓶颈已经出现（很可能是压测客户端本身 + Tomcat 线程数）；
- P99 从 30ms 涨到 95ms，用户能感知到变慢。

**写简历时的正确表述**（别写"支撑 10w QPS"）：

> 本地压测（JMeter/自写脚本，100 并发 10s）缓存命中路径 QPS ≈ 1.5k，P99 < 100ms，0 错误。

## 3.5 怎么做「有意义」的压测

1. **预热**：先跑 10% 流量，让 JIT、连接池、本地缓存热起来，再统计。
2. **分离客户端瓶颈**：本机压本机时，客户端和服务端抢 CPU。真正的压测应客户端在另一台机器。
3. **分层压**：
   - 缓存命中路径（`/shop/1`）：测框架/网络极限；
   - 缓存未命中（先删缓存再打）：测 DB + 缓存重建（互斥锁）能力；
   - 秒杀下单：测 Lua + Stream + 库存扣减；
   - 写接口（下单/预约）：测事务与锁。
4. **同时看服务端指标**：`/actuator/prometheus` 的 JVM 线程数、GC、DB 连接池、缓存命中率；否则只看到 QPS 不知道瓶颈。
5. **记录环境**：CPU/内存/JDK/数据库版本，否则数字没有可比性。
6. **找拐点**：逐步加并发（10→50→100→200），画 QPS/延迟曲线，找到 QPS 不再涨、延迟陡增的点。

## 3.6 常见误区

- ❌ 只压一次就下结论（没预热、没重复）
- ❌ 只看平均延迟
- ❌ 用同一台机器既当客户端又当服务端，还声称"服务器性能"
- ❌ 压测数据和生产数据量级差几个数量级（本项目 14 家店 vs 生产几十万）
- ❌ 忽略错误率（很多脚本把 5xx 当成功计数）

---

# 第四部分：动手复现清单

```powershell
# 1) 容器化（需要 Docker）
cd E:\hmdp-Java
copy .env.docker.example .env
docker compose up -d --build
docker compose ps
start http://localhost:8080

# 2) 安全验证（本机脚本方式）
curl.exe -s -o NUL -w "%{http_code}`n" http://127.0.0.1:8081/actuator/health
curl.exe -s -o NUL -w "%{http_code}`n" http://127.0.0.1:8081/actuator/health -H "X-API-Key: your-api-key"
curl.exe -s -X POST "http://127.0.0.1:8080/api/user/code?phone=13000000099"
curl.exe -s -X POST "http://127.0.0.1:8080/api/user/code?phone=13000000099"   # 第二次应返回 42900

# 3) 压测
pwsh -File scripts/loadtest.ps1 -Url http://127.0.0.1:8080/api/shop/1 -Concurrency 20 -DurationSeconds 5
pwsh -File scripts/loadtest.ps1 -Url http://127.0.0.1:8080/api/shop/1 -Concurrency 100 -DurationSeconds 10
```

---

# 第五部分：自检题

**部署**
1. 多阶段构建解决了什么问题？
2. 为什么 `COPY pom.xml` 要放在 `COPY src` 前面？
3. `depends_on` 和 `healthcheck` 分别解决什么问题？
4. 容器里为什么不能写 `localhost` 连 MySQL？
5. `docker compose down` 和 `down -v` 有什么区别？

**安全**
6. 认证和授权的区别？你的 API Key 方案属于哪种？
7. 为什么用 Filter 而不是 Interceptor 保护 `/mcp`？
8. 固定窗口限流的边界问题是什么？怎么改进？
9. `INCR + EXPIRE` 为什么可能泄漏 key？
10. API Key 方案有哪些局限？

**压测**
11. QPS 和并发数的关系？为什么 100 并发不是 100 QPS？
12. 为什么看 P99 而不是平均值？
13. 为什么本机压测的数字不能当生产容量？
14. 要测出缓存的价值，应该怎么设计两组压测？
15. 压测时除了 QPS，还要看哪些服务端指标？
