# 29-ModelLayerAndRegistry：Model、Registry 与 Provider SPI

## 1. 前面我们一直把 Model 当成一个 Bean

过去大部分课程都是：

```java
@Bean
ReActAgent agent(Model model) {
    return ReActAgent.builder()
            .model(model)
            .build();
}
```

这很适合先学习 Agent。

但如果继续往平台化走，会马上遇到：

```text
用户 A -> OpenAI
用户 B -> DashScope
用户 C -> 自建 vLLM
用户 D -> Ollama
```

此时必须真正理解 AgentScope 的 Model 层。

---

## 2. Model 层的结构

```text
Credential
   ↓
Provider-specific ChatModel
   ↓
Model 接口
   ↓
ReActAgent
```

Core 不应该直接依赖每一家 SDK。

因此 AgentScope 把具体 Provider 放到扩展模块：

```text
agentscope-extensions-model-openai
agentscope-extensions-model-dashscope
agentscope-extensions-model-anthropic
agentscope-extensions-model-gemini
agentscope-extensions-model-ollama
...
```

---

## 3. Model 最小契约

本节自己实现：

```java
public final class LessonEchoModel implements Model
```

真正核心的方法是：

```java
Flux<ChatResponse> stream(
    List<Msg> messages,
    List<ToolSchema> tools,
    GenerateOptions options
)
```

以及：

```java
String getModelName()
```

所以对 Agent 来说：

```text
DashScopeChatModel
OpenAIChatModel
LessonEchoModel
```

最终都只是：

```text
Model
```

---

## 4. 为什么本节不用真实 API Key

本节重点不是某家模型 SDK，而是：

```text
Model abstraction
ModelRegistry
ModelCreationContext
ModelProvider SPI
cache policy
```

因此使用仓库自己的 `LessonEchoModel`。

这样：

```bash
./mvnw -pl 29-ModelLayerAndRegistry spring-boot:run
```

不配置任何模型 API Key 也可以完整实验。

---

## 5. ModelRegistry 解决什么问题

不用 Registry：

```java
new OpenAIChatModel(...)
new DashScopeChatModel(...)
```

业务层需要认识每个具体 Provider。

使用 Registry：

```java
Model model = ModelRegistry.resolve("provider:model-name");
```

业务层只认识字符串 ID。

例如官方 Provider 可以使用：

```text
openai:gpt-4.1-mini
dashscope:qwen-plus
ollama:llama3
```

本课使用：

```text
lesson:echo
```

---

## 6. ModelRegistry 的解析优先级

核心顺序：

```text
resolve(modelId)
      ↓
1. named model
      ↓
2. cache
      ↓
3. user registered factory
      ↓
4. Java SPI ModelProvider
      ↓
5. cannot resolve
```

所以 Registry 同时适用于：

- Spring/Application 手工注册实例；
- 平台动态模型工厂；
- AgentScope 官方 Provider 扩展；
- 自己开发的模型扩展包。

---

## 7. Step 1：实现一个 ModelProvider

```java
public final class LessonModelProvider implements ModelProvider {

    @Override
    public String providerId() {
        return "lesson";
    }

    @Override
    public boolean supports(String modelId) {
        return modelId.startsWith("lesson:");
    }

    @Override
    public Model create(String modelId, ModelCreationContext context) {
        return new LessonEchoModel(modelId, context);
    }
}
```

这就是一个真正的 Provider adapter。

---

## 8. Step 2：注册 Java SPI

本节最重要的文件之一：

```text
src/main/resources/
└── META-INF/
    └── services/
        └── io.agentscope.core.model.spi.ModelProvider
```

内容：

```text
com.example.agentscope.modelregistry.model.LessonModelProvider
```

没有：

```java
new LessonModelProvider()
```

也没有：

```java
ModelRegistry.registerFactory(...)
```

AgentScope 会通过：

```java
ServiceLoader<ModelProvider>
```

发现它。

这就是 Java SPI 的真实使用场景。

---

## 9. Step 3：直接 resolve

```java
Model model = ModelRegistry.resolve("lesson:echo");
```

Registry 会：

```text
扫描 ModelProvider
      ↓
LessonModelProvider.supports("lesson:echo")
      ↓
true
      ↓
create(...)
      ↓
LessonEchoModel
```

---

## 10. canResolve()

有时候平台只想先判断：

```text
这个 model id 有没有 Provider？
```

而不想真正创建模型。

使用：

```java
ModelRegistry.canResolve("lesson:echo")
```

这很适合：

- 配置校验；
- 模型列表页面；
- 启动自检；
- 用户提交模型 ID 前预验证。

---

## 11. Named Model

除了 Provider，还可以直接注册一个实例：

```java
ModelRegistry.register("my-model", model);
```

以后：

```java
ModelRegistry.resolve("my-model")
```

直接返回这个对象。

Named Model 优先级最高。

适合：

```text
Spring 已经帮你构造好的 Model Bean
      ↓
注册一个业务名称
      ↓
后续统一用 Registry 获取
```

---

## 12. User Factory

还有一个中间层：

```java
ModelRegistry.registerFactory(
    "tenant:.*",
    (modelId, context) -> ...
);
```

它比 SPI Provider 优先。

可以理解成：

```text
ModelProvider SPI
= 第三方/扩展模块能力

registerFactory
= 应用自己的动态覆盖能力
```

---

## 13. ModelCreationContext

只传：

```text
openai:gpt-x
```

还不够解决多租户平台。

用户 A/B 可能使用不同：

```text
API Key
Base URL
stream
thinking
proxy / formatter / options
```

因此还有：

```java
ModelCreationContext
```

本课接口会构造：

```java
ModelCreationContext.builder()
        .baseUrl(...)
        .stream(...)
        .cachePolicy(...)
        .cacheId(...)
        .build();
```

然后：

```java
ModelRegistry.resolve(modelId, context)
```

---

## 14. ModelCreationContext 的标准字段

重点包括：

```text
apiKey
baseUrl
endpointPath
stream
enableThinking
cachePolicy
cacheId
options
components
```

其中：

```text
options / components
```

是 Provider 自己的高级扩展入口。

Core 不需要知道：

```text
OpenAI formatter
DashScope 特殊参数
某个 HTTP Transport
```

具体 Provider 自己读取。

---

## 15. API Key 为什么不能进日志

本课测试专门验证：

```java
context.toString()
```

不会输出真实 API Key。

而是：

```text
apiKey=[REDACTED]
```

平台开发中不要为了 Debug 把整个凭证对象手工序列化到日志。

---

## 16. CachePolicy

这是很容易忽略但非常关键的能力。

### DEFAULT

空 Context：

```text
按 modelId cache
```

非空 Context：

```text
默认不 cache
```

这是为了避免：

```text
Tenant A 的 API Key / BaseURL
       ↓
缓存 Model
       ↓
Tenant B 意外复用
```

### DISABLED

```text
永远不缓存
```

### ENABLED

显式开启缓存。

多租户平台推荐同时提供：

```java
.cacheId("tenant-a")
```

让缓存身份明确。

---

## 17. 为什么 options/components 开缓存时要求 cacheId

例如：

```java
.option("specialConfig", someObject)
```

Core 不知道：

```text
someObject 到底怎么比较相等
```

也不能随便：

```text
hashCode()
```

因为可能不稳定或泄露配置。

所以复杂 Context 想缓存：

```text
必须明确给 cacheId
```

---

## 18. Step 4：通过 Registry 创建 Agent

本节 `/chat` 并不是把 Model 写死在构造函数里。

而是：

```java
Model model = ModelRegistry.resolve(modelId, context);

try (ReActAgent agent = ReActAgent.builder()
        .name("model-registry-agent")
        .model(model)
        .build()) {
    ...
}
```

这就是平台常见结构：

```text
HTTP Request
   ↓
modelId + tenant config
   ↓
ModelRegistry
   ↓
Model
   ↓
Agent Factory
   ↓
call()
```

---

## 19. 为什么每个请求 new Agent，但 Model 可以复用

`ReActAgent` 本身有执行状态与并发约束。

而模型对象更适合作为共享底层资源。

因此平台型代码经常是：

```text
Model / Toolkit / StateStore
      = 可共享基础设施

Agent
      = 请求/任务执行实例
```

HarnessAgent 有自己的并发设计，具体还要看使用场景。

---

## 20. 启动

```bash
./mvnw -pl 29-ModelLayerAndRegistry spring-boot:run
```

不需要 API Key。

---

## 21. 实验一：SPI 自动发现

```bash
curl 'http://localhost:18081/api/model-registry/resolve?modelId=lesson:echo'
```

重点：

```text
canResolve = true
modelClass = LessonEchoModel
```

而项目里没有手工 new Provider。

---

## 22. 实验二：非空 Context 默认不缓存

```bash
curl 'http://localhost:18081/api/model-registry/resolve?modelId=lesson:echo&baseUrl=https://tenant-a.example.invalid'
```

观察：

```text
sameInstanceOnSecondResolve = false
```

---

## 23. 实验三：显式 cacheId

```bash
curl 'http://localhost:18081/api/model-registry/resolve?modelId=lesson:echo&baseUrl=https://tenant-a.example.invalid&cache=true&cacheId=tenant-a'
```

观察：

```text
sameInstanceOnSecondResolve = true
```

---

## 24. 实验四：真正通过 Registry 驱动 Agent

```bash
curl -X POST http://localhost:18081/api/model-registry/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "modelId":"lesson:echo",
    "baseUrl":"https://tenant-a.example.invalid",
    "message":"hello model registry"
  }'
```

返回会包含 Lesson Model 的 deterministic 输出。

---

## 25. 自动化测试

```bash
./mvnw -pl 29-ModelLayerAndRegistry test
```

不调用外部模型。

测试验证：

```text
ServiceLoader 能发现 Provider
空 Context 按 modelId 缓存
非空 Context 默认不缓存
ENABLED + cacheId 可缓存
API Key 不以明文出现在 toString()
```

---

## 26. 官方 Provider 和本课 Provider 有什么区别

机制一样：

```text
META-INF/services
      ↓
ModelProvider
      ↓
ModelRegistry
```

差异只是官方 Provider 真正连接：

```text
OpenAI
DashScope
Gemini
Anthropic
Ollama
...
```

本课 Provider 只返回本地 deterministic model，方便把机制看透。

---

## 27. 本课边界

本课不逐个学习每一家模型 Provider 的专属配置。

后续 `MultiModelProviders` 再系统比较：

- OpenAI；
- DashScope；
- Anthropic；
- Gemini；
- Ollama；
- DeepSeek/Kimi 等 OpenAI-compatible Provider。

---

## 28. 一句话总结

```text
Model 是统一推理接口，
ModelProvider 是模型适配器 SPI，
ModelRegistry 是根据 modelId + creation context 动态找到/创建 Model 的注册中心。
```
