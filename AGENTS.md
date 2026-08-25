# Project Guidelines

- 本项目用于逐节学习 AgentScope Java 2.x，文档官网：https://java.agentscope.io/v2/zh/docs/。
- 模块使用 `NN-TopicName` 命名，如 `01-HelloWorld`。
- 每个模块只学习一个主要知识点。
- 每个模块都是可独立启动、测试的 Spring Boot 服务。
- 模块之间不互相依赖，允许为便于学习而重复少量代码。
- 每个模块包含自己的 `pom.xml`、配置和启动类。
- 每个模块包含一份 `README.md` 学习文档，说明理论背景、核心概念、工作原理、核心代码、启动及测试方式。
- 根项目只管理模块和统一依赖版本。
- 修改后执行 `./mvnw clean verify`，并验证对应模块能独立启动。
- 每个独立模块都用一样的端口启动服务(18081)
