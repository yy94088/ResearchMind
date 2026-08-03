# ResearchMind-智能科研文献管理与分析平台 V3.0.0

ResearchMind-智能科研文献管理与分析平台是一套面向科研人员的文献管理与知识发现系统。本仓库包含 Vue 3 前端、Spring Boot 3.5 后端、MySQL 8、Redis 7 和 MinIO 对象存储，核心功能已经接通真实后端。

## 已实现功能

- Spring Security、JWT 与 Redis 支持注册、登录、会话恢复和退出
- MySQL 支持用户隔离的文献新增、编辑、删除、收藏和阅读进度
- 支持一次选择最多 10 篇 PDF，逐篇解析、核对并批量导入
- MinIO 支持私有 PDF 上传、下载和头像存储，PDFBox 负责标题、作者、机构等本地元数据与文本层解析
- 按标题、作者、关键词、DOI、领域、年份和阅读状态检索
- 服务端论文笔记、引用导出和操作动态
- 基于真实文献构建并持久化的知识图谱与科研数据分析
- DeepSeek `deepseek-v4-flash` 补全缺失文献元数据，并支持论文摘要、核心贡献、方法脉络和连续问答
- 团队创建、邮件地址邀请、邀请处理、角色权限、成员管理和共享专题
- 团队专题与成员本人论文的真实关联
- 个人资料、头像、密码修改、登录历史和 Redis 用户偏好
- 系统管理员可管理账户角色与状态、查看全站概览和审计日志、清理过期上传
- 可选 SMTP 密码重置；未配置邮件服务时返回明确的配置错误

用户账户、文献元数据、笔记、团队关系、协作专题和 AI 分析结果保存在
MySQL。JWT 登录会话与用户偏好由 Redis 管理，PDF 和头像存放在 MinIO
私有 bucket。图谱和分析页使用当前账户的真实文献数据，不再依赖前端内置样例。

## 本地运行

环境要求：Java 17、Maven 3.8+、Docker、Docker Compose、Node.js 18+ 和 npm 9+。

```bash
cp .env.example .env
# 编辑 .env，设置数据库、对象存储、JWT 和 DeepSeek 配置
docker compose up -d
```

终端一，启动后端：

```bash
cd ResearchMind-server
set -a
source ../.env
set +a
mvn spring-boot:run
```

终端二，启动前端：

```bash
cd ResearchMind-frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:3000`，使用已注册账户登录，也可以在页面中创建新账户。前端通过 Vite 将 `/api` 请求代理到 `http://localhost:8080`。

如果当前网络必须经过代理才能访问 DeepSeek，可在 `.env` 中补充：

```bash
DEEPSEEK_PROXY_URL=http://127.0.0.1:7897
```

留空时后端会自动尝试使用启动环境中的 `HTTPS_PROXY` 或 `https_proxy`。API Key
只由 Spring Boot 后端读取，不要把它写入任何 `VITE_*` 前端变量。

后端状态检查：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/system/status
```

生产构建使用 `cd ResearchMind-frontend && npm run build`。

代码更新后，如果 8080 端口上已有旧后端进程，需要先停止它，再重新执行
`mvn spring-boot:run`，否则浏览器仍会访问旧代码。

## 可选：配置密码重置邮件

密码重置默认关闭，不会伪造“邮件已发送”的成功结果。要启用它，在 `.env`
中填写 SMTP 参数：

```bash
PASSWORD_RESET_ENABLED=true
PASSWORD_RESET_BASE_URL=http://localhost:3000/login
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=your-account@example.com
MAIL_PASSWORD=your-smtp-password
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS_ENABLE=true
MAIL_FROM=your-account@example.com
```

用户提交邮箱后，邮件中的链接会携带一次性令牌返回登录页。令牌仅以 SHA-256
摘要形式存入 Redis，默认 15 分钟过期，成功使用后立即失效。

## 主要 API

- `/api/auth/*`：认证、个人资料、密码、头像、登录历史和密码重置
- `/api/preferences`：读取和保存个人偏好
- `/api/papers`：文献、PDF、收藏、进度、笔记和 AI 分析
- `/api/graph`：读取并同步当前用户的知识图谱
- `/api/graph/rebuild`：手动重新构建当前用户的知识图谱
- `/api/activities`：当前用户的真实操作动态
- `/api/teams`：团队、邀请、成员、角色、专题和专题论文
- `/api/admin`：系统管理员概览、用户权限、审计日志和数据维护
- `/actuator/health`、`/api/system/status`：基础设施健康检查

## 数据库

[ResearchMind-schema.sql](ResearchMind-schema.sql) 面向 MySQL 8，包含 23 张业务表，覆盖：

- 用户、团队与权限
- 文献、作者、关键词、领域与标签
- 收藏、阅读进度、笔记和共享专题
- 知识图谱节点与关系
- AI 分析和上传解析任务
- 登录日志与操作审计

推荐通过 Docker Compose 启动 MySQL 和 Redis：

```bash
cp .env.example .env
# 修改 .env 中的三个密码
docker compose up -d
docker compose ps
```

MySQL 首次启动时会自动执行 `ResearchMind-schema.sql`。数据分别保存在
`researchmind-mysql-data`、`researchmind-redis-data` 和
`researchmind-minio-data` 命名卷中。

## 工作台阅读统计口径

- **文献总量**：当前账户拥有且未删除的文献数量。
- **已读文献**：网页 PDF 阅读器已渲染到最后一页的文献数量；阅读完成率为
  “已读文献 ÷ 文献总量”。
- **累计阅读时间**：汇总用户在网页 PDF 阅读器中的有效停留秒数。只有阅读器完成
  加载、浏览器标签页可见且窗口处于焦点时才计时；每 15 秒及退出阅读时自动保存。
  页面右侧仍展示按“文献页数 × 阅读进度”计算的约合已读页数。
- **阅读进度**：用户在网页阅读器中实际渲染过的最远页码 ÷ PDF 总页数，结果向上
  取整。阅读器连续展示整篇 PDF，并对临近视口的页面懒渲染；视口中心所在页作为
  当前页。最远页码由服务端单调保存，返回前页不会降低进度；再次打开会回到该页。
- **完成阅读趋势**：以阅读器首次到达最后一页的时间归入对应月份，而不是文献的
  导入月份。

## 项目结构

```text
.
├── README.md
├── ResearchMind.md                 # 产品需求与架构说明
├── ResearchMind-schema.sql         # MySQL 8 数据库结构
├── ResearchMind-server             # Spring Boot 3.5 后端服务
└── ResearchMind-frontend
    ├── src
    │   ├── api                     # 后端请求封装
    │   ├── components              # 图标、导入和详情组件
    │   ├── layouts                 # 应用主布局
    │   ├── router                  # 路由与登录保护
    │   ├── stores                  # Pinia 状态与服务端数据协调
    │   ├── styles                  # 全局设计系统
    │   └── views                   # 业务页面
    └── vite.config.js
```

## 当前交付边界

当前版本已经完成可运行的核心闭环。尚未纳入本版本的增强项包括扫描版 PDF
的 OCR、Neo4j 图谱持久化，以及 WebSocket 实时协同编辑。
密码重置代码已完成，但实际发信依赖部署方提供可用的 SMTP 账户。

全新数据库的首个注册账户会成为系统管理员。已有数据库首次启用系统管理时，
在 `.env` 中设置 `INITIAL_ADMIN_EMAIL` 为一个已注册且启用的账户邮箱并重启后端；
系统已有管理员后，该配置不会再修改用户角色。

## 知识图谱实现

知识图谱以当前登录用户的文献库为数据源，并同步到 MySQL 的 `graph_node` 和
`graph_relation` 表。系统会生成论文、作者、关键词和研究领域四类节点，以及：

- 论文—作者：`AUTHORED_BY`
- 论文—关键词：`HAS_KEYWORD`
- 论文—研究领域：`BELONGS_TO`（一条主领域边，可有多条关联领域边，保留置信度）
- 共享关键词或研究领域的论文：`RELATED_TO`
- 在同一论文中出现的作者：`COOPERATES_WITH`

`GET /api/graph` 在返回图谱前会事务性同步派生数据，因此新增、编辑或删除文献
后不会读到旧图谱。前端支持节点类型筛选、搜索、拖拽、缩放、关联详情和 PNG
导出。“重新构建”按钮会调用 `POST /api/graph/rebuild`。

## AI 元数据补全

PDF 上传时系统先使用 PDFBox 提取已有元数据和正文，再在用户勾选“使用 AI
补全缺失信息”时，将最多前 12 页、24,000 字符的正文节选发送给已配置的
DeepSeek 服务。AI 可以补全中文标题、作者、关键词、摘要、DOI、年份、期刊/
会议和研究领域。领域识别采用“一个主领域 + 0–2 个可选关联领域”，并保留每个
领域的判断置信度。AI 必须原样摘录标题、摘要或正文依据，服务端会核对依据确实
存在并符合候选领域语义；主领域置信度不低于 0.70、关联领域不低于 0.80，
不满足规则的建议不会写入。

关键词保持论文原文的主要语言：PDF 自带关键词原样保留；缺失时由 AI 补全，
英文论文只写入英文关键词，中文论文使用中文关键词并允许保留标准英文缩写。

已有的本地解析值不会被 AI 覆盖；作者、DOI、年份和期刊/会议要求原文有明确
依据，研究领域只能从系统支持的领域集合中选择。AI 未配置、超时、返回无效内容
或用户取消勾选时，上传会自动退回 PDF 本地解析，不会阻断导入。导入确认页会
列出 AI 实际补全的字段，所有结果仍允许用户修改后再写入 MySQL。

“AI 深度解读”也会在生成摘要、贡献和方法脉络的同一次模型调用中提取这些基础
字段。解读成功后，服务端会再次读取数据库，只补写当时仍为空的中文标题、作者、
关键词、摘要、DOI、年份、期刊/会议和研究领域；已有主领域不会被替换，AI
识别出的新领域会作为关联领域补入，未分类文献则会写入 AI 判断的主领域。重复
DOI 会被跳过。响应中的 `metadataFilledFields` 会列出实际写回的字段，前端随后
刷新文献列表并显示补全提示。图谱页面下次打开或重新构建时会使用补全后的数据。

文献接口保留兼容字段 `area`（主领域名称），并新增完整的 `areas` 数组。数组项
格式为 `{"name":"图神经网络","confidence":0.96,"primary":true}`，服务端会去重
并保证恰好一个主领域。
