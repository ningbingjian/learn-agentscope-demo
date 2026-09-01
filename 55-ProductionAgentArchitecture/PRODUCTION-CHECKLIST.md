# Production Checklist

第 55 课的本地切片可以运行，但 `productionReadyByDefault=false`。真正上线前至少逐项确认下面这些边界。

## 1. Request / Idempotency

- [ ] requestId 来自稳定业务语义，而不是每次重试重新生成。
- [ ] 所有可能产生副作用的入口在 Agent/Tool 执行前完成幂等 claim。
- [ ] 多 Pod 共享同一个 durable idempotency store。
- [ ] `PROCESSING` 有 lease / TTL / heartbeat 或其他过期机制。
- [ ] 有 reconciler/repair job 扫描 abandoned `PROCESSING`。
- [ ] 明确 `FAILED` 是否允许重试，以及重试是否复用原 requestId。

本课只能捕获 JVM 内正常抛出的异常并写 `FAILED`。如果 Pod 在 claim 后被 SIGKILL、节点宕机或进程崩溃，catch 不会执行，记录可能永久停留在 `PROCESSING`。生产必须额外设计租约和恢复流程。

## 2. Session / State

- [ ] 同 `(userId, sessionId)` 的并发策略明确。
- [ ] 多 Pod 不依赖单 JVM session serialization 作为分布式锁。
- [ ] AgentState 使用共享 Redis/MySQL 等持久化后端，或采用 session actor/queue。
- [ ] state write 有 version/CAS 或明确的锁策略。
- [ ] duplicate wakeup / webhook / MQ redelivery 可安全重复处理。

## 3. Model

- [ ] provider credentials 不进入 Prompt、Memory、RAG、Trace。
- [ ] timeout / retry / backoff 已配置。
- [ ] retry 与 Tool side effect 的关系明确。
- [ ] fallback model 的 Tool / Thinking / Structured Output 能力兼容。
- [ ] tenant quota、token budget、cost budget 已设置。

## 4. Retrieval / Memory / Context

- [ ] RAG 先做 tenant ACL/filter，再进入模型上下文。
- [ ] retrieved content 标记为 data，不获得 instruction authority。
- [ ] RAG topK、rerank、citation、cache 有策略。
- [ ] Conversation / Harness Memory / Business User Memory 分层。
- [ ] Context budget、Compaction、ToolResultEviction 已配置并有监控。

## 5. Tool / MCP / Permission

- [ ] 模型只看到最小必要 Tool surface。
- [ ] MCP Server provenance、版本、allowlist、auth scope 已审核。
- [ ] write/destructive Tool 有 Permission/HITL。
- [ ] Tool 参数做业务校验，不能只信 LLM 生成的 JSON。
- [ ] side-effect Tool 有自己的业务幂等与 reconciliation。

## 6. Sandbox / Network / Secrets

- [ ] Shell、用户代码、Code Interpreter 在隔离 Sandbox 中运行。
- [ ] Sandbox 不可用时 fail closed，绝不回退宿主机执行。
- [ ] filesystem root、CPU、memory、timeout 有限制。
- [ ] outbound network 有 egress allowlist / SSRF 防护。
- [ ] Secret 通过 Secret Manager / workload identity 在执行侧注入。
- [ ] Agent、Tool Result、Log、Trace 不返回 Secret 原值。

## 7. Evaluation / Security Gate

- [ ] Dataset 版本化并进入 CI/CD。
- [ ] Tool selection / arguments / final answer 有回归指标。
- [ ] RAG / Memory / Permission / HITL 有场景测试。
- [ ] prompt injection / malicious RAG / SSRF / cross-tenant 有 adversarial cases。
- [ ] latency / token / cost 有阈值。
- [ ] critical Eval/Security failure 会阻止 rollout。

## 8. Observability / SRE

- [ ] request → retrieval → agent → model → tool → persistence Trace 可串联。
- [ ] Metrics 至少覆盖 QPS、P95/P99、model/tool error、token/cost、task success。
- [ ] Log/Trace 做 PII/Secret redaction。
- [ ] readiness/liveness 与业务 Eval Gate 分开。
- [ ] SIGTERM 有 drain + graceful shutdown。
- [ ] SLO、告警、容量和 rollback 方案已定义。

## 9. Database / Deployment

- [ ] `prod` 使用共享 MySQL/Redis，而不是每 Pod H2。
- [ ] DB migration 在应用 Pod 启动前完成。
- [ ] 生产 `initialize-schema=false`。
- [ ] Secret 中只存引用/凭证，不提交仓库。
- [ ] HPA/PDB/resource request/limit 已按压测结果调整。

## 10. 最终判断

只有当上面的业务相关条目都明确回答后，才可以把：

```text
Demo Running
```

提升为：

```text
Production Ready
```

AgentScope Java 提供的是 Agent Runtime 的核心能力；生产可靠性还依赖应用架构、分布式一致性、安全、评估、可观测和 SRE。
