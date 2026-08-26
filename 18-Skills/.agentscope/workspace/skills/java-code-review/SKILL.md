---
name: java-code-review
description: 当用户需要评审 Java、Spring Boot、接口实现、服务层代码或 PR 设计时使用。
---

# Java Code Review

目标：用统一流程发现 Java / Spring Boot 代码中的可维护性、边界和工程风险。

执行步骤：
1. 先读取 `references/checklist.md`。
2. 识别被评审代码的职责层：Controller / Service / Repository / Infrastructure。
3. 先检查 correctness，再检查 architecture，不要先纠结格式问题。
4. 每个问题写清楚：问题 → 为什么 → 风险 → 修改建议。
5. 将问题分成 `BLOCKER / MAJOR / MINOR`。
6. 最后给出“建议合并 / 修改后合并 / 不建议合并”的结论。

输出不要泛泛而谈；优先给具体可执行的修改建议。
