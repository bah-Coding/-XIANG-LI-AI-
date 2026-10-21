# 改造记录（便于逐批 review）

> 每批改动都保持「可编译 + 可启动 + 有验证」，下面按批次记录动机与影响面。

## 批次 1：工程基线（错误码 / 校验 / 可观测性 / traceId）

**动机**：原项目异常一律返回「服务器异常」，没有任何监控指标，日志无法按请求串联。

| 文件 | 说明 |
|---|---|
| `common/ErrorCode.java` | 新增。统一错误码枚举（0 / 40000 / 40100 / 40300 / 40400 / 40900 / 42900 / 50000） |
| `common/BizException.java` | 新增。业务异常携带 ErrorCode，由全局处理器转 Result |
| `common/TraceIdFilter.java` | 新增。请求级 traceId：写入 MDC + 响应头 `X-Trace-Id`，`finally` 清理 |
| `dto/Result.java` | 增加 `code` 字段与 `fail(ErrorCode)` 等工厂；保留旧构造器与 `fail(String)`，前端零改动 |
| `config/WebExceptionAdvice.java` | 重写。BizException / 参数校验 / 类型不匹配 / 缺参 / 请求体错误 / 上传超限 / 兜底 Exception 分类处理 |
| `dto/LoginFormDTO.java`、`dto/ReservationDTO.java` | 增加 `@NotBlank/@Pattern/@NotNull/@Future/@Min` 校验 |
| `controller/UserController.java`、`ReservationController.java` | 参数加 `@Valid` |
| `utils/CacheClient.java` | 注入 `MeterRegistry`，输出 `hmdp_cache_requests_total`、`hmdp_cache_local_size` |
| `pom.xml` | 增加 actuator、micrometer-registry-prometheus |
| `application.yml` | 增加 `management.*`（health/metrics/prometheus） |
| `logback.xml` | 日志模板加入 `[%X{traceId:-}]` |

**验证**：`/actuator/health` UP（含 db/redis）；`/actuator/prometheus` 有缓存指标；非法 `id` 返回 `code=40000`；空手机号返回字段级校验信息；响应头带 `X-Trace-Id`。

**已知取舍**：异常仍返回 HTTP 200 + `success=false`（兼容现有前端）；`/actuator/*` 未加鉴权，公网部署前需限制。

## 批次 2：测试体系（本批）

**动机**：原项目只有 1 个需要真实 MySQL/Redis 的集成测试类，无法在 CI 中运行。

| 文件 | 说明 |
|---|---|
| `src/test/java/com/hmdp/dto/ResultTest.java` | 新增。5 个用例覆盖 Result 各工厂与错误码 |
| `src/test/java/com/hmdp/config/WebExceptionAdviceTest.java` | 新增。4 个用例用 MockMvc standalone 验证异常→错误码映射（业务异常/未知异常/类型不匹配/参数校验） |
| `src/test/java/com/hmdp/service/impl/UserServiceImplTest.java` | 新增。4 个用例（Mockito）验证验证码校验、6 位码写入与 TTL、错误码拒绝登录、新用户注册后把回填 id 写入 token hash |
| `src/test/java/com/hmdp/HmDianPingApplicationTests.java` | 加 `@EnabledIfEnvironmentVariable(RUN_INTEGRATION_TESTS=true)`，默认跳过，避免无外部服务时构建失败 |
| `.github/workflows/ci.yml` | 新增。push/PR 时在 JDK 17 上执行 `mvn verify` 并上传测试报告 |

**验证**：`mvn test` → `Tests run: 15, Failures: 0, Errors: 0, Skipped: 2`（跳过的 2 个是需要 MySQL/Redis 的集成测试）。

**为什么这样分层**：
- 纯单元测试（Result / 异常映射 / 服务逻辑）：快、无依赖，CI 必跑；
- 集成测试（`HmDianPingApplicationTests`）：依赖真实中间件，用环境变量显式开启，本地执行 `$env:RUN_INTEGRATION_TESTS='true'` 后跑。

## 批次 3：订单超时取消（RabbitMQ）

**动机**：`tb_voucher_order.status` 定义了「未支付/已支付/已取消」，但原项目没有超时取消逻辑，订单会永远停在未支付。

| 文件 | 说明 |
|---|---|
| `pom.xml` | 新增 `spring-boot-starter-amqp` |
| `application.yml` | 新增 `spring.rabbitmq.*`（全部环境变量）、`hmdp.order.timeout-ms`；MQ 关闭时禁用 rabbit 健康检查 |
| `mq/RabbitMQConfig.java` | 延迟交换机/队列、取消交换机/队列、死信交换机/DLQ、JSON 消息转换器 |
| `mq/OrderTimeoutMessage.java` | 消息体（orderId/voucherId/userId/createTime） |
| `mq/OrderTimeoutProducer.java` | 下单成功后投递带 TTL 的消息；`RABBITMQ_ENABLED=false` 时跳过 |
| `mq/OrderTimeoutConsumer.java` | `@RabbitListener` 消费取消队列；失败重试 3 次后进入 DLQ |
| `mq/OrderTimeoutService.java` | 状态 CAS 取消 + 回滚 DB/Redis 库存 + 移除 `seckill:order` 标记 |
| `utils/RedisConstants.java` | 新增 `SECKILL_ORDER_KEY` |
| `service/impl/VoucherOrderServiceImpl.java` | 事务提交成功后发送超时消息（发送失败只记日志，不影响下单） |
| `src/test/java/com/hmdp/mq/OrderTimeoutServiceTest.java` | 4 个用例：订单不存在 / 状态已变跳过 / 未支付取消并回滚 / null 入参；含 MP `TableInfo` 初始化 |

**验证**：`Tests run: 19, Failures: 0`（2 个集成测试默认跳过）；真实 RabbitMQ 端到端：TTL=8s，订单 status 1→4，库存 9→10，`seckill:order:10` 清空，三个队列清空。

**设计取舍**：
- 用「消息级 TTL + DLX」而不是 delayed-message 插件（少一个插件依赖），TTL 统一所以无队头阻塞；
- 消息发送放在事务提交之后：发送失败只记录日志，不阻塞下单（后续可加本地消息表做最终一致）；
- 幂等靠 `UPDATE ... WHERE status=1` 的 CAS，重复消息无副作用。

## 批次 4：部署工程化 + 安全加固 + 压测

**动机**：项目只能在本机手工启动；`/mcp`、`/actuator` 无鉴权；发验证码接口可被脚本刷；没有任何性能数据。

| 文件 | 说明 |
|---|---|
| `AI-dianping-backend/Dockerfile` | 多阶段构建：Maven 构建 → JRE 运行，依赖层缓存 |
| `AI-dianping-backend/.dockerignore` | 排除 target、日志、IDE 文件 |
| `docker-compose.yml` | MySQL / Redis / RabbitMQ / 后端 / Nginx 五件套，含健康检查、数据卷、上传目录双挂载 |
| `deploy/nginx-docker.conf` | 容器内 Nginx：静态页 + `/api` 反代到 `backend:8081` |
| `.env.docker.example` | compose 环境变量模板 |
| `common/ApiKeyAuthFilter.java` | 为 `/mcp`、`/actuator` 增加 `X-API-Key` 鉴权（Filter 层，覆盖 Servlet/Actuator） |
| `service/impl/UserServiceImpl.java` | 验证码限流：同手机号 60s 一次 + 同 IP 20 次/小时（Redis INCR+EXPIRE，固定窗口） |
| `utils/RedisConstants.java` | 新增限流相关 key 与阈值 |
| `scripts/loadtest.ps1` | PowerShell 7 并发压测脚本（HttpClient 复连、P50/P95/P99、错误率、状态码分布） |
| `docs/tutorial-deploy-security-loadtest.md` | 三部分教学文档（概念、实现、实测、误区、自检题） |

**验证**：
- 安全：`/actuator/health` 无 key 401、带 key 200；`/mcp` 无 key 401；验证码同号连发第二次返回 `42900`
- 压测（Nginx → 后端，缓存命中路径）：20 并发 5s → 1197 QPS / P99 29.6ms；100 并发 10s → 1529 QPS / P99 95.1ms / 0 错误
- 测试：`Tests run: 20, Failures: 0`（新增验证码限流用例）

**已知边界**：本机未安装 Docker，compose 未实跑（语法按官方规范）；压测为本机回环，数字不能当生产容量。

## 批次 5：全链路排查与缺陷修复

**动机**：上线/开源前对前后端做一次系统性冒烟，修复接口语义不清与前后端不一致的问题。

| 问题 | 修复 |
|---|---|
| `sortBy` 参数后端不接收，人气/评分点击无效；前端只用 `concat` 追加导致"点了没反应" | 后端支持 `sortBy=distance|comments|score`；前端排序时重置分页与列表 |
| 距离单位错位（后端公里、前端按米显示，170m 显示成 0.2米） | 后端统一折算成米返回；人气/评分结果用 Haversine 补距离 |
| 「不存在」类业务失败返回 50000 | 统一改用 `ErrorCode.NOT_FOUND(40400)`：店铺/笔记/预约/类型/AI 技能 |
| 预约重复、非法参数等语义弱 | 重复预约改 40900，参数错误改 40000，未登录改 40100 |
| 图片删除接口带 `/imgs` 前缀时找不到文件（实际删不掉） | 落盘前剥离 `/imgs`，并增加目录穿越校验 |
| 未配置 `API_KEY` 时 `/chat` 直接 500「服务器异常」 | `onErrorResume` 返回可读提示，说明如何配置 |
| 「编辑资料」页面只有展示，没有保存（后端也无接口） | 新增 `PUT /user/info`（昵称写 tb_user 并同步 Redis，其余写 tb_user_info）；页面支持点击编辑 |
| `UserInfo.userId` 误用 `IdType.AUTO`（表里不是自增列） | 改为 `IdType.INPUT` |

**验证**：接口冒烟 20+ 个（读/写/MCP/流式）；40400、40900、40000 语义正确；上传→删除文件真实消失；资料更新后 `/user/me` 昵称同步；20 个单元测试通过。

## 批次 6：品牌重做（去「黑马味」）+ 注释归一

**动机**：原前端与课程项目高度相似，直接放进简历辨识度过高。

**前端**
| 改动 | 说明 |
|---|---|
| 品牌 | 产品名改为「巷里 XIANG LI」，标题/协议/登录页全部替换，新增品牌标识 |
| 视觉系统 | 新增 `css/xiangli.css`：米白纸张底色、墨绿主色、衬线标题、大圆角卡片、柔和阴影；覆盖 Element 主按钮/输入框/标签/分页条 |
| 首页 | 完全重写：品牌栏 + 衬线大标题 + 搜索 + 分类宫格 + 双列动态卡片 |
| 登录页 | 完全重写：品牌卡片、手机号 + 验证码、协议勾选、密码登录入口 |
| 底部导航 | 重做为「首页 / 附近 / 发布 / AI 助手 / 我的」，图标与配色统一 |
| 去橙色 | 全站 `#ff6633 / #F63 / #ff7a45` 等黑马橙替换为主题绿；券按钮、榜单标签、折扣标签、AI 悬浮球同步改色 |
| 素材 | 「点评榜单」图片徽章替换为文本徽章「本地榜单」 |

**后端**
| 改动 | 说明 |
|---|---|
| 注释 | 53 个 Java 文件的 `@author` 统一为 `bzh`，`@since` 统一为 `2026-08-02` |

**验证**：Chrome headless 截图逐一检查首页/登录/列表/详情（截图存放在 `docs/screenshots/`）；全部页面 200；后端 `mvn compile` 通过。

**遗留**：Java 包名 `com.hmdp`、artifactId `AI-dianping-backend`、目录名 `AI-dianping-*` 仍带原名缩写，彻底改名工作量大，列入待办。

## 批次 7：体验细节修复（遮挡 / 假数据 / 按钮）

| 问题 | 修复 |
|---|---|
| 首页底部导航遮挡动态卡片 | `.xl-home` 改 flex 纵向布局，动态区 `flex:1 + min-height:0` 内部滚动，页面底部预留 72px，卡片不再被底栏压住 |
| 城市定位写死「杭州」且不可修改 | 首页删除城市胶囊；个人中心城市改为读取 `info.city`；他人主页移除硬编码城市；发笔记弹窗删除装饰性城市选择 |
| 关注按钮显示为纯文本（点击出现光标竖线） | 改用 `<button class="follow-btn">` 胶囊按钮（未关注=实心绿，已关注=描边），`user-select:none` 防止选中文本 |
| 店铺/笔记详情伪造「网友评价 119 条」「查看全部 119 条评价」 | 两处占位评论块整体删除（后端无评论接口，不做假数据） |
| 店铺详情底部 `copyright ©2021 hmdp.com` | 替换为「© 2026 巷里 XIANG LI · 本地生活实验室」 |

**验证**：无头浏览器截图确认首页滚动区结束于底栏之上、详情页无假评论、关注按钮为胶囊样式；临时注入登录态的 `_seed.html` 截图后已删除；所有页面 200。

## 批次 8：关注/粉丝列表与首页动态可读性

| 问题 | 修复 |
|---|---|
| 关注用户后，个人主页「关注」没有列表；被关注者主页也没有「粉丝」列表 | 新增后端接口 `GET /follow/follows/{id}`、`/follow/fans/{id}`、`/follow/count/{id}`（返回 UserDTO 列表与数量）；`info.html` 与 `other-info.html` 的粉丝/关注 Tab 改为真实用户列表，可点击进入对方主页；`info.html` 支持 `?tab=3/4` 直接定位 |
| 首页动态卡片被压成图片条，看不出内容 | 动态区改为**单列大卡**：16:9 图片 + 两行标题 + 两行摘要（由正文去标签截取）+ 作者/点赞；网格 `grid-auto-rows:min-content` + `align-content:start` 防止行高被压缩 |

**验证**：接口实测（关注用户 2 后：`follows/1024` 返回用户 2，`fans/2` 返回 1024，count 分别为 `followee=1,fans=0` 与 `followee=0,fans=1`）；截图确认「关注(1)」列表渲染、首页大卡可读。

**追加优化（顶部空间压缩）**：品牌栏 padding 14→10、标题 30→26px、搜索框 46→42px、区块间距 22→14px、分类卡片图标 38→30px 且内边距收紧，动态区整体上移约 120px，首屏可完整看到一张动态卡片。

## 待办（下一批候选）

- 秒杀工程化：死信队列、库存对账、重试上限、压测脚本
- 部署工程化：Dockerfile + docker-compose（MySQL/Redis/后端/Nginx）
- 安全：`/mcp`、`/actuator` 鉴权；登录验证码限流
- 数据规模：生成 10 万级店铺数据用于压测
