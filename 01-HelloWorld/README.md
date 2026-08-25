# 01-HelloWorld

本节通过一个最小的 Spring Boot 接口，学习 AgentScope 的基本调用链：

```text
HTTP 请求 -> Controller -> ReActAgent -> Model -> Msg -> JSON 响应
```

## 学习目标

完成本节后，你应该能够理解：

- Spring Boot Starter 如何创建模型对象。
- 如何创建一个最小的 `ReActAgent`。
- 如何向 Agent 发送消息并取得回复。
- `Model`、`ReActAgent`、`Msg` 分别负责什么。

## 理论基础

### 大模型不等于 Agent

大模型可以根据输入生成内容，但它本身只负责推理。一个 Agent 还需要组织提示词、维护上下文、决定是否调用工具，并控制一次任务何时结束。

在本节中，各对象的职责如下：

| 对象 | 职责 |
| --- | --- |
| `Model` | 对接 DashScope，把消息发送给大模型并接收生成结果 |
| `ReActAgent` | 组织 system prompt、上下文和模型调用，执行推理循环 |
| `Msg` | 表示一次完整的用户输入或 Agent 回复 |
| `RuntimeContext` | 描述本次调用所属的用户、会话等运行时身份 |

因此，应用代码通常调用 Agent，而不是直接调用 Model。

### 什么是 ReAct

ReAct 是 Reasoning（推理）和 Acting（行动）的组合。它的基本循环是：

```text
接收任务
   ↓
模型推理
   ↓
是否需要工具？ ── 否 ──> 生成最终回复
   │
   是
   ↓
执行工具并取得结果
   ↓
把工具结果交还模型，继续推理
```

普通的一次模型调用通常是“输入 -> 输出”。ReAct Agent 则可以在得到最终答案之前，多次进行“推理 -> 工具调用 -> 观察结果”。这使它能够查询外部信息或执行实际操作。

本节没有注册工具，所以循环会退化成最简单的路径：

```text
用户消息 -> 模型推理 -> 最终回复
```

虽然当前示例很简单，但使用的仍然是后续工具调用章节所依赖的同一个 `ReActAgent` 推理内核。

### AgentScope 的消息模型

AgentScope 使用 `Msg` 表示一个完整对话轮次。消息不只是一段字符串，还包含：

- 角色，例如 `USER`、`ASSISTANT`、`SYSTEM`。
- 一个或多个 `ContentBlock`，例如文本、图片或工具调用。
- 消息 ID、时间、元数据和 Token 用量。

`call()` 最终返回一条完整的 assistant `Msg`。本节只关心文本，因此使用：

```java
reply.getTextContent()
```

把所有 `TextBlock` 提取并拼接成字符串。

### RuntimeContext 是什么

`RuntimeContext` 描述“一次调用属于谁、属于哪个会话”。后续可以在其中设置 `userId` 和 `sessionId`，实现不同用户、不同会话之间的状态隔离。

本节使用：

```java
RuntimeContext.empty()
```

表示暂不提供明确的用户和会话身份。本节只关注一次问答，不验证会话恢复；`02-SessionMemory` 会专门讲解这个概念。

### 为什么可以把 Agent 声明成单例 Bean

Spring 默认创建单例 Bean。AgentScope Java 2.x 把每次调用的可变状态放在运行时上下文中，因此同一个 Agent 实例可以被多个请求复用。

示例中的职责关系是：

```text
Spring 容器
├── Model Bean       # Starter 自动创建
└── ReActAgent Bean  # 注入并复用 Model
```

### Mono 和 block

AgentScope 基于 Reactor，`agent.call(...)` 返回 `Mono<Msg>`，表示一个稍后完成的异步结果。

本节使用传统 Spring MVC Controller，因此调用 `.block()`，把异步结果转换成当前请求线程中的同步等待。这种写法适合第一个示例，便于观察完整调用链；后续学习流式输出时会直接处理事件流。

## 项目结构

```text
01-HelloWorld
├── pom.xml
└── src/main
    ├── java/com/example/agentscope/helloworld
    │   ├── HelloWorldApplication.java
    │   ├── config/AgentConfiguration.java
    │   └── web/ChatController.java
    └── resources/application.yml
```

## 核心依赖

`pom.xml` 中使用了三个主要依赖：

- `spring-boot-starter-web`：提供 HTTP 服务。
- `agentscope-core`：提供 `ReActAgent` 等核心 API。
- `agentscope-dashscope-spring-boot-starter`：创建 DashScope `Model` Bean。

## 1. 配置模型

`application.yml` 指定模型提供商和模型名称：

```yaml
agentscope:
  model:
    provider: dashscope
  dashscope:
    api-key: ${DASHSCOPE_API_KEY}
    model-name: qwen-plus
    stream: true
```

Spring Boot 启动时，DashScope Starter 会读取这些配置并创建一个 `Model` Bean。`${DASHSCOPE_API_KEY}` 表示从同名环境变量获取真实 Key，避免把密钥写入代码仓库。

## 2. 创建 ReActAgent

`AgentConfiguration` 把 Starter 创建的 `Model` 注入 Agent：

```java
@Bean(destroyMethod = "close")
ReActAgent helloWorldAgent(Model model) {
    return ReActAgent.builder()
            .name("hello-world")
            .sysPrompt("你是一个简洁、友好的 AI 助手。")
            .model(model)
            .build();
}
```

这里最重要的三个配置是：

- `name`：Agent 的名称。
- `sysPrompt`：Agent 的角色与行为说明。
- `model`：实际执行推理的模型。

`destroyMethod = "close"` 表示 Spring Boot 关闭时同时释放 Agent 使用的资源。

## 3. 调用 Agent

Controller 中的核心代码只有一行：

```java
Msg reply = agent.call(request.message(), RuntimeContext.empty()).block();
```

执行过程：

1. `request.message()` 取得用户输入。
2. `RuntimeContext.empty()` 表示本节暂不使用用户和会话上下文。
3. `agent.call(...)` 返回 Reactor `Mono<Msg>`。
4. `.block()` 在当前 Spring MVC 请求线程中等待模型完成。
5. `reply.getTextContent()` 提取回复中的文本内容。

接口最后返回：

```json
{
  "reply": "模型生成的回复"
}
```

## 请求参数

请求地址：

```text
POST http://localhost:18081/api/chat
```

请求体：

```json
{
  "message": "你好，请用一句话介绍 AgentScope"
}
```

## 启动

先在项目根目录切换到 JDK 21：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

设置环境变量并启动当前模块：

```bash
export DASHSCOPE_API_KEY="你的 DashScope API Key"
./mvnw -pl 01-HelloWorld spring-boot:run
```

## 测试

```bash
curl -X POST http://localhost:18081/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"你好，请用一句话介绍 AgentScope"}'
```

## 本节边界

本节只演示一次独立问答，没有设置 `userId` 和 `sessionId`，也不演示工作区和会话恢复。

下一节 `02-SessionMemory` 会改用 `HarnessAgent` 和 `RuntimeContext`，让 Agent 在相同会话中记住之前的内容。

## 延伸阅读

- [AgentScope Java：智能体](https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html)
- [AgentScope Java：消息与事件](https://java.agentscope.io/v2/zh/docs/building-blocks/message-and-event.html)
- [AgentScope Java：模型](https://java.agentscope.io/v2/zh/docs/building-blocks/model.html)
- [ReAct 原始论文](https://arxiv.org/abs/2210.03629)
