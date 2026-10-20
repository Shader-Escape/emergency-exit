# EXIT（应急出口）

[English](README.md)

## 这是什么

Emergency Exit（应急c出口） 是一个渲染类库。它旨在提供便捷的方式，协助模组的自定义渲染逃离由Iris模组加载的ShaderPack（光影包）的魔爪。

该库以Minecraft26.3为原点向下移植，支持直到1.16.5的主要常见版本，支持Fabric与Neoforge。

## API调用

所有 API 都要在渲染线程上、关卡渲染（LevelRendering）期间调用。下面是 26.3 上能用的事件，其它版本见 [ChangeLog.md](ChangeLog.md) 对应版本的 **使用**。

| 适用的API| Fabric | NeoForge |
|---|---|---|
| `batch` | `LevelRenderEvents.COLLECT_SUBMITS` | `SubmitCustomGeometryEvent` |
| `draw` | `LevelRenderEvents.AFTER_SOLID_FEATURES`、`END_MAIN` 等 | `RenderLevelStageEvent.AfterOpaqueFeatures` 等 |
| `portal` | `LevelRenderEvents.END_MAIN` | 没有合适的事件 |

> NeoForge 的 `RenderLevelStageEvent.AfterLevel` 太晚了，在里面画的东西会被丢掉。

## draw：在世界某处画点东西

```java
LevelRenderEvents.END_MAIN.register(context ->
    Exit.draw(exit -> {
        Vec3 cam = exit.cameraPos();
        PoseStack pose = exit.poseStack();
        pose.translate(target.x - cam.x, target.y - cam.y, target.z - cam.z);
        exit.collector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, buffer) -> {
            buffer.addVertex(p, 0, 0, 0).setColor(0x80FF0000);
            // ...
        });
    }));
```

适合：
- 手头上没有合适的渲染器，需要即兴发挥。
- 选区框、高亮方块、路径线、标记点等极简自定义渲染。


> 注意到下例中，SubmitNodeCollector参数的传入。当光影包没有开启时，你的绘制会被递交给它。这意味着它在渲染时，会保持**原版情况**下的顺序与时机。如果不传入，你的绘制仍然会被渲染，但会被延后到关卡渲染结束前夕，不保证顺序与时机。

```java
LevelRenderEvents.AFTER_SOLID_FEATURES.register(context ->
    Exit.draw(context.submitNodeCollector(), exit -> 
    { /*画你的东西*/}
 ));
```

`exit` 提供：

| 方法 | 返回 |
|---|---|
| `collector()` | 往这里提交 |
| `poseStack()` | 原点在相机位置、不带旋转的 `PoseStack`。平移 `世界坐标 - cameraPos()` 就到了目标位置 |
| `cameraPos()` | 相机位置 |
| `camera()` | 本帧的 `CameraRenderState` |
| `partialTick()` | 相机实体本帧的 partial tick，做插值用 |
| `modelView()` | 视图旋转矩阵 |
| `routed()` | 这次是否绕过了光影包 |

## batch：把已有的提交代码整体搬过来

```java
LevelRenderEvents.COLLECT_SUBMITS.register(context ->
    myRenderer.submit(Exit.batch(context.submitNodeCollector())));
```

适合：
- 你已经有一个往 `SubmitNodeCollector` 提交的渲染器（Renderer），只想让它的输出绕过光影包。
- 你希望给一个现有模组的现有渲染器增加光影保护。

至于`draw` 和 `batch` 本身，它们画出来的东西进的是同一个批量，在同一时间画。区别只是 `draw` 多给了一些上下文。

## portal：自己开 RenderPass 的代码

```java
LevelRenderEvents.END_MAIN.register(context ->
    Exit.portal(() -> {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        mySchematicRenderer.render(main);  
        // 里面对 main 开 RenderPass、用自己的管线和顶点缓冲
    }));
```

适合：
- 因为各种原因，不合适走 `SubmitNodeCollector` 的渲染代码，比如投影/原理图渲染（Litematica的东西）、自己编译顶点缓冲的地形类渲染、自定义管线和着色器。

`portal` 里用原版 `Gizmos`（`Gizmos.cuboid`、`Gizmos.line` 等，1.21.11 起存在）加的图形也会绕过光影包，持续时间、淡出、总在最前这些属性照常生效。

```java
Exit.portal(() -> Gizmos.cuboid(box, GizmoStyle.stroke(0xFF30FFFF, 3)));
```

> 注意。lambda 当场执行。portal 期间，**任何 mod** 新开的、画进主画面的 RenderPass 都会被换走，不止你自己的。

### 半透明处理

多数的场景下，portal 当场画内容，而批量（draw，batch）要到关卡渲染结束才画，所以在没有处理的情况下，批量里的不透明内容会盖在 portal 画的半透明内容上面，哪怕它在更远处。如果有必要（确认你知道你在做什么）可以在 portal 里画半透明之前调用 `flushBatch()`，让收集的批量先画：

```java
Exit.portal(() -> {
    renderSolid(main);
    Exit.main().flushBatch();     // 批量里已有的内容现在就画
    renderTranslucent(main);
});
```

`flushBatch()` 在已打开的 RenderPass 里调用不起作用。

### 调整 Iris 的开关

portal 会临时设置 Iris 的三个开关，结束（或抛异常）后恢复原值。默认值一般不用改：

| 开关 | 默认 | 打开时 |
|---|---|---|
| `bypass` | `ON` | 光影包不替换原版管线 |
| `skipExtension` | `ON` | 这时候构建的顶点缓冲保持原版顶点格式（只影响当前线程） |
| `renderingLevel` | `KEEP` | Iris 会给提交管线写出的顶点加上光影包需要的数据 |

```java
Exit.portal(
   PortalOverrides.DEFAULT.withRenderingLevel(
  PortalOverrides.Switch.OFF), () -> { ... }
);
```

`Switch` 有三个值：`ON`、`OFF`、`KEEP（原封不动）`。

## redirect：portal 管不到的目标

portal 只会把"新开的、画进主画面的 RenderPass"换走。面对这以外的目标，要你自己用 `redirect` 替换：

- 画进主画面以外的目标；
- 对主画面的清除、复制（`clear*`、`copyTextureToTexture` 等）。

```java
Exit.portal(() -> {
    RenderTarget out = Exit.redirect(main);  
    // 绕过光影包时返回 EXIT 的目标，否则原样返回 main
    RenderSystem.getDevice().createCommandEncoder()
        .clearDepthTexture(out.getDepthTexture(), 1.0); 
        // 只清 EXIT 目标的深度，不碰真正的主画面
    renderOnTop(out);
});
```

拿得到通道对象时用 `channel.redirect(target)`，拿不到时用静态的 `Exit.redirect(target)`，它找当前打开 portal 的那个通道。

`redirect` 只能在 portal 或 `draw` 的 lambda 里调用，在外面、在别的线程、或在另一个通道的 portal 里调用都会抛 `IllegalStateException`。如果同一段代码在 portal 内外都会跑，先检查：

```java
RenderTarget out = Exit.isRouting() ? Exit.redirect(main) : main;
```

## plainFormat：在工作线程上构建顶点缓冲

```java
// 编译线程
Exit.plainFormat(() -> buildMyVertexBuffer());
```
你可能会因为各种原因需要在非渲染线程上构建顶点缓冲（比如大量方块的渲染），然而，Iris 会给构建出来的顶点缓冲加上额外数据，之后用原版管线在 portal 里画也会出错。
因此，在 `plainFormat` 里构建你的缓冲，确保缓冲保持原版格式。它在任何线程、任何时候都能用，阴影阶段也会执行。

> 虽然在非渲染线程调用 `portal` 效果相同，但3个开关中，只能关掉当前线程的格式加宽，其它开关无效，也不能重定向任何内容。

## 通道：给不同内容不同的显示方式

上面的 `Exit.draw/batch/portal` 用的都是共享的主通道。但若想要透视等效果，你可以声明自己的通道：

```java
ExitChannel xray = Exit.channel(Identifier.fromNamespaceAndPath("mymod", "xray"),
    ExitOptions.builder()
        .occludedByWorld(false)    // 透过方块显示
        .build());

xray.draw(exit -> { ... });
xray.batch(collector);
xray.portal(() -> { ... });
xray.redirect(target);
xray.flushBatch();
```

几种常见设置：

```java
// 透视：矿物、实体、隐藏方块高亮
ExitOptions.builder().occludedByWorld(false).build();

// 总在最前：任何东西都挡不住的标记，包括其它 EXIT 通道
ExitOptions.builder().occludedByWorld(false).blendWithChannels(false).priority(100).build();

// 只在开启功能时工作，省掉没用的开销
ExitOptions.builder().wantsFrame(() -> MyConfig.enabled).build();
```

| 选项 | 默认 | 说明 |
|---|---|---|
| `occludedByWorld` | `true` | 被方块遮挡。`false` 时透过方块显示 |
| `hideUnderHand` | `true` | 第一人称的手挡在前面 |
| `dimBehindTranslucent` | `0.5` | 在半透明方块后面时的亮度，0 到 1，`1` 表示不变暗 |
| `priority` | `0` | 数字大的盖在上面。两个都开着 `blendWithChannels` 的通道只在深度相同时看它 |
| `blendWithChannels` | `true` | 和其它通道按远近互相遮挡。`false` 时盖在所有混合通道上面 |
| `wantsFrame` | `null` | 本帧会不会画。`null` 表示"上一帧画过就算会画" |

通道之间怎么遮挡：

* 同一通道里的内容按远近互相遮挡。
* 开着 `blendWithChannels` 的通道之间也按远近遮挡，近的在前。`occludedByWorld(false)` 只能透过方块，透不过其它通道里更近的内容。
* 关掉 `blendWithChannels` 的通道盖在所有混合通道上面，它们之间按 `priority` 排，相同时先创建的在下面。

同一个 id 第二次调用 `Exit.channel` 返回同一个通道；选项和第一次不同会被视为找茬*。

## 状态查询

| 方法 | 为 `true` 时 |
|---|---|
| `Exit.isActive()` | 现在画的东西会绕过光影包（开着光影包、在关卡渲染中、不在阴影阶段） |
| `Exit.isRouting()` | 现在在一个正在绕过光影包的 portal 里 |
| `Exit.inPortal()` | 现在在 portal 或 `draw` 的 lambda 里 |

在渲染线程以外，`isRouting()` 和 `inPortal()` 总是 `false`。

## 限制

* **调用时机。** 只能在渲染线程上、关卡渲染期间调用（`plainFormat` 和非渲染线程的 `portal` 除外）。在这之外调用，`draw` 和 `batch` 的内容会被丢掉（日志里警告一次），portal 照常执行但不绕过光影包。
* **portal 的位置。** portal 要在关卡**开始渲染之后**调用，比如 Fabric 的 `END_MAIN`，而且不能在已打开的 RenderPass 里。portal 开始前就开着的 RenderPass 不会被换走，在里面画的东西照样被光影包处理。
* **没有影子。** 光影包画阴影时，`draw` 和 `portal` 的 lambda 不执行，`batch` 的内容被丢掉。
* **portal 里的半透明。** 先看上面的"先画批量，再画半透明"。另外，你自己在 portal 里开 RenderPass 画的半透明内容，和其它通道比较远近时会被当成在很远处，容易被盖住；通过 `draw`、`batch` 提交的半透明内容没有这个问题。
* **三个以上通道。** 三个以上的混合通道在同一个像素上前后交错时，遮挡可能不准。两个通道总是准的。
* **嵌套。** portal 可以嵌套，`draw` 里也能再调用 `draw`，但同一时间只能在一个通道上；在一个通道的 portal 里打开另一个通道会抛异常。
* **显存。** 每个通道多占一张和画面一样大的颜色+深度纹理，画过半透明再多一张深度纹理；有两个以上通道混合时再多两张。不要每个物体建一个通道。
* **`wantsFrame` 返回 `false` 却还是画了。** 内容照样显示，只是那一帧被方块遮挡的效果可能不准。
* **颜色。** 内容的颜色按预乘 alpha 合到画面上，原版的半透明渲染类型没问题。自定义管线要输出预乘 alpha 的颜色。
* **API 只在同一个 Minecraft 版本内稳定。** API 里用到了 Minecraft 的类型（`SubmitNodeCollector`、`PoseStack`、`RenderTarget`），旧版本里有的会换成作用相同的类型，见 ChangeLog。只用 `ml.mypals.exit.api` 包，`impl`、`compat`、`mixin` 随时会改。

## 调试

* `/emergency_exit debug shapes`：在视线前方 5 格生成四个测试方块，再输入一次关掉。
  * 红（右）：主通道上 `draw` 的半透明方块。
  * 绿（中）：主通道 `batch` 的不透明方块。
  * 黄（绿色后面偏右上）：另一个通道上 `draw` 的不透明方块，和绿色按远近互相遮挡。
  * 蓝（左）：透视通道 `portal` 里直接对主画面开 RenderPass 画的方块，外面的青色线框是同一个 portal 里加的 Gizmo。
* `/emergency_exit debug <color|target_depth|scene_depth|main_depth|translucent_depth|off> [通道]`：需要开着光影包。用某个通道（默认主通道）的缓冲区替换画面，看你的内容到底画进去没有、被谁挡住了。`color` 是通道里画的颜色，`off` 恢复正常画面，其余几个是各种深度。

## 贡献者

* [hotpad100c](https://github.com/hotpad100c)（Ryan100C）：作者。
* [wwwzzzxxx](https://github.com/wwwzzzxxx)：移植到 Minecraft 1.16.5 至 1.20.6。
