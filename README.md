# 智慧医疗

面向医疗场景的智能问答与预约助手，包含流式对话、ChatMemory 持久化、工具调用和 RAG 知识库检索。

> 智能问诊 · 预约挂号 · 健康咨询

## 功能

- 通义千问对话
- Flux 流式输出
- ChatMemory + MongoDB 会话记忆
- `@Tool` 预约、取消预约、计算器
- RAG 文档检索
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
| MySQL Connector | 8.3.0 |
| Knife4j | 4.3.0 |
| PDFBox | 2.0.32 |

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

## 目录结构

```text
代码/
├── README.md
├── infra/
│   ├── docker-compose.yml
│   └── mysql/init.sql
├── 后端代码/
│   └── java-ai-langchain4j/
└── 前端代码/
    └── smart-healthcare-ui/
```

## 环境要求

- JDK 17
- Maven 3.6+
- Node.js 18+
- Docker Desktop
- 阿里云百炼 API Key

## 快速启动

### 1. 启动 MySQL 和 MongoDB

```powershell
docker compose -f infra/docker-compose.yml up -d
```

检查：

```powershell
docker ps --filter "name=xiaozhi"
```

### 2. 配置 API Key

PowerShell 临时设置：

```powershell
$env:DASH_SCOPE_API_KEY="sk-..."
```

不要把 Key 写进 `application.properties`，也不要提交到 Git。

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

### 4. 启动前端

```powershell
cd 前端代码\smart-healthcare-ui
npm install
npm run dev
```

前端地址：`http://localhost:5173`

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
- `PINECONE_API_KEY`：可选
  - 未配置时使用 `InMemoryEmbeddingStore`
  - 内存向量库在重启后数据丢失
- MongoDB 默认连接 `localhost:27018`

## 说明

- 仓库只包含代码与运行配置。
- `环境说明.md`、`依赖版本报告.md`、`run-backend.ps1`、`run-frontend.ps1` 为本地文件，不纳入仓库。
- 部分测试依赖本地 `E:/knowledge` 目录和 API Key，运行前需自行调整。
- 生产环境请修改默认数据库密码，并使用环境变量或密钥管理服务。
