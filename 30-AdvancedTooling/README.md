# 30-AdvancedTooling：Tool Group、Context 注入与 ToolEmitter

## 1. 这节不是再学一次 @Tool

第 03 课已经学过：

```text
@Tool
@ToolParam
Toolkit.registerTool(...)
```

第 19 课又学了 MCP Tool，第 25 课学了 Tool timeout。

但是生产 Agent 的 Tool 体系还有一个核心问题：

> 当 Agent 有几十甚至几百个 Tool 时，如何控制 Tool Surface、给 Tool 注入请求上下文，并把长任务进度实时发给 UI？

本课聚焦三个机制：

```text
Tool Group
RuntimeContext / typed POJO injection
ToolEmitter
```

---

## 2. 总体心智模型

```text
                     ReActAgent
                         │
                 per-session AgentState
                         │
                         ▼
                 activated tool groups
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
    always-visible    database      deployment
       tools          group          group
          │              │              │
          └──────────────┴──────────────┘
                         │
                         ▼
                 ToolSchema[] 给 LLM
                         │
                         ▼
                    Tool Call
                         │
            ┌────────────┴────────────┐
            ▼                         ▼
      LLM JSON 参数            RuntimeContext 注入
                                      │
                               RequestProfile 注入
                                      │
                                      ▼
                                   Tool
                                      │
                         ┌────────────┴────────────┐
                         ▼                         ▼
                   ToolEmitter                final result
                   给 UI/Hook                  给 LLM
```

---

## 3. Tool Group 解决什么问题

假设平台有：

```text
数据库工具 20 个
Kubernetes 工具 15 个
GitHub 工具 20 个
报表工具 30 个
运维工具 15 个
```

如果每次都把 100 个 Tool Schema 发给模型：

```text
Prompt 变长
Token 成本增加
模型选错 Tool 的概率增加
工具说明互相干扰
```

Tool Group 的思路是：

```text
注册 100 个 Tool
       ↓
当前只激活 database
       ↓
模型本轮只看到当前真正需要的 schema
```

---

## 4. ToolGroupScope：2.0.1 只有 META / EXTERNAL

这是一个容易被旧资料误导的地方。

AgentScope Java 2.0.1 的 `ToolGroupScope` 是：

```text
META
EXTERNAL
```

### META

由 Agent 的 meta tool 管理。

```text
reset_equipped_tools
```

可以动态激活/关闭。

### EXTERNAL

由业务代码管理。

meta tool 看不到，也不会改变。

不要把旧文档里的 SESSION 之类枚举直接套到 2.0.1。

---

## 5. Step 1：定义两个默认关闭的 Group

```java
ToolGroup database = ToolGroup.builder()
        .name("database")
        .description("Read-only database lookup tools")
        .active(false)
        .scope(ToolGroupScope.META)
        .build();

ToolGroup deployment = ToolGroup.builder()
        .name("deployment")
        .active(false)
        .scope(ToolGroupScope.META)
        .build();
```

注册：

```java
toolkit.registerToolGroup(database);
toolkit.registerToolGroup(deployment);
```

---

## 6. Step 2：把 Tool 注册进 Group

不是：

```java
toolkit.registerTool(databaseTools);
```

而是：

```java
toolkit.registration()
        .tool(databaseTools)
        .group("database")
        .apply();
```

部署工具同理。

于是：

```text
db_lookup
    ↓
database group
```

当 database 未激活：

```text
getToolNames()      -> 仍然注册着
getToolSchemas()    -> 不向模型暴露 db_lookup
```

这两个概念一定要区分：

```text
Registered Tool != Visible Tool Schema
```

---

## 7. Step 3：开启 Meta Tool

Agent Builder：

```java
.enableMetaTool(true)
```

AgentScope 会自动注册：

```text
reset_equipped_tools
```

注意：2.0.1 的实际名字是：

```text
reset_equipped_tools
```

不是一些旧资料或早期规划里写的 `reset_tools`。

---

## 8. reset_equipped_tools 是替换语义

它的参数核心是：

```json
{
  "to_activate": ["database"]
}
```

意思不是：

```text
在原集合上额外加 database
```

而是：

```text
META Group 的最终激活集合 = [database]
```

比如之前：

```text
database + deployment
```

调用：

```json
{"to_activate":["database"]}
```

之后：

```text
database = active
deployment = inactive
```

EXTERNAL scope 不受影响。

---

## 9. 激活状态为什么属于 session state

ReActAgent 在新 session 创建时会把 builder 时的 active group 状态写入：

```text
AgentState
└── ToolContextState
    └── activatedGroups
```

模型调用前根据当前 session 的：

```java
state.getToolContext().getActivatedGroups()
```

计算当前 `ToolSchema[]`。

因此真正的心智模型是：

```text
alice/session-a
    database active

alice/session-b
    deployment active
```

而不是所有用户必须共享同一个工具集合。

---

## 10. /state 接口观察 Tool Surface

本课提供：

```text
GET /api/advanced-tooling/state
```

例如：

```bash
curl 'http://localhost:18081/api/advanced-tooling/state?userId=alice&sessionId=s1'
```

返回三个集合：

```text
activeGroups
visibleToolSchemas
registeredTools
```

重点比较：

```text
registeredTools
       ≠
visibleToolSchemas
```

---

## 11. RuntimeContext 自动注入

本节定义：

```java
@Tool(name = "who_am_i")
public String whoAmI(
    @ToolParam(name = "prefix") String prefix,
    RuntimeContext context,
    RequestProfile profile
)
```

只有：

```text
prefix
```

是 LLM 参数。

下面两个：

```text
RuntimeContext context
RequestProfile profile
```

是框架注入。

它们不会出现在 JSON Schema 里。

---

## 12. Step 4：给 RuntimeContext 放业务对象

Controller 创建：

```java
RequestProfile profile =
        new RequestProfile(request.tenant(), request.locale());

RuntimeContext context = RuntimeContext.builder()
        .userId(request.userId())
        .sessionId(request.sessionId())
        .put(RequestProfile.class, profile)
        .build();
```

Tool 执行时，AgentScope 根据类型找到：

```text
RequestProfile.class
```

自动传给方法。

这特别适合：

```text
tenant config
user profile
request credential
feature flags
request-scoped client
```

而不是让 LLM 自己生成这些敏感/可信参数。

---

## 13. 为什么 tenantId 不应该让模型传

错误：

```java
@Tool
void query(
    @ToolParam String tenantId,
    @ToolParam String sql
)
```

因为 `tenantId` 变成模型可控输入。

更合理：

```java
@Tool
void query(
    @ToolParam String sql,
    TenantContext tenant
)
```

其中 TenantContext 来自服务端 RuntimeContext。

安全边界变成：

```text
用户身份认证
    ↓
服务端创建 RuntimeContext
    ↓
Tool 自动注入
```

而不是：

```text
LLM 猜 tenantId
```

---

## 14. 还能注入什么

2.0.1 reflective Tool 支持的典型框架注入包括：

```text
RuntimeContext
AgentState
Agent
ToolEmitter
自定义 POJO
```

核心规则：

```text
@ToolParam
    -> 来自模型 JSON

特定未标注参数
    -> 来自框架上下文
```

---

## 15. ToolEmitter 是什么

有些 Tool 很慢：

```text
生成报告
拉取 1000 个文件
执行数据分析
运行部署流程
```

如果只有最终 return：

```text
开始
  ↓
30 秒无响应
  ↓
完成
```

UI 体验很差。

所以 Tool 可以声明：

```java
ToolEmitter emitter
```

然后：

```java
emitter.emit(ToolResultBlock.text("progress 25%"));
emitter.emit(ToolResultBlock.text("progress 75%"));
return ToolResultBlock.text("completed");
```

---

## 16. ToolEmitter 的一个关键边界

Emitter 的 chunk：

```text
发送给事件流 / Hook / UI
```

但不会作为 Tool 最终结果喂给 LLM。

只有：

```java
return ToolResultBlock...
```

是最终 Tool Result。

因此：

```text
ToolEmitter = progress channel
return value = semantic result
```

不要混在一起。

---

## 17. 本节 progress_task

```java
@Tool(name = "progress_task")
public ToolResultBlock run(
        @ToolParam(name = "task") String task,
        ToolEmitter emitter
) {
    emitter.emit(ToolResultBlock.text("progress 25%: ..."));
    emitter.emit(ToolResultBlock.text("progress 75%: ..."));
    return ToolResultBlock.text("completed: " + task);
}
```

用 `/stream` 调用时，可以观察 Tool Result 的增量事件。

---

## 18. 启动

```bash
export DASHSCOPE_API_KEY="你的 DashScope API Key"
./mvnw -pl 30-AdvancedTooling spring-boot:run
```

---

## 19. 实验一：Context 自动注入

```bash
curl -X POST http://localhost:18081/api/advanced-tooling/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"tool-demo",
    "tenant":"acme",
    "locale":"zh-CN",
    "message":"调用 who_am_i 告诉我当前请求身份，prefix 使用 identity:"
  }'
```

模型只负责生成：

```json
{"prefix":"identity:"}
```

而：

```text
alice
session
tenant=acme
locale=zh-CN
```

来自服务端 RuntimeContext。

---

## 20. 实验二：动态 Tool Group

先看：

```bash
curl 'http://localhost:18081/api/advanced-tooling/state?userId=alice&sessionId=group-demo'
```

database/deployment 默认 inactive。

然后：

```bash
curl -X POST http://localhost:18081/api/advanced-tooling/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"group-demo",
    "tenant":"acme",
    "locale":"zh-CN",
    "message":"查询业务记录 O-1001。先使用 reset_equipped_tools 激活 database，再调用 db_lookup。"
  }'
```

再看 state。

应该能观察：

```text
activeGroups
```

以及当前模型可见 schema 的变化。

---

## 21. 实验三：ToolEmitter

```bash
curl -N -X POST http://localhost:18081/api/advanced-tooling/stream \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"alice",
    "sessionId":"progress-demo",
    "tenant":"acme",
    "locale":"zh-CN",
    "message":"必须调用 progress_task 执行 index documents，并展示执行进度"
  }'
```

重点观察 Tool Result 的 delta/chunk 事件。

---

## 22. 自动化测试

```bash
./mvnw -pl 30-AdvancedTooling test
```

完全不调用模型。

测试一验证：

```text
JSON Schema 只有 prefix

RuntimeContext / RequestProfile
不出现在 Schema
但执行时能正确注入
```

测试二验证：

```text
ToolEmitter chunks = 2
final result = 单独的 completed
```

测试三验证：

```text
inactive database
    -> db_lookup schema 不可见

activate database
    -> db_lookup schema 可见

registerMetaTool
    -> reset_equipped_tools 存在
```

---

## 23. ToolBase 什么时候用

注解式 `@Tool` 足够适合大多数业务方法。

需要以下能力时可以进一步继承 `ToolBase`：

```text
自定义 PermissionDecision
自定义 matchRule
自定义 suggestions
手写 JSON Schema
external execution
完全异步 callAsync
```

下一课 External Tool 会直接使用这部分能力。

---

## 24. 其它高级 @Tool 属性

2.0.1 还有：

```text
stateInjected
converter
dangerousFiles
dangerousDirectories
externalTool
readOnly
concurrencySafe
```

这里不为了“覆盖名词”全部塞进一个例子。

本节先把最影响架构设计的：

```text
Tool Surface
Trusted Context Injection
Progress Streaming
```

学透。

---

## 25. 与已有课程的关系

```text
03 ToolCalling
    ↓
基本 Java Tool

08 PermissionHITL
    ↓
Tool 权限

19 MCPAndToolsConfig
    ↓
外部 Tool Provider

25 ExecutionResilience
    ↓
Tool timeout/retry

30 AdvancedTooling
    ↓
Tool Surface + Context Injection + Progress

31 ExternalToolAndHITL
    ↓
Tool 不在 Agent JVM 内执行
```

---

## 26. 一句话总结

```text
ToolGroup 决定“模型现在看得到什么能力”，
RuntimeContext 决定“服务端可信上下文怎么进入 Tool”，
ToolEmitter 决定“Tool 执行过程中怎么持续把进度告诉外部世界”。
```
