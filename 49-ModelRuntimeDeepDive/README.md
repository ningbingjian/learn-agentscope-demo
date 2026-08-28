# 49-ModelRuntimeDeepDive

## 本课目标

前面的第 29 课解决了 `ModelRegistry / ModelProvider SPI`，第 37 课解决了多 Provider 路由；本课继续进入 Model Runtime 内部，回答：**AgentScope 如何把统一的 Msg、Tool 和生成参数翻译成不同厂商的请求，又如何把不同厂商响应还原成统一的 ChatResponse / ContentBlock？**

## 一、核心链路

```text
ReActAgent
   │
   ├── List<Msg>
   ├── List<ToolSchema>
   └── GenerateOptions
          │
          ▼
        Model
          │
          ▼
      Formatter
   ┌──────┼────────┐
   ▼      ▼        ▼
messages options   tools
   │      │        │
   └──────┴────────┘
          ▼
 Provider SDK / HTTP request
          │
          ▼
 Provider response
          │
      Formatter
          │
          ▼
     ChatResponse
          │
          ▼
TextBlock / ThinkingBlock / ToolUseBlock ...
```

`Formatter<TReq,TResp,TParams>` 在 2.0.1 负责四件事：`format()`、`parseResponse()`、`applyOptions()`、`applyTools()`。它是 Model 内部的 provider translation layer，而不是 Agent 业务逻辑。

## 二、GenerateOptions 不只是 temperature

2.0.1 的 `GenerateOptions` 同时承载常见生成参数和部分 provider/runtime 参数：

```text
temperature / topP / topK / seed
maxTokens / maxCompletionTokens
frequencyPenalty / presencePenalty
thinkingBudget / reasoningEffort
parallelToolCalls / toolChoice
cacheControl
responseFormat
stream
additionalHeaders / additionalBodyParams / additionalQueryParams
executionConfig
```

本模块配置：

```java
GenerateOptions.builder()
    .temperature(0.2)
    .maxTokens(512)
    .thinkingBudget(1024)
    .parallelToolCalls(false)
    .cacheControl(true)
    .seed(42L)
    .build();
```

并通过 `RuntimeInspectionModel` 直接观察这些值确实进入 `Model.stream(...)`。

## 三、Options layering

模型通常存在默认参数与单次请求参数两层。AgentScope 2.0.1 提供：

```java
GenerateOptions.mergeOptions(primary, fallback)
```

语义是逐字段覆盖：primary 非 null 时覆盖 fallback；Map 类型合并时 primary 的同名 key 优先。

示例：

```text
defaults: temperature=.7, parallelToolCalls=true, cacheControl=true
request : temperature=.2, thinkingBudget=2048

merged  : temperature=.2
          thinkingBudget=2048
          parallelToolCalls=true
          cacheControl=true
```

## 四、Thinking / Reasoning

`ThinkingBlock` 是 AgentScope 的统一 reasoning content block。不同 Provider 可能使用 completely different wire format，但进入 Agent Runtime 后统一为：

```java
ThinkingBlock.builder()
    .thinking("...")
    .build();
```

`thinkingBudget` 与 `reasoningEffort` 是两种不同 provider family 常见的控制方式。不要把“模型支持 thinking”理解成所有厂商拥有完全一样的 HTTP 字段；Formatter/Model extension 负责适配。

## 五、Prompt Cache

`GenerateOptions.cacheControl(true)` 表示开启 prompt caching 意图。2.0.1 文档说明 Formatter 可对支持的 Provider 在 system message 与请求尾部应用 cache-control 策略；也可以通过 message metadata 精确标记。

注意：**cacheControl 是请求语义，不保证任何 Provider 都支持，也不保证命中缓存。** 最终是否生效取决于具体 Model extension 和上游服务。

## 六、Parallel Tool Calls

`parallelToolCalls` 控制的是模型是否可以在一次 reasoning 中产生多 Tool Call；它与 Tool 自身的 `concurrencySafe` 不是一个概念：

```text
parallelToolCalls
= 模型层：是否允许一次生成多个 Tool Call

@Tool(concurrencySafe=false)
= 执行层：同一 Tool 的调用是否可以并发执行
```

第 30/47 课已经学习执行侧，本课补齐模型侧。

## 七、多模态统一模型

2.0.1 Core 已提供：

```text
TextBlock
ImageBlock
AudioBlock
VideoBlock
```

媒体 Source 支持 URL 与 Base64。本课只构造以下对象，不访问外网：

```java
ImageBlock.builder().source(new URLSource("https://example.invalid/demo.png", "image/png")).build();
AudioBlock.builder().source(new URLSource("https://example.invalid/demo.mp3", "audio/mpeg")).build();
VideoBlock.builder().source(new URLSource("https://example.invalid/demo.mp4", "video/mp4")).fps(2.0f).build();
```

真正调用不同模型时，是各 Provider Formatter 决定如何把这些统一 Block 转成自己的 request content。

## 八、本课为什么自己实现 DemoFormatter

直接使用 OpenAI/DashScope SDK 会把注意力带到厂商 DTO。本课实现：

```java
DemoFormatter implements Formatter<String, String, DemoParams>
```

让四个职责一眼可见：

1. `Msg -> provider request message`
2. `provider response -> ChatResponse`
3. `GenerateOptions -> provider request params`
4. `ToolSchema -> provider tool declarations`

理解这一层后，再看 OpenAI、DashScope、Anthropic Formatter 会容易很多。

## 九、RuntimeInspectionModel

本课的 Model 不联网，它只记录：

```text
lastOptions
lastToolCount
lastBlockTypes
```

然后返回一个 `ThinkingBlock + TextBlock`。因此测试可以稳定证明：

```text
ReActAgent.generateOptions
        ↓
Model.stream(... options)

Toolkit
        ↓
ToolSchema
        ↓
Model.stream(... tools)
```

## 十、接口实验

启动：

```bash
./mvnw -pl 49-ModelRuntimeDeepDive spring-boot:run
```

查看配置：

```bash
curl http://localhost:18081/api/model-runtime/options
```

查看多模态 Block：

```bash
curl http://localhost:18081/api/model-runtime/multimodal
```

查看 Formatter 演示：

```bash
curl http://localhost:18081/api/model-runtime/formatter
```

走一次真实 ReActAgent -> Model Runtime：

```bash
curl -X POST http://localhost:18081/api/model-runtime/call \
  -H 'Content-Type: application/json' \
  -d '{"message":"inspect runtime"}'
```

## 十一、测试

```bash
./mvnw -pl 49-ModelRuntimeDeepDive test
```

测试覆盖：

- GenerateOptions layering
- Formatter format / parse / applyOptions
- ReActAgent 将 GenerateOptions 与 ToolSchema 传给 Model
- ThinkingBlock 最终进入 Agent reply
- Text/Image/Audio/Video 统一 ContentBlock

## 十二、与前后课程的关系

```text
29 ModelRegistry / SPI
        ↓
37 Multi Provider
        ↓
49 Model Runtime ← 本课
        ↓
50 Runtime Extension
        ↓
51 Context Engineering
```

学完本课后，Model 不再只是一个 `model(model)` Bean，而是一条完整的 provider adaptation pipeline。
