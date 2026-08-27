# 第 37 课：MultiModelProviders —— 多模型 Provider 与统一模型路由

> 本课目标：把第 29 课学过的 `ModelRegistry / ModelProvider SPI` 真正连接到 AgentScope Java 2.0.1 官方模型扩展，理解“换模型”和“换 Agent 框架”为什么不是一回事。

---

## 1. 这一课解决什么问题

前面的课程大多数使用 DashScope `qwen-plus`。真实系统里经常需要同时支持：

- OpenAI
- DeepSeek
- Kimi
- GLM
- MiniMax
- DashScope / Qwen
- Gemini
- Anthropic / Claude
- Ollama 本地模型

如果每增加一个模型就重新写一套 Agent 业务代码，模型层就失去了抽象意义。

AgentScope 2.0.1 的正确结构是：

```text
业务 Agent
   |
   v
Model
   |
   v
ModelRegistry
   |
   +-- named Model
   +-- user factory
   `-- ModelProvider SPI
          |
          +-- openai
          +-- deepseek
          +-- kimi
          +-- glm
          +-- minimax
          +-- dashscope
          +-- gemini
          +-- anthropic
          `-- ollama
```

本课不调用外部模型，因此不需要任何 API Key。我们只验证“Provider 是否已经被发现”和“model id 是否能被正确路由”。

---

## 2. Core 和 Provider Extension 的边界

`agentscope-core` 只提供共享契约：

```text
Model
ChatModelBase
Formatter
CredentialBase
ModelRegistry
ModelCreationContext
ModelProvider
```

真正的厂商实现放在独立扩展中：

| Provider | Maven artifact |
| --- | --- |
| OpenAI | `agentscope-extensions-model-openai` |
| DeepSeek | `agentscope-extensions-model-openai` |
| Kimi | `agentscope-extensions-model-openai` |
| GLM | `agentscope-extensions-model-openai` |
| MiniMax | `agentscope-extensions-model-openai` |
| DashScope | `agentscope-extensions-model-dashscope` |
| Gemini | `agentscope-extensions-model-gemini` |
| Anthropic | `agentscope-extensions-model-anthropic` |
| Ollama | `agentscope-extensions-model-ollama` |

为什么 DeepSeek / Kimi / GLM / MiniMax 共用 OpenAI extension？

因为这些 Provider 的适配实现复用了 `OpenAIChatModel` 的 OpenAI-compatible HTTP 形态，但各自仍有独立的：

- `ModelProvider`
- API Key 环境变量
- Base URL
- Formatter / 参数兼容处理

所以：

```text
同一个 Maven extension
!=
同一个 Provider
```

---

## 3. 第一步：同时引入五个官方模型扩展

本模块 `pom.xml` 同时加入：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-openai</artifactId>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-dashscope</artifactId>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-gemini</artifactId>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-anthropic</artifactId>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-model-ollama</artifactId>
</dependency>
```

这些模块内部通过 Java SPI 提供：

```text
META-INF/services/
└── io.agentscope.core.model.spi.ModelProvider
```

因此业务代码不需要 `new OpenAIModelProvider()`。

---

## 4. 第二步：直接观察 Java ServiceLoader

核心代码：

```java
ServiceLoader.load(ModelProvider.class)
    .forEach(provider -> {
        System.out.println(provider.providerId());
    });
```

这一步回答：

> 当前 JVM classpath 上到底有哪些模型 Provider？

本课通过：

```text
GET /api/models/providers
```

返回 Provider、artifact、示例 model id、凭证环境变量、SPI 实现类和 `ModelRegistry` 路由结果。

启动：

```bash
./mvnw -pl 37-MultiModelProviders spring-boot:run
```

测试：

```bash
curl http://localhost:18081/api/models/providers
```

你会看到类似：

```json
[
  {
    "id": "openai",
    "artifact": "agentscope-extensions-model-openai",
    "exampleModelId": "openai:gpt-4.1-mini",
    "credentialEnvironment": "OPENAI_API_KEY",
    "discoveredBySpi": true,
    "providerClass": "...OpenAIModelProvider",
    "resolvableByModelRegistry": true
  }
]
```

---

## 5. 第三步：理解 `provider:model`

推荐的动态模型 id 是：

```text
provider:model-name
```

例如：

```text
openai:gpt-4.1-mini
deepseek:deepseek-chat
kimi:moonshot-v1-8k
glm:glm-4
minimax:MiniMax-M2.1
dashscope:qwen-plus
gemini:gemini-2.0-flash
anthropic:claude-sonnet-4-20250514
ollama:llama3.2
```

可以调用：

```bash
curl 'http://localhost:18081/api/models/can-resolve?modelId=deepseek:deepseek-chat'
```

响应：

```json
{
  "modelId": "deepseek:deepseek-chat",
  "resolvable": true
}
```

注意 `canResolve()` **不会创建模型，也不会访问网络**。

它只回答：

```text
有没有 Provider 声明自己支持这个 id？
```

所以非常适合做配置校验。

---

## 6. `canResolve` 和 `resolve` 的区别

```java
ModelRegistry.canResolve("openai:gpt-4.1-mini");
```

只查路由。

而：

```java
Model model = ModelRegistry.resolve("openai:gpt-4.1-mini");
```

会真正让 Provider 创建 `Model`。

此时 OpenAI Provider 会读取：

```text
OPENAI_API_KEY
```

没有 Key 就会抛出错误。

因此本课自动化测试选择 `canResolve()`，做到：

```text
真实 SPI
真实 Provider
真实 ModelRegistry
无外部网络
无 Secret
```

---

## 7. 第四步：Agent 可以直接使用字符串 Model ID

当运行环境已经配置凭证：

```java
ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model("dashscope:qwen-plus")
    .build();
```

换 OpenAI：

```java
.model("openai:gpt-4.1-mini")
```

换 DeepSeek：

```java
.model("deepseek:deepseek-chat")
```

Agent 业务代码没有变化。

这就是模型层抽象真正产生价值的位置。

---

## 8. OpenAI-compatible 不等于 OpenAI Provider

例如：

```text
DeepSeek
Kimi
GLM
MiniMax
```

虽然底层共享 OpenAI-compatible 通信能力，但 AgentScope 为它们定义独立 Provider：

```text
deepseek:xxx
kimi:xxx
glm:xxx
minimax:xxx
```

好处是 Provider 可以分别控制：

- 默认 Base URL
- Credential 环境变量
- Formatter
- Thinking 参数
- Structured Output 能力
- context window

不要把所有 OpenAI-compatible 厂商都硬编码成：

```text
openai:xxx + 自己改 baseUrl
```

只有需要完全自定义端点时才这么做。

---

## 9. ModelCreationContext：多租户模型平台的关键

如果你的平台保存了每个租户自己的：

```text
API Key
Base URL
stream
thinking
```

不要修改全局环境变量。

应该：

```java
ModelCreationContext context = ModelCreationContext.builder()
    .apiKey(tenantApiKey)
    .baseUrl(tenantBaseUrl)
    .stream(true)
    .cacheId("tenant-a")
    .build();

Model model = ModelRegistry.resolve(
    "openai:gpt-4.1-mini",
    context
);
```

这样：

```text
同一个 model id
+ 不同 ModelCreationContext
=
不同租户配置
```

第 29 课已经讲了 context 和 cache，本课要把它理解成真实 Provider 的动态入口。

---

## 10. ModelRegistry 的解析优先级

2.0.1 的顺序是：

```text
ModelRegistry.resolve(modelId)
        |
        +-- 1. register(name, model)
        |
        +-- 2. resolved cache
        |
        +-- 3. registerFactory(regex, factory)
        |
        `-- 4. ModelProvider SPI
```

因此业务平台可以覆盖官方 Provider：

```java
ModelRegistry.registerFactory(
    "corp:.*",
    modelId -> createCompanyGatewayModel(modelId)
);
```

这非常适合公司内部统一 AI Gateway。

---

## 11. Spring Boot Starter 和 ModelRegistry 不要混为一谈

AgentScope 有两种常见方式。

### 方式 A：Registry 动态解析

```java
.model("openai:gpt-4.1-mini")
```

适合：

```text
动态模型平台
用户运行时切模型
多租户
插件系统
```

### 方式 B：Provider Spring Boot Starter

例如：

```text
agentscope-openai-spring-boot-starter
agentscope-dashscope-spring-boot-starter
agentscope-gemini-spring-boot-starter
agentscope-anthropic-spring-boot-starter
agentscope-ollama-spring-boot-starter
```

Starter 根据 `application.yml` 创建 Spring `Model` Bean。

适合：

```text
一个服务固定一个主要模型
配置驱动
Spring Bean 注入
```

Starter 并不是通过静态 `ModelRegistry.resolve()` 来创建模型。

---

## 12. Ollama 为什么特殊

Ollama 通常运行在本地：

```text
http://localhost:11434
```

所以：

```text
OLLAMA_BASE_URL
```

是可选的。

它适合学习：

- 无云端 API Key
- 私有模型
- 本地开发
- 数据不出本机

但是“本地”不代表“没有资源成本”——模型本身的内存/GPU/CPU 仍由你的机器承担。

---

## 13. 凭证速查

| Provider | 环境变量 |
| --- | --- |
| OpenAI | `OPENAI_API_KEY` |
| DeepSeek | `DEEPSEEK_API_KEY` |
| Kimi | `MOONSHOT_API_KEY` / `KIMI_API_KEY` |
| GLM | `ZAI_API_KEY` / `GLM_API_KEY` / `ZHIPUAI_API_KEY` |
| MiniMax | `MINIMAX_API_KEY` |
| DashScope | `DASHSCOPE_API_KEY` |
| Gemini | `GEMINI_API_KEY` |
| Anthropic | `ANTHROPIC_API_KEY` |
| Ollama | `OLLAMA_BASE_URL` 可选 |

Secret 不应该写入 Git。

---

## 14. 自动化测试验证什么

```bash
./mvnw -pl 37-MultiModelProviders test
```

测试分三层：

### 14.1 SPI

验证 `ServiceLoader` 真的发现 Provider。

### 14.2 Registry routing

验证九类 model id 都能 `canResolve()`。

### 14.3 Catalog

验证：

```text
SPI discovered = true
Registry resolvable = true
Provider class != blank
```

没有 mock Provider。

---

## 15. 本课最终心智模型

```text
               ReActAgent / HarnessAgent
                        |
                        v
                      Model
                        |
                        v
                  ModelRegistry
                        |
        +---------------+---------------+
        |               |               |
      OpenAI         DashScope       Anthropic
        |                               |
        +-- DeepSeek                   Claude
        +-- Kimi
        +-- GLM
        +-- MiniMax
        |
      Gemini
        |
      Ollama
```

关键结论：

> AgentScope 的 Agent 逻辑不应该绑定某一个模型厂商；Provider Extension 负责把不同模型世界统一成 `Model` 契约。

---

## 16. 与前面课程的关系

```text
29-ModelLayerAndRegistry
        |
        | 学底层抽象和自定义 SPI
        v
37-MultiModelProviders
        |
        | 接真实官方 Provider
        v
后续企业 Model Gateway / 多租户平台
```

第 29 课回答“ModelRegistry 是什么”。

第 37 课回答：

> 真正同时接九类模型时，它到底怎么工作？
