# -*- coding: utf-8 -*-
"""生成 hmdp-Java 技术栈教学手册（DOCX）。"""

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

OUT = r"E:\hmdp-Java\docs\hmdp-Java-技术栈教学手册.docx"

CN_FONT = "微软雅黑"
CN_BODY = "宋体"
EN_FONT = "Segoe UI"
CODE_FONT = "Consolas"

ACCENT = RGBColor(0x1F, 0x4E, 0x79)
MONO_BG = "F2F4F7"


def set_style_font(style, latin, eastasia, size=None, bold=None, color=None):
    style.font.name = latin
    rpr = style.element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), latin)
    rfonts.set(qn("w:hAnsi"), latin)
    rfonts.set(qn("w:eastAsia"), eastasia)
    if size is not None:
        style.font.size = Pt(size)
    if bold is not None:
        style.font.bold = bold
    if color is not None:
        style.font.color.rgb = color


def shade(element, fill):
    ppr = element.get_or_add_pPr() if hasattr(element, "get_or_add_pPr") else element
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    ppr.append(shd)


def cell_shade(cell, fill):
    tcpr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    tcpr.append(shd)


def add_page_number(paragraph):
    run = paragraph.add_run()
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = "PAGE"
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run._r.append(fld_begin)
    run._r.append(instr)
    run._r.append(fld_end)


class Manual:
    def __init__(self):
        self.doc = Document()
        self._h1_count = 0
        self._setup_page()
        self._setup_styles()
        self._setup_footer()

    def _setup_page(self):
        section = self.doc.sections[0]
        section.page_width = Cm(21.0)
        section.page_height = Cm(29.7)
        section.top_margin = Cm(2.2)
        section.bottom_margin = Cm(2.0)
        section.left_margin = Cm(2.4)
        section.right_margin = Cm(2.2)

    def _setup_styles(self):
        normal = self.doc.styles["Normal"]
        set_style_font(normal, EN_FONT, CN_BODY, size=10.5)
        normal.paragraph_format.line_spacing = 1.45
        normal.paragraph_format.space_after = Pt(4)

        for name, size, color in (
            ("Heading 1", 18, ACCENT),
            ("Heading 2", 14, RGBColor(0x2E, 0x5C, 0x8A)),
            ("Heading 3", 12, RGBColor(0x33, 0x33, 0x33)),
        ):
            style = self.doc.styles[name]
            set_style_font(style, CN_FONT, CN_FONT, size=size, bold=True, color=color)
            style.paragraph_format.space_before = Pt(12 if name != "Heading 1" else 18)
            style.paragraph_format.space_after = Pt(6)
            style.paragraph_format.keep_with_next = True

        code = self.doc.styles.add_style("CodeBlock", 1)
        code.base_style = self.doc.styles["Normal"]
        set_style_font(code, CODE_FONT, CN_BODY, size=8.5)
        code.paragraph_format.line_spacing = 1.05
        code.paragraph_format.space_before = Pt(3)
        code.paragraph_format.space_after = Pt(6)
        code.paragraph_format.left_indent = Cm(0.2)

        note = self.doc.styles.add_style("NoteBlock", 1)
        note.base_style = self.doc.styles["Normal"]
        set_style_font(note, EN_FONT, CN_BODY, size=9.5)
        note.paragraph_format.left_indent = Cm(0.4)
        note.paragraph_format.space_after = Pt(6)

    def _setup_footer(self):
        footer = self.doc.sections[0].footer
        p = footer.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = p.add_run("hmdp-Java 技术栈教学手册    ·    ")
        run.font.size = Pt(8)
        run.font.color.rgb = RGBColor(0x88, 0x88, 0x88)
        add_page_number(p)

    # ---------- building blocks ----------
    def title_page(self):
        for _ in range(4):
            self.doc.add_paragraph()
        p = self.doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = p.add_run("hmdp-Java")
        run.font.size = Pt(40)
        run.font.bold = True
        run.font.color.rgb = ACCENT
        run.font.name = CN_FONT
        run._element.rPr.rFonts.set(qn("w:eastAsia"), CN_FONT)

        p2 = self.doc.add_paragraph()
        p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run2 = p2.add_run("技术栈教学手册")
        run2.font.size = Pt(28)
        run2.font.bold = True
        run2.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
        run2.font.name = CN_FONT
        run2._element.rPr.rFonts.set(qn("w:eastAsia"), CN_FONT)

        p3 = self.doc.add_paragraph()
        p3.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run3 = p3.add_run("按项目结构讲解 · 含部署工程化 / 安全加固 / 压测")
        run3.font.size = Pt(12)
        run3.font.color.rgb = RGBColor(0x66, 0x66, 0x66)
        run3.font.name = CN_FONT
        run3._element.rPr.rFonts.set(qn("w:eastAsia"), CN_FONT)

        self.doc.add_paragraph()
        line = self.doc.add_paragraph()
        line.alignment = WD_ALIGN_PARAGRAPH.CENTER
        line.add_run("—" * 26).font.color.rgb = RGBColor(0xBB, 0xBB, 0xBB)

        info = self.doc.add_paragraph()
        info.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = info.add_run(
            "Spring Boot 3.5.6 · JDK 17 · MySQL 8 · Redis 6 · RabbitMQ 3 · "
            "LangChain4j · MCP · Nginx · Docker"
        )
        r.font.size = Pt(9.5)
        r.font.color.rgb = RGBColor(0x77, 0x77, 0x77)

        meta = self.doc.add_paragraph()
        meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
        m = meta.add_run("2026-10-21  ·  版本 1.0")
        m.font.size = Pt(9.5)
        m.font.color.rgb = RGBColor(0x99, 0x99, 0x99)

        self.doc.add_page_break()

    def toc(self, items):
        self.doc.add_heading("目录", level=1)
        for idx, (level, text) in enumerate(items, 1):
            p = self.doc.add_paragraph()
            p.paragraph_format.space_after = Pt(2)
            p.paragraph_format.left_indent = Cm(0 if level == 1 else 0.8)
            run = p.add_run(("" if level == 1 else "• ") + text)
            run.font.size = Pt(10 if level == 1 else 9.5)
            run.font.bold = level == 1
            if level == 1:
                run.font.color.rgb = ACCENT
        self.doc.add_page_break()

    def h1(self, text):
        if self._h1_count > 0:
            self.doc.add_page_break()
        self._h1_count += 1
        self.doc.add_heading(text, level=1)

    def h2(self, text):
        self.doc.add_heading(text, level=2)

    def h3(self, text):
        self.doc.add_heading(text, level=3)

    def p(self, text, bold_prefix=None):
        p = self.doc.add_paragraph()
        if bold_prefix:
            run = p.add_run(bold_prefix)
            run.bold = True
        p.add_run(text)
        return p

    def bullets(self, items, numbered=False):
        style = "List Number" if numbered else "List Bullet"
        for item in items:
            p = self.doc.add_paragraph(item, style=style)
            p.paragraph_format.space_after = Pt(2)

    def note(self, text):
        p = self.doc.add_paragraph(style="NoteBlock")
        run = p.add_run("提示  ")
        run.bold = True
        run.font.color.rgb = RGBColor(0xC0, 0x50, 0x00)
        p.add_run(text)
        shade(p._p, "FFF6E5")

    def code(self, text, size=8.5):
        lines = text.strip("\n").split("\n")
        for idx, line in enumerate(lines):
            p = self.doc.add_paragraph(line if line else " ", style="CodeBlock")
            for run in p.runs:
                run.font.size = Pt(size)
            shade(p._p, MONO_BG)
            pf = p.paragraph_format
            pf.keep_together = True
            if idx < len(lines) - 1:
                pf.keep_with_next = True

    def table(self, headers, rows, widths=None):
        t = self.doc.add_table(rows=1, cols=len(headers))
        t.style = "Table Grid"
        t.alignment = WD_TABLE_ALIGNMENT.CENTER
        hdr = t.rows[0].cells
        for i, h in enumerate(headers):
            hdr[i].text = ""
            para = hdr[i].paragraphs[0]
            run = para.add_run(h)
            run.bold = True
            run.font.size = Pt(9)
            cell_shade(hdr[i], "E8EEF6")
        for row in rows:
            cells = t.add_row().cells
            for i, value in enumerate(row):
                cells[i].text = ""
                para = cells[i].paragraphs[0]
                run = para.add_run(str(value))
                run.font.size = Pt(9)
                para.paragraph_format.space_after = Pt(1)
        if widths:
            for row in t.rows:
                for i, w in enumerate(widths):
                    row.cells[i].width = Cm(w)
        # 表头跨页重复 + 行不跨页拆分
        header_tr_pr = t.rows[0]._tr.get_or_add_trPr()
        header_tr_pr.append(OxmlElement("w:tblHeader"))
        for row in t.rows:
            row._tr.get_or_add_trPr().append(OxmlElement("w:cantSplit"))
        self.doc.add_paragraph().paragraph_format.space_after = Pt(2)
        return t

    def save(self):
        self.doc.save(OUT)


def build():
    m = Manual()
    m.title_page()

    m.toc([
        (1, "第一章 项目全景"),
        (1, "第二章 基础设施：JDK、Maven、Spring Boot 与配置"),
        (1, "第三章 Web 层：MVC、拦截器、统一响应与异常"),
        (1, "第四章 持久层：MyBatis-Plus 与 MySQL"),
        (1, "第五章 Redis 与多级缓存"),
        (1, "第六章 消息队列：RabbitMQ 与订单超时"),
        (1, "第七章 AI 能力：LangChain4j 与 MCP"),
        (1, "第八章 前端与 Nginx"),
        (1, "第九章 测试体系与 CI"),
        (1, "第十章 可观测性"),
        (1, "第十一章 部署工程化：Docker 与 Compose"),
        (1, "第十二章 安全加固"),
        (1, "第十三章 压测"),
        (1, "第十四章 改造记录"),
        (1, "第十五章 学习路径与自检题"),
        (1, "附录 A 环境变量速查"),
        (1, "附录 B 常用命令"),
        (1, "附录 C 关键文件索引"),
    ])

    # ---------------- 第一章 ----------------
    m.h1("第一章 项目全景")

    m.h2("1.1 项目定位")
    m.p("hmdp-Java 是一个本地生活业务平台：在「黑马点评」业务底座（店铺、探店笔记、优惠券、秒杀、预约）之上，"
        "叠加了 AI Skills、AI 智能客服与 MCP Server 三个 AI 能力，并完成了工程化改造"
        "（错误码、参数校验、可观测性、消息队列、测试、容器化、安全、压测）。")

    m.p("与代码位置：", "业务模块")
    m.table(
        ["模块", "核心接口", "实现位置"],
        [
            ["用户", "发送验证码、登录、登出、签到", "controller/UserController、service/impl/UserServiceImpl"],
            ["店铺", "详情、按类型（附近）、搜索、类型列表", "controller/ShopController、ShopServiceImpl"],
            ["探店笔记", "发布、热门、详情、点赞、关注流", "controller/BlogController、BlogServiceImpl"],
            ["社交关注", "关注/取关、是否关注、共同关注", "controller/FollowController、FollowServiceImpl"],
            ["优惠券与秒杀", "券列表、新增秒杀券、秒杀下单", "controller/VoucherController、VoucherOrderServiceImpl"],
            ["预约", "创建、详情、我的预约", "controller/ReservationController、ReservationServiceImpl"],
            ["AI Skills", "技能列表/详情/执行/记录", "controller/AiSkillController、AiSkillServiceImpl"],
            ["AI 客服", "流式对话 + 工具调用", "controller/ChatController、ConsultantService"],
            ["MCP Server", "tools/list、tools/call", "mcp/config、mcp/ 包"],
        ],
        widths=[3.2, 6.2, 6.6],
    )

    m.h2("1.2 总体架构")
    m.code("""
浏览器
  |  GET /api/shop/1
  v
Nginx :8080    (静态页 html/hmdp + /api 反向代理)
  |  /shop/1
  v
Spring Boot :8081    (Controller -> Service -> Mapper)
  |
  +-- MySQL 8        业务数据
  +-- Redis 6        缓存 / GEO / Stream / 位图 / 分布式锁
  +-- RabbitMQ 3     订单超时（延迟队列 + 死信队列）
  +-- DashScope      大模型（OpenAI 兼容接口）
""", size=8)

    m.h2("1.3 技术栈总览")
    m.table(
        ["层次", "技术", "版本", "在本项目中的作用"],
        [
            ["语言/构建", "JDK / Maven", "17 / 3.9", "编译与依赖管理"],
            ["框架", "Spring Boot", "3.5.6", "Web、自动配置、事务、Actuator"],
            ["持久层", "MyBatis-Plus", "3.5.14", "CRUD、条件构造器、分页"],
            ["数据库", "MySQL", "8.0.34", "用户/店铺/笔记/券/订单/技能"],
            ["缓存", "Redis", "6.2.22", "验证码、Token、缓存、GEO、ZSet、Bitmap、Stream"],
            ["分布式锁", "Redisson", "3.27.2", "秒杀用户级下单锁"],
            ["本地缓存", "Guava Cache + BloomFilter", "32.1.3", "二级缓存与防穿透"],
            ["消息队列", "RabbitMQ", "3.x", "订单超时取消（TTL+DLX+DLQ）"],
            ["AI", "LangChain4j", "1.0.1 / 1.0.1-beta6", "Skills、流式客服、工具调用"],
            ["AI 协议", "MCP Java SDK", "0.12.1", "对外暴露 11 个业务工具"],
            ["前端", "Vue 2 + Element UI（静态）", "—", "由 Nginx 直接托管"],
            ["网关/静态", "Nginx", "1.18（本机）/ 1.27（容器）", "静态资源 + 反向代理"],
            ["可观测", "Actuator + Micrometer", "Boot 内置", "健康检查、Prometheus 指标"],
            ["测试", "JUnit 5 + Mockito + MockMvc", "Boot 内置", "单元测试与 Web 层测试"],
            ["CI/CD", "GitHub Actions", "—", "push/PR 自动 mvn verify"],
            ["部署", "Docker + Compose", "—", "五件套一键启动"],
        ],
        widths=[2.6, 4.4, 3.4, 5.6],
    )

    m.h2("1.4 项目结构")
    m.code("""
hmdp-Java/
|-- AI-dianping-backend/            # Spring Boot 后端
|   |-- Dockerfile                  # 多阶段构建
|   |-- pom.xml
|   `-- src/main/java/com/hmdp/
|       |-- common/                 # ErrorCode / BizException / TraceIdFilter / ApiKeyAuthFilter
|       |-- config/                 # MVC、Redis、Redisson、异常处理、初始化器
|       |-- controller/             # 9 个 REST 控制器
|       |-- dto/                    # Result、登录表单、预约 DTO
|       |-- entity/                 # 13 个实体（与表对应）
|       |-- interceptor/            # 刷新 Token / 登录校验
|       |-- mapper/                 # MyBatis-Plus Mapper
|       |-- mcp/                    # MCP Server 与工具注册
|       |-- mq/                     # RabbitMQ 配置、生产者、消费者、取消服务
|       |-- service/                # 业务接口与实现
|       |-- tools/                  # LangChain4j 客服工具
|       `-- utils/                  # 缓存、锁、常量、用户上下文、ID 生成
|   `-- src/main/resources/
|       |-- application.yml         # 全部连接信息走环境变量
|       |-- logback.xml             # 日志（含 traceId）
|       |-- seckill.lua             # 秒杀原子脚本
|       `-- mapper/*.xml            # 自定义 SQL
|-- AI-dianping-frontend/           # 前端（Nginx 托管）
|   |-- conf/nginx.conf
|   `-- html/hmdp/                  # 页面 + css/js/imgs
|-- db/db.sql                       # 建表与种子数据（15 张表）
|-- deploy/nginx-docker.conf        # 容器内 Nginx 配置
|-- docker-compose.yml              # MySQL/Redis/RabbitMQ/后端/Nginx
|-- scripts/loadtest.ps1            # 压测脚本
|-- docs/                           # 教学与改造文档
|-- .github/workflows/ci.yml        # CI
`-- start-all.cmd / stop-all.cmd    # 本机启停脚本
""", size=7.8)

    m.h2("1.5 一次请求的完整链路（以 /shop/1 为例）")
    m.bullets([
        "浏览器请求 http://localhost:8080/api/shop/1 —— 命中 Nginx 的 /api location。",
        "Nginx 用 rewrite 去掉 /api 前缀，proxy_pass 到 127.0.0.1:8081/shop/1。",
        "请求进入 Spring MVC：TraceIdFilter 生成 traceId（写入 MDC 与响应头），ApiKeyAuthFilter 只拦 /mcp 与 /actuator，直接放行。",
        "RefreshTokenInterceptor（order 0）解析 authorization 头，从 Redis 取出用户写入 ThreadLocal。",
        "LoginInterceptor（order 1）：/shop/** 在排除列表内，直接放行；其它接口若无用户则返回 401。",
        "ShopController 调 ShopServiceImpl.queryById：布隆过滤器 → Guava 本地缓存 → Redis → 互斥锁 + 双重检查 → MySQL。",
        "结果封装为 Result（success/code/data）返回；拦截器 afterCompletion 清理 ThreadLocal 与 MDC。",
    ])

    # ---------------- 第二章 ----------------
    m.h1("第二章 基础设施：JDK、Maven、Spring Boot 与配置")

    m.h2("2.1 JDK 17 与 Maven")
    m.p("项目使用 JDK 17（LTS）：Spring Boot 3.x 要求 JDK 17+；用到 record、文本块、switch 表达式等特性。"
        "Maven 负责依赖与生命周期（compile → test → package → verify）。")
    m.table(
        ["关注点", "说明"],
        [
            ["pom 关键配置", "spring-boot-starter-parent 3.5.6；java.version=17；mcp-bom 统一 MCP 版本"],
            ["依赖范围", "mysql-connector-j 为 runtime；lombok 为 optional；测试依赖 scope=test"],
            ["构建命令", "mvn clean package -DskipTests / mvn test / mvn verify"],
            ["本地仓库", "本机配置在 settings.xml 与 localRepository（不在仓库里）"],
        ],
        widths=[3.4, 12.4],
    )
    m.note("Maven 的 -D 参数在 PowerShell 里可能被拆分，脚本中用 cmd /c 包裹执行更稳妥。")

    m.h2("2.2 Spring Boot 自动配置与启动流程")
    m.bullets([
        "读取 application.yml 与环境变量，完成 Environment 装配。",
        "SpringApplication 创建上下文，扫描 @Component/@Service/@Configuration。",
        "自动配置：WebMvc、DataSource（Hikari）、Redis（Lettuce）、RabbitMQ、Actuator、MyBatis-Plus。",
        "@PostConstruct 初始化：秒杀库存预热到 Redis、创建 Stream 消费组、启动单线程消费线程。",
        "RabbitListener 容器启动、Tomcat 监听 8081。",
    ], numbered=True)

    m.h2("2.3 配置管理：环境变量 + 脱敏")
    m.p("所有连接信息在 application.yml 中都是占位默认值，真实值来自环境变量；本机用 local.env.ps1 注入，"
        "线上用容器环境变量或 .env。这样仓库里没有明文密码。")
    m.table(
        ["变量", "默认值", "用途"],
        [
            ["DB_URL / DB_USERNAME / DB_PASSWORD", "localhost:3306/hmdp / root / 空", "MySQL 连接"],
            ["REDIS_HOST / REDIS_PORT / REDIS_PASSWORD", "localhost / 6379 / 空", "Redis 连接"],
            ["RABBITMQ_HOST/PORT/USERNAME/PASSWORD", "localhost / 5672 / guest / guest", "RabbitMQ 连接"],
            ["RABBITMQ_ENABLED", "true", "false 时不起监听、不投递"],
            ["ORDER_TIMEOUT_MS", "900000", "订单超时毫秒数"],
            ["MCP_API_KEY", "空（不鉴权）", "/mcp 与 /actuator 的访问密钥"],
            ["IMAGE_UPLOAD_DIR", "imgs/", "图片上传目录"],
            ["LOG_PATH", "logs/backend", "日志目录（logback 只认系统属性，由脚本转 -D）"],
            ["API_KEY", "sk-not-configured", "大模型 Key，空则 AI 走兜底"],
        ],
        widths=[6.2, 4.6, 5.0],
    )

    m.h2("2.4 日志与 traceId")
    m.p("logback 配置了控制台与滚动文件两个 appender，文件名按天滚动、单文件 10MB、保留 30 天。"
        "日志模板里加了 [%X{traceId:-}]，配合 TraceIdFilter 把一次请求的所有日志串起来。")
    m.code("""
# logback.xml（节选）
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId:-}] %-5level %logger{50}-%msg%n</pattern>
<FileNamePattern>${LOG_PATH:-logs/backend}/hmdp-%d{yyyy-MM-dd}-%i.log</FileNamePattern>
""")
    m.note("logback 只读系统属性，不读环境变量，所以启动脚本会把 LOG_PATH 转成 -DLOG_PATH 传入。")

    # ---------------- 第三章 ----------------
    m.h1("第三章 Web 层：MVC、拦截器、统一响应与异常")

    m.h2("3.1 Spring MVC 请求处理")
    m.p("请求依次经过：Filter 链 → DispatcherServlet → HandlerMapping 找到 Controller → HandlerInterceptor 前置 → "
        "参数解析与校验 → Controller → 返回值转换（Jackson）→ 后置/完成回调。")

    m.h2("3.2 过滤器与拦截器")
    m.table(
        ["组件", "层", "职责", "顺序"],
        [
            ["TraceIdFilter", "Filter", "生成/透传 traceId，写 MDC 与响应头", "HIGHEST_PRECEDENCE"],
            ["ApiKeyAuthFilter", "Filter", "校验 /mcp、/actuator 的 X-API-Key", "HIGHEST_PRECEDENCE+10"],
            ["RefreshTokenInterceptor", "Interceptor", "解析 token，刷新有效期，写 ThreadLocal", "order 0"],
            ["LoginInterceptor", "Interceptor", "无用户则 401；afterCompletion 清理 ThreadLocal", "order 1"],
        ],
        widths=[4.0, 2.4, 7.6, 3.4],
    )
    m.p("为什么 /mcp 与 /actuator 要用 Filter？因为 /mcp 是 Servlet 注册、/actuator 走独立 HandlerMapping，"
        "MVC 拦截器覆盖不到，只有 Filter 能保证所有请求都经过。", "关键区别。")

    m.h2("3.3 统一响应与错误码")
    m.code("""
public class Result {
    private Boolean success;   // 兼容前端：前端只判断它
    private Integer code;      // 0 / 40000 / 40100 / 40300 / 40400 / 40900 / 42900 / 50000
    private String  errorMsg;
    private Object  data;
    private Long    total;
}
""")
    m.table(
        ["错误码", "含义", "典型场景"],
        [
            ["0", "成功", "所有正常响应"],
            ["40000", "参数错误", "手机号为空、id 不是数字、预约时间早于当前"],
            ["40100", "未登录/未授权", "缺少 token、/actuator 缺少 API Key"],
            ["40300", "无权限", "预留（属主校验等）"],
            ["40400", "资源不存在", "业务异常示例"],
            ["40900", "资源冲突", "重复预约、优惠券抢光"],
            ["42900", "请求过于频繁", "验证码限流"],
            ["50000", "服务器异常", "未预期异常兜底"],
        ],
        widths=[2.8, 4.6, 8.4],
    )
    m.note("为了兼容现有前端，异常仍返回 HTTP 200 + success=false，只在 body 里增加 code；改成 401/400 需要前后端一起调整。")

    m.h2("3.4 全局异常处理")
    m.p("WebExceptionAdvice 用 @RestControllerAdvice 把不同异常映射为带错误码的 Result：")
    m.table(
        ["异常类型", "映射结果"],
        [
            ["BizException", "使用异常自带的 ErrorCode 与消息，日志用 warn 不打堆栈"],
            ["MethodArgumentNotValidException / BindException", "40000 + 字段级错误信息（如 phone 手机号格式不正确）"],
            ["ConstraintViolationException", "40000 + 约束路径"],
            ["MissingServletRequestParameterException / MethodArgumentTypeMismatchException / HttpMessageNotReadableException", "40000 + 参数名（如 /user/info/undefined → id 格式不正确）"],
            ["MaxUploadSizeExceededException", "40000 + 文件大小超出限制"],
            ["其它 Exception", "50000 + 服务器异常，日志记录完整堆栈"],
        ],
        widths=[8.0, 7.8],
    )

    m.h2("3.5 参数校验")
    m.code("""
public class LoginFormDTO {
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
// Controller：public Result login(@Valid @RequestBody LoginFormDTO form, ...)
""")

    # ---------------- 第四章 ----------------
    m.h1("第四章 持久层：MyBatis-Plus 与 MySQL")

    m.h2("4.1 MyBatis-Plus 要点")
    m.bullets([
        "实体用 @TableName 绑定表、@TableId 指定主键策略，@Accessors(chain=true) 支持链式赋值。",
        "Mapper 继承 BaseMapper 获得 CRUD；复杂 SQL 写在 resources/mapper/*.xml。",
        "条件构造器：LambdaQueryWrapper/LambdaUpdateWrapper 用方法引用代替字符串字段名，避免写错列名。",
        "分页依赖 mp 的拦截器（本项目主要用 limit 与手写分页）。",
    ])

    m.p("主键策略对照：", "易考点。")
    m.table(
        ["实体", "策略", "原因"],
        [
            ["User / Shop / Blog / Voucher", "IdType.AUTO", "数据库自增"],
            ["VoucherOrder", "IdType.INPUT", "订单号由 RedisIdWorker 生成（全局唯一、趋势递增）"],
            ["SeckillVoucher", "IdType.INPUT", "用 voucher_id 作为主键，一对一"],
        ],
        widths=[4.6, 3.2, 8.0],
    )

    m.h2("4.2 一个真实 Bug：注册后 id 为空")
    m.p("原项目 UserMapper.xml 的自定义 insert 没有配置 useGeneratedKeys，插入后 user.id 仍是 null，"
        "登录写 Redis 的 id 为空，个人主页拼出 /user/info/undefined 导致 500。")
    m.code("""
<!-- 修复前 -->
<insert id="save">
    insert into tb_user (phone, nick_name, create_time, update_time) values (...)
</insert>

<!-- 修复后：把自增主键回填到实体 -->
<insert id="save" useGeneratedKeys="true" keyProperty="id">
    insert into tb_user (phone, nick_name, create_time, update_time) values (...)
</insert>
""")
    m.note("这个案例已固化为单元测试：loginShouldCreateUserAndStoreBackfilledIdInTokenHash。")

    m.h2("4.3 MySQL 设计与索引")
    m.p("共 15 张表：用户（tb_user、tb_user_info）、店铺（tb_shop、tb_shop_type）、内容（tb_blog、tb_blog_comments）、"
        "社交（tb_follow）、券与订单（tb_voucher、tb_seckill_voucher、tb_voucher_order）、签到（tb_sign）、"
        "预约（tb_reservation）、AI（tb_ai_skill、tb_ai_skill_run）。")
    m.p("秒杀相关的两个关键约束：", "索引与幂等。")
    m.bullets([
        "tb_seckill_voucher.stock 用 UPDATE ... SET stock = stock - 1 WHERE stock > 0 做数据库层兜底，防止超卖。",
        "tb_voucher_order 上 (user_id, voucher_id) 唯一索引，作为 Redis 防重之后的最终幂等保证。",
        "tb_reservation 上 (user_id, shop_id, reservation_time) 唯一索引，防重复预约。",
        "tb_ai_skill.code 唯一索引，技能编码不重复。",
    ])

    m.h2("4.4 事务边界")
    m.p("秒杀创建订单用 TransactionTemplate 而不是 @Transactional：因为要在同一个事务里做「查重 → 扣库存 → 写订单」，"
        "且方法内部有加锁/解锁逻辑，显式事务模板更清晰、可控。")
    m.code("""
Boolean created = transactionTemplate.execute(status -> {
    Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    if (count > 0) return false;                       // 数据库层幂等兜底
    boolean ok = seckillVoucherService.update()
        .setSql("stock = stock - 1").eq("voucher_id", voucherId).gt("stock", 0).update();
    if (!ok) return false;                             // 库存不足
    if (!save(order)) { status.setRollbackOnly(); return false; }
    return true;
});
// 事务提交成功后再发送超时消息
if (Boolean.TRUE.equals(created)) orderTimeoutProducer.send(...);
""")

    # ---------------- 第五章 ----------------
    m.h1("第五章 Redis 与多级缓存")

    m.h2("5.1 数据结构与用途")
    m.table(
        ["结构", "Key 示例", "用途", "为什么选它"],
        [
            ["String", "login:code:{phone}、seckill:stock:{id}", "验证码、库存、店铺 JSON", "单值、可设置 TTL"],
            ["Hash", "login:token:{token}", "登录用户信息", "字段级读写，便于刷新 TTL"],
            ["Set", "seckill:order:{voucherId}、follows:{userId}", "防重复下单、关注关系", "O(1) 判重、支持交并集"],
            ["ZSet", "blog:liked:{blogId}、feed:{userId}", "点赞、关注收件箱", "按时间戳排序、范围查询"],
            ["GEO", "shop:geo:{typeId}", "附近店铺", "按距离排序、半径查询"],
            ["Bitmap", "sign:{userId}:{yyyyMM}", "签到", "一个月仅 4 字节"],
            ["Stream", "stream.orders", "秒杀异步下单", "消费组、PENDING、可重投"],
        ],
        widths=[2.4, 5.2, 4.6, 4.6],
    )

    m.h2("5.2 缓存三大问题")
    m.table(
        ["问题", "现象", "本项目做法", "代码位置"],
        [
            ["穿透", "查不存在的数据，每次都打到 DB", "BloomFilter 先拦一层 + 空值缓存 2 分钟", "CacheClient.queryWithPassThroughAndMutex"],
            ["击穿", "热点 key 过期瞬间大量请求打 DB", "Redis 互斥锁 + 双重检查", "同方法内 tryLock/二次查询"],
            ["雪崩", "大量 key 同时过期", "TTL 加 3~10 分钟随机值", "set 前计算 random"],
        ],
        widths=[2.2, 5.0, 5.4, 5.2],
    )
    m.p("布隆过滤器的代价：存在误判率（可能把「存在」判成「不存在」？不会；只会把「不存在」误判为「存在」），"
        "所以它只能挡掉「一定不存在」的请求，不能替代数据库判断；且数据新增后需要重新预热。")

    m.h2("5.3 秒杀 Lua 脚本逐段读")
    m.code("""
local voucherId = ARGV[1]
local userId   = ARGV[2]
local orderId  = ARGV[3]

local stockKey = "seckill:stock:" .. voucherId
local orderKey = "seckill:order:" .. voucherId

-- 1) 库存是否充足
local stock = tonumber(redis.call('get', stockKey))
if (stock == nil or stock <= 0) then return 1 end

-- 2) 一人一单
if (redis.call('sismember', orderKey, userId) == 1) then return 2 end

-- 3) 扣库存 + 记录用户 + 投递 Stream（三步原子）
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'orderId', orderId)
return 0
""")
    m.p("为什么必须放在一个脚本里？Redis 单线程执行 Lua，脚本内的「判断库存 → 判断重复 → 扣减 → 记录 → 投递」"
        "不会被其它请求打断，天然避免超卖与重复；如果拆成多条命令，中间就可能被并发插入。")

    m.h2("5.4 分布式锁：Redisson")
    m.p("创建订单时对 lock:order:{userId} 加锁，保证同一用户的下单串行化；Redisson 的看门狗机制会在业务未完成时自动续期，"
        "避免「业务还没跑完锁就过期」。与手写 SETNX 相比，它解决了续期、可重入、可中断等问题。")

    m.h2("5.5 本地缓存与二级缓存")
    m.bullets([
        "Guava Cache：进程内缓存，maximumSize=10000，TTL≤300 秒，用来挡住 Redis 之前的热点读。",
        "本地缓存有「多实例不一致」问题，所以 TTL 设得短；写操作会同时 invalidate 本地缓存与删除 Redis key。",
        "BloomFilter 在启动时用全部店铺 id 预热，新增店铺需要加入过滤器（当前实现是重启预热）。",
    ])

    # ---------------- 第六章 ----------------
    m.h1("第六章 消息队列：RabbitMQ 与订单超时")

    m.h2("6.1 AMQP 基本模型")
    m.table(
        ["概念", "说明", "本项目取值"],
        [
            ["Exchange", "交换机，按 routing key 把消息路由到队列", "order.delay / order.cancel / order.dlx（均为 direct）"],
            ["Queue", "队列，消息最终存放处", "order.delay.queue / order.cancel.queue / order.cancel.dlq"],
            ["Binding", "交换机与队列的绑定关系", "delay→delay.queue、cancel→cancel.queue、dlx→dlq"],
            ["Routing Key", "路由键", "order.delay / order.cancel / order.dlq"],
            ["DLX", "死信交换机", "delay.queue 的死信投到 cancel.exchange"],
        ],
        widths=[3.0, 6.6, 7.2],
    )

    m.h2("6.2 延迟队列的两种实现")
    m.table(
        ["方案", "原理", "优点", "缺点"],
        [
            ["TTL + DLX（本项目）", "消息设置过期时间，过期后由死信交换机转发到取消队列", "不依赖插件，RabbitMQ 原生支持", "消息级 TTL 下，若 TTL 不一致会出现队头阻塞"],
            ["delayed-message 插件", "交换机内置延迟能力，到期投递", "支持任意延迟、无队头阻塞", "需要安装并启用插件，运维成本高"],
        ],
        widths=[3.6, 6.4, 3.6, 3.2],
    )
    m.note("本项目所有超时消息 TTL 相同（都取 ORDER_TIMEOUT_MS），所以不存在队头阻塞问题。")

    m.h2("6.3 项目拓扑")
    m.code("""
下单成功
  |  convertAndSend(expiration = 15min)
  v
order.delay.exchange  --order.delay-->  order.delay.queue
                        （无消费者，消息在此等待过期）
  |  过期后由死信交换机转发
  v
order.cancel.exchange --order.cancel--> order.cancel.queue
  |  消费者：CAS status 1 -> 4
  |          回滚 DB / Redis 库存
  |          移除 seckill:order:{id} 标记
  |  消费异常重试 3 次
  v
order.dlx.exchange    --order.dlq----->  order.cancel.dlq
""", size=8)

    m.h2("6.4 幂等、重试与死信")
    m.bullets([
        "幂等：取消前先查订单，再用 UPDATE ... WHERE id=? AND status=1 的 CAS 更新；影响行数为 0 说明已被支付/取消，直接跳过。",
        "重试：spring.rabbitmq.listener.simple.retry 配置 3 次、间隔递增（1s/2s/4s），失败后拒绝消息。",
        "死信：队列配置 x-dead-letter-exchange，重试耗尽的消息进入 order.cancel.dlq，人工排查后可重放。",
        "发送时机：订单事务提交成功后才发消息；发送失败只记日志（后续可用本地消息表做最终一致）。",
    ])

    m.h2("6.5 端到端实测（TTL=8 秒）")
    m.table(
        ["时间", "事件", "数据变化"],
        [
            ["08:23:43.5", "秒杀下单，Redis 扣库存，Stream 投递", "库存 10 → 9"],
            ["08:23:43.5", "Stream 消费者建单成功，投递超时消息", "order.delay.queue 有 1 条，TTL 8000ms"],
            ["08:23:51.6", "消息过期 → 取消队列 → 消费者 CAS", "订单 status 1 → 4"],
            ["08:23:51.6", "回滚库存与标记", "DB/Redis 库存 9 → 10，seckill:order:10 清空"],
            ["最终", "三个队列清空", "delay/cancel/dlq 均为 0"],
        ],
        widths=[3.0, 7.4, 6.4],
    )

    # ---------------- 第七章 ----------------
    m.h1("第七章 AI 能力：LangChain4j 与 MCP")

    m.h2("7.1 OpenAI 兼容接口")
    m.p("DashScope 提供 OpenAI 兼容端点（https://dashscope.aliyuncs.com/compatible-mode/v1），所以可以直接用 LangChain4j 的 "
        "OpenAiChatModel / OpenAiStreamingChatModel，只改 base-url、api-key、model-name。")

    m.h2("7.2 AI Skills（固定技能）")
    m.p("设计原则：不让模型自主规划，而是用户选技能、填表单、后端渲染模板、调用模型、保存记录。")
    m.table(
        ["编码", "名称", "场景"],
        [
            ["blog_draft", "AI 写探店笔记", "内容创作"],
            ["review_summary", "AI 总结店铺评价", "商家运营"],
            ["review_reply", "AI 生成评价回复", "商家客服"],
            ["coupon_copy", "AI 生成优惠券文案", "营销"],
            ["shop_tags", "AI 提取店铺标签", "推荐/搜索"],
            ["merchant_daily_report", "AI 生成经营日报", "经营分析"],
            ["short_video_script", "AI 生成短视频脚本", "内容营销"],
            ["promotion_plan", "AI 生成活动方案", "活动策划"],
        ],
        widths=[5.0, 6.0, 5.8],
    )
    m.p("可靠性设计：每分钟每用户限 20 次（Redis 计数）；模型不可用或返回空时使用本地兜底文案；"
        "所有调用落入 tb_ai_skill_run 便于审计。")

    m.h2("7.3 智能客服与工具调用")
    m.p("基于 LangChain4j @AiService 声明式接口，注入 ChatModel 与 StreamingChatModel，"
        "并注册 ShopTool / VoucherTool / OrderTool / ReservationTool / AiSkillTool 五类工具；"
        "系统提示词要求「涉及业务数据必须调工具，不得编造」。多轮记忆存 Redis，每会话最多 20 条、TTL 1 天。")

    m.h2("7.4 MCP Server")
    m.p("用 MCP Java SDK 的 HttpServletStatelessServerTransport 暴露 11 个业务工具（店铺 4 个、券 1 个、订单 1 个、"
        "预约 3 个、AI 技能 2 个），端点 /mcp，JSON-RPC 传输。")
    m.code("""
# 列出工具（需要在命令行里带 API Key）
curl -X POST http://127.0.0.1:8081/mcp \\
  -H "Content-Type: application/json" \\
  -H "Accept: application/json, text/event-stream" \\
  -H "X-API-Key: your-api-key" \\
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
""")
    m.note("MCP 与普通 REST 的区别：REST 由人（或前端）决定调用哪个接口；MCP 把「能力」以工具描述的形式交给客户端/模型，"
        "由调用方决定用哪个工具、传什么参数，更强调自描述与可组合。")

    # ---------------- 第八章 ----------------
    m.h1("第八章 前端与 Nginx")
    m.p("前端是仓库自带的 Vue 2 + Element UI 静态页面，没有 npm 工程，直接由 Nginx 托管。")
    m.code("""
server {
    listen 8080;
    location / { root html/hmdp; index index.html; }
    location /api {
        rewrite /api(/.*) $1 break;          # /api/shop/1 → /shop/1
        proxy_pass http://127.0.0.1:8081;
    }
}
""")
    m.bullets([
        "rewrite 去掉 /api 前缀，所以后端控制器不需要统一加 /api 前缀。",
        "静态图片 /imgs/** 与店铺类型图标 /types/** 都由 root 目录提供；类型图标实际请求是 /imgs//types/*.png，Nginx 会合并重复斜杠。",
        "图片上传目录必须同时是后端写入目录与 Nginx 静态目录（本机用绝对路径配置，容器用双挂载）。",
    ])

    # ---------------- 第九章 ----------------
    m.h1("第九章 测试体系与 CI")

    m.h2("9.1 分层策略")
    m.table(
        ["层次", "范围", "依赖", "运行时机"],
        [
            ["单元测试", "Result、异常映射、UserService 业务逻辑、订单超时取消", "无外部依赖（Mockito）", "每次 mvn test / CI"],
            ["Web 层测试", "MockMvc standalone 验证参数校验与异常码", "无 Spring 容器", "同 CI"],
            ["集成测试", "HmDianPingApplicationTests（GEO 加载、ID 生成）", "真实 MySQL/Redis", "本地显式开启"],
        ],
        widths=[3.0, 7.2, 4.2, 3.8],
    )
    m.code("""
# 只跑单元测试
mvn test

# 连真实中间件跑集成测试（默认跳过）
$env:RUN_INTEGRATION_TESTS='true'; mvn test
""")

    m.h2("9.2 测试类清单")
    m.table(
        ["测试类", "用例数", "覆盖点"],
        [
            ["ResultTest", "5", "各工厂方法、错误码、旧 fail(String) 兼容"],
            ["WebExceptionAdviceTest", "4", "业务异常/未知异常/类型不匹配/参数校验 → 错误码"],
            ["UserServiceImplTest", "5", "验证码校验与 TTL、错误码拒绝登录、注册后 id 回填、限流"],
            ["OrderTimeoutServiceTest", "4", "订单不存在、状态已变跳过、取消并回滚、null 入参"],
        ],
        widths=[5.6, 2.6, 8.4],
    )
    m.note("OrderTimeoutServiceTest 需要手动初始化 MyBatis-Plus 的 TableInfo（纯单测没有 MP 启动流程），"
        "否则 LambdaUpdateWrapper 会抛 can not find lambda cache。")

    m.h2("9.3 CI")
    m.code("""
name: CI
on: [push, pull_request]
jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: maven }
      - run: mvn -B verify
        working-directory: AI-dianping-backend
""")

    # ---------------- 第十章 ----------------
    m.h1("第十章 可观测性")
    m.bullets([
        "健康检查：/actuator/health 聚合 db、redis、diskSpace 等组件状态。",
        "指标：/actuator/prometheus 暴露 JVM、HTTP、连接池与自定义指标。",
        "自定义缓存指标：hmdp_cache_requests_total{layer=local|redis|none,result=hit|miss}、hmdp_cache_local_size。",
        "traceId：每个请求生成 16 位 id，写入响应头 X-Trace-Id 与日志，排查时按 id 串联。",
    ], numbered=True)
    m.code("""
# 带鉴权头访问（MCP_API_KEY 已配置时）
curl -H "X-API-Key: your-api-key" http://127.0.0.1:8081/actuator/health
curl -H "X-API-Key: your-api-key" http://127.0.0.1:8081/actuator/prometheus | findstr hmdp_cache
""")

    # ---------------- 第十一章 ----------------
    m.h1("第十一章 部署工程化：Docker 与 Compose")

    m.h2("11.1 三个概念")
    m.table(
        ["概念", "一句话", "类比"],
        [
            ["镜像 Image", "只读安装包：程序 + 运行时 + 依赖", "系统 ISO"],
            ["容器 Container", "镜像运行实例，独立进程/网络/文件系统", "装好的机器"],
            ["编排 Compose", "用 YAML 描述多个容器如何协作", "机架图"],
        ],
        widths=[3.4, 8.0, 4.6],
    )
    m.p("容器化的收益：环境一致、交付物明确、隔离、可回滚；代价：学习成本、镜像体积、跨容器排查稍麻烦。")

    m.h2("11.2 Dockerfile 逐段")
    m.code("""
FROM maven:3.9-eclipse-temurin-17 AS build     # 构建阶段
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true     # 依赖层可缓存
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre                     # 运行阶段，镜像更小
WORKDIR /app
ENV TZ=Asia/Shanghai LANG=C.UTF-8
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
""")
    m.bullets([
        "多阶段构建：最终镜像不包含源码与 Maven，体积从约 700MB 降到 200MB 左右。",
        "先 COPY pom 再 COPY src：只改代码时依赖层命中缓存，构建从几分钟降到几十秒。",
        "MaxRAMPercentage：容器内存受限时按比例设置堆，避免被 OOM Kill。",
    ])

    m.h2("11.3 Compose 关键配置")
    m.code("""
services:
  mysql:
    image: mysql:8.0
    environment: { MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}, MYSQL_DATABASE: hmdp }
    volumes:
      - ./db/db.sql:/docker-entrypoint-initdb.d/01-db.sql:ro
      - mysql-data:/var/lib/mysql
    healthcheck: { test: ["CMD","mysqladmin","ping","-h","127.0.0.1"], interval: 5s, retries: 30 }

  backend:
    build: ./AI-dianping-backend
    environment:
      DB_URL: jdbc:mysql://mysql:3306/hmdp?...
      REDIS_HOST: redis
      RABBITMQ_HOST: rabbitmq
    depends_on:
      mysql: { condition: service_healthy }
    ports: ["8082:8081"]

  nginx:
    image: nginx:1.27-alpine
    volumes:
      - ./AI-dianping-frontend/html/hmdp:/usr/share/nginx/html:ro
      - ./AI-dianping-frontend/html/hmdp/imgs:/usr/share/nginx/html/imgs
      - ./deploy/nginx-docker.conf:/etc/nginx/conf.d/default.conf:ro
    ports: ["8080:8080"]
""")
    m.p("三个必须记住的点：", "易错点。")
    m.bullets([
        "容器内互访用服务名（mysql/redis/rabbitmq），不能用 localhost。",
        "depends_on 只保证启动顺序，服务可用要靠 healthcheck + condition: service_healthy。",
        "db.sql 只在数据卷为空时执行一次；改脚本要 down -v（会清数据）。",
        "上传目录要同时挂给后端（写）和 Nginx（读），否则图片 404。",
    ])
    m.code("""
cp .env.docker.example .env
docker compose up -d --build
docker compose ps
docker compose logs -f backend
docker compose down          # 保留数据
docker compose down -v       # 删除数据卷（清库！）
""")
    m.note("本机没有安装 Docker，compose 未实跑验证；在装了 Docker 的机器上先执行 docker compose config 校验语法。")

    # ---------------- 第十二章 ----------------
    m.h1("第十二章 安全加固")

    m.h2("12.1 认证与授权")
    m.p("认证解决「你是谁」，授权解决「你能做什么」。本项目原来的问题：/mcp 暴露 11 个业务工具却在登录白名单里；"
        "/actuator 暴露健康与指标；发验证码接口无频率限制。")

    m.h2("12.2 API Key 鉴权")
    m.code("""
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    @Value("${MCP_API_KEY:}") private String expectedKey;

    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return !(p.startsWith("/mcp") || p.startsWith("/actuator"));
    }
    // 比较请求头 X-API-Key 与 expectedKey；不一致返回 401 + JSON(code=40100)
}
""")
    m.p("为什么用 Filter：/mcp 是 Servlet 注册、/actuator 走独立 HandlerMapping，MVC 拦截器覆盖不到。")
    m.p("局限：共享密钥无法区分调用方、无法细粒度授权、轮换成本高。更完善：JWT + 网关，或工具级权限 + 调用审计。")

    m.h2("12.3 验证码限流")
    m.table(
        ["算法", "思路", "优点", "缺点"],
        [
            ["固定窗口（本项目）", "窗口内计数，超阈值拒绝", "实现最简单", "窗口边界可能被放大到两倍"],
            ["滑动窗口", "记录请求时间或 ZSet", "边界平滑", "存储与计算成本高"],
            ["令牌桶", "匀速发令牌，允许突发", "允许突发流量", "需维护桶状态"],
            ["漏桶", "恒定速率流出", "输出平滑", "不允许突发"],
        ],
        widths=[3.6, 5.4, 3.6, 3.2],
    )
    m.code("""
Long count = redis.opsForValue().increment("login:code:limit:" + phone);
if (count != null && count > 1) return Result.fail(TOO_MANY_REQUESTS, "发送过于频繁");
if (count != null && count == 1L) redis.expire(key, 60, SECONDS);   // 只在第一次设置过期
""")
    m.p("为什么判断 count == 1：每次都 expire 会把窗口不断续期，等于永不过期。")
    m.p("为什么不原子：INCR 成功后、EXPIRE 前宕机，key 会永不过期（内存泄漏）。彻底方案是 Lua 合并，或 SET NX EX + INCR 组合。")

    m.h2("12.4 安全待办")
    m.bullets([
        "HTTPS（证书 + Nginx 配置）。",
        "MCP 工具级授权与调用审计。",
        "登录失败次数限制/账号锁定。",
        "敏感字段脱敏（手机号打码）。",
        "依赖漏洞扫描（OWASP Dependency-Check）。",
    ])

    # ---------------- 第十三章 ----------------
    m.h1("第十三章 压测")

    m.h2("13.1 指标定义")
    m.table(
        ["指标", "含义", "关注点"],
        [
            ["QPS", "每秒完成请求数", "与并发数不是一回事"],
            ["并发", "同时在途请求数", "受客户端、网络、服务端共同限制"],
            ["P50/P95/P99", "50%/95%/99% 请求快于该值", "P99 更接近用户真实体验"],
            ["错误率", "非 2xx/超时比例", "最容易被忽略"],
        ],
        widths=[3.2, 6.8, 6.0],
    )
    m.p("QPS ≈ 并发数 / 平均响应时间。想提高 QPS：降延迟（缓存）或加实例。")

    m.h2("13.2 工具选型")
    m.table(
        ["工具", "优点", "缺点"],
        [
            ["ab", "简单", "功能弱"],
            ["wrk", "高性能、可脚本化", "Windows 需自行编译"],
            ["JMeter", "图形化、场景丰富", "重、脚本维护成本高"],
            ["自写脚本（本项目）", "零依赖、可解释、可进 CI", "功能有限、客户端可能先成瓶颈"],
        ],
        widths=[4.0, 6.4, 5.6],
    )

    m.h2("13.3 脚本设计")
    m.code("""
1..$Concurrency | ForEach-Object -Parallel {
    $client = [System.Net.Http.HttpClient]::new()   # 复用连接
    while ((Get-Date) -lt $end) {
        $sw = [Stopwatch]::StartNew()
        $resp = $client.GetAsync($u).GetAwaiter().GetResult()
        $sw.Stop()
        $latencies.Add($sw.Elapsed.TotalMilliseconds)
    }
} -ThrottleLimit $Concurrency
""")
    m.p("要点：HttpClient 复连避免握手污染延迟；记录单请求延迟算分位数；ThrottleLimit 控制并发。")

    m.h2("13.4 本机实测（2026-10-21）")
    m.p("环境：Windows 宿主机 + 虚拟机中间件，Nginx → 后端 8081，缓存命中路径（/api/shop/1）。")
    m.table(
        ["并发", "时长", "总请求", "QPS", "P50", "P95", "P99", "错误率"],
        [
            ["20", "5s", "5,999", "1,197", "13.4ms", "21.7ms", "29.6ms", "0%"],
            ["100", "10s", "15,358", "1,529", "55ms", "75ms", "95.1ms", "0%"],
        ],
        widths=[1.8, 1.8, 2.6, 2.4, 2.2, 2.2, 2.2, 2.2],
    )
    m.p("对照：/api/shop/999（布隆过滤器拦截）50 并发 5s → 1,427 QPS，P99 52ms，0 错误。")
    m.p("解读：", "怎么读这组数据。")
    m.bullets([
        "1,500 QPS 是本机回环 + 缓存命中的结果，不能当作生产容量。",
        "并发 20→100，QPS 只涨 27% 而延迟涨 4 倍，说明瓶颈已出现（压测客户端与 Tomcat 线程数）。",
        "P99 从 30ms 到 95ms，用户能感知到变慢。",
        "简历应写「本地压测 100 并发 10s，QPS≈1.5k，P99<100ms，0 错误」，而不是「支撑 10w QPS」。",
    ])

    m.h2("13.5 有意义的压测与常见误区")
    m.bullets([
        "先预热（10% 流量），再统计，避免 JIT/连接池/本地缓存冷启动影响。",
        "客户端与服务端分离，否则两边抢 CPU。",
        "分层压：缓存命中、缓存未命中（先删缓存）、秒杀下单、写接口。",
        "同时看服务端指标：线程数、GC、连接池、缓存命中率。",
        "逐步加并发（10/50/100/200）找 QPS 拐点。",
        "记录环境（CPU/内存/JDK/DB 版本），否则数字无意义。",
    ])
    m.p("五个误区：只压一次、只看平均延迟、同机压测还声称服务器性能、数据量级差几个数量级、把 5xx 当成功。")

    # ---------------- 第十四章 ----------------
    m.h1("第十四章 改造记录")
    m.table(
        ["批次", "主题", "关键改动", "验证"],
        [
            ["1", "工程基线", "错误码、参数校验、Actuator/Prometheus、traceId", "非法 id 返回 40000；/actuator 有缓存指标"],
            ["2", "测试体系", "Result/异常/UserService 单测、集成测试开关、CI", "19 个测试通过"],
            ["3", "订单超时", "RabbitMQ 延迟队列 + DLX + 状态 CAS + 库存回滚", "TTL 8s 实测订单 1→4、库存回滚"],
            ["4", "部署/安全/压测", "Dockerfile、compose、API Key、验证码限流、压测脚本", "401/200、42900、1529 QPS"],
        ],
        widths=[1.4, 3.6, 8.0, 5.6],
    )

    # ---------------- 第十五章 ----------------
    m.h1("第十五章 学习路径与自检题")

    m.h2("15.1 四周学习路径")
    m.table(
        ["周", "主题", "产出"],
        [
            ["第 1 周", "Redis 数据结构 + 缓存三大问题 + Lua 秒杀脚本", "能逐行解释 seckill.lua，讲清防超卖"],
            ["第 2 周", "Spring 事务/拦截器/AOP + MyBatis-Plus + 幂等设计", "能说清三处幂等与事务边界"],
            ["第 3 周", "RabbitMQ + 测试", "管理台走一遍延迟队列；重写 OrderTimeoutServiceTest"],
            ["第 4 周", "可观测性 + Docker + 压测", "本地起 compose；跑压测并解读结果"],
        ],
        widths=[2.0, 8.0, 6.0],
    )

    m.h2("15.2 自检题（15 题）")
    m.p("部署：", "一、")
    m.bullets([
        "多阶段构建解决了什么问题？为什么 COPY pom.xml 要放在 COPY src 前面？",
        "depends_on 与 healthcheck 分别解决什么问题？",
        "容器里为什么不能写 localhost 连 MySQL？",
        "docker compose down 与 down -v 的区别？",
        "上传目录为什么要同时挂给后端和 Nginx？",
    ], numbered=True)
    m.p("安全：", "二、")
    m.bullets([
        "认证与授权的区别？API Key 属于哪一种？",
        "为什么 /mcp 和 /actuator 要用 Filter 而不是 Interceptor？",
        "固定窗口限流的边界问题是什么？如何改进？",
        "INCR + EXPIRE 为什么可能泄漏 key？如何原子化？",
        "API Key 方案有哪些局限？",
    ], numbered=True)
    m.p("压测：", "三、")
    m.bullets([
        "QPS 与并发数的关系？为什么 100 并发不等于 100 QPS？",
        "为什么看 P99 而不是平均延迟？",
        "本机压测数字为什么不能当生产容量？",
        "要体现缓存价值，应如何设计两组压测？",
        "压测时除了 QPS 还应看哪些服务端指标？",
    ], numbered=True)

    # ---------------- 附录 ----------------
    m.h1("附录 A 环境变量速查")
    m.table(
        ["变量", "必填", "说明"],
        [
            ["DB_URL / DB_USERNAME / DB_PASSWORD", "是", "MySQL 连接"],
            ["REDIS_HOST / REDIS_PORT / REDIS_PASSWORD", "是", "Redis 连接"],
            ["RABBITMQ_HOST / PORT / USERNAME / PASSWORD", "是（MQ 启用时）", "RabbitMQ 连接"],
            ["RABBITMQ_ENABLED", "否", "false 时禁用 MQ 监听与投递"],
            ["ORDER_TIMEOUT_MS", "否", "订单超时毫秒数，默认 900000"],
            ["MCP_API_KEY", "生产必填", "/mcp 与 /actuator 鉴权"],
            ["IMAGE_UPLOAD_DIR", "是", "图片上传目录（指向 Nginx 静态 imgs）"],
            ["LOG_PATH", "否", "日志目录（脚本转为 -DLOG_PATH）"],
            ["API_KEY", "否", "大模型 Key"],
        ],
        widths=[7.2, 3.4, 5.2],
    )

    m.h1("附录 B 常用命令")
    m.code("""
# 构建与测试
cd AI-dianping-backend
mvn clean package -DskipTests
mvn test

# 本机启动 / 停止
start-all.cmd
stop-all.cmd

# 查看日志
Get-Content E:\\hmdp-Java\\logs\\backend.out.log -Tail 50

# 健康检查与指标（需要 API Key）
curl -H "X-API-Key: your-api-key" http://127.0.0.1:8081/actuator/health

# 压测
pwsh -File scripts/loadtest.ps1 -Url http://127.0.0.1:8080/api/shop/1 -Concurrency 100 -DurationSeconds 10

# 容器化
copy .env.docker.example .env
docker compose up -d --build
docker compose logs -f backend
""")

    m.h1("附录 C 关键文件索引")
    m.table(
        ["主题", "文件"],
        [
            ["统一响应/错误码", "common/ErrorCode.java、common/BizException.java、dto/Result.java"],
            ["异常处理/校验", "config/WebExceptionAdvice.java、dto/LoginFormDTO.java、dto/ReservationDTO.java"],
            ["日志 traceId", "common/TraceIdFilter.java、resources/logback.xml"],
            ["鉴权", "common/ApiKeyAuthFilter.java、interceptor/*.java"],
            ["缓存", "utils/CacheClient.java、utils/RedisConstants.java"],
            ["秒杀", "resources/seckill.lua、service/impl/VoucherOrderServiceImpl.java"],
            ["消息队列", "mq/RabbitMQConfig.java、mq/OrderTimeoutProducer.java、mq/OrderTimeoutConsumer.java、mq/OrderTimeoutService.java"],
            ["可观测", "application.yml(management)、utils/CacheClient.java(指标)"],
            ["测试", "src/test/java/com/hmdp/**"],
            ["部署", "AI-dianping-backend/Dockerfile、docker-compose.yml、deploy/nginx-docker.conf"],
            ["压测", "scripts/loadtest.ps1"],
            ["文档", "docs/tech-stack.md、docs/refactor-log.md、docs/tutorial-deploy-security-loadtest.md、DEPLOY.md"],
        ],
        widths=[4.2, 11.6],
    )

    m.save()
    print("saved:", OUT)


if __name__ == "__main__":
    build()
