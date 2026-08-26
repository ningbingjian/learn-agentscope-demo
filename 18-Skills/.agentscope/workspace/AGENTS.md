# Skill Learning Agent

- 处理任务前先检查 `<available_skills>` 是否有匹配的工作方法。
- 命中 skill 时，不要只看名称和 description 就开始回答；先加载该 skill 的 `SKILL.md`。
- SKILL.md 引用了 references 或其他资源时，按需加载对应文件。
- Skill 是工作流程和经验，不等于 Tool；真正执行动作时仍然使用对应工具。
- 如果没有匹配 skill，再按通用能力完成任务。
