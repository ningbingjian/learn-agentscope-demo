# 42-SkillRepositoryBackends

本课把第 18 课和第 32 课的 Skills 继续推进到“技能从哪里来、谁负责版本与发布、多个 Agent 实例如何共享技能”。

前面我们主要使用：

```text
workspace/skills/<skill>/SKILL.md
```

这很适合单项目学习，但生产系统通常需要一个独立的 Skill Repository。

AgentScope Java 2.0.1 提供：

```text
Git
MySQL
PostgreSQL
Nacos
```

四类官方后端。

---

## 1. 本课学习目标

完成本课后应该能回答：

1. `AgentSkillRepository` 的职责是什么？
2. Workspace Skill 和 Repository Skill 有什么区别？
3. GitSkillRepository 为什么是只读的？
4. `autoSync` 是怎么避免每次都完整 pull 的？
5. MySQL/PostgreSQL 为什么更适合管理后台在线编辑？
6. Skill resources 如何跟 Skill 一起存储？
7. Nacos Skill Repository 适合什么场景？
8. Skill Repository 如何与 Toolkit/Harness 组合？
9. 多实例部署时 Git clone 和数据库中心化的取舍是什么？

---

## 2. 四种后端

| Backend | Artifact | 核心类 | 写能力 | 典型场景 |
| --- | --- | --- | --- | --- |
| Git | `agentscope-extensions-skill-git-repository` | `GitSkillRepository` | 只读 | Git PR 审核、版本化发布 |
| MySQL | `agentscope-extensions-skill-mysql-repository` | `MysqlSkillRepository` | CRUD | 管理后台在线运营 |
| PostgreSQL | `agentscope-extensions-skill-postgresql-repository` | `PostgresSkillRepository` | CRUD | PostgreSQL 技术栈 |
| Nacos | `agentscope-extensions-nacos-skill` | `NacosSkillRepository` | 中心化管理 | 已有 Nacos 基础设施 |

---

## 3. Repository 在架构中的位置

```text
Git / MySQL / PostgreSQL / Nacos
              │
              ▼
      AgentSkillRepository
              │
              ▼
          AgentSkill
              │
              ▼
           Toolkit
              │
              ▼
        Agent / Harness
```

Skill Repository 不负责 LLM 推理。

它负责：

```text
发现
读取
版本/来源
资源文件
可选写入/删除
```

---

## 4. GitSkillRepository

Git 是最容易理解的生产方式：

```text
skill author
    ↓
commit
    ↓
Pull Request review
    ↓
merge
    ↓
Agent instance sync
    ↓
new skill version
```

### 4.1 最小代码

```java
GitSkillRepository repo = new GitSkillRepository(
    "https://github.com/my-org/agent-skills.git"
);
```

读取：

```java
repo.getSkill("code-review");
repo.getAllSkillNames();
repo.getAllSkills();
repo.skillExists("code-review");
```

### 4.2 它为什么只读

官方实现：

```text
save(...)   → false
delete(...) → false
isWriteable → false
```

原因不是 Git 不能写，而是 AgentScope 把 Git Repository 定位成“经过 Git 工作流治理后的发布源”。

生产中不希望一个正在运行的 Agent 自己 push 到主技能仓库。

### 4.3 autoSync

默认：

```text
每次 read
    ↓
ls-remote
    ↓
remote HEAD 变了吗？
    ├── no  → 直接读本地 clone
    └── yes → pull
```

因此不是每次读取都完整 clone/pull。

可以关闭：

```java
new GitSkillRepository(remoteUrl, false);
```

再通过 scheduler 手动：

```java
repo.sync();
```

---

## 5. 本课的真实 Git 实验

测试不会访问 GitHub。

它会：

```text
JUnit @TempDir
    ↓
创建 local Git repository
    ↓
skills/demo/SKILL.md
    ↓
git add
    ↓
git commit
    ↓
GitSkillRepository(file://...)
    ↓
sync()
    ↓
clone
    ↓
getSkill("demo")
```

所以它验证的是真实 JGit clone + AgentScope Skill parser。

不是 mock。

核心构造：

```java
new GitSkillRepository(
    remote.toUri().toString(),
    null,
    clone,
    "lesson-git",
    false,
    "skills"
);
```

最后断言：

```text
skillExists("demo") = true
getAllSkillNames() = [demo]
AgentSkill.name = demo-skill
source = lesson-git
```

---

## 6. MySQL Skill Repository

MySQL 与 Git 的定位完全不同。

```text
Admin UI
   ↓
Save Skill
   ↓
MySQL
   ↓
Agent read
```

最小：

```java
MysqlSkillRepository repo = new MysqlSkillRepository(dataSource, true);
```

`true` 表示自动初始化 schema。

官方默认两张表：

```text
agentscope_skills
agentscope_skill_resources
```

Skill 表保存：

```text
name
description
skill_content
source
metadata_json
```

Resources 表保存：

```text
resource_path
resource_content
```

并通过外键级联删除。

### CRUD

```java
repo.save(List.of(skill), true);
repo.getSkill("code-review");
repo.getAllSkillNames();
repo.delete("code-review");
```

写入/删除走数据库事务。

---

## 7. PostgreSQL

PostgreSQL Repository 与 MySQL 的产品定位相近：

```text
central DB
+ CRUD
+ multi-instance shared source
```

如果企业已经统一 PostgreSQL，就没有必要为了 Skill 再专门引入 MySQL。

重点不是“哪个数据库更适合 AI”，而是复用已有可靠基础设施。

---

## 8. Nacos

Nacos Skill Repository 适合：

```text
已经有 Nacos
希望 Skill 像配置一样中心化
多个 Agent 实例动态读取
```

它属于：

```text
AgentScope
   ↓
agentscope-extensions-nacos-skill
   ↓
Nacos
```

后面企业基础设施课程还会继续学习 Nacos 在 Prompt / A2A 等场景的能力。

---

## 9. 怎么选

### Git

推荐给：

```text
工程师维护 Skill
必须 Code Review
技能改动应该有 Git 历史
```

优点：

```text
PR
Diff
Rollback
Tag
Audit
```

### MySQL/PostgreSQL

推荐给：

```text
产品/运营在线编辑
后台 CRUD
实时生效
```

优点：

```text
数据库事务
业务权限
管理后台
审计表
```

### Nacos

推荐给：

```text
已有配置中心治理体系
```

不要为了“Agent”三个字额外引入一套不熟悉的基础设施。

---

## 10. Skill Source 很重要

同名 Skill 可能来自：

```text
project global
marketplace
workspace
per-user
```

Repository 的 source 可以帮助定位：

```text
这个 Skill 到底是谁提供的？
```

Git 默认 source 会带 repository 信息，也可以显式指定：

```java
new GitSkillRepository(..., "company-skill-market", ...);
```

---

## 11. Repository 与 Skill Marketplace

第 32 课学习：

```text
propose
  ↓
draft
  ↓
review
  ↓
promote
```

第 42 课学习：

```text
promoted skill
     ↓
放在哪里作为组织级 source？
     ↓
Git / MySQL / PostgreSQL / Nacos
```

所以两课连接起来就是：

```text
Agent learns
    ↓
Draft
    ↓
Human/Gate review
    ↓
Promote
    ↓
Skill Repository
    ↓
Other Agents consume
```

这才是完整 Skill 生命周期。

---

## 12. 多实例部署

### Git

```text
Git remote
  ├── Pod A local clone
  ├── Pod B local clone
  └── Pod C local clone
```

优点是读取完全本地。

缺点是更新存在同步窗口。

### DB/Nacos

```text
central repository
  ├── Pod A
  ├── Pod B
  └── Pod C
```

更新传播更直接，但运行时依赖中心服务。

---

## 13. Spring Bean 生命周期

GitSkillRepository 使用临时目录时会注册 cleanup hook。

业务项目里推荐单例 Bean：

```java
@Bean(destroyMethod = "close")
GitSkillRepository skillRepository() {
    return new GitSkillRepository(url);
}
```

不要每个 HTTP request new 一个 repository。

---

## 14. 启动

```bash
./mvnw -pl 42-SkillRepositoryBackends spring-boot:run
```

查看后端能力：

```bash
curl http://localhost:18081/api/skill-repositories
```

Web 服务本身不会连接 Git/MySQL/PostgreSQL/Nacos，因此可直接启动。

---

## 15. 自动化测试

```bash
./mvnw -pl 42-SkillRepositoryBackends test
```

核心测试是真实本地 Git：

```text
create repo
commit SKILL.md
clone
sync
parse AgentSkill
```

数据库和 Nacos 因为需要真实服务，默认 contract test 只验证它们的官方类型存在于 classpath。

生产项目可以另外建立：

```text
src/integrationTest
```

配 Testcontainers MySQL/PostgreSQL/Nacos 做 E2E。

---

## 16. 安全与治理

Skill 本质上是给 Agent 的操作说明，所以 Repository 不是普通 CMS。

至少考虑：

```text
谁能发布？
谁能审核？
Skill 是否包含危险 shell？
资源文件是否可信？
是否允许立即全量生效？
能不能快速 rollback？
```

因此 Git PR workflow 在高风险 Skill 上非常有价值。

---

## 17. 本课结论

Skill 平台最终不是：

```text
很多 SKILL.md
```

而是：

```text
Authoring
  ↓
Review
  ↓
Repository
  ↓
Distribution
  ↓
Runtime loading
  ↓
Usage/Audit
  ↓
Curator
```

第 18、32、42 三课连起来，才是完整的企业 Skill 生命周期。
