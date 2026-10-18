# 移植 EXIT 到其它 Minecraft 版本（给 AI 智能体的说明）

这份说明写给接手移植的 AI 智能体。EXIT 的目标是支持 Minecraft **1.16.5 到 26.3** 的整个区间，每个版本一个
git 分支。目前已经有一部分版本（见第 3 节），其余要一段一段补。

**开始之前，先问用户这次移植哪个范围、从哪个分支出发**（第 1 节）。没有得到回答前不要开分支、不要改代码。
读完整份说明再动手；遇到这里没写到、或和代码对不上的地方，先查代码和参考，查不清就问用户，不要猜。

## 1. 先问用户

用一条消息问清下面几件事，并给出你的建议（建议的依据见第 3、4 节）：

1. **移植哪些版本。** 列出第 3 节表里还没做的版本，请用户选一个范围或几个版本，例如“1.21.10 到 1.21.5”。
2. **从哪个分支出发。** 默认从已完成的、最接近的更新版本出发（例如做 1.21.10 就从 `port/1.21.11` 开），
   然后一个版本接一个版本往下（1.21.10 → 1.21.8 → 1.21.6 …），每个新分支都从上一个刚做完的分支开出。
   用户也可能指定别的起点。
3. **每个版本用哪些加载器。** 已有的分支都是 Fabric + NeoForge。NeoForge 只在较新的版本上有；更早的版本
   只有 Forge，Iris 在 Forge 上也没有官方版本（见第 4 节）。问用户：在这些版本上，第二个加载器是 Forge、
   NeoForge，还是只做 Fabric。
4. **做到哪一步停下来汇报。** 例如每做完一个版本汇报一次，还是整个范围做完再汇报。

用户回答后，把回答原样写进你的第一个提交说明或汇报里，后面照着做。

## 2. 你会用到的材料

### 2.1 EXIT 仓库

地址 https://github.com/hotpad100c/emergency-exit

下载下来以在其中工作，先读这些：

- `master:README.md`：API、规则（线程、portal、嵌套、阴影通道、绘制顺序、混合）和开发命令。**API 的语义以它为准。**
- `master:ChangeLog.md`：每个版本和上一个更新版本的差异，每个版本一节。你的工作从最后一节接着往下写。
- `git log master..port/<版本>`：看已有的移植是怎么拆成提交的：先一个 “Port EXIT to Minecraft …”，
  再一个个适配提交。

代码结构（三个子项目，按 MultiLoader-Template 的布局）：

- `common/`：与加载器无关的全部代码。
  - `api/`：`Exit`、`ExitChannel`、`ExitContext`、`ExitOptions`、`PortalOverrides`，对外的 API。
  - `impl/`：通道、合并、深度、调试视图、调试方块等实现。
  - `compat/`：`IrisCompat`、`IrisHooks`。
  - `mixin/`：钩子。
  - 着色器在 `resources/assets/emergency_exit/shaders/core/`。
  - 冒烟测试在 `src/smoke/`。
- `fabric/`、`neoforge/`：入口、元数据、命令注册、调试方块的事件钩子，以及 `Platform` 服务的实现。
  `fabric/src/audit` 是混入检查。

### 2.2 LitematicaPass（整个区间都有现成实现）

EXIT 是从 [LitematicaPass](https://github.com/hotpad100c/litematicapass) 里拆出来的渲染核心。LitematicaPass
已经在 1.16.5 到 26.3 的每个版本上跑通了同样的思路（独立目标、深度种子、按深度合并、半透明后变暗、手部遮挡、
调试视图），是移植时最重要的参考。自己下载：

```bash
git clone https://github.com/hotpad100c/litematicapass.git
```

| 版本 | 在哪里 |
|---|---|
| 26.3、26.2、26.1.2、1.21.11、1.21.10、1.21.8、1.21.5、1.21.4、1.21.3、1.21.1 | `master` 分支 |
| 1.21.1、1.20.6、1.20.4、1.20.2、1.20.1、1.20、1.19.3、1.18.2、1.18.1、1.17.1、1.16.5 | `1.21.1-` 分支（标签 `1.1.0-1.20.6-1.16.5`） |

怎么读：

- LitematicaPass 用 stonecutter 管多个版本。代码里的 `//? if <1.17 {` … `//?}` 是按版本分开的段落，
  被 `/* … */` 注释掉的那一半是别的版本用的。你要的版本落在哪一半，就读哪一半。
- 你也可以运行构建，每一个版本对应的有效代码版会出现在`versions/<版本>/`的`src`目录下。
- `stonecutter.gradle.kts` 里有按版本替换的包名和类名（例如 Iris 旧包名 `net.coderbot.iris`）。
- 每个版本的依赖在 `versions/<版本>/gradle.properties`：Fabric API、Iris、Sodium 的 Modrinth 版本号，
  以及 `mod.java`。
- LitematicaPass 只有 Fabric 版。
- EXIT 的规则是每个版本一个分支、写普通代码，所以从 LitematicaPass 取代码时只取你那个版本的一半，
  去掉 `//?` 注释。

LitematicaPass 和 EXIT 里对应的东西：

| LitematicaPass | EXIT | 说明 |
|---|---|---|
| `render/Gpu.java` | `impl/Gpu.java` | 建目标、清除、拷深度、全屏通道。分支最多的文件，每个渲染时代的写法都在这里 |
| `render/SchematicTarget.java` | `impl/Channel.java`、`ExitRuntime` | 开始/结束计数、把绘制挪进独立目标 |
| `mixin/MinecraftMixin.java` | portal 的“挪进通道” | 1.21.6 之前没有输出覆盖：重定向期间让 `getMainRenderTarget()` 返回独立目标 |
| `mixin/GlStateManagerMixin.java` | 混合 | 1.21.5 之前：重定向期间把 alpha 混合因子换成 `(ONE, ONE_MINUS_SRC_ALPHA)` |
| `render/SceneDepth.java` | `impl/SceneDepth.java` | 手部前、半透明前两次拷深度；旧 Iris 先同步它自己的深度纹理 |
| `render/SchematicEntities.java` | `draw` / `batch` | 没有 `SubmitNodeCollector` 的版本：用自己的 `MultiBufferSource` 收集，画完冲进目标 |
| `compat/IrisHooks.java`、`mixin/iris/*` | `compat/`、`mixin/iris/` | 各代 Iris 的 bypass、渲染关卡开关、场景深度、钩子点 |
| `LitematicaPass.java` | 入口、命令 | 命令 API、文字组件、日志在各版本的写法 |
| `src/smoke/` | `common/src/smoke/` | 各版本建世界和截图的写法 |

## 3. 现在的进度

| Minecraft | EXIT 分支 | 状态 |
|---|---|---|
| 26.3 | `master` | 完成（Fabric + NeoForge） |
| 26.2 | `port/26.2` | 完成（Fabric + NeoForge） |
| 26.1.2 | `port/26.1.2` | 完成（Fabric + NeoForge） |
| 1.21.11 | `port/1.21.11` | 完成（Fabric + NeoForge） |
| 1.21.10、1.21.8、1.21.5、1.21.4、1.21.3、1.21.1 | — | 未做，**由另一组负责**（不要动这些分支） |
| 1.20.6、1.20.4、1.20.2、1.20.1、1.20 | — | 未做 |
| 1.19.3、1.18.2、1.18.1、1.17.1、1.16.5 | — | 未做 |

**分工。** 1.21.10 到 1.21.1 由另一组在做，1.20.6 及以下交给你（具体范围仍以第 1 节用户的回答为准）。
你的第一个版本 1.20.6 最接近的更新分支是 `port/1.21.1`：

- 如果 `port/1.21.1` 已经存在，就从它开出；
- 还不存在时，问用户是等它做完，还是先从 `port/1.21.11` 开、按 LitematicaPass 1.20.6 的写法改。
  选后者时，等 `port/1.21.1` 做完，再把它的修复用 cherry-pick 同步过来。

- 已做的分支一个接一个开出：`master` → `port/26.2` → `port/26.1.2` → `port/1.21.11`。每个都能构建，
  冒烟测试 `RESULT: OK`。
- `master` 上 Kappa 降分辨率光影的缩放修复（`39e729c`、`fa9e4de`）已经 cherry-pick 到三个移植分支，
  每个分支后面再加了适配提交。
- 未做的版本列表照 LitematicaPass 支持的版本；用户也可能增减。动手前以第 1 节用户的回答为准，
  开始前用 `git branch` 确认有没有别人已经开了分支。

## 4. 各渲染时代的差异（从新到旧）

下表是从 LitematicaPass 的版本分支和 EXIT 的 ChangeLog 整理出来的，说明每过一条线 EXIT 大概要改什么。
26.3 到 1.21.11 已经在 ChangeLog 里写清楚。**1.21.11 以下的各条只在 LitematicaPass 里验证过，EXIT 还没走过，
先在代码和 jar 里核实再用。**

| 版本线 | 变化 | EXIT 要做的 |
|---|---|---|
| 26.3 | 渲染 API 在 `com.mojang.renderpearl`；没有输出覆盖；Iris 有 `skipExtension` | `master` 的写法 |
| ≤26.2 | 回到 `com.mojang.blaze3d`；有 `RenderSystem.outputColorTextureOverride` 等输出覆盖；Iris 没有 `skipExtension` | 见 ChangeLog 26.2 |
| ≤26.1.x | 深度改回标准方向（近 0 远 1） | 见 ChangeLog 26.1.2 |
| ≤1.21.11 | 混淆，Mojang 映射；Java 21；`WorldRenderEvents` | 见 ChangeLog 1.21.11 |
| ≤1.21.10 | `ResourceLocation` 代替 `Identifier`；没有 JSpecify，用 `org.jetbrains.annotations.Nullable` | 改名 |
| ≤1.21.8 | 没有 `SubmitNodeCollector` 和特征分发器；全屏通道顶点着色器不是 `core/blit_screen`；画四边形要自己处理索引（LitematicaPass `drawQuadIndices`） | `draw`/`batch` 改用 `MultiBufferSource`（见 4.1）；改 `Gpu` |
| ≤1.21.5 | 没有 `GpuTextureView`，直接用纹理；**没有输出覆盖** | portal 用 `MinecraftMixin` 换 `getMainRenderTarget()` |
| ≤1.21.4 | **没有 `RenderPipeline`/`RenderPass`**，纯 OpenGL（还有核心着色器）；绘制画进当前绑定的帧缓冲 | `Gpu` 换成 LitematicaPass 的纯 GL 版本；portal 绑定通道的帧缓冲；带上 `GlStateManagerMixin` |
| ≤1.21.3 | Iris 1.8.1 及更早没有 `ImmediateState.bypass` | bypass 由 EXIT 自己记录；用 mixin 让 `shouldOverrideShaders` 在 bypass 时返回 false，`getPhase` 返回 `NONE` |
| ≤1.21.1 | `RenderTarget.resize` 要带 `ON_OSX`；`ResourceLocation.fromNamespaceAndPath` 只到 1.21 | 改调用 |
| ≤1.20.6 | 没有 `isPackInUseQuick`，用 `IrisApi.isShaderPackInUse()` | 改 `IrisHooks` |
| ≤1.20.4 | Java 17（1.20.5 起才是 21） | 改构建 |
| ≤1.20.2 | Iris 包名是 `net.coderbot.iris`，管线类 `NewWorldRenderingPipeline` | 改 import |
| ≤1.18.x | 客户端命令用 Fabric API v1；`new TextComponent(…)`；建世界界面不同 | 改命令和冒烟测试 |
| ≤1.18.1 | Iris 1.2 及更早：场景深度在 Iris 自己的纹理里；没有 `isRenderingLevel`；地形着色器只要有管线就替换 | 读深度前先拷 Iris 的深度纹理；bypass 时让 `getPipelineNullable` 返回 null |
| ≤1.17.1 | 日志用 Log4j；Java 16 | 改日志和构建 |
| 1.16.5 | **固定管线，没有核心着色器**；`RenderTarget` 本身就是带纹理的目标；Iris 管线类是 `DeferredWorldRenderingPipeline`，只在它认为主目标被绑定时才绑定自己的程序 | 全屏通道自己编译 GL 程序（`#version 330` 加 `STANDARD_DEPTH`，用 `gl_VertexID` 画全屏三角形）；bypass 时让 `setIsMainBound` 收到 false |

各条的出处：LitematicaPass 里搜对应的 `//? if` 条件（例如 `<1.21.5`、`<1.18.2`），旁边都有注释说明原因。

### 4.1 API 里的 Minecraft 类型

API 用到了 Minecraft 类型（`SubmitNodeCollector`、`PoseStack`、`RenderTarget`），README 说 API 只在一个
Minecraft 版本内稳定。某个类型在旧版本不存在时，换成作用相同的类型。下面是建议，**第一次用到时先和用户确认**：

| 26.3 | 没有 `SubmitNodeCollector` 的版本（≤1.21.8） |
|---|---|
| `Exit.draw(SubmitNodeCollector, Consumer<ExitContext>)` | `Exit.draw(MultiBufferSource, Consumer<ExitContext>)`：不重定向时原样使用传进来的 |
| `ExitContext.collector()` | `ExitContext.bufferSource()`：通道自己的 buffer source，记进通道的 batch |
| `Exit.batch(SubmitNodeCollector)` | `Exit.batch(MultiBufferSource)`：返回记录用的 buffer source，帧末统一画 |
| gizmo 进通道 | 没有 gizmo 的版本删掉这条规则 |

“先实体后半透明”的顺序：`MultiBufferSource.BufferSource` 按 `RenderType` 分开攒，`endBatch(type)` 时才画。
帧末先 `endBatch` 不混合的类型，再 `endBatch` 半透明的类型。半透明的深度再画一次，这次只写深度
（`colorMask(false)` 加 `depthMask(true)`，画进通道的深度目标）。

### 4.2 加载器

- **Fabric**：所有版本都有，照 LitematicaPass 对应版本的构建：`fabric-loom-remap`、`loom.officialMojangMappings()`、
  `mod*` 依赖。
- **NeoForge**：大约 1.20.2 起才有（1.20.1 是从 Forge 分出来的过渡版）；Iris 的 NeoForge 版大约从 1.21 起才有。
  这两条都要先核实。没有NeoForge或者NeoForge版Iris的版本版本不用再支持 NeoForge。也不支持 Forge，只留下 Fabric版本。
- common 子项目在 1.21.11 用 NeoForm/ModDevGradle。
- 第二个加载器的事件和 Fabric 的对应关系要逐个版本查，写进 README 的加载器表。注意事件触发的时机是否早于 EXIT 的帧末合并；
  晚于它的事件里画的东西会被丢掉（README 里 NeoForge `AfterLevel` 的说明）。

## 5. 移植规则

1. **一个版本一个分支，写普通代码。** 分支名 `port/<版本>`，从第 1 节约定的分支开出。分支里只放这个版本的代码：
   不用 stonecutter，不留 `//?` 指令，不留别的版本的死代码。
2. **API 包名和语义不变。** `ml.mypals.exit.api` 里的类名、方法名和 README 的规则尽量保持不变。类型必须换时，照 4.1 的方式换，
   在 README 和 ChangeLog 里写清楚。不要另造一套 API。
3. **行为和 26.3 一致。** 没有光影时什么都不重定向；世界照样挡住你的绘制；半透明表面后面变暗（默认 50%）；第一人称手在最前；
   阴影通道里什么都不画；先画实体再画半透明；多个通道按深度混合。做不到就写下来，不要悄悄删功能。
4. **`impl`、`compat`、`mixin` 可以随意改。** 只为新版本存在的代码（Super Resolution、缩放探针、`StrictValidationMixin`、
   gizmo 等），在旧版本用不上就删掉，在 ChangeLog 里列出来。
5. **记录差异。** 每个版本在 `master` 的 `ChangeLog.md` 里加一节“<版本>（相对 <上一个版本>）”，版本表加一行。
   格式照抄已有各节：分“构建 / 包名和类 / 渲染通道 / 管线 / 特征 / 关卡钩子 / 加载器事件 / Iris / 开发运行”小标题，
   每条一句，写清是什么、EXIT 怎么做。ChangeLog 只在 `master` 上维护，移植分支里不放。
6. **提交风格照抄已有分支。** 先一个 “Port EXIT to Minecraft <版本> (<加载器>)” 提交，提交说明里逐条列出主要差异
   （参考 `git log -1 fe36921`）；之后每修一个问题一个提交，说明里写为什么这样改。
7. **`master` 上的修复要往下同步。** 有新修复时，用 `git cherry-pick -x` 依次拿到每个移植分支，再加适配提交。
8. **不推送，不发布。** 只在本地提交；推送、建远程、发 Maven 都等用户决定。
9. **规则冲突或要取舍时问用户。** 用户会看到的决定先问再做：放弃一个功能、换 API 类型、放弃一个加载器。

## 6. 一个版本怎么做

1. **核实。** 对照第 4 节和 LitematicaPass 这个版本的代码，列出要改的地方，在 Iris 和 Minecraft 的 jar 里确认用到的类和方法都存在。
2. **开分支，搭构建。** 在 `gradle.properties` 里写好版本号、Java、映射和依赖（Iris、Sodium 的 Modrinth 号从
   LitematicaPass 的 `versions/<版本>/gradle.properties` 取），先能空跑进游戏。
3. **改到能编译**，再按第 7 节一项项测；先 Fabric，后第二个加载器。
4. **修问题**：每个问题一个提交。
5. **写 ChangeLog** 那一节，并在这个分支上把 README 改成这个版本的说法（加载器、事件表、API 类型）。
6. **汇报**（第 8 节），再做下一个版本。

## 7. 测试：用冒烟测试快速验证

EXIT 自带一个**自动化冒烟测试**。它会自己建一个创造世界，打开调试方块，按顺序切换调试视图并截图，然后退出游戏。
每做完一个版本都要跑它，并把截图收集起来，这是确认一个版本画得对最快的办法。

### 7.1 运行

```bash
./gradlew :fabric:runSmokeClient
```

```bash
./gradlew :neoforge:runSmokeClient
```

- 运行目录是 `run/<加载器>-<版本>`（例如 `run/fabric-1.21.11`），每个版本各用一个，互不干扰。
- 日志最后一行是 `RESULT: OK` 才算通过。退出码 2 表示没能按时进入世界，3 表示有检查没过。
- 日志里还会打印场景、窗口和主目标的尺寸；Iris 阴影回调里 EXIT 绘制和 portal 运行的次数应该是 0。
- 每个版本至少跑两遍：
  - **没有光影**：确认什么都没重定向，调试方块原样画出。
  - **有光影**：在 `run/<加载器>-<版本>/shaderpacks/` 放一个支持这个版本的光影包（BSL 等常见的包；
    想测降分辨率就用 Kappa），在 `config/iris.properties` 里启用（Oculus 用它自己的配置），再跑一次。
- 混入检查（只有 Fabric）：`./gradlew :fabric:runAuditClient`，确认每个混入都应用成功。

### 7.2 收集截图

截图在 `run/<加载器>-<版本>/screenshots/`，文件名是 `emergency_exit-smoke-<版本>-<加载器>-<视图>.png`：

| 视图 | 内容 | 正常时 |
|---|---|---|
| `normal` | 正常画面 | 红色半透明盒子（后面的世界看得见）；绿色实体盒子；黄色盒子在绿色后面，按深度混合；蓝色盒子透过世界可见，带青色 gizmo 轮廓（没有 gizmo 的版本没有轮廓）。颜色没有被光影染色或吞掉 |
| `presented` | 窗口前缓冲，也就是玩家实际看到的 | 和 `normal` 一样 |
| `color` | 主通道目标的颜色 | 只有 EXIT 画的盒子 |
| `target_depth` | 主通道目标的深度 | 有深浅层次，不是全黑或全白 |
| `scene_depth` | 场景深度 | 世界的深度图 |
| `side` | 从侧面看这一排盒子 | 红色在绿色、黄色前面，蓝色在后面，前后关系正确 |

每做完一个版本，把这一组截图（有光影和没光影各一组）复制到一个汇总目录，例如
`smoke-shots/<版本>/<加载器>-<光影名或 none>/`。这个目录放在仓库之外，或者不提交。汇报时附上截图，并和已完成版本
（例如 `port/1.21.11` 的同一组截图）逐张对比；哪张不一样，就写出差在哪里。

### 7.3 手动检查（冒烟测试覆盖不到的）

在开发运行里进游戏，开着光影：

1. `/emergency_exit debug shapes`：打开、关闭测试方块。
2. 在测试方块前放水或玻璃：被挡住的部分变暗（默认 50%）。
3. 举起手、拿物品：手总在测试方块前面。
4. 走到墙后：测试方块被挡住；只有蓝色（不受世界遮挡的通道）透过墙可见。
5. 测试方块不投影。
6. 调整窗口大小、切换全屏：画面不错位。
7. 游戏里关掉光影再打开：关掉时什么都不重定向，打开后恢复。
8. 逐个试 `/emergency_exit debug color|target_depth|scene_depth|main_depth|translucent_depth|off`，`off` 恢复正常。
9. 日志里没有 GL 错误、没有 “Could not set up …”、没有混入失败。

每一项都记下结果：通过、失败或没测。没测的写明原因，不要写成通过。

## 8. 每个版本的汇报

- 分支名和提交（本地，不推送）。
- `master` 上 ChangeLog 新加的一节。
- 和 26.3 不一样的地方，以及原因。
- 冒烟测试结果：每个加载器、有光影和没光影各一次，`RESULT` 那一行，以及截图汇总目录的路径和对比结论。
- 第 7.3 节每一项的结果。
- 还要用户决定的事。
