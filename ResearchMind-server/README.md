# ResearchMind Server

基于 Java 17、Spring Boot 3.5、MySQL 8、Redis 7 和 MinIO 的后端服务。

## 启动

先在项目根目录启动基础设施：

```bash
docker compose up -d
```

再加载根目录 `.env` 并启动后端：

```bash
cd ResearchMind-server
set -a
source ../.env
set +a
mvn spring-boot:run
```

`.env` 还需要包含一个随机 JWT 签名密钥，可通过下面的命令生成：

```bash
openssl rand -base64 48
```

## 认证接口

| 方法 | 地址 | 说明 | 是否需要 JWT |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | 注册并签发令牌 | 否 |
| `POST` | `/api/auth/login` | 用户名或邮箱登录 | 否 |
| `GET` | `/api/auth/me` | 获取当前用户 | 是 |
| `POST` | `/api/auth/logout` | 注销当前令牌 | 是 |

受保护接口通过请求头传递令牌：

```text
Authorization: Bearer <access-token>
```

## 文献接口

所有文献接口都需要 JWT，并且只会访问令牌所属用户的数据。

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `GET` | `/api/papers` | 查询当前用户的文献 |
| `GET` | `/api/papers/{id}` | 查询文献详情 |
| `POST` | `/api/papers` | 新增文献元数据 |
| `PUT` | `/api/papers/{id}` | 更新文献元数据 |
| `DELETE` | `/api/papers/{id}` | 移除文献 |
| `PUT` | `/api/papers/{id}/favorite` | 更新收藏状态 |
| `PUT` | `/api/papers/{id}/progress` | 保存 PDF 阅读器当前页、有效停留秒数并自动计算进度 |

阅读器每次调用进度接口时提交 `currentPage` 和本次尚未上报的
`readSeconds`。服务端以数据库中的 PDF 总页数计算百分比，最远页码只增不减，
并把单次不超过 60 秒的有效阅读时长累加到 `total_read_seconds`。
| `GET` | `/api/papers/{id}/file` | 下载当前用户的 PDF 原文 |

## PDF 上传与解析

`POST /api/uploads/papers` 接收名为 `file` 的 multipart PDF 文件，最大 50 MB。
服务端会校验 PDF 文件头，使用 Apache PDFBox 提取页数、标题、作者、关键词、
摘要和 DOI，再将原文件写入私有 MinIO bucket。响应中的 `uploadId` 应在创建
文献时一并提交，以将原文和文献记录安全关联。
如果用户在确认建档前关闭导入窗口，前端会调用 `DELETE /api/uploads/{uploadId}`
清理未关联的上传记录和 MinIO 对象。

当前解析适用于包含文本层的 PDF；纯扫描图片需要后续 OCR 模块。

上传接口还接受可选的 multipart 参数 `aiEnrich`，默认是 `true`。启用后，服务端
会把最多前 12 页、24,000 字符的 PDF 正文节选交给 DeepSeek，仅补全本地缺失的
中文标题、作者、关键词、摘要、DOI、年份、期刊/会议和研究领域。AI 可返回一个
主领域与 0–2 个可选关联领域，并为每项提供原文依据和 0–1 的置信度。服务端会
核对依据确实出现在论文资料中并符合候选领域语义，同时过滤主领域低于 0.70 或
关联领域低于 0.80 的候选项。响应通过
`aiEnriched`、`aiEnrichedFields`、`aiModel` 和 `aiWarning` 说明补全结果。
AI 调用失败不会使上传失败，而是自动保留本地解析结果。
PDF 自带关键词会原样保留；AI 生成的关键词须与论文原始语言一致，英文论文返回
英文关键词，服务端还会拒绝向英文论文写入包含中文字符的 AI 关键词。

`POST /api/ai/papers/{paperId}/analysis` 的深度解读响应也包含
`metadataFilledFields`。模型会在解读 JSON 的 `metadata` 部分返回基础字段候选，
服务端通过独立事务只写入数据库中仍为空的字段；已有主领域会保留，新的可靠领域
作为关联领域追加。元数据写回异常不会导致已经生成的深度解读失败；无法确认的
字段、非候选研究领域和重复 DOI 会被忽略。

## 知识图谱接口

知识图谱接口需要 JWT，并且只会构建令牌所属用户的文献数据。

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `GET` | `/api/graph` | 同步并获取当前用户的知识图谱 |
| `POST` | `/api/graph/rebuild` | 手动重新构建并返回知识图谱 |

图谱节点和关系会持久化到 MySQL 的 `graph_node`、`graph_relation` 表。数据源是
文献、作者、关键词和研究领域关系表；关键词和研究领域分别连接到文献，不建立
关键词到研究领域的推断边。每篇文献有一条主领域关系，也可以有多条关联领域关系，
关系属性保留 `primary` 标识，权重保存领域置信度。图谱还会计算共享关键词/领域
的论文关联与共同署名的作者合作关系。

验证接口：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/system/status
```
