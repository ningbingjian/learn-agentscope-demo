# 48-AdvancedPermissionAndSecurity

第 08 课解决的是：**遇到 ASK 后如何做人机确认并恢复 Agent**。

本课继续往下拆：**Permission Engine 到底为什么给出 ALLOW / DENY / ASK？**

## 1. 五种 PermissionMode

AgentScope Java 2.0.1：

```text
DEFAULT
ACCEPT_EDITS
EXPLORE
BYPASS
DONT_ASK
```

含义：

```text
DEFAULT
未命中规则时默认 ASK

ACCEPT_EDITS
偏开发场景；read-only 自动放行，文件编辑结合工作目录策略

EXPLORE
只读探索：read-only ALLOW，mutation DENY

BYPASS
普通操作默认 ALLOW，但不能理解成“关闭所有安全检查”

DONT_ASK
无人值守：本来需要 ASK 的操作直接 DENY
```

## 2. PermissionEngine 真正的顺序

2.0.1 源码顺序：

```text
1. Deny Rules
2. Ask Rules
3. Tool built-in check
4. Allow Rules
5. BYPASS fallback
6. Default ASK / DONT_ASK -> DENY
```

所以：

```text
deny > ask > built-in check > allow > bypass > default
```

## 3. 为什么 BYPASS 仍然不等于无安全

本课 `deploy_service` 自定义 Tool check：

```text
target=prod-*
→ ASK (safety)

target=forbidden-*
→ DENY
```

即使 PermissionMode.BYPASS：

```text
prod-api      → ASK
forbidden-root → DENY
normal target → ALLOW
```

这就是 bypass-immune built-in check。

## 4. ToolBase.checkPermissions

自定义 Tool 可以覆盖：

```java
public Mono<PermissionDecision> checkPermissions(
    Map<String,Object> input,
    PermissionContextState context)
```

返回：

```text
ALLOW
DENY
ASK
PASSTHROUGH
```

PASSTHROUGH 表示：

```text
Tool 自己不做最终判断
→ 继续走 Rule / Mode
```

## 5. 危险路径保护

`ToolBase` 内置危险路径判断基础设施。

本课 `write_file` 额外声明：

```text
dangerousFiles:
.env
secrets.txt

dangerousDirectories:
.ssh
.kube
```

然后 Tool 的自检把命中的路径转成 safety ASK。

因此：

```text
PermissionMode.BYPASS
+
path=.env

仍然 ASK
```

安全策略应该绑定真实 Tool Input，而不是只靠一个全局开关。

## 6. Rule

```java
new PermissionRule(
    "deploy_service",
    "prefix:dev-",
    PermissionBehavior.ALLOW,
    "lesson"
)
```

字段：

```text
toolName
ruleContent
behavior
source
```

`ruleContent` 怎么解释由 Tool 的 `matchRule()` 决定。

本课定义：

```text
prefix:dev-
```

表示 target 以 `dev-` 开头。

## 7. Rule 优先级实验

同时配置：

```text
DENY
ASK
ALLOW
```

同一个 Tool 全部命中：

```text
结果 = DENY
```

只有 ASK + ALLOW：

```text
结果 = ASK
```

证明 deny/ask 的优先级不能被普通 allow 覆盖。

## 8. 动态 suggested rule

第一次：

```text
DEFAULT
+
dev-api
+
没有 rule

→ ASK
```

用户如果接受建议规则，可通过：

```java
engine.addRule(... ALLOW ...)
```

添加：

```text
prefix:dev- → ALLOW
```

下一次相同类别调用：

```text
→ ALLOW
```

第 08 课里的 `ConfirmResult.acceptedRules` 最终就是为了支持类似的运行时策略学习。

## 9. withMode

`PermissionContextState` 是不可变对象。

要从 DEFAULT 切 BYPASS：

```java
PermissionContextState next = current.withMode(PermissionMode.BYPASS);
```

已有 working directories 和 rules 都保留。

## 10. 为什么 DONT_ASK 很重要

计划任务、后台 Agent、无人值守任务没有真人等着点按钮。

错误做法：

```text
没人回答
→ 永久挂起
```

更安全：

```text
DONT_ASK
→ 所有需要人工确认的默认行为直接 DENY
```

这和第 44 课 Scheduler 能直接组合。

## 11. API

启动：

```bash
./mvnw -pl 48-AdvancedPermissionAndSecurity spring-boot:run
```

查看五种 Mode：

```bash
curl http://localhost:18081/api/permission/modes
```

查看决策矩阵：

```bash
curl http://localhost:18081/api/permission/matrix
```

查看 Rule 优先级：

```bash
curl http://localhost:18081/api/permission/precedence
```

查看动态添加规则：

```bash
curl http://localhost:18081/api/permission/dynamic-rule
```

## 12. 自动化测试

```bash
./mvnw -pl 48-AdvancedPermissionAndSecurity test
```

测试直接调用真实 `PermissionEngine`，不依赖 LLM，因此能稳定验证权限算法本身。

## 13. 本课结论

生产 Agent 的安全不能只有一个 `if (admin)`：

```text
Security
=
Mode
+ Rule
+ Tool self-check
+ dangerous input protection
+ HITL
+ unattended policy
+ audit
```

第 08 课学“ASK 后怎么恢复”，第 48 课学“为什么是 ASK”。两节合起来才是完整权限体系。
