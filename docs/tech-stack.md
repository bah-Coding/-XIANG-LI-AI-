# 技术栈学习地图（对照本项目）

> 用法：每个模块按「项目里的文件 → 要掌握的点 → 自检问题」走一遍，答不出自检题就回去补。
> 标记：⭐ = 面试高频，🔧 = 本项目已落地，📌 = 待补/可加分。

## 1. Java / JVM 基础 ⭐

**项目位置**：全项目；重点 `utils/RedisIdWorker.java`（雪花算法变体）、`common/*`

| 要掌握 | 自检问题 |
|---|---|
| 集合：HashMap 扩容/树化、ConcurrentHashMap | 为什么 `userMap` 用 HashMap 而 token 缓存用 Redis Hash？ |
| 并发：线程池参数、`synchronized` vs `ReentrantLock`、`Atomic*` | `SECKILL_ORDER_EXECUTOR` 为什么用单线程？多线程会怎样？ |
| `ThreadLocal` 原理与内存泄漏 | `UserHolder` 用的是哪种 ThreadLocal？不 remove 会出什么问题？ |
| JVM：类加载、GC、常见调优参数 | 堆内存怎么估？OOM 时你会看什么？ |
| 时间 API：`LocalDateTime` vs `Date` | 为什么项目统一用 `LocalDateTime`？ |

## 2. Spring 生态 ⭐

**项目位置**：`config/`、`interceptor/`、`service/impl/`

| 要掌握 | 自检问题 |
|---|---|
| IoC/DI、Bean 生命周期、循环依赖 | `@Resource` 和 `@Autowired` 区别？ |
| AOP 与事务：`@Transactional` 传播/失效场景 | 为什么 `createVoucherOrder` 用 `TransactionTemplate` 而不是 `@Transactional`？ |
| Spring MVC：DispatcherServlet、拦截器 vs 过滤器 | `TraceIdFilter` 为什么用 Filter 不用 Interceptor？ |
| 自动配置原理（`spring.factories` / `AutoConfiguration.imports`） | actuator 是怎么被自动装配的？ |
| 全局异常处理 `@RestControllerAdvice` | 参数校验失败为什么能被统一转换成 40000？ |

## 3. 持久层 MyBatis-Plus / SQL ⭐

**项目位置**：`entity/`、`mapper/`、`resources/mapper/*.xml`、`db/db.sql`

| 要掌握 | 自检问题 |
|---|---|
| MP 条件构造器、分页、逻辑删除 | `LambdaUpdateWrapper` 与 `UpdateWrapper` 区别？ |
| 主键策略：`IdType.AUTO` / `INPUT` / 雪花 | `tb_voucher_order.id` 为什么是 `INPUT`？（订单号由 Redis 生成） |
| XML 自定义 SQL 与 `useGeneratedKeys` | 注册用户后 `id` 为空的 bug 根因是什么？ |
| 索引：联合索引最左前缀、唯一索引兜底幂等 | `uk_voucher_order_user_voucher` 在秒杀里起什么作用？ |
| 事务隔离级别、MVCC | 秒杀「扣库存 + 建订单」为什么必须同一事务？ |

## 4. MySQL 🔧

- 库表：`db/db.sql`（15 张表）
- 要掌握：`EXPLAIN`、慢查询、`UPDATE ... WHERE stock > 0` 的乐观扣减、唯一索引防重
- 自检：`update ... where stock > 0` 为什么能防止超卖？并发下会不会死锁？

## 5. Redis（本项目用得最重）⭐🔧

**项目位置**：`utils/CacheClient.java`、`utils/RedisConstants.java`、`resources/seckill.lua`、`service/impl/ShopServiceImpl.java`

| 数据结构 | 项目用途 | 自检问题 |
|---|---|---|
| String | 验证码、token 哈希、库存、店铺 JSON | 库存为什么用 String 而不是 Hash？ |
| Hash | `login:token:{token}` | 为什么不直接存一个 JSON 字符串？ |
| Set | `seckill:order:{voucherId}` 防重复下单、关注关系 | Lua 里为什么用 SISMEMBER 而不是查库？ |
| ZSet | 点赞、关注 Feed 收件箱 | 为什么用时间戳做 score？ |
| GEO | `shop:geo:{typeId}` 附近店铺 | GEOSEARCH 的参数含义？ |
| Bitmap | 签到 `sign:{userId}` | 一个月签到为什么只用 4 字节？ |
| Stream | `stream.orders` 异步下单 | 消费组 / PENDING / XACK 分别解决什么？ |
| Lua | `seckill.lua` 原子扣减 + 投递 | 为什么「判断+扣减+发消息」必须在一个脚本里？ |

**缓存三大问题**（`CacheClient` 全部落地）：

| 问题 | 本项目做法 | 自检问题 |
|---|---|---|
| 穿透 | BloomFilter + 空值缓存（2 分钟） | 布隆过滤器误判会导致什么？怎么控制误判率？ |
| 击穿 | Redis 互斥锁 + 双重检查 | 锁超时了业务还没跑完怎么办？ |
| 雪崩 | TTL 加 3–10 分钟随机值 | 随机区间怎么选？ |

**Redisson**：`lock:order:{userId}` 用户级分布式锁
- 自检：Redisson 锁的看门狗机制？和 `SETNX` 手写锁的区别？

## 6. RabbitMQ（本轮新增）⭐🔧

**项目位置**：`mq/RabbitMQConfig.java`、`OrderTimeoutProducer.java`、`OrderTimeoutConsumer.java`、`OrderTimeoutService.java`

| 要掌握 | 自检问题 |
|---|---|
| AMQP 模型：Exchange / Queue / Binding / RoutingKey | 为什么用 DirectExchange？ |
| 延迟队列两种实现：TTL+DLX vs `delayed-message` 插件 | 本项目为什么选 TTL+DLX？消息级 TTL 的队头阻塞问题怎么规避？ |
| 死信队列 DLX 触发条件 | 哪三种情况消息会进死信？ |
| 消息确认：publisher confirm / consumer ack / 手动 ack | 本项目用 auto ack + 本地重试，消息会不会丢？ |
| 重试策略与幂等 | 重复消费同一条超时消息，订单会被取消两次吗？（看 `cancelIfUnpaid` 的 CAS） |
| 消费失败处理 | 重试 3 次仍失败后消息去哪了？怎么排查？ |

**链路**：
```
下单成功 → order.delay.exchange → order.delay.queue（TTL 15min，无消费者）
        → 过期经 DLX → order.cancel.exchange → order.cancel.queue
        → 消费者 CAS: status 1→4，回滚 DB/Redis 库存，移除已下单标记
        → 消费异常重试耗尽 → order.dlx.exchange → order.cancel.dlq
```

**动手验证**（管理台 `http://<vm-ip>:15672`）：
1. 下单后看 `order.delay.queue` 是否有 1 条消息
2. 15 分钟后看它是否进入 `order.cancel.queue`
3. 把订单状态改成 2（已支付）再等，观察消费者日志「订单状态已变更，跳过」

## 7. 分布式与并发 ⭐

- 幂等：DB 唯一索引 + Lua 防重 + 状态 CAS（三处，面试可讲「多层幂等」）
- 分布式锁：Redisson（用户级下单锁）
- 消息异步：Redis Stream + RabbitMQ（两种方案对比）
- 自检：为什么有了 Lua 还要数据库唯一索引？为什么有了锁还要 CAS？

## 8. 可观测性（本轮新增）🔧

**项目位置**：`common/TraceIdFilter.java`、`logback.xml`、`application.yml` 的 `management.*`、`CacheClient` 的计数器

| 要掌握 | 自检问题 |
|---|---|
| Actuator：health / metrics / prometheus | health 里 db/redis 状态怎么来的？ |
| Micrometer：Counter / Gauge / Timer | 缓存命中率怎么算出来？ |
| traceId + MDC | 日志里的 `[traceId]` 怎么串起一次请求？为什么 finally 要 remove？ |
| Prometheus + Grafana（待接）📌 | 如果要画 QPS/延迟面板，需要哪些指标？ |

## 9. 测试 ⭐🔧

**项目位置**：`src/test/java/**`

| 要掌握 | 自检问题 |
|---|---|
| JUnit 5：`@Test`/`@BeforeEach`/断言 | `assertThat` 来自哪个库？ |
| Mockito：`@Mock`/`when`/`verify`/`ArgumentCaptor` | 怎么验证「注册后 id 被写进 token」？ |
| MockMvc standalone | 为什么测异常映射不需要启动 Spring 容器？ |
| 测试分层：单测 vs 集成测试 | 集成测试为什么要用环境变量开关？ |
| CI：GitHub Actions | `mvn verify` 和 `mvn test` 区别？ |

## 10. AI 能力（LangChain4j + MCP）🔧

**项目位置**：`service/ConsultantService.java`、`service/impl/AiSkillServiceImpl.java`、`tools/`、`mcp/`

| 要掌握 | 自检问题 |
|---|---|
| OpenAI 兼容接口（DashScope） | 为什么 `base-url` 指向 DashScope 也能用 OpenAI SDK？ |
| LangChain4j `@AiService` / 工具调用 | 模型如何知道要调用 `ShopTool`？ |
| 流式输出（SSE / text stream） | `/chat` 为什么返回 `text/plain` 流？ |
| MCP 协议：JSON-RPC、tools/list、tools/call | MCP 和普通 REST 接口的本质区别？ |
| 兜底与降级 | 模型不可用时为什么 Skills 还能用？ |

## 11. 前端与部署 🔧

- Nginx：静态托管 + `proxy_pass` 反向代理（`AI-dianping-frontend/conf/nginx.conf`）
- 自检：`rewrite /api(/.*) $1 break;` 这行在做什么？为什么后端收到的是 `/shop/1`？
- Docker / docker-compose 📌（下一步做）
- 云服务器部署：安全组、免备案、`IP:端口` 访问

## 12. 工程化 🔧

| 主题 | 项目位置 |
|---|---|
| 配置脱敏（环境变量 + `.env.example`） | `application.yml`、`local.env.ps1` |
| Git 忽略规则 | `.gitignore`、`.idea/.gitignore` |
| CI | `.github/workflows/ci.yml` |
| 启动/停止脚本 | `start-all.cmd`、`stop-all.cmd` |
| 改造记录 | `docs/refactor-log.md` |

## 13. 建议学习顺序（4 周）

1. **第 1 周**：Redis 全部数据结构 + `CacheClient` 三大问题 + Lua 秒杀脚本（逐行读懂 `seckill.lua`）
2. **第 2 周**：Spring 事务/拦截器/AOP + MyBatis-Plus + 本项目的幂等设计（三处）
3. **第 3 周**：RabbitMQ（管理台手动验证一遍延迟队列）+ 测试（把 `OrderTimeoutServiceTest` 自己重写一遍）
4. **第 4 周**：可观测性 + 部署（Docker Compose）+ 压测（JMeter 打 `/shop/1` 与秒杀）

## 14. 面试高频题清单（本项目可直接答）

1. 缓存穿透/击穿/雪崩分别怎么解决？你的项目里对应哪段代码？
2. 秒杀如何防超卖？Lua 脚本里哪几行是关键？
3. 为什么有了 Redis 库存还要数据库 `stock > 0` 条件？
4. Redis Stream 消费失败怎么处理？PENDING 列表是什么？
5. 订单超时取消为什么用 MQ 而不是定时扫表？两者各有什么优劣？
6. 延迟队列的两种实现方式？消息级 TTL 有什么坑？
7. 消息重复消费怎么保证幂等？你项目里 CAS 是哪一行？
8. 分布式锁为什么用 Redisson？看门狗解决什么问题？
9. 你的项目怎么排查线上问题？traceId 怎么用？
10. 单元测试和集成测试边界怎么划？
