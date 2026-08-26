# 19-MCPAndToolsConfig：MCP 与 tools.json

## 1. 本节目标

前面的 `@Tool` 是 Java 进程内部能力。本节学习另一种边界：**工具运行在 Agent 进程之外，Agent 通过 MCP 协议发现并调用它们。**

完成本节后应能回答：

- MCP Server 与 Java `@Tool` 有什么区别？
- `tools.json` 是什么时候被 Harness 读取的？
- `stdio / sse / http` 三种 MCP transport 分别是什么？
- `enableTools`、顶层 `allow`、顶层 `deny` 各过滤什么？
- 为什么 secret 应通过 `${ENV_VAR}` 注入而不是直接写进 JSON？
- MCP Server 挂了时，故障边界在哪里？

本节只学习 **MCP 接入与 Tool Surface 管理**，不继续展开 Sandbox、Gateway 或远程生产部署。

---

## 2. 先建立心智模型

```text
User
  ↓
HarnessAgent
  ↓
LLM reasoning
  ↓
Toolkit
  ├── Java @Tool
  ├── Harness built-in tools
  └── MCP imported tools
          ↓
       MCP Client
          ↓
  stdio / SSE / HTTP
          ↓
      MCP Server
```

MCP 的关键不是“又一种函数注解”，而是把工具提供方从当前 Java 进程解耦出去。

---

## 3. AgentScope 2.0.1 中 tools.json 的职责

Harness 的 `tools.json` 同时负责两件事：

```text
tools.json
├── allow / deny      → 过滤最终 Tool Surface
└── mcpServers        → 声明外部 MCP Server
```

构建 `HarnessAgent` 时，框架会：

```text
WorkspaceManager
    ↓
ToolsConfigLoader.load(...)
    ↓
ToolsConfig
    ↓
McpServerRegistrar.register(...)
    ↓
MCP tools 注册进 Toolkit
    ↓
ToolFilter.apply(...)
    ↓
最终暴露给模型的 tools
```

也就是说，顺序很重要：**先注册 MCP Tool，再统一做 allow/deny 过滤。**

---

## 4. 本案例为什么默认关闭 MCP

如果把 stdio MCP 在 Spring Bean 构建阶段无条件开启，那么缺少 `python3` 或 MCP 进程启动失败时，整个服务都可能启动失败。

为了让学习模块保持可独立启动，本节默认：

```yaml
demo:
  mcp:
    enabled: false
```

默认启动时：

- Harness 正常启动；
- `tools.json` 仍然可以通过 `/api/mcp/config` 解析查看；
- 不启动 MCP 子进程；
- `/api/mcp/chat` 会提示需要开启 demo。

真正实验时：

```bash
export MCP_DEMO_ENABLED=true
```

然后重新启动模块。

---

## 5. 项目结构

```text
19-MCPAndToolsConfig
├── .agentscope/workspace
│   ├── AGENTS.md
│   ├── tools.json
│   └── mcp-server.py
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example/agentscope/mcpandtoolsconfig
    │   │   ├── McpAndToolsConfigApplication.java
    │   │   ├── config/AgentConfiguration.java
    │   │   └── web/McpController.java
    │   └── resources/application.yml
    └── test
        └── java/com/example/agentscope/mcpandtoolsconfig/ToolsConfigLoaderTest.java
```

---

# 6. 一步步编码

## Step 1：创建独立 Spring Boot 模块

依赖：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
</dependency>
```

Harness 已经包含 MCP 编排入口，不需要在本节额外引入一套 MCP 客户端框架。

## Step 2：写 tools.json

本案例：

```json
{
  "deny": ["execute"],
  "mcpServers": {
    "learning-demo": {
      "transport": "stdio",
      "command": "python3",
      "args": [".agentscope/workspace/mcp-server.py"],
      "enableTools": ["echo_text", "add_numbers"],
      "timeout": "PT20S",
      "initializationTimeout": "PT10S"
    }
  }
}
```

这里有三层过滤概念：

### `mcpServers.learning-demo.enableTools`

只决定从这个 MCP Server 导入哪些工具。

### 顶层 `allow`

非空时，只保留 allow 中的最终工具名。

### 顶层 `deny`

无论 allow 如何，deny 最终都优先移除。

本案例用：

```json
"deny": ["execute"]
```

表示即使 Harness 默认有宿主 shell 工具，也不让模型看到它。

## Step 3：实现最小 stdio MCP Server

`mcp-server.py` 完全使用 Python 标准库，不依赖 pip。

它处理：

```text
initialize
notifications/initialized
ping
tools/list
tools/call
```

并暴露：

```text
echo_text(text)
add_numbers(a, b)
```

重要规则：

```text
stdout → 只能输出 MCP JSON-RPC
stderr → 日志
```

如果把 debug 日志写到 stdout，就会污染协议流。

## Step 4：让 Harness 按开关决定是否加载 tools.json

```java
var builder = HarnessAgent.builder()
        .name("mcp-learning-agent")
        .model(model)
        .workspace(Paths.get(".agentscope/workspace"));

if (!mcpDemoEnabled) {
    builder.disableToolsConfig();
}

return builder.build();
```

当不开启 MCP 时，服务仍然能启动。

当开启时，Harness 会自动：

```text
读取 tools.json
→ 启动 python3 子进程
→ MCP initialize
→ tools/list
→ 注册 echo_text / add_numbers
→ 应用 deny filter
```

不需要在 Controller 里手动 `registerMcpClient`。

## Step 5：增加配置观察接口

```text
GET /api/mcp/config
```

它直接调用：

```java
ToolsConfigLoader.load(agent.getWorkspaceManager())
```

所以即便 MCP demo 没开启，也能先学习配置模型。

## Step 6：增加 Agent 调用接口

```text
POST /api/mcp/chat
```

仍然用：

```java
RuntimeContext.builder()
    .userId(...)
    .sessionId(...)
    .build();
```

MCP 并不会替代 RuntimeContext、AgentState 或 Session。

## Step 7：增加无外部进程测试

`ToolsConfigLoaderTest` 只验证：

```text
JSON
 ↓
ToolsConfigLoader
 ↓
ToolsConfig
 ↓
McpServerConfig
```

不启动 MCP Server，也不调用真实模型，因此测试稳定、快速。

---

# 7. 启动与实验

## 7.1 先跑测试

```bash
./mvnw -pl 19-MCPAndToolsConfig test
```

## 7.2 默认模式启动

```bash
export DASHSCOPE_API_KEY="你的 Key"
./mvnw -pl 19-MCPAndToolsConfig spring-boot:run
```

查看配置：

```bash
curl http://localhost:18081/api/mcp/config
```

此时：

```json
"mcpDemoEnabled": false
```

## 7.3 开启真实 stdio MCP

确保：

```bash
python3 --version
```

然后：

```bash
export MCP_DEMO_ENABLED=true
./mvnw -pl 19-MCPAndToolsConfig spring-boot:run
```

请求：

```bash
curl -X POST http://localhost:18081/api/mcp/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"mcp-1",
    "message":"请使用工具计算 12.5 + 7.5，并告诉我工具返回了什么"
  }'
```

再试：

```bash
curl -X POST http://localhost:18081/api/mcp/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"mcp-2",
    "message":"请调用 echo_text，把 AgentScope MCP 传给它"
  }'
```

---

# 8. 三种 Transport 怎么理解

## stdio

```text
Java Agent
   ↓ stdin/stdout
Local Process
```

适合：

- 本机 filesystem server
- Git server
- CLI 工具包装

## SSE

```text
Agent → HTTP/SSE → Remote MCP Server
```

适合已有 SSE MCP 服务。

## Streamable HTTP

```text
Agent → HTTP → Remote MCP Server
```

更适合标准远程服务化部署。

在 AgentScope `McpServerConfig` 中，对应：

```text
transport = stdio / sse / http
```

---

# 9. MCP 与 Java @Tool 的区别

| 维度 | Java `@Tool` | MCP Tool |
|---|---|---|
| 运行位置 | 当前 JVM | 外部进程或远程服务 |
| 注册方式 | Java 代码 | MCP tools/list |
| 部署 | 和 Agent 一起 | 可独立部署 |
| 语言 | Java | 任意 MCP 实现语言 |
| 复用 | 当前应用 | 多个 Agent/客户端共享 |
| 故障 | JVM 内部 | 网络/进程边界 |

不要问“哪一个替代哪一个”。真实系统通常同时存在。

---

# 10. 常见误区

### 误区 1：MCP 就是 HTTP API

不是。HTTP/SSE 只是 transport，MCP 还定义工具发现、schema 和调用语义。

### 误区 2：MCP 接上以后就不用 Toolkit

不是。对 Agent 来说，MCP 工具最终仍进入 Toolkit 的 Tool Surface。

### 误区 3：enableTools 就等于全局 allow

不是。

```text
enableTools → 单个 MCP Server 的导入白名单
allow/deny   → 最终 Toolkit 的统一过滤
```

### 误区 4：Token/API Key 写进 tools.json

不要这样做。`ToolsConfigLoader` 支持 `${ENV_VAR}` 替换，生产 Secret 应交给环境变量或 Secret 管理系统。

---

# 11. 本节边界

本节不学习：

- Docker/Kubernetes Sandbox；
- Gateway/Channel；
- MCP Server 的生产部署；
- OAuth 与复杂远程 MCP 鉴权。

下一节进入：

```text
20-FilesystemAndSandbox
```

回答一个越来越关键的问题：**Agent 能执行文件和 shell 以后，到底应该让它在哪里执行？**
