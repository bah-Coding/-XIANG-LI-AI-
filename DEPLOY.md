# hmdp-Java 部署说明

> 本文件是通用部署步骤（不含任何真实账号/密码）。本机实际取值放在 `local.env.ps1`（已 gitignore），
> 可参考 `.env.example` 创建。项目目录：`<project>`，访问入口：<http://localhost:8080>。

## 一、运行组件

| 组件 | 版本 / 位置 | 端口 | 说明 |
|---|---|---|---|
| 数据库 | MySQL 8.x，库名 `hmdp`（`db/db.sql` 初始化） | 3306 | 账号密码由 `DB_USERNAME` / `DB_PASSWORD` 注入 |
| Redis | Redis 6.x+（本机或虚拟机均可） | 6379 | 由 `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` 注入 |
| 后端 | Spring Boot 3.5.6 + JDK 17 | 8081 | 产物 `AI-dianping-backend/target/AI-dianping-backend-0.0.1-SNAPSHOT.jar` |
| 前端 | 仓库自带 Windows Nginx | 8080 | 静态页 + `/api` 反代到 8081 |
| 日志 | `LOG_PATH`（默认 `<工作目录>/logs/backend`） | - | 启动失败先看这里 |

## 二、配置（全部走环境变量，仓库零明文）

复制 `.env.example` 为 `local.env.ps1` 并填入本机取值：

```powershell
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '<db-password>'
$env:REDIS_HOST   = '<redis-host>'
$env:REDIS_PASSWORD = '<redis-password>'
$env:IMAGE_UPLOAD_DIR = '<project>/AI-dianping-frontend/html/hmdp/imgs/'
$env:LOG_PATH = '<project>/logs/backend'
```

`application.yml` 里全部是占位默认值（`${DB_PASSWORD:}`、`${REDIS_HOST:localhost}` 等），
代码里没有任何绝对路径或明文账号。

## 三、启动 / 停止

```bat
:: 拉起后端和 Nginx（已在运行的服务会跳过）
start-all.cmd
:: 停止（按端口 6379/8081/8080）
stop-all.cmd
```

脚本会读取同目录的 `local.env.ps1`。若 `REDIS_HOST` 为本机地址，脚本会尝试拉起 `E:\Programs\Memurai\memurai.exe`；
为远程地址时只探测连通性并提示。

## 四、启用真实 AI（可选）

未配置模型时：AI Skills 返回本地兜底文案；智能客服实时对话不可用。

```powershell
$env:API_KEY = 'sk-你的key'
stop-all.cmd
start-all.cmd
```

模型地址 `https://dashscope.aliyuncs.com/compatible-mode/v1`，模型 `qwen-flash`。

## 五、相对原始仓库的改动（脱敏 + 修复）

1. `application.yml`：数据库/Redis 全部改为环境变量占位（`${DB_URL:...}`、`${DB_USERNAME:root}`、`${DB_PASSWORD:}`、`${REDIS_HOST:localhost}`…），新增 `hmdp.image-upload-dir: ${IMAGE_UPLOAD_DIR:imgs/}`；AI 用 `${API_KEY:sk-not-configured}`
2. `logback.xml`：日志目录改为 `${LOG_PATH:-logs/backend}`（原为作者本机绝对路径 `D:/Java_project/...`，换机器会启动失败）。注意 logback 只认系统属性，脚本会把它转成 `-DLOG_PATH=...`
3. `UploadController` + `SystemConstants`：图片上传目录从硬编码常量改为配置项 `hmdp.image-upload-dir`（`@Value` 注入），删除 `SystemConstants.IMAGE_UPLOAD_DIR`
4. `McpToolsConfig`：9 处 `request.getArguments()` → `request.arguments()`（MCP SDK 0.12.1 record 访问器改名，原代码无法编译）
5. `McpServerConfig`：`mcpObjectMapper` 改用 `Jackson2ObjectMapperBuilder` 构建（原 `new ObjectMapper()` 让 Jackson 自动配置退让，导致 `/shop/1` 序列化 `LocalDateTime` 报「服务器异常」）
6. `db/db.sql`：重复的 `CREATE TABLE tb_follow` 改为 `CREATE TABLE IF NOT EXISTS`（原脚本重复执行会在第 367 行中断，后面的 `tb_reservation`、`tb_ai_skill*` 建不出来）
7. `nginx.conf`：`proxy_pass` / `upstream` 占位符 → `127.0.0.1`；补齐 `logs/`、`temp/*` 目录
8. `AI-dianping-frontend/html/hmdp/imgs/`：补齐 59 个图片资源（仓库原本未提交，导致 `/types/*.png`、`/imgs/*` 全部 404）
   - `tb_shop.images` 是外链 CDN，不依赖本地文件；`tb_shop_type.icon` 是 `/types/*.png`，前端拼成 `/imgs//types/*.png`，Nginx 合并重复斜杠后命中本地文件

## 六、验证过的功能

- 首页返回 200，标题「巷里」
- `/api/shop-type/list`、`/shop/1` 正常（MySQL + Redis + BloomFilter + GEO 预热 14 家店）
- 验证码 → Redis `login:code:{phone}` → 登录拿 token → `/user/me` 鉴权成功
- `/ai/skills` 列表、`/ai/skills/blog_draft/run`（无 Key 时 `fallback=true` 返回兜底文案）
- MCP：`POST /mcp` 的 `initialize`、`tools/list` 正常返回（共 11 个工具）

## 七、可观测性与错误码

- 健康检查：`GET /actuator/health`（含 db、redis 状态）
- 指标：`GET /actuator/prometheus`，包含 JVM/HTTP 指标与自定义缓存指标：
  - `hmdp_cache_requests_total{layer="local|redis|none",result="hit|miss"}` 缓存命中分布
  - `hmdp_cache_local_size` Guava 本地缓存条目数
- 每个请求生成 `traceId`：响应头 `X-Trace-Id`，日志格式含 `[traceId]`，便于串联排查
- 错误码：`Result.code`（0 成功 / 40000 参数 / 40100 未登录 / 42900 限流 / 50000 服务端），前端仍可只看 `success`
- ⚠️ `/actuator/*` 目前未加鉴权（与 `/mcp` 相同），公网部署前需限制访问（单独管理端口或网关鉴权）

## 八、RabbitMQ（订单超时未支付自动取消）

**用途**：下单成功后投递一条带 TTL 的消息；超时仍未支付则自动取消订单并回滚库存。

```
order.delay.exchange --order.delay--> order.delay.queue（消息 TTL，无消费者）
     过期经 DLX --> order.cancel.exchange --order.cancel--> order.cancel.queue
                    --> 消费者 CAS(status 1->4) + 回滚 DB/Redis 库存 + 移除 seckill:order 标记
     消费异常重试 3 次 --> order.dlx.exchange --> order.cancel.dlq
```

| 配置项 | 环境变量 | 默认 |
|---|---|---|
| RabbitMQ 地址/端口 | `RABBITMQ_HOST` / `RABBITMQ_PORT` | localhost / 5672 |
| 账号 | `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | guest / guest |
| 开关（false 时不起监听、不投递） | `RABBITMQ_ENABLED` | true |
| 订单超时（毫秒） | `ORDER_TIMEOUT_MS` | 900000（15 分钟） |

**验证方法**：
1. 登录后调用 `POST /api/voucher-order/seckill/{voucherId}` 下单
2. 管理台（`http://<vm-ip>:15672`）查看 `order.delay.queue` 有 1 条消息（统计有约 5 秒延迟）
3. 超时后看 `order.cancel.queue` 出现过消息、订单 `status` 变为 4、库存 +1
4. 消费失败超过 3 次时消息落到 `order.cancel.dlq`

**本地实测**：TTL=8000ms，08:23:43 下单 → 08:23:51 消费者取消，`status` 1→4，DB/Redis 库存 9→10，
`seckill:order:10` 清空，三个队列均清空。

## 九、容器化部署（Docker Compose）

```bash
cd <project>
cp .env.docker.example .env       # 改密码
docker compose up -d --build
docker compose ps                 # 全部 healthy 再访问
```

- 访问 `http://localhost:8080`；RabbitMQ 管理台 `http://localhost:15673`
- 服务：mysql / redis / rabbitmq / backend / nginx；后端只在 compose 网络内暴露（宿主 8082 仅调试）
- `db/db.sql` 只在数据卷为空时初始化一次；改脚本需 `docker compose down -v`（会清库）
- 上传目录同时挂给后端（写）和 nginx（读）

> 本机未安装 Docker，compose 未实跑；在装了 Docker 的机器上先 `docker compose config` 校验。
> 详细原理与常见坑见 `docs/tutorial-deploy-security-loadtest.md`。

## 十、安全

| 措施 | 实现 | 验证 |
|---|---|---|
| `/mcp`、`/actuator` 鉴权 | `common/ApiKeyAuthFilter`，请求头 `X-API-Key` 与环境变量 `MCP_API_KEY` 比对 | 无 key 401，带 key 200 |
| 验证码限流 | 同手机号 60s 一次 + 同 IP 20 次/小时（Redis INCR+EXPIRE） | 连发第二次返回 `code=42900` |

`MCP_API_KEY` 为空时放行并打 WARN（仅本地开发）。仍未做：HTTPS、MCP 工具级授权、登录失败锁定、依赖漏洞扫描。

## 十一、压测

```powershell
pwsh -File scripts/loadtest.ps1 -Url http://127.0.0.1:8080/api/shop/1 -Concurrency 20 -DurationSeconds 5
pwsh -File scripts/loadtest.ps1 -Url http://127.0.0.1:8080/api/shop/1 -Concurrency 100 -DurationSeconds 10
```

本机实测（Nginx → 后端，缓存命中）：20 并发 5s → **1197 QPS / P99 29.6ms**；100 并发 10s → **1529 QPS / P99 95.1ms / 0 错误**。
数字仅代表本机回环，不能当生产容量；解读方法与压测设计见教学文档。

## 十二、发布优惠券（运营操作）

前端已补发布页：<http://localhost:8080/voucher-admin.html>
（店铺详情页「代金券」区右上角也有「+ 发布优惠券」入口，会自动带上店铺 ID）

**字段说明**
- 金额单位是「分」：`500` = 5 元
- 发布秒杀券会同时写 `tb_voucher`、`tb_seckill_voucher`，并把库存写入 Redis `seckill:stock:{id}`，**发布后立即可抢**
- 开始/结束时间必填，格式 `yyyy-MM-ddTHH:mm:ss`

**接口方式**（`/voucher/**` 在登录白名单内，所以不需要登录；生产环境应加管理员校验）

```bash
curl -X POST http://127.0.0.1:8080/api/voucher/seckill \
  -H "Content-Type: application/json" \
  -d '{"shopId":1,"title":"100元代金券","subTitle":"5元购","rules":"全场通用",
       "payValue":500,"actualValue":1000,"type":1,"status":1,"stock":10,
       "beginTime":"2026-10-21T00:00:00","endTime":"2026-12-31T23:59:59"}'
```

**纯 SQL 方式**（不推荐：必须手动同步 Redis 库存，否则抢券会提示库存不足）

```sql
INSERT INTO tb_voucher (shop_id,title,sub_title,rules,pay_value,actual_value,type,status,create_time,update_time)
VALUES (1,'100元代金券','5元购','全场通用',500,1000,1,1,NOW(),NOW());
SET @vid = LAST_INSERT_ID();
INSERT INTO tb_seckill_voucher (voucher_id,stock,create_time,begin_time,end_time)
VALUES (@vid,10,NOW(),NOW(),DATE_ADD(NOW(), INTERVAL 30 DAY));
-- 再执行：redis-cli -a <pwd> SET seckill:stock:@vid 10
```

> 当前库里留了一张测试秒杀券 `id=10`（库存 10），可直接演示；不需要时按 id 删除表记录并 `DEL seckill:stock:10`。

## 十三、常见问题

- **8081 起不来**：看 `logs/backend.out.log`；日志目录路径问题会直接抛 `Logback configuration error`
- **登录/店铺查询失败**：确认 `REDIS_HOST:REDIS_PORT` 可连通；本地 Redis 没起就用 `start-all.cmd`
- **Nginx 启动报 `logs` / `temp` 不存在**：重新创建 `logs`、`temp\client_body_temp` 等目录
- **端口冲突**：后端 8081、前端 8080；改后端端口需同步改 `nginx.conf` 的 `proxy_pass`
- **图片上传 404**：确认 `IMAGE_UPLOAD_DIR` 指向 Nginx 静态目录下的 `imgs`，且 Nginx 已重启
- **Memurai 许可**：Developer 版免费用于开发/测试，生产建议用 Linux 原生 Redis
