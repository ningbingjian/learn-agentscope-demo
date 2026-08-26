# Java / Spring Boot Review Checklist

## Controller
- Controller 是否只负责协议转换和参数入口？
- 是否把 SQL、复杂业务规则直接写进 Controller？
- 输入是否做 Bean Validation / 边界校验？
- HTTP 状态码和错误响应是否清晰？

## Service
- 事务边界是否合理？
- 是否混合基础设施细节和业务规则？
- 异常是否被吞掉或过度 catch？

## Data Access
- 是否存在 N+1、无索引查询或无界查询？
- SQL 是否参数化？
- Repository 是否泄漏数据库实现细节到上层？

## General
- null / empty / timeout / retry / idempotency 边界是否明确？
- 是否有对应自动化测试？
- 是否为了“设计模式”而过度抽象？
