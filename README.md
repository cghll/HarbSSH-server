# HarbSSH Server

HarbSSH Server 是一个面向 SSH 运维场景的 AI Agent 服务端。它将远程连接、终端操作和大模型能力组合在一起，可通过自然语言完成服务器信息查询、故障诊断和经确认的运维操作。

## 主要功能

- SSH 连接信息管理与连接状态维护
- 远程命令执行、交互式终端读写和窗口调整
- AI Agent 对话与流式响应
- 面向 SSH 场景的诊断、分析和变更执行能力
- 顺序、并行和循环 Agent 工作流
- MCP 工具、Skills 与自定义插件接入
- 对话上下文、里程碑和长期记忆持久化

## 技术栈

| 技术 | 版本 | 用途 |
| --- | --- | --- |
| JDK | 17 | 运行环境 |
| Spring Boot | 3.4.3 | Web 服务与应用配置 |
| Google ADK | 1.2.0 | Agent 构建与运行 |
| Spring AI | 1.1.8 | 模型与 MCP 集成 |
| LangChain4j | 1.4.0 | 大模型能力集成 |
| MyBatis | 3.0.4 | 数据访问 |
| MySQL | 8.x | 业务数据持久化 |
| Maven | 3.x | 项目构建 |

## 环境要求

- JDK 17
- Maven 3.x
- MySQL 8.x
- 可用的大模型 API
- 可选：Docker 与 Docker Compose

## 快速开始

### 1. 获取项目

```bash
git clone https://github.com/cghll/HarbSSH-server.git
cd HarbSSH-server
```

### 2. 初始化数据库

确认 MySQL 已启动，然后执行：

```bash
mysql -uroot -p < docs/dev-ops/mysql/sql/harbssh.sql
```

脚本会自动创建 `harbssh` 数据库及所需数据表。

### 3. 配置应用

开发环境配置位于：

```text
harbssh-server-app/src/main/resources/application-dev.yml
```

请根据本机环境修改以下内容：

- MySQL 地址、端口、用户名和密码
- 需要加载的 Agent 配置文件

Agent 配置文件位于：

```text
harbssh-server-app/src/main/resources/agent/
```

至少需要配置模型的 `base-url`、`api-key` 和 `model`。API Key 等敏感信息应仅保存在本地或通过安全的配置管理方式注入，不要提交到版本库。

### 4. 构建并启动

```bash
mvn clean package "-Dmaven.test.skip=true"
java -jar harbssh-server-app/target/harbssh-server-app.jar
```

开发环境默认启用 `dev` Profile，服务启动后监听：

```text
http://localhost:8091
```

也可以使用 Maven 启动应用：

```bash
mvn clean install "-Dmaven.test.skip=true"
mvn -pl harbssh-server-app spring-boot:run
```

## 接口概览

| 能力 | 接口前缀 |
| --- | --- |
| Agent 配置查询、会话创建与对话 | `/api/v1/` |
| SSH 连接管理 | `/api/v1/ssh/connections` |
| SSH 终端操作 | `/api/v1/ssh/terminal` |

主要接口包括 Agent 列表查询、会话创建、普通对话、流式对话，以及 SSH 连接的创建、查询、连接、断开和终端命令执行。

## 前端演示页

仓库在 `docs/dev-ops/nginx/html/` 下提供了静态演示页面。使用前请将 `config.js` 中的 `API_BASE_URL` 调整为实际服务地址，例如：

```javascript
const API_BASE_URL = 'http://127.0.0.1:8091';
```

随后可通过 Nginx 或其他静态文件服务器托管该目录。

## 项目结构

```text
harbssh-server/
├── harbssh-server-api             # 对外接口与数据对象
├── harbssh-server-app             # 应用入口与运行配置
├── harbssh-server-case            # Agent ReAct 用例编排
├── harbssh-server-domain          # 核心领域能力
├── harbssh-server-infrastructure  # 数据库及外部服务适配
├── harbssh-server-trigger         # HTTP 接口入口
├── harbssh-server-types           # 公共类型与异常
└── docs                           # SQL、部署文件和提示词
```

## 测试

```bash
mvn test
```

部分测试依赖 MySQL、模型 API 或可访问的 SSH 主机，运行前请确认对应配置和外部服务可用。

## 部署

项目提供了以下部署相关文件：

- `harbssh-server-app/Dockerfile`：应用镜像定义
- `docs/dev-ops/docker-compose-environment.yml`：MySQL、Redis 等基础环境
- `docs/dev-ops/docker-compose-app.yml`：应用容器编排模板
- `docs/dev-ops/nginx/`：静态页面与 Nginx 资源

部署模板中的镜像名、版本、挂载目录和数据库连接信息需要根据实际环境调整后使用。

