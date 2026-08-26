---
name: api-design
description: 当用户需要设计或评审 REST API、请求响应模型、错误码、幂等性和接口版本策略时使用。
---

# API Design

执行步骤：
1. 明确资源、操作和调用方，而不是先决定 URL。
2. 选择合适的 HTTP method 和 status code。
3. 定义 request / response DTO，不直接暴露数据库实体。
4. 明确校验规则、错误响应和业务错误码。
5. 对写操作检查幂等性、重复提交和重试语义。
6. 检查分页、过滤、排序、版本兼容和可观测字段。
7. 最后给出一组可直接实现的 endpoint 示例。

优先简单、稳定、可演进的接口，不为了“REST 纯度”牺牲业务可用性。
