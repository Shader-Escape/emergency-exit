# EXIT（应急出口）for Minecraft 1.21.11

This branch is EXIT for Minecraft **1.21.11**: Fabric and NeoForge, mod id `emergency_exit`, Maven
`ml.mypals.exit:emergency_exit-<loader>:0.1.4+1.21.11`.

这个分支是 Minecraft **1.21.11** 的 EXIT：Fabric 和 NeoForge，mod id `emergency_exit`，Maven
`ml.mypals.exit:emergency_exit-<加载器>:0.1.4+1.21.11`。

The documentation is kept on `master` only. 文档只在 `master` 上维护：

- [README](https://github.com/hotpad100c/emergency-exit/blob/master/README.md) / [中文 README](https://github.com/hotpad100c/emergency-exit/blob/master/README.zh-CN.md): the API, the rules, debugging and the development
  commands. API、规则、调试和开发命令。
- [ChangeLog](https://github.com/hotpad100c/emergency-exit/blob/master/ChangeLog.md), section **1.21.11**: what differs on this version (events, API types, missing features),
  under **使用** (Use). 这个版本有什么不同（事件、API 类型、缺少的功能），见该节的 **使用**。

Development on this branch. 在这个分支上开发：

- `./gradlew build`
- `./gradlew :fabric:runSmokeClient`, `./gradlew :neoforge:runSmokeClient` (run directory `run/<loader>-1.21.11`)
- `./gradlew :fabric:runAuditClient`
