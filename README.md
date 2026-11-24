# 智慧医疗

面向医疗场景的智能问答与预约助手，包含流式对话、ChatMemory 持久化、工具调用和 RAG 知识库检索。

> 智能问诊 · 预约挂号 · 健康咨询

## 功能

- 通义千问对话
- Flux 流式输出
- 手机号验证码登录，JWT access/refresh token
- 会话归属校验，不能使用他人的 memoryId
- ChatMemory + MongoDB 会话记忆
- Token 预算窗口 + 旧对话摘要压缩
- `@Tool` 预约、取消预约、日期时间、计算器
- 预约记录绑定当前登录用户
- Milvus 向量库 RAG 文档检索
- 文档上传、解析、切片、Embedding、幂等入库
- MyBatis-Plus + MySQL 预约数据

## 技术栈

### 后端

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Spring Boot | 3.2.6 |
| Spring Framework | 6.1.8 |
| LangChain4j | 1.0.0-beta3 |
| LangChain4j Community | 1.0.0-beta3 |
| MyBatis-Plus | 3.5.11 |
| Spring Security | 6.2.4 |
| JJWT | 0.12.6 |
| MySQL Connector | 8.3.0 |
| Knife4j | 4.3.0 |
| PDFBox | 2.0.32 |
| Milvus | 2.5.10 |

### 前端

| 组件 | 版本 |
|---|---|
| Vue | 3.5.13 |
| Vite | 5.4.8 |
| Element Plus | 2.8.4 |
| Axios | 1.7.7 |

### 基础设施

| 组件 | 端口 |
|---|---|
| MySQL 8.0 | 3307 |
| MongoDB 7.0 | 27018 |
| Milvus 2.5.10 | 19530 |
| Milvus WebUI | 9091 |

## 目录结构

```text
代码/
├── README.md
├── infra/
│   ├── docker-compose.yml
│   └── mysql/
│       ├── init.sql
│       └── migrate-phase2.sql
├── 后端代码/
│   └── java-ai-langchain4j/
└── 前端代码/
    └── smart-healthcare-ui/
```

后端按层组织：

```text
com.atguigu.java.ai.langchain4j
├── controller    接口入口
├── service       业务逻辑，impl 放实现
├── mapper        MyBatis-Plus Mapper
├── entity        数据库表与 MongoDB 文档
├── dto            请求参数
├── vo             响应数据
├── security       JWT、Filter、SecurityContext
├── config         Spring 配置与配置属性
├── common         异常、统一响应、公共常量
├── memory         会话记忆实现
├── store          MongoDB 存储
├── tools          LangChain4j 工具
└── assistant      AiService 接口
```

## 环境要求

- JDK 17
- Maven 3.6+
- Node.js 18+
- Docker Desktop
- 阿里云百炼 API Key

## 快速启动

### 1. 启动 MySQL、MongoDB 和 Milvus

```powershell
docker compose -f infra/docker-compose.yml up -d
```

检查：

```powershell
docker ps --filter "name=xiaozhi"
```

Milvus 使用本地存储，不依赖 MinIO。启动后可以访问自带 WebUI：

```text
http://localhost:9091/webui
```

也可以使用 Attu 2.5 连接 `127.0.0.1:19530`，认证选择 None。

### 2. 配置 API Key

PowerShell 临时设置：

```powershell
$env:DASH_SCOPE_API_KEY="sk-..."
$env:JWT_SECRET="至少32字节的随机字符串"
```

不要把 Key 写进 `application.properties`，也不要提交到 Git。未设置 `JWT_SECRET` 时会使用开发默认值，生产环境必须覆盖。

本地未接真实短信服务，验证码会打印在后端日志中，例如：

```text
短信验证码 phone=13800000001 code=123456
```

### 3. 启动后端

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

cd 后端代码\java-ai-langchain4j
mvn spring-boot:run
```

后端地址：

- API：`http://localhost:8080`
- Knife4j：`http://localhost:8080/doc.html`

认证接口：

```text
POST /auth/sms/send        发送验证码
POST /auth/login/sms       验证码登录，首次自动注册
POST /auth/login/password  密码登录
POST /auth/password/set    设置密码，需要 Bearer Token
POST /auth/refresh         刷新 Token
GET  /auth/me              当前用户，需要 Bearer Token
```

业务接口 `/medical/**` 需要携带：

```text
Authorization: Bearer <accessToken>
```

聊天请求里的 `memoryId` 必须是 UUID，前端会自动生成并保存在本地。

知识库接口：

```text
POST /medical/knowledge/ingest    上传并入库文档，multipart/form-data
GET  /medical/knowledge/documents 查看文档入库状态
```

上传示例：

```powershell
curl -X POST http://localhost:8080/medical/knowledge/ingest `
  -H "Authorization: Bearer <accessToken>" `
  -F "file=@E:/knowledge/医院信息.md" `
  -F "department=神经内科"
```

当前支持 `.md`、`.txt`、`.pdf`。PDF 使用 PDFBox 抽取文本；MinerU 解析作为后续增强。

### 4. 启动前端

```powershell
cd 前端代码\smart-healthcare-ui
npm install
npm run dev
```

前端地址：`http://localhost:5173`

首次打开会进入登录页，输入手机号后查看后端日志中的验证码。首次登录自动注册，并可选择设置密码。

Vite 已配置 `/api` 代理到 `http://localhost:8080`。

## 数据库配置

### MySQL

```text
地址：localhost:3307
数据库：guiguxiaozhi
用户：root
密码：123456
```

初始化脚本：`infra/mysql/init.sql`

已有旧数据库（数据卷已存在）按阶段执行迁移：

```powershell
$env:MYSQL_PWD="你的MySQL密码"
docker exec -i -e MYSQL_PWD xiaozhi-mysql mysql -uroot < infra/mysql/migrate-phase2.sql
docker exec -i -e MYSQL_PWD xiaozhi-mysql mysql -uroot < infra/mysql/migrate-phase3.sql
```

主要表：

- `users`：用户、手机号、BCrypt 密码
- `chat_sessions`：`memoryId` 与 `userId` 的归属关系
- `appointment`：预约记录，按 `user_id` 隔离
- `knowledge_documents`：知识库文档、doc_hash、入库状态
- `knowledge_chunks`：文档切片、chunk_id、content_hash

### MongoDB

```text
地址：localhost:27018
数据库：chat_memory_db
```

MongoDB 使用 `27018`，避免与本机默认 `27017` 冲突。

## 配置说明

主要配置在：

```text
后端代码/java-ai-langchain4j/src/main/resources/application.properties
```

- `DASH_SCOPE_API_KEY`：百炼 API Key
- `xiaozhi.rag.embedding-dimension`：向量维度，默认 `1024`，必须与 Milvus collection 一致
- `xiaozhi.rag.chunk-size`：切片长度，默认 `500`
- `xiaozhi.rag.chunk-overlap`：切片重叠，默认 `80`
- `xiaozhi.rag.milvus.host`：Milvus 地址，默认 `localhost`
- `xiaozhi.rag.milvus.port`：Milvus gRPC 端口，默认 `19530`
- `xiaozhi.rag.milvus.database-name`：Milvus 数据库，默认 `default`
- `xiaozhi.rag.milvus.collection-name`：向量集合名，默认 `smart_healthcare_chunks`
- `xiaozhi.memory.max-tokens`：单会话 token 预算，默认 200000
- `xiaozhi.memory.keep-recent-turns`：压缩时保留的最近对话轮数，默认 8
- `xiaozhi.memory.summary-enabled`：是否启用摘要压缩，默认 true
- `JWT_SECRET`：JWT 签名密钥，至少 32 字节，生产必须覆盖
- `app.jwt.access-token-ttl-minutes`：access token 有效期，默认 30 分钟
- `app.jwt.refresh-token-ttl-days`：refresh token 有效期，默认 7 天
- `app.sms.debug`：是否把验证码打印到日志，本地默认 true
- MongoDB 默认连接 `localhost:27018`

## 说明

- 仓库只包含代码与运行配置。
- `环境说明.md`、`依赖版本报告.md`、`run-backend.ps1`、`run-frontend.ps1` 为本地文件，不纳入仓库。
- 部分测试依赖本地 `E:/knowledge` 目录和 API Key，运行前需自行调整。
- 生产环境请修改默认数据库密码，并使用环境变量或密钥管理服务。
