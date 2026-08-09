# ai-agent-scaffold

基于 Spring Boot 3.4 + DDD 架构的 AI 智能体脚手架项目，集成 Google ADK、Spring AI、LangChain4j 等主流 AI 框架，提供开箱即用的智能体构建能力。

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 17 | 基础运行环境 |
| Spring Boot | 3.4.3 | 应用框架 |
| Spring AI | 1.1.0-M3 | Spring 官方 AI 集成框架 |
| Google ADK | 0.4.0 | Google Agent Development Kit |
| LangChain4j | 1.4.0 | LLM 应用开发框架 |
| MyBatis | 3.0.4 | ORM 持久层框架 |
| MySQL | 8.x | 数据库 |
| Maven | 3.x | 构建工具 |

## 项目结构

```
ai-agent-scaffold/
├── ai-agent-scaffold-api/           # API 层：DTO 定义、RPC 接口
├── ai-agent-scaffold-app/           # 应用启动层：Spring Boot 入口、配置
├── ai-agent-scaffold-domain/        # 领域层：核心业务逻辑、智能体服务
├── ai-agent-scaffold-trigger/       # 触发器层：HTTP/REST 接口实现
├── ai-agent-scaffold-infrastructure/# 基础设施层：持久化、外部服务适配
├── ai-agent-scaffold-types/         # 类型层：通用常量、异常、枚举
├── docs/                            # 项目文档
│   ├── dev-ops/                     # 运维相关（Nginx、Docker）
│   └── prompt/                      # 提示词模板
└── data/                            # 数据文件
```



## 快速开始

### 环境要求

- JDK 17+
- Maven 3.x
- MySQL 8.x（可选，不启用数据库时无需配置）

### 启动项目

```bash
# 克隆项目
git clone https://github.com/cghll/ai-agent-scaffold.git
cd ai-agent-scaffold

# 编译打包
mvn clean install -DskipTests

# 启动应用
cd ai-agent-scaffold-app
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

启动后访问 `http://localhost:8080`。

### 配置说明

主要配置文件位于 `ai-agent-scaffold-app/src/main/resources/`：

- `application.yml` — 主配置
- `application-dev.yml` — 开发环境配置
- `application-test.yml` — 测试环境配置
- `application-prod.yml` — 生产环境配置

## 核心功能

### 1. Armory 智能体装配引擎

基于树形策略路由的结构化智能体工作流定义，支持：

- **AgentNode** — 智能体装配节点，支持 LLM Agent 构建
- **ChatModelNode** — 对话模型节点，集成 MCP 工具调用
- **RunnerNode** — 执行器节点，支持自定义执行逻辑
- **Workflow** — 工作流编排，支持多种 Agent 模式和复杂节点流转

### 2. MCP（Model Context Protocol）支持

- 本地化 MCP 服务配置与装配
- Spring AI MCP Client 集成
- 自定义 ToolCallbackProvider 注册

### 3. 多 AI 框架集成

- **Spring AI**：OpenAI 集成、MCP Client
- **Google ADK**：Agent 构建、Spring AI 适配器、LangChain4j 适配器
- **LangChain4j**：LLM 调用链路构建

### 4. 对外服务接口（Trigger）

- RESTful API 智能体对外服务
- Chat 对话服务接口
- API 测试工具

### 5. 前端页面

提供基础的前端交互页面。

