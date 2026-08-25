# learn-agentscope-demo

用于跟随 AgentScope Java 2.x 官方文档逐节学习的 Spring Boot 多模块项目。

## 模块结构

```text
learn-agentscope-demo
├── 01-HelloWorld      # 最小 ReActAgent 问答，端口 18081
├── 02-SessionMemory   # HarnessAgent 会话记忆，端口 18081
├── 03-ToolCalling     # ReActAgent 调用 Java 工具，端口 18081
├── 04-StreamingEvents # AgentEvent 通过 SSE 流式输出，端口 18081
└── 05-MultiUserConcurrency # 同会话串行、不同会话并行，端口 18081
```

每个学习模块都是完整、可独立启动的 Spring Boot 服务，模块之间没有代码依赖。

## 环境要求

- JDK 17+（本机已有 JDK 21，推荐直接使用）
- Maven 3.9+（项目使用 Maven Wrapper，无需修改全局 Maven）
- DashScope API Key

## 配置 API Key

五个模块统一从环境变量读取 DashScope API Key。启动任一模块前执行：

```bash
export DASHSCOPE_API_KEY="你的 DashScope API Key"
```

环境变量只对当前终端会话生效，不会写入代码或提交到 GitHub。

## 编译

当前终端的默认 Java 是 11，先切换到本机已有的 JDK 21：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean verify
```

## 01-HelloWorld

```bash
./mvnw -pl 01-HelloWorld spring-boot:run
```

```bash
curl -X POST http://localhost:18081/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"你好，请用一句话介绍 AgentScope"}'
```

详细说明见 [`01-HelloWorld/README.md`](01-HelloWorld/README.md)。

## 02-SessionMemory

```bash
./mvnw -pl 02-SessionMemory spring-boot:run
```

详细的两轮会话测试见 [`02-SessionMemory/README.md`](02-SessionMemory/README.md)。

## 03-ToolCalling

```bash
./mvnw -pl 03-ToolCalling spring-boot:run
```

工具调用和观察方式见 [`03-ToolCalling/README.md`](03-ToolCalling/README.md)。

## 04-StreamingEvents

```bash
./mvnw -pl 04-StreamingEvents spring-boot:run
```

SSE 事件流测试见 [`04-StreamingEvents/README.md`](04-StreamingEvents/README.md)。

## 05-MultiUserConcurrency

```bash
./mvnw -pl 05-MultiUserConcurrency spring-boot:run
```

多用户并发与会话串行化测试见
[`05-MultiUserConcurrency/README.md`](05-MultiUserConcurrency/README.md)。
