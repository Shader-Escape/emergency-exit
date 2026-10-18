# EXIT（应急出口）

[English](README.md)

EXIT 是一个 Fabric 和 NeoForge 通用的库，让自定义的世界渲染绕过光影包（Iris）。通过 EXIT 绘制的内容会画进一个独立的渲染目标，
等光影包完成整帧的着色之后，再按深度合并回画面上。光影包既不会替换你的渲染管线，也不会把你的绘制吞掉。

值得注意的是，它由AI从模组 [LitematicaPass](https://github.com/hotpad100c/litematicapass) 的渲染核心中拆出。我知道有些人对AI有意见，但这是唯一可以**快速达到**我预期目的迁移方式。
请放心，我知道自己在干什么，自己也在使用这个库，我大概能确保它不吃掉你宠物的石头。这不是AI Slop，也不是Wish Coding。如果你遇到任何困难，我很乐意提供帮助。

这个分支对应 Minecraft **1.21.10**（mod id `emergency_exit`，API 包 `ml.mypals.exit.api`）。
每个 Minecraft 版本一个 git 分支，分支里只有该版本的代码；`master` 是 26.3。

### 加载器

两个加载器用同一套 API，各有一个 jar：`emergency_exit-fabric` 和 `emergency_exit-neoforge`（Maven 坐标
`ml.mypals.exit:emergency_exit-<加载器>:0.1.0+1.21.10`）。Iris 不是必需的，没有光影包时 EXIT 什么都不改道。

调用时机要在关卡渲染期间（`LevelRenderer.renderLevel` 里面）。各加载器可用的位置：

| 用途 | Fabric | NeoForge |
|---|---|---|
| 提交到批量 | `WorldRenderEvents.BEFORE_ENTITIES`（`context.commandQueue()`） | `RenderLevelStageEvent.AfterOpaqueBlocks` 等，没有可直通的收集器：`Exit.batch()` |
| 绘制（记进批量） | `WorldRenderEvents.BEFORE_ENTITIES`、`AFTER_ENTITIES`、`END_MAIN` 等 | `RenderLevelStageEvent.AfterOpaqueBlocks` 等 |
| 主渲染通道之后、不在任何通道内（portal） | `WorldRenderEvents.END_MAIN` | 没有对应的事件 |

NeoForge 1.21.10 没有交出关卡 `SubmitNodeCollector` 的事件，没有可直通的收集器：用 `Exit.batch()` 和 `Exit.draw(null, ...)`。
没有光影包时，批量在关卡渲染结束时画进主目标。

NeoForge 的 `RenderLevelStageEvent.AfterLevel` 在关卡渲染结束之后才触发，那时 EXIT 已经合并完本帧，在里面调用会被丢弃。

## 基础概念

| 概念 | 含义                                                                                                                       |
|---|--------------------------------------------------------------------------------------------------------------------------|
| **通道**（`ExitChannel`） | 一组共用同一个目标、同一套合并选项（`ExitOptions`）的绘制。同一通道内的绘制彼此按深度遮挡；不同通道之间默认也按深度混合（见"通道混合"），关掉 `blendWithChannels` 的通道则按优先级盖在上面。 |
| **主通道** | 共享的默认通道 `emergency_exit:main`，`Exit.draw/portal/batch` 时使用。当你需要不同选项（比如透过世界显示）时，需要用 `Exit.channel(id, options)` 声明自己的通道。  |
| **通道目标** | 每个通道一张场景大小的颜色 + 深度目标（RGBA8 + D32）。本帧第一次使用时清空，并用场景深度初始化。                                                                  |
| **改道**（`routed`） | 绘制从进主目标转至进通道目标，之后再合并回来。只在"开启光影包，且不在阴影通道，在关卡渲染中"时发生。`Exit.isActive()` 表示此刻会不会改道。                                          |
| **场景深度** | EXIT 在光影包画手部之前，画半透明表面之前各复制一次的主深度。用来初始化通道目标的深度（世界遮挡），以及合并时找出手部和半透明表面。                                                     |
| **合并**（`composite`） | 关卡渲染结束时，把各通道目标按深度画回主目标。手部所在像素丢弃，半透明表面之后的部分变暗。 |
| **通道混合**（`blendWithChannels`） | 合并前，参与混合的通道先依次并入一张累积层：逐像素比较深度，近的盖在远的上面（按两边的 alpha 混合），然后累积层作为一层合并到画面上。 |
| **绘制**（`draw`） | 给一个托管上下文（`SubmitNodeCollector`、相机相对的 `PoseStack`、相机），lambda 立即执行，提交的内容记进通道的批量。 |
| **批量**（`batch`） | 本帧随时往里提交的收集器，关卡渲染结束时和 `draw` 记下的内容一起，在一帧里画进通道目标：先实体，后半透明。可用 `flushBatch()` 提前。 |
| **直通收集器** | 传给绘制方法的原版收集器（可为 `null`），不改道时绘制直接交给它。                                                                                     |
| **bypass** | Iris 的开关 `ImmediateState.bypass`：打开时光影包不再把原版管线换成自己的程序。改道的绘制都在它打开时进行。                                                     |
| **portal**| 一个明确的区间，执行期间按 `PortalOverrides` 设置 Iris 的开关（bypass、顶点格式加宽、`isRenderingLevel`），结束后恢复。给已有渲染上下文的代码用。改道时，区间内新开的、画进主目标的渲染通道会自动改画进通道目标。 |
| **重定向**（`redirect`） | portal 区间内把"你本来要画进的目标"换成通道目标（不改道时原样返回）。只在 portal 自动改道管不到的地方需要：画进主目标以外的目标，或画进区间开始前就已打开的渲染通道。区间外调用会抛异常。 |
| **顶点格式加宽** | Iris 构建顶点缓冲时会给原版顶点格式加上光影包需要的额外数据（`skipExtension` 关掉它，每线程一份）。自建的缓冲要用原版管线改道绘制时，用 `Exit.plainFormat` 构建。                    |
| **阴影通道** | 光影包从太阳视角画阴影图的那一遍。EXIT 在这里什么都不会画。`draw` 的 lambda,`portal` 的区间都不执行，`batch` 的内容被丢弃。                                         |

## 它做了什么

* **世界照样遮挡你的绘制。** 通道目标的深度用"半透明表面绘制之前"的场景深度初始化，所以墙后面的东西看不见。
* **半透明表面之后变暗。** 水、玻璃等半透明表面由光影包在你的内容离开画面之后才绘制，二者无法混合；位于它们
  后方的部分会被变暗（默认亮度 50%），看起来像在"后面"。
* **第一人称手部始终在最前。** 比较绘制手部前后的深度，找出手所在的像素，在那里丢弃你的绘制。
* **没有光影包（或没装 Iris）时什么都不改道**，一切按原版方式绘制。

![Pass.png](docs/Pass.png)

### 每帧的流程

1. `LevelRenderer.renderLevel` 开始：EXIT 记下本帧相机（视图旋转、位置）和关卡投影矩阵。
2. 光影包绘制手部之前、绘制半透明表面之前：各复制一次场景深度（只在有通道本帧要绘制时）。
3. 你的绘制进入通道目标。通道目标在本帧第一次使用时被清空，并用场景深度初始化。
4. `LevelRenderer.renderLevel` 结束：绘制各通道的批量内容；参与混合的通道按深度并成一层合并到主目标上，其余通道再按优先级盖在上面。

## 快速开始

所有调用都要在渲染线程上、关卡渲染进行期间进行，比如 Fabric 的 `WorldRenderEvents` 或 NeoForge 的渲染事件（见上文"加载器"）。

### 1. 绘制（托管上下文）

```java
WorldRenderEvents.BEFORE_ENTITIES.register(context ->
    Exit.draw(context.commandQueue(), exit -> {
        PoseStack pose = exit.poseStack();  
        Vec3 cam = exit.cameraPos();
        pose.translate(x - cam.x, y - cam.y, z - cam.z);
        exit.collector().submitCustomGeometry(pose, RenderType.debugQuads(), (p, buffer) -> { ... });
    }));
```

lambda 立即执行，提交的内容记进通道的批量，在关卡渲染结束时和批量一起画（见"顺序"）。
第一个参数是"直通收集器"（可为 `null`），不改道（比如没有加载光影包）时，绘制直接交给它。
>`draw()` 和 `batch()` 画的时间相同，`draw()` 只是多给一个托管上下文（相机、相机相对的 `PoseStack`）。
要自己开 `RenderPass` 画，请用 portal。

| 方法 | 说明 |
|---|---|
| `collector()` | 提交绘制的 `SubmitNodeCollector` |
| `poseStack()` | 位于相机位置的 `PoseStack`（相机相对坐标，不含旋转） |
| `cameraPos()` / `camera()` | 相机位置 / 本帧的 `CameraRenderState` |
| `partialTick()` | 相机实体本帧的 partial tick |
| `modelView()` | 视图旋转矩阵，EXIT 画提交内容时用的 model-view |
| `routed()` | 这次绘制是否绕过了光影包 |

### 2. portal 区间（已有渲染上下文的代码）

```java
channel.portal(() -> {    // 或 主通道Exit.portal(...)
    myRenderer.render(Minecraft.getInstance().gameRenderer.mainRenderTarget());  // 对主目标开的 pass 会自动改画进通道目标
    RenderTarget target = channel.redirect(fb);  // 其它目标需要自己重定向
    ...
});

// 设置覆盖 Iris 状态的三个开关，区间结束后恢复原值
Exit.portal(PortalOverrides.DEFAULT.withRenderingLevel(PortalOverrides.Switch.OFF), () -> { ... });
```

`portal` 立即执行 lambda 内内容。lambda 执行期间，Iris 的开关按 `PortalOverrides` 的内容设置，返回（或抛异常）后恢复原值。

| 开关 | 默认 | 说明                                                                   |
|---|---|----------------------------------------------------------------------|
| `bypass` | `ON` | Iris 的 `ImmediateState.bypass`：不把原版管线替换成光影包的程序                       |
| `skipExtension` | `ON` | Iris 的 `ImmediateState.skipExtension`：该开关线程独立。此刻构建的顶点缓冲保持原版顶点格式，不被加宽。1.21.10 上 EXIT 不动它（见 `PortalOverrides`） |
| `renderingLevel` | `KEEP` | Iris 的 `ImmediateState.isRenderingLevel`：开着时 Iris 会加宽提交管线写出的顶点格式     |

 **注意**

* **portal 输出行为：** portal 改道期间，EXIT 用一个 mixin 把**新开的**、把主目标当作颜色或深度附件的渲染通道换成通道目标。
  所以自己对主目标开 pass 的代码不用改。（1.21.10 也有 `RenderSystem.outputColorTextureOverride`，但它只管渲染类型；
  这个 mixin 连直接开的 pass 也一起改道。）以下情况不会自动改道，
  需要用 `channel.redirect(你的目标)`（拿不到通道对象时用静态的 `Exit.redirect(original)`）：
  * 画进主目标以外的目标；
  * 画进 portal 开始之前就已打开的渲染通道（比如事件交给你的 pass）——附件在开 pass 时就定了；
  * 对主目标的清除、复制（`clear*`、`copyTextureToTexture`）。

  这个替换是全局的：portal 期间任何 mod 新开的、画进主目标的 pass 都会被改道。
* **只在区间内有效：** `redirect` 只能在 portal（或 `draw` 的 lambda）里调用。如果在区间外、非渲染线程、或在另一个通道的
  portal 里调用会抛出 `IllegalStateException`。在区间内外都可能会跑的代码，执行前务必检查`Exit.isRouting()`或 `Exit.inPortal()`。
* **何时改道：** 在渲染线程上：有光影包、不在阴影通道、关卡渲染中时改道（主目标上的新 pass 被换到通道目标，`redirect` 返回通道目标；否则都保持原样）；
  开关在有光影包且不在阴影通道时就会设置（关卡渲染之外也是）。阴影通道里 lambda 不执行。
* **嵌套。** portal 可以嵌套，但同一时间只能在一个通道上；里面的绘制（须在同一通道）沿用 portal 设好的开关。
* **异线程（编译 VBO 等需求）。** 在非渲染线程调用 portal 时，只设置**当前线程**的 `skipExtension`，
  `bypass` 和 `renderingLevel` 是全局渲染状态，不会被设置。`redirect` 同样不可用。只想关掉格式加宽时，用更直接的
  `Exit.plainFormat(Runnable)`或许是更好的选择。

```java
// 工作线程上构建自己的顶点缓冲（之后在改道状态下用原版管线绘制）
Exit.plainFormat(() -> buildMyVbo());
```

### 3. 批量收集

```java
WorldRenderEvents.BEFORE_ENTITIES.register(context ->
    myRenderer.submit(Exit.batch(context.commandQueue())));
```

`batch()` 返回一个 `SubmitNodeCollector`。本帧提交给它的内容，在关卡渲染结束时和 `draw` 记下的内容一起，一次性绘制进通道目标：先画全部实体，再画全部半透明。
需要提前画（比如 portal 里画半透明之前），调用 `flushBatch()`（确保你知道你在做什么）。这玩意不能复用，每帧都要重新获取。

## 通道

`Exit.draw/portal/batch` 使用共享的**主通道**（`emergency_exit:main`，默认选项）。需要不同选项的 mod 声明自己的通道：

```java
ExitChannel xray = Exit.channel(ResourceLocation.fromNamespaceAndPath("mymod", "xray"),
    ExitOptions.builder()
        .occludedByWorld(false)                     // 透过世界显示
        .hideUnderHand(true)                        // 手部保持在最前
        .dimBehindTranslucent(0.5F)                 // 半透明表面之后的亮度，1 = 不变暗
        .priority(10)                               // 合并顺序（大的后合并）
        .blendWithChannels(true)                    // 与其它通道按深度混合
        .wantsFrame(() -> myMod.enabled())          // 本帧是否会绘制
        .build());
xray.draw(...); xray.portal(...); xray.redirect(fb); xray.batch(); xray.flushBatch();
```

* 同一个 id 再次调用返回同一个通道，选项不同则抛异常（你找茬是吧.jpg）。
* 同一通道内的绘制共用一个目标，彼此按深度遮挡。
* **通道混合**（`blendWithChannels`，默认开）：本帧有两个以上参与混合的通道画了东西时，EXIT 按合并顺序把它们依次并入一张累积层，
  逐像素比较深度：通道更近就盖在累积内容上面，更远就垫在下面（都按预乘 alpha 混合）；手部丢弃、半透明之后变暗在并入时处理。
  最后累积层作为一层合并到画面上。优先级只在深度相同时决定先后。
  * 累积层每个像素只记一个深度：两个通道的结果是准确的，三个以上在同一像素交错时是近似的。
  * 半透明的渲染类型不写深度。对 EXIT 自己画的内容（绘制、批量、portal 里经由 EXIT 渲染的 submit），EXIT 会把其中的半透明
    submit 用只写深度的管线副本再画一遍，写进该通道的一张深度目标；合并前取两者中更近的深度作为通道深度，所以半透明内容也能和别的通道正确排序。
    你自己在 portal 里开的 RenderPass 里的半透明内容仍然没有自己的深度，比较时用初始化的场景深度，会被当成在很远处。
  * 关掉 `blendWithChannels` 的通道不参与混合，在累积层之后按优先级、再按创建顺序盖在上面（比如永远显示在最前的标记）。
* 每个通道占用一张场景大小的 RGBA8 + D32 目标，画过半透明 submit 后再多一张 D32；有两个以上通道混合时，再多两张同样大小的累积层。

| 选项 | 默认 | 说明 |
|---|---|---|
| `occludedByWorld` | `true` | 世界（半透明表面之前）遮挡通道内容 |
| `hideUnderHand` | `true` | 第一人称手部保持在最前 |
| `dimBehindTranslucent` | `0.5` | 位于场景半透明表面之后的亮度（0..1） |
| `priority` | `0` | 合并顺序；参与混合的通道之间只在深度相同时起作用 |
| `blendWithChannels` | `true` | 与其它参与混合的通道按深度混合；关掉则按优先级盖在混合结果之上 |
| `wantsFrame` | `null` | 场景深度复制之前询问本帧是否会绘制；`null` 表示"上一帧画过就算" |

> **提示**：`occludedByWorld(false)` 只让通道透过**世界**显示，不会透过其它 EXIT 通道。只要 `blendWithChannels` 还开着（默认），
> 它仍和其它参与混合的通道按深度比较，位于它们后面时照样会被盖住（调试方块里蓝色在绿色后面时就是这样）。
> 想让它永远显示在所有 EXIT 内容之上，再加上 `blendWithChannels(false)`，它就会按 `priority` 盖在混合结果之上。

## 规则与限制

* **线程与时机** 除了 `portal`（异线程时只关格式加宽）和 `plainFormat`，都只能在渲染线程、`LevelRenderer.renderLevel`
  开始到结束之间调用。在此之外，绘制会被丢弃（只警告一次），portal 不改道。通道目标在本帧第一次使用时用场景深度
  初始化，所以改道的 portal（和 `redirect`）要在关卡的渲染通道开始执行之后、且不在已打开的 `RenderPass` 里使用（Litematica 就是这样），不要更早。
* **嵌套** portal 可以嵌套，但同一时间只能在一个通道上：绘制里再调用绘制会共用外层的收集器；
  在一个通道的 portal 里打开另一个通道会抛异常。
* **阴影通道** EXIT 不往光影包的阴影图里画任何东西，所以通过 EXIT 绘制的内容没有影子：阴影通道里 `draw` 的 lambda
  和 `portal` 的区间都不执行，`batch` 返回的收集器会丢弃提交的内容（`plainFormat` 不受影响）。另外，Fabric 的
  `WorldRenderEvents` 本来就不会在 Iris 的阴影通道里触发。
* **顺序** `draw` 的 lambda 立即执行，但提交的内容不论在哪里调用（主渲染通道里、关卡渲染通道开始之前也一样）都记进通道的批量。
  批量在关卡渲染结束时（或 `flushBatch()` 时）在一帧里画完：先画全部实体，再画全部半透明，所以实体不会盖住更近的半透明。
  portal 是当场画的，排在批量之前：在 portal 里画半透明之前先调用 `flushBatch()`，否则批量里的实体会把它盖住。
  要自己开 RenderPass 画，请用 portal。
* **portal 和批量的区别** 批量（包括 `draw`）交给 EXIT 的是提交内容：EXIT 一直拿着它们，到画的时候才决定顺序，
  所以能先画全部实体、再画全部半透明，还能另记半透明的深度。portal 包住的是别人的代码，它自己开 RenderPass、
  自己选管线，当场画进通道目标，EXIT 只能看到画完的像素。
* **为什么 portal 里的半透明不能和之后的批量混合** 半透明一画进目标，就已经和它后面的颜色混在一起了，
  像素里只剩混合后的结果，半透明层自己的颜色和覆盖率都没了。之后批量里有实体要放到它后面，就得把"后面的颜色"
  换掉，这已经做不到；知道深度也没用（半透明本来就不写深度，即使写了，每个像素也只有一个颜色）。
  所以批量的实体只能盖住它，或者在它之前画：在 portal 里画半透明之前调用 `flushBatch()`
  （比如 Litematica 的不透明地形画完、半透明地形之前）。
* **混合** 合并时把通道颜色视为预乘 alpha（原版半透明管线没问题）。
* **自建顶点缓冲** Iris 在构建顶点缓冲时会加宽顶点格式；如果要用原版管线在改道状态下绘制自建的缓冲，
  请在 `Exit.plainFormat(...)` 里构建（编译多在工作线程上，`plainFormat` 在任何线程都可用）。
* **场景深度复制** 有通道本帧要绘制时（它的 `wantsFrame`，或默认"上一帧画过"），每帧复制两次场景深度
  （手部之前、半透明之前）。没有复制时通道照样能画，只是那一帧的遮挡改用当时的主深度。
* **API 稳定性** `ml.mypals.exit.api` 是 API；`impl`、`compat`、`mixin` 不是。API 里用到了 Minecraft 的类型
  （`SubmitNodeCollector`、`PoseStack`、`RenderTarget`），因此只在同一个 Minecraft 版本内保持稳定。

其它工具方法：`Exit.isActive()`（此刻是否会改道：有光影包、不在阴影通道、关卡渲染中）、`Exit.isRouting()`
（此刻是否有改道中的 portal）、`Exit.inPortal()`（此刻是否在 portal 里）、`Exit.plainFormat(Runnable)`。

## 调试

* `/emergency_exit debug <color|target_depth|scene_depth|main_depth|translucent_depth|off> [通道]`：用某个通道
  （默认主通道）的合并缓冲区替换画面（需要启用光影包）。
  * `color`：通道目标的颜色（灰色背景）
  * `target_depth`：通道目标的深度（由场景深度初始化）
  * `scene_depth`：半透明表面之前的场景深度（初始化的来源）
  * `main_depth`：最终的主深度
  * `translucent_depth`：只含半透明表面的深度
* `/emergency_exit debug shapes`：在玩家视线前方 5 格处生成三个测试方块，再输入一次关闭：
  * 红色（右）：主通道上的半透明绘制（Fabric 的 `BEFORE_ENTITIES`、NeoForge 的 `AfterOpaqueBlocks`），记进批量；从侧面看它在绿色前面，绿色透过它显出一点
  * 绿色（中）：主通道的批量（同样在 Fabric 的 `BEFORE_ENTITIES`、NeoForge 的 `AfterOpaqueBlocks`），不透明、写深度
  * 黄色（中，绿色后方偏右上）：另一个通道上的绘制（关卡渲染结束时），不透明、写深度；与主通道按深度混合，重叠处绿色在前
  * 蓝色（左）：portal 里直接对主目标开 RenderPass 绘制（同样在关卡渲染结束时，不调用 `redirect`），由 portal 自动改道到一个透过世界显示的独立通道（1.21.10 没有 Gizmo，所以没有青色线框）

## 开发

项目分三个子工程：`common`（与加载器无关的全部代码，包括 API、渲染和 mixin）、`fabric` 和 `neoforge`（入口、元数据、
命令注册和调试方块的事件挂钩），结构来自 MultiLoader-Template。加载器相关的功能通过 `Platform` 服务提供。

* `./gradlew build`：构建两个加载器的 jar（`fabric/build/libs`、`neoforge/build/libs`）。
* `./gradlew :fabric:runSmokeClient`、`./gradlew :neoforge:runSmokeClient`：创建世界，打开 `/emergency_exit debug shapes`
  的测试方块，截图后退出；日志最后是 `RESULT: OK`。运行目录分别是 `run/fabric-1.21.10` 和 `run/neoforge-1.21.10`，要测试改道，需要在
  其中的 `shaderpacks` 放入光影包并在 `config/iris.properties` 里启用。
* `./gradlew :fabric:runAuditClient`：检查每个 mixin 都已生效（只有 Fabric 版）。
* 冒烟测试另外保存 `*-presented.png`：窗口前缓冲，即玩家实际看到的画面。装了 Super Resolution 时 EXIT 仍按它的渲染尺寸工作，
  但 SR 没有 1.21.10 的版本，开发运行不加载它。
* NeoForge 的开发运行默认开着 Blaze3D 校验层，Iris 在它下面起不来：在 `run/neoforge-1.21.10/config/neoforge-client.toml`
  里设 `enableB3DValidationLayer = false`。
* `./gradlew publishToMavenLocal`：发布到本地 Maven，供其它 mod 的开发环境使用。

## 与 LitematicaPass 的关系（计划）

之后 LitematicaPass 在 26.3 上依赖 EXIT（用 `publishToMavenLocal` 构建，以 `include` 嵌入），
旧版本在 EXIT 移植过去之前继续使用自己的那份渲染流程：

| LitematicaPass 现在（26.3） | 改用 EXIT 后 |
|---|---|
| `SchematicTarget` + `SceneDepth` + `DepthSeed` + `Composite` | 一个通道 `litematicapass:schematic`，`wantsFrame` = Litematica 的渲染开关 |
| 包装 `piecewiseDrawBlockLayerGroup` | `channel.portal(() -> original.call(channel.redirect(fb), group));` |
| 实体进自己的存储，在不透明地形之后绘制 | `piecewiseRenderEntities` 里用 `channel.batch(queue)`；不透明组之后 `channel.flushBatch()` |
| 覆盖层和 `RenderHandler` 的框：`begin/end` | 外面包一层 `channel.portal(...)` |
| malilib `RenderUtils.mainTarget()` → `targetFor` | `Exit.isRouting() ? Exit.redirect(original) : original` |
| `ChunkRendererSchematicVboMixin.plainFormat` | `Exit.plainFormat(...)` |
| `LegacyTerrainFixUniformMixin`：`!SchematicTarget.isActive()` | `!Exit.isActive()` |
| `/litematicapass debug` | `/emergency_exit debug ... litematicapass:schematic` |
| `LevelRendererMixin`、`IrisRenderingPipelineMixin` | 在 EXIT 里 |
