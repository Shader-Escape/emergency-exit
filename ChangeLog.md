# 版本差异记录（ChangeLog）

EXIT 每个 Minecraft 版本一个 git 分支，分支里只有该版本的代码。本文件只在 `master` 上维护，
按版本从新到旧，记下每个版本和**上一个更新的版本**相比，Minecraft、加载器和 Iris 的哪些差异影响到 EXIT。
移植到更旧的版本时，从最接近的分支开出，照着这里改。

每节开头的 **使用** 写给用 EXIT 的 mod：这个版本的 Maven 坐标、各加载器在哪些事件里调用 EXIT、API 类型和 README（以 26.3 为准）
有什么不同。README 只在 `master` 上维护。1.16.5 到 1.20.4 的分支已经删掉了自己的 README，以 `master` 的 README 和本文件为准。

Maven 坐标是 JitPack 的：`com.github.Shader-Escape.emergency-exit:emergency_exit-<加载器>:<EXIT版本>-<Minecraft版本>`，
下面简写成 `…:0.1.5-<mc>`。JitPack 不接受 `+`，模组自己的版本仍写作 `0.1.5+<mc>`。目前 19 个版本都是 0.1.5。
在分支里 `publishToMavenLocal` 会用同样的坐标发布到本地；把 mavenLocal 排在 JitPack 前面的项目（比如 LFM）会用这份本地构建，
只要它不比 JitPack 上的旧。改完先本地发布、测试，再发正式版本。

所有版本都有的改动（不算版本差异，记在这里）：
- 0.1.3 起，合并通道时（`Composite` 和 `ChannelBlend`），`occludedByWorld` 的通道在半透明之前的场景深度更近的地方丢掉像素。
  这份深度里已经有实体和方块实体，所以在实体之前画的东西（Litematica 的方块层、EXIT 的调试形状）也会被实体挡住。
  以前只靠深度种子遮挡，而种子在通道本帧第一次使用时就取了，那时实体还没画。
- 0.1.4 起，深度种子和合并时的遮挡都容许世界深度一个像素的斜率（每个轴取和邻居差得较小的那个，边缘照样遮挡），
  贴在世界表面上的通道内容，不会因为两份深度之间的细微差别在斜面上成条地被挡掉。
  合并时世界深度也换到"种子空间"再比较（和种子一样按 `PUSH` 推远、加上斜率）：不写深度的线和选区框，通道深度就是种子，
  以前和没推远的世界深度比，取整决定每个像素，结果断成固定的条纹。
- 0.1.5 起，1.20 及更旧（Sodium 0.4）：Sodium 0.4 在实心层之后才画 cutout 和 cutout_mipped 地形（草方块、树叶），
  而 Litematica 的方块层挂在实心层末尾，通道在那里第一次使用、取深度种子，种子里还没有这些地形。
  之后不写深度的选区框按旧种子做深度测试，合并时却和完整的场景深度比，在草地前面整块消失。
  现在 Iris 取半透明之前的场景深度时，本帧已经播种的通道重新播种：只改深度仍等于原种子（没画过东西）的像素，
  画过的像素保留自己的深度。

| Minecraft | 分支 | Java | 映射 | Iris（开发运行） |
|---|---|---|---|---|
| 26.3 | `master` | 25 | 无混淆 | 1.11.x |
| 26.2 | `port/26.2` | 25 | 无混淆 | 1.11.4 |
| 26.1.2 | `port/26.1.2` | 25 | 无混淆 | 1.11.4（26.1） |
| 1.21.11 | `port/1.21.11` | 21 | 混淆，Mojang 映射 | 1.10.8 |
| 1.21.10 | `port/1.21.10` | 21 | 混淆，Mojang 映射 | 1.9.7 |
| 1.21.8 | `port/1.21.8` | 21 | 混淆，Mojang 映射 | 1.9.6 |
| 1.21.5 | `port/1.21.5` | 21 | 混淆，Mojang 映射 | 1.8.11 |
| 1.21.4 | `port/1.21.4` | 21 | 混淆，Mojang 映射 | 1.8.8 |
| 1.21.3 | `port/1.21.3` | 21 | 混淆，Mojang 映射 | 1.8.1 |
| 1.21.1 | `port/1.21.1` | 21 | 混淆，Mojang 映射 | 1.8.8 |
| 1.20.6 | `port/1.20.6` | 21 | 混淆，Mojang 映射 | 1.7.2 |
| 1.20.4 | `port/1.20.4` | 17 | 混淆，Mojang 映射 | 1.7.2 |
| 1.20.2 | `port/1.20.2` | 17 | 混淆，Mojang 映射 | 1.6.14 |
| 1.20.1 | `port/1.20.1` | 17 | 混淆，Mojang 映射 | 1.7.6 |
| 1.20 | `port/1.20` | 17 | 混淆，Mojang 映射 | 1.6.4 |
| 1.19.4 | `port/1.19.4` | 17 | 混淆，Mojang 映射 | 1.6.11 |
| 1.18.2 | `port/1.18.2` | 17 | 混淆，Mojang 映射 | 1.6.11 |
| 1.17.1 | `port/1.17.1` | 16 | 混淆，Mojang 映射 | 1.2.7 |
| 1.16.5 | `port/1.16.5` | 16 | 混淆，Mojang 映射 | 1.4.5 |

## 26.2（相对 26.3）

**使用**
- Maven `…:0.1.5-26.2`。事件和 API 同 26.3（见 README）。
- `PortalOverrides.DEFAULT` 把 `renderingLevel` 设为 `OFF`（26.3 是 `KEEP`），更旧的分支也一样，原因见下面的 **Iris**。
  在 portal 里画 `Exit.plainFormat` 里建的缓冲时，`renderingLevel` 要保持关着。

**包名**
- 渲染 API 还在 `com.mojang.blaze3d`，没有 `com.mojang.renderpearl`。例如 `RenderPass`、`RenderPassDescriptor` 在
  `com.mojang.blaze3d.systems`，`GpuFormat`、`PrimitiveTopology` 在 `com.mojang.blaze3d`，`CompareOp` 在
  `com.mojang.blaze3d.platform`，`GpuBufferSlice` 在 `com.mojang.blaze3d.buffers`。

**渲染通道**
- `RenderPassDescriptor` 用 `create(label)` 创建，可变，没有 `builder()` 和 `build()`。空的颜色附件用 `withUnusedColorAttachment()`。
- 每次 `createCommandEncoder()` 都返回新的编码器，`isInRenderPass` 只管自己。EXIT 在 `PortalPassMixin` 里给所有编码器的打开和提交计数。
- 所有 `createRenderPass` 重载最后都进 `CommandEncoder.createRenderPass(RenderPassDescriptor)`，portal 在这里换附件。
- 没有 `RenderSystem.getCompiledPipeline`。`RenderPass.setPipeline` 直接接收 `RenderPipeline`，深度变体在这里替换。
- 绑定采样器用 `pass.bindTexture(name, view, sampler)`，不是 `setUniform`。
- 缩放探针在打开自己的渲染通道之前写好变换（`writeTransform`）、取好索引缓冲；在通道里做会抛
  `Close the existing render pass`（0.1.1 起，26.1.2 相同）。

**管线和目标**
- 采样器用 `BindGroupLayout.withSampler(name)`，没有 `UniformType.COMBINED_IMAGE_SAMPLER`。
- 只写深度的管线也要有颜色目标（`WRITE_NONE`），通道也要挂上颜色附件。
- `TextureTarget(label, w, h, useDepth, colorFormat)`：总有颜色纹理，没有只有深度的目标。
- `RenderPipeline` 的构造参数是分开的 vertex/fragment 着色器，没有 `pushConstantSize`。

**特征（feature）绘制**
- 没有在一个通道里画全部特征的方法。每个特征自己开通道，输出到 `RenderSystem.outputColorTextureOverride` /
  `outputDepthTextureOverride` 指向的目标。EXIT 画的时候把它们指向目标，并设好关卡投影。
- `PreparedFrame` 没有 `isEmpty()`，也没有 `executeSeeThrough`。EXIT 看画的时候有没有开过通道。
- `SubmitNodeStorage` 没有 `setUseImprovedTransparency`。

**关卡钩子**
- `LevelRenderer.render(allocator, DeltaTracker, renderOutline, CameraRenderState, Matrix4fc modelView, fog, fogColor, sky)`。
- `CameraRenderState` 没有 `cameraEntityPartialTicks`，partial tick 从 `DeltaTracker` 取。

**Iris**
- Iris 1.11.4 有 `ImmediateState.skipExtension`（每线程），`BufferBuilder` 只在没设它、`isRenderingLevel` 开着时加宽格式。
  但它在 `isRenderingLevel` 开着时把原版管线的格式（`RenderPipeline.getVertexFormatBinding(s)`、`RenderType.format()`）
  也换成加宽的，**在所有线程上**。`isRenderingLevel` 是渲染线程设的全局变量，所以工作线程上按管线格式建的缓冲是原版还是
  加宽格式，要看那一刻渲染线程在不在渲染关卡；在开着 `isRenderingLevel` 的 portal 里画时总按加宽格式，三角形就乱了，
  每次重建都不一样。Iris 1.11.6（26.3）的管线钩子只在渲染线程、且没设 `skipExtension` 时加宽，所以 26.3 没这个问题。
- 所以 EXIT 在这里真的设 `skipExtension`（`IrisCompat.hasSkipExtension()` 为 true），`PlainFormatPipelineMixin` 和
  `PlainFormatRenderTypeMixin` 在设了它的线程上返回原版格式（在 HEAD 返回，Iris 在 RETURN 的回调就不跑了）。
  portal 默认关掉 `isRenderingLevel`，画的时候管线也是原版格式。以前这里不动 `skipExtension`，是因为只设它时缓冲拿到的是
  管线给的加宽格式却没有额外数据（"Missing elements in vertex: mc_midTexCoord, at_tangent"）。
- 画特征时 `isRenderingLevel` 仍要设为 false（和 26.3 一样）。LitematicaPass 对实体设 true，但在 EXIT 里那样
  `entitySolid` 会画不出来。

**开发运行**
- `StrictValidationMixin` 的目标是 `GlRenderPass` 和 `VulkanRenderPass`。
- 运行目录按版本分开：`run/<加载器>-<版本>`。共用目录里的模组（Mod Menu、Super Resolution）只支持 26.3。

## 26.1.2（相对 26.2）

**使用**
- Maven `…:0.1.5-26.1.2`。事件和 API 同 26.3。

**深度方向**
- 深度是标准方向：近 = 0，远 = 1，默认深度测试是 `LESS_THAN_OR_EQUAL`。26.2 起是反向（近 = 1，远 = 0）。
- `Gpu.FAR_DEPTH = 1.0`。着色器里 `closeness(depth) = 1.0 - depth`，`depthOf(c) = 1.0 - c`。

**渲染通道**
- 没有 `RenderPassDescriptor`，也没有渲染区域。`createRenderPass(label, color, OptionalInt clearColor, depth, OptionalDouble clearDepth)`，颜色附件必须有。
- portal 用 `@WrapMethod` 包住这个方法换附件。全屏通道用 scissor 限制在场景区域。
- 关闭通道在 `RenderPass.close()` 里调 `RenderPassBackend.close()`，EXIT 在这里计数。
- 清除颜色用 `int`，不是 `Vector4f`。
- 画全屏三角形用 `pass.draw(0, 3)`。

**管线**
- `ColorTargetState(blend, writeMask)` 只有一个，没有格式。
- 采样器直接 `builder.withSampler(name)`，没有 `BindGroupLayout`。
- 要设顶点格式：`withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)`。
- `TextureTarget(label, w, h, useDepth)`。

**特征和 gizmo**
- `FeatureRenderDispatcher` 构造时绑定一个 `SubmitNodeStorage`，画进 buffer source，`endBatch()` 时才开通道。
  EXIT 复用一个分发器，用 `FeatureRenderDispatcherAccessor`（`@Mutable @Accessor`）换 storage。
- 方法是 `renderSolidFeatures`、`renderTranslucentFeatures`、`renderTranslucentParticles`、`clearSubmitNodes`。
- 构造参数：`(storage, ModelManager, bufferSource, AtlasManager, outlineBufferSource, crumblingBufferSource, Font, GameRenderState)`。
- `RenderBuffers` 没有 `close()` 和 `endFrame()`。
- gizmo 不能提交进 storage：`DrawableGizmoPrimitives.render(poseStack, bufferSource, camera, modelView)` 直接画。
  通道在画 batch 时一起画。总在最前的 gizmo 不清深度，仍会被挡住（和 26.3 不同）。

**关卡钩子和其它**
- 方法名是 `LevelRenderer.renderLevel`，最后多一个 `ChunkSectionsToRender` 参数。
- 主目标用 `Minecraft.getMainRenderTarget()`，当前界面是 `Minecraft.screen`，`getOverlay()`。
- 只有 OpenGL 后端，`StrictValidationMixin` 只针对 `GlRenderPass`。
- Iris 同 26.2，只是管线钩子在 `RenderPipeline.getVertexFormat()` 上，没有 `RenderType.format()` 的钩子；
  `PlainFormatPipelineMixin` 改挂 `getVertexFormat()`。

## 1.21.11（相对 26.1.2）

**使用**
- Maven `…:0.1.5-1.21.11`。API 同 26.3。关卡在 `LevelRenderer.renderLevel` 里渲染。
- Fabric（`WorldRenderEvents`，`net.fabricmc.fabric.api.client.rendering.v1.world`）：批量和绘制在 `BEFORE_ENTITIES`
  （直通 `context.commandQueue()`），绘制也可在 `AFTER_ENTITIES`、`END_MAIN` 等；portal 在 `END_MAIN`。
- NeoForge：`RenderLevelStageEvent.AfterOpaqueBlocks` 等。没有交出关卡收集器的事件，没东西可直通：用 `Exit.batch()`、
  `Exit.draw(null, …)`，没有光影包时批量在关卡渲染结束时画进主目标。

**构建**
- Java 21。Minecraft 是混淆的。
- Fabric：插件 `net.fabricmc.fabric-loom-remap`，`mappings(loom.officialMojangMappings())`，依赖用 `modImplementation`、
  `modCompileOnly`、`modLocalRuntime`。Loom 直接改写 jar 里的混入注解（变成 intermediary 名），不用 refmap。
- common 和 NeoForge：NeoForm `1.21.11-20251209.172050`，NeoForge `21.11.48`，运行时就是 Mojang 名，不用重映射。

**包名和类**
- `CameraRenderState` 在 `net.minecraft.client.renderer.state`（26.x 在 `...state.level`），没有 `viewRotationMatrix`。
- 已经有 `Identifier`（不是 `ResourceLocation`）和 JSpecify。
- `CommandEncoder`、`RenderPass` 是接口，唯一实现是 `GlCommandEncoder`、`GlRenderPass`。混入要针对实现类：
  `GlCommandEncoder.createRenderPass`（5 参数）、`GlCommandEncoder.finishRenderPass`、`GlRenderPass.setPipeline`。

**管线**
- 没有 `ColorTargetState`、`DepthStencilState`、`CompareOp`。在 builder 上设：`withBlend` / `withoutBlend`、
  `withColorWrite(color, alpha)`、`withDepthWrite`、`withDepthTestFunction(DepthTestFunction)`。
- `DepthTestFunction.NO_DEPTH_TEST` 会关掉 OpenGL 深度测试，深度也就不写了。所以混合层（ChannelBlend）先把目标清成远处，
  再用 `LEQUAL_DEPTH_TEST` 写深度。只写深度的变体在原管线不测深度时改用 `LEQUAL_DEPTH_TEST`。
- `RenderPipeline` 构造参数：`(location, vsh, fsh, defines, samplers, uniforms, Optional<BlendFunction>, DepthTestFunction,
  PolygonMode, cull, writeColor, writeAlpha, writeDepth, LogicOp, VertexFormat, Mode, biasScale, biasConstant, sortKey)`。

**特征**
- `FeatureRenderDispatcher` 只有 `renderAllFeatures()`，画完会清空 storage，没有实体/半透明之分。自定义几何按渲染类型
  存在 `HashMap` 里，先画哪种不固定。EXIT 用 `FeatureRenderDispatcherMixin` 在 `FeatureDrawer.keepSubmits` 时跳过清空，
  把同一份提交画几次：自己的 buffer source 第一次只收实体（管线没有混合）的渲染类型，第二次只收半透明的，
  写半透明深度时再画一次半透明。
- 构造参数：`(storage, BlockRenderDispatcher, bufferSource, AtlasManager, outlineBufferSource, crumblingBufferSource, Font)`。
- `DrawableGizmoPrimitives.render` 的视图矩阵参数是 `Matrix4f`。

**关卡钩子**
- `LevelRenderer.renderLevel(allocator, DeltaTracker, renderOutline, Camera, Matrix4f modelView, Matrix4f projection,
  Matrix4f cullingProjection, fog, fogColor, sky)`。相机状态从 `levelRenderState.cameraRenderState` 取（同一个对象，
  每帧原地更新）。视图旋转用 `modelView` 参数。

**加载器事件**
- Fabric API：`WorldRenderEvents`（`net.fabricmc.fabric.api.client.rendering.v1.world`），上下文是 `commandQueue()`、
  `worldState()`。没有 `LevelRenderEvents`。调试方块用 `BEFORE_ENTITIES`。
- NeoForge 21.11：没有 `SubmitCustomGeometryEvent`，`RenderLevelStageEvent` 的子事件是 `AfterOpaqueBlocks`、`AfterEntities` 等，
  拿不到关卡的 collector。调试方块的 batch 不给 collector。

**Iris**
- Iris 1.10.8 没有 `IrisApi.registerShadowRenderCallback`。冒烟测试改用 `ShadowRendererMixin`（在 `ShadowRenderer.renderShadows`
  设 `ACTIVE` 之后）。
- Iris 1.10.8 的格式钩子和 26.1.2 相同（`skipExtension`、`RenderPipeline.getVertexFormat()`），EXIT 的处理也相同。

**开发运行**
- NeoForge 开发环境默认开着 Blaze3D 校验层，Iris 1.10.8 启动时会崩（`ValidationGpuDevice` 不能转成 `GlDevice`）。
  在 `run/neoforge-1.21.11/config/neoforge-client.toml` 里设 `enableB3DValidationLayer = false`。

## 1.21.10（相对 1.21.11）

**使用**
- Maven `…:0.1.5-1.21.10`。事件同 1.21.11。
- API 用 `ResourceLocation` 代替 `Identifier`（`Exit.MAIN`、`Exit.channel`、`ExitChannel.id()`），`@Nullable` 是
  `org.jetbrains.annotations.Nullable`。
- 没有 Gizmo：README 里 portal 收 Gizmo 的规则和调试方块的青色线框都没有。

**构建**
- NeoForm `1.21.10-20251010.172816`，NeoForge `21.10.64`，Fabric API `0.138.4+1.21.10`，Iris 1.9.7、Sodium 0.7.3。
- NeoForm 1.21.10 把 ASM 钉死在 9.6（`strictly`），和 sponge-mixin 要的 9.8 冲突。common 里 sponge-mixin 不带传递依赖，另加 `asm-tree:9.6`。
- Super Resolution 没有 1.21.10 的版本，去掉了 `-Pexit.superResolution`（`SceneSize` 仍会跟随装上的 SR）。

**包名和类**
- `ResourceLocation` 代替 `Identifier`，API 里也一样（`Exit.MAIN`、`Exit.channel`、`ExitChannel.id()`）。
- 没有 JSpecify，用 `org.jetbrains.annotations.Nullable`。
- 渲染类型是 `net.minecraft.client.renderer.RenderType`，工厂方法在它自己身上（`RenderType.entitySolid`、`debugQuads`），
  没有 `RenderTypes`、`RenderSetup`。

**渲染通道**
- 没有采样器对象：绑定纹理用 `RenderPass.bindSampler(name, view)`，没有 `bindTexture(name, view, sampler)`、`getSamplerCache()`。
- `DynamicUniforms.writeTransform` 多一个线宽参数；`GpuDevice.createBuffer` 的大小是 `int`。
- 渲染类型的纹理由 `setupRenderState()` 设成着色器纹理，画的时候绑定为 `Sampler0`…`Sampler11`。缩放探针照这样做，
  去掉了 `RenderTypeAccessor`。
- `writeTransform` 会映射缓冲区，不能在打开的渲染通道里调用。缩放探针原来在通道里写，1.21.11 上同样失败
  （`Close the existing render pass`，Kappa 不再被测出缩放），1.21.10 改成开通道之前写。

**特征和 gizmo**
- 没有 gizmo（`net.minecraft.gizmos` 是 1.21.11 才有的）。去掉了 gizmo 进通道的规则、调试方块的青色线框和冒烟测试里的这项检查。
- 特征分发器、关卡钩子 `renderLevel(...)`、`GlCommandEncoder`/`GlRenderPass` 的混入点和 1.21.11 相同。

**加载器事件**
- Fabric API 的 `WorldRenderEvents` 和 1.21.11 相同（`BEFORE_ENTITIES`、`END_MAIN` 等，`commandQueue()`）。
- NeoForge 21.10 和 21.11 一样没有 `SubmitCustomGeometryEvent`，调试方块的批量不给 collector。

**Iris**
- Iris 1.9.7 的格式钩子和 1.10.8 相同，EXIT 的处理也相同。
- 没有 `registerShadowRenderCallback`，冒烟测试仍用 `ShadowRendererMixin`。

**开发运行**
- NeoForge 开发运行同样要在 `run/neoforge-1.21.10/config/neoforge-client.toml` 里设 `enableB3DValidationLayer = false`。
- 冒烟测试一次跑完：先不开光影，再对 `shaderpacks` 里每个光影包各跑一轮（通过 Iris 的配置和 `Iris.reload()` 切换），
  截图名里带上光影包名。

## 1.21.8（相对 1.21.10）

**使用**
- Maven `…:0.1.5-1.21.8`。
- API 的 `SubmitNodeCollector` 换成 `MultiBufferSource`：`Exit.draw(MultiBufferSource, …)`、`ExitContext.bufferSource()`
  （代替 `collector()`）、`Exit.batch(MultiBufferSource)` 收、交 `MultiBufferSource`；`ExitContext.camera()` 是 `Camera`。
  绘制和批量里画的东西照样记进通道的批量，帧末先实体后半透明。
- Fabric（`WorldRenderEvents`，`net.fabricmc.fabric.api.client.rendering.v1`）：批量和绘制在 `BEFORE_ENTITIES`
  （直通 `context.consumers()`），也可在 `AFTER_ENTITIES`、`LAST`；portal 也能在这些事件里用（关卡不会一直开着一个渲染通道）。
  不要用 `END`：它和 EXIT 的合并在同一处。
- NeoForge：`RenderLevelStageEvent.AfterOpaqueBlocks` 等，不给 buffer source：用 `Exit.batch()`、`Exit.draw(null, …)`。
  `AfterLevel` 同样不要用。

**构建**
- NeoForm `1.21.8-20250717.133445`，NeoForge `21.8.57`，Fabric API `0.136.1+1.21.8`，Iris 1.9.6、Sodium 0.7.3。
- NeoForge 21.8 的 FML 是静态的 `FMLLoader.getLoadingModList()`、`FMLLoader.isProduction()`，没有 `FMLLoader.getCurrent()`。
- 冒烟测试的建世界：`CreateWorldScreen.openFresh(mc, lastScreen)` 收的是上一个界面，不是回调。

**API**
- 没有 `SubmitNodeCollector`，按移植说明 4.1 换成 `MultiBufferSource`：`Exit.draw(MultiBufferSource, …)`、
  `ExitChannel.draw(MultiBufferSource, …)`、`ExitContext.bufferSource()`（代替 `collector()`）、
  `Exit.batch(…)`/`ExitChannel.batch(…)` 收、交 `MultiBufferSource`。
- 没有相机渲染状态 `CameraRenderState`，`ExitContext.camera()` 交出 `Camera`（`cameraPos()` 取 `getPosition()`）。

**特征（批量）**
- 没有 `SubmitNodeStorage` 和特征分发器。通道的批量是 EXIT 自己的 buffer source（`BatchDrawer.Batch`）：每种渲染类型
  一块 `ByteBufferBuilder`，跨帧复用。原版 `BufferSource` 在再次取同一类型（线带、扇形等相连图元）或换到共享缓冲时
  会当场画出，EXIT 的不会：相连图元的上一段先封成网格存着。
- 帧末先画不混合的渲染类型，再画半透明的。判断用渲染类型的管线有没有混合（`CompositeRenderTypeAccessor`，
  1.21.8 的 `RenderType` 没有 `pipeline()`），别的 `RenderType` 子类按 `sortOnUpload()`。
- 半透明渲染类型的顶点同时写进第二组缓冲（`VertexMultiConsumer`），半透明深度用它再画一遍。
- 开始缓冲时关掉 Iris 的 `isRenderingLevel`，因为 Iris 在 `BufferBuilder` 构造时决定要不要加宽格式。
- 去掉了 `FeatureRenderDispatcherAccessor`、`FeatureRenderDispatcherMixin`；`FeatureDrawer` 改名 `BatchDrawer`。

**渲染通道**
- 没有 `core/screenquad`（1.21.9 才有）。全屏通道用 `core/blit_screen`、`POSITION`/`QUADS` 格式、
  `RenderSystem.getQuadVertexBuffer()` 和四边形索引画（照 LitematicaPass）。索引缓冲在开通道之前取。
- 1.21.8 画的时候必须有槽 0 的顶点缓冲（开发环境的校验），不能像 1.21.9 起那样不绑顶点就画。

**关卡钩子**
- `LevelRenderer.renderLevel(GraphicsResourceAllocator, DeltaTracker, boolean, Camera, Matrix4f, Matrix4f, GpuBufferSlice,
  Vector4f, boolean)`：没有剔除投影参数，也没有 `LevelRenderState`。相机直接取参数里的 `Camera`。
- 帧图（`FrameGraphBuilder.execute`）和 1.21.10 相同。主渲染通道里不会一直开着一个 `RenderPass`（每种渲染类型画时自己开），
  所以主渲染通道里的事件都能用 portal。

**加载器事件**
- Fabric API 的 `WorldRenderEvents` 在 `net.fabricmc.fabric.api.client.rendering.v1`（不是 `.world`），
  上下文给 `consumers()`（`MultiBufferSource`）和 `camera()`；没有 `END_MAIN`，用 `LAST`。`END` 在 `renderLevel` 返回时触发，
  和 EXIT 的合并在同一处，不保证在合并之前。
- NeoForge 21.8 的 `RenderLevelStageEvent.*` 不给 buffer source，调试方块用 `getCamera()`，不直通。

**Iris**
- Iris 1.9.6 的格式钩子和 1.9.7 相同，EXIT 的处理也相同。
- 没有 `registerShadowRenderCallback`，冒烟测试仍用 `ShadowRendererMixin`。

**开发运行**
- NeoForge 开发运行同样要在 `run/neoforge-1.21.8/config/neoforge-client.toml` 里设 `enableB3DValidationLayer = false`。
- 冒烟测试另外检查 EXIT 的每条管线都能编译（`precompilePipeline(...).isValid()`）。没有这一项时，全屏着色器缺失只会
  在日志里报错，计数照样通过。

## 1.21.5（相对 1.21.8）

**使用**
- Maven `…:0.1.5-1.21.5`。API 和 Fabric 事件同 1.21.8。
- NeoForge 只有一个 `RenderLevelStageEvent`，看 `getStage()`：比如 `Stage.AFTER_CUTOUT_BLOCKS`；不要用 `Stage.AFTER_LEVEL`。
- `PortalOverrides.skipExtension` 在这个版本真的起作用（Iris 1.8），和更新的版本一样。

**构建**
- NeoForm `1.21.5-20250325.162830`，NeoForge `21.5.101`，Fabric API `0.128.2+1.21.5`，Iris 1.8.11、Sodium 0.6.13，
  glsl-transformer 2.0.1。

**渲染通道和纹理**
- 没有 `GpuTextureView`：通道附件、采样器、目标都直接用 `GpuTexture`（`getColorTexture()`、`getDepthTexture()`）。
- `createRenderPass(color, OptionalInt, depth, OptionalDouble)` 没有标签；`RenderPass.drawIndexed(first, count)`。
- 没有 `bindDefaultUniforms`、`DynamicUniforms`：画的时候直接从 `RenderSystem` 读投影和 model-view。投影是 `Matrix4f`，
  EXIT 用 `backupProjectionMatrix`/`restoreProjectionMatrix` 换关卡投影；缩放探针在 model-view 栈上压入自己的矩阵。
- `GpuBuffer` 用 `BufferType`/`BufferUsage` 创建，回读用 `readBuffer`。`RenderPipeline` 构造没有排序键。
- 没有 `BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA`，直接写 `(ONE, ONE_MINUS_SRC_ALPHA)` 两组因子。

**输出覆盖**
- 没有 `RenderSystem.outputColorTextureOverride`。EXIT 自己记一个输出（`ExitRuntime.setOutput`），`PortalPassMixin`
  在 `GlCommandEncoder.createRenderPass` 里把主目标的附件换成它；批量的渲染类型就这样画进通道目标。portal 仍用同一个混入改道
  （没有照 LitematicaPass 换 `getMainRenderTarget()`，因为 1.21.5 的绘制都经过 `createRenderPass`）。

**关卡钩子和加载器事件**
- `LevelRenderer.renderLevel(allocator, DeltaTracker, renderOutline, Camera, GameRenderer, Matrix4f modelView, Matrix4f projection)`。
- NeoForge 21.5 只有一个 `RenderLevelStageEvent`，用 `getStage()` 区分；调试方块用 `Stage.AFTER_CUTOUT_BLOCKS`。
- Fabric API 的 `WorldRenderEvents` 同 1.21.8。

**Iris**
- Iris 1.8.11 只要光影包在用，就加宽之后新建的每个 `BufferBuilder` 的格式，除非设了 `ImmediateState.skipExtension`；
  `isRenderingLevel` 开着时，原版管线（`getVertexFormat()`，也就是 `RenderType.format()`）也换成加宽的格式，在所有线程上。
  批量开始缓冲时两个都要设（`skipExtension` 开、`isRenderingLevel` 关）。只关 `isRenderingLevel` 时，实体方块（绿、黄）
  在光影下完全画不出来，计数却照样通过。
- `skipExtension` 和更新的版本一样是真的开关，`PlainFormatPipelineMixin` 也一样。不同的是这里 `BufferBuilder` 不看
  `isRenderingLevel`：portal 里 Iris 的 `BufferSource` 开过一次缓冲后会把 `skipExtension` 重置为 false，之后直接
  `new BufferBuilder` 的 `BLOCK`/`ENTITY` 缓冲又会加宽，而 portal 里的管线是原版格式。经 `MultiBufferSource` 画的不受影响。
- Iris 1.8 只要它想替换着色器（不管 bypass），就锁住它不认识的原版程序的颜色和深度写入。`IrisShaderOverrideMixin`
  让 `shouldOverrideShaders` 在 bypass 时返回 false（和 LitematicaPass 一样）。
- 没有 `registerShadowRenderCallback`，冒烟测试仍用 `ShadowRendererMixin`。

**开发运行**
- NeoForge 开发运行同样要在 `run/neoforge-1.21.5/config/neoforge-client.toml` 里设 `enableB3DValidationLayer = false`。
- 冒烟测试另外读呈现帧中心附近的像素，绿色方块不在就判失败（计数只说明调用过，上面的问题就是这样漏掉的）。

## 1.21.4（相对 1.21.5）

**使用**
- Maven `…:0.1.5-1.21.4`。API 和事件同 1.21.5。
- 没有渲染通道：portal 改道时绑定通道目标，并让 `Minecraft.getMainRenderTarget()` 返回它，所以画进主目标的代码（含渲染类型）
  自动画进通道。区间里的绘制按 `(ONE, ONE_MINUS_SRC_ALPHA)` 混合 alpha。portal 开始之前就拿到的主目标不会被换掉，要用 `redirect`。

**构建**
- NeoForm `1.21.4-20241203.161809`，NeoForge `21.4.161`，Fabric API `0.119.4+1.21.4`，Iris 1.8.8、Sodium 0.6.13。

**纯 OpenGL**
- 没有 `RenderPipeline`、`RenderPass`、`GpuTexture`：所有绘制都画进当前绑定的帧缓冲，混合、写入掩码由渲染类型自己设。
  `Gpu` 照 LitematicaPass 换成纯 GL：自己编译程序（同样的片元着色器，顶点着色器用 `gl_VertexID` 画一个全屏三角形），
  画完放回帧缓冲、视口、程序、顶点数组、纹理、掩码、深度测试和混合。四种全屏通道保留：颜色（预乘混合或替换）、深度（LEQUAL）、
  替换深度、整层（用 `GL_ALWAYS`，不用先清成远处）。编译失败的程序只记一次日志，冒烟测试会列出来。
- 深度写入：原版渲染类型只会关掉 depthMask，默认它是开着的；Iris 的 final 通道把它关掉后不再打开，EXIT 在那之后画批量，
  实体和方块实体就只写颜色不写深度。所以 EXIT 画进通道时（批量和 portal，在 `Gpu.bindForDrawing` 和清除目标时）打开深度写入，画完放回；
  全屏通道画完放回它找到的 depthMask 和深度函数，不再强制设定。1.21.4 和 1.21.1 从 0.1.2 起，1.21.3 从 0.1.1 起，1.20.6 到 1.16.5 从 0.1.2 起。
- `TextureTarget(width, height, useDepth)`，清除色设成透明；`RenderTarget.clear()`、`copyDepthFrom` 会把帧缓冲绑回 0，EXIT 自己放回原来的绑定。
- 去掉了 `PortalPassMixin`、`RenderPassMixin`、`CompiledPipelineMixin`、`RenderPipelinesAccessor`、`CompositeRenderTypeAccessor`、
  `StrictValidationMixin` 和打开通道的计数。

**改道**
- portal 改道时绑定通道目标，`MinecraftMixin` 让 `Minecraft.getMainRenderTarget()` 返回通道目标（原版绑定主目标都经过它，
  渲染类型的 `MAIN_TARGET` 输出也是）。批量画的时候设同样的输出。`Gpu.mainTarget()` 用 `MinecraftAccessor` 读真正的主目标。
- `GlStateManagerMixin`（`GlOverrides`）：画进通道时 alpha 混合因子换成 `(ONE, ONE_MINUS_SRC_ALPHA)`（同 LitematicaPass）；
  记录半透明深度和测缩放时强制不写颜色、写深度。EXIT 自己的全屏通道不受影响。这取代了只写深度的管线副本。
- 半透明渲染类型按 `sortOnUpload()` 判断：组合状态里的透明度分片类型不可见，拿不到。

**缩放探针**
- 用 `entitySolid` 渲染类型经光影包的程序画探针四边形，颜色被强制屏蔽；遮罩用 `glReadPixels` 同步读回（每个光影包、每个尺寸一次）。
  Kappa 照样测出 0.75。

**加载器**
- NeoForge 21.4 没有 `ClientStoppingEvent`，`ExitMod.close` 挂在 `GameShuttingDownEvent` 上；没有 Blaze3D 校验层，不用关。

**开发运行**
- Super Resolution 只有 1.21.4 的 0.8.2-alpha.1：在开发运行里它让实体模型（含第一人称手）画不出来，不开光影也一样；
  配 Sundial Lite 1.2.0 时在它自己的配置里崩溃（`superresolution$config.sr` 为 null）。所以 1.21.4 不带 `-Pexit.superResolution`，没有测 SR。

## 1.21.3（相对 1.21.4）

**使用**
- Maven `…:0.1.5-1.21.3`。API、事件、portal 同 1.21.4。
- `PortalOverrides.bypass` 是 EXIT 自己的开关（Iris 1.8.1 没有），效果相同：开着时 Iris 不替换原版着色器。

**构建**
- NeoForm `1.21.3-20241023.131943`，NeoForge `21.3.100`，Fabric API `0.114.1+1.21.3`，Iris 1.8.1、Sodium 0.6.8（同 LitematicaPass）。
- NeoForm 1.21.3 把 ASM 钉死在 9.3：common 编译时用的 `asm-tree:9.6` 也不带传递依赖。

**关卡钩子**
- `LevelRenderer.renderLevel(allocator, DeltaTracker, renderOutline, Camera, GameRenderer, LightTexture, Matrix4f modelView, Matrix4f projection)`：
  比 1.21.4 多一个 `LightTexture`。

**Iris**
- Iris 1.8.1 没有 `ImmediateState.bypass`。EXIT 自己记 bypass（`IrisHooks`），`IrisShaderOverrideMixin` 在它开着时让
  `shouldOverrideShaders` 返回 false（同 LitematicaPass）。
- 核实过：Iris 1.8.8 只在 `MixinShaderManager_Overrides` 里、和 `shouldOverrideShaders` 一起读 bypass（给原版着色器挑程序时）；
  1.8.1 里没有混入调用 `getPhase`。所以移植说明里提到的 `getPhase` 返回 `NONE` 不需要。
- `BufferBuilder` 的格式加宽和 1.8.8、1.8.11 一样只看 `skipExtension` 和光影包是否在用；批量照旧开缓冲时设 `skipExtension`、关 `isRenderingLevel`。

## 1.21.1（相对 1.21.3）

**使用**
- Maven `…:0.1.5-1.21.1`。API、事件、portal 同 1.21.4。
- 没有帧图：关卡清空主目标、开始绘制之后才能改道（主渲染通道里的事件都可以）。

**构建**
- NeoForm `1.21.1-20240808.144430`，NeoForge `21.1.262`，Fabric API `0.116.17+1.21.1`，两个加载器都用 Iris 1.8.8、Sodium 0.6.13
  （Fabric 这一对同 LitematicaPass）。

**关卡钩子**
- 没有帧图（1.21.2 才有）：`LevelRenderer.renderLevel(DeltaTracker, renderOutline, Camera, GameRenderer, LightTexture, Matrix4f modelView,
  Matrix4f projection)` 直接画。EXIT 把它第一次 `RenderSystem.clear(int, boolean)`（清空主目标）之后算作关卡的绘制已经开始。

**目标和投影**
- `TextureTarget`、`RenderTarget.resize`、`clear` 都要多传 `Minecraft.ON_OSX`。
- 投影配 `VertexSorting`（没有 `ProjectionType`）：`setProjectionMatrix(matrix, getVertexSorting())`，排序也用 `getVertexSorting()`。

**model-view**
- 绘制读的是 `RenderSystem` 的 model-view 矩阵，`applyModelViewMatrix()` 才把栈顶复制过去。EXIT 一开始用压栈 + `applyModelViewMatrix()`
  设视图旋转、画完再 apply 回去：结果整个会话里实体模型（第一人称手、原版画的 `entity_solid`）都画不出来，两个加载器都会，
  而 EXIT 自己的方块正常（冒烟测试的绿色方块检查发现的；去掉这两个调用，手就回来了）。原因看来是 apply 换掉了矩阵对象。
  现在用 `RenderSystemAccessor` 直接换上 EXIT 的矩阵，画完放回**同一个对象**，不动栈（批量和缩放探针都是）。

**Iris**
- 1.21.1 的 Iris 1.8.8 又有 `ImmediateState.bypass`（在它的 `MixinGameRenderer` 里读，1.21.1 在那里挑着色器），EXIT 照 1.21.4 用它；
  `IrisShaderOverrideMixin` 保留。

**开发运行**
- Super Resolution 1.21.1 的 1.21-0.9.2-alpha.2：开发运行里加上它内嵌的库后能启动，但实体模型（含手）画不出来、SR 不缩放（配 Sundial Lite
  也是场景尺寸 = 窗口尺寸），退出时原生崩溃。所以 1.21.1 也不带 `-Pexit.superResolution`，没有测 SR。

## 1.20.6（相对 1.21.11）

这一节把 1.21.11 到 1.20.6 之间的差异一起记下（1.21.10、1.21.8、1.21.5、1.21.4、1.21.3、1.21.1 都跳过了，直接从
`port/1.21.11` 开出并一次性改到 1.20.6）。

**使用**
- Maven `…:0.1.5-1.20.6`，只有 Fabric（`emergency_exit-fabric`）。Iris 不是必需的，没有光影包时什么都不改道。
- API 同 1.21.8：没有 `SubmitNodeCollector`，绘制和批量收、交 `MultiBufferSource`（`ExitContext.bufferSource()`）。
- Fabric（`WorldRenderEvents`，`net.fabricmc.fabric.api.client.rendering.v1`）：批量和绘制在 `BEFORE_ENTITIES`（直通上下文的
  `consumers()`），绘制也可在 `AFTER_ENTITIES`；portal 在这些事件里调用，或在关卡渲染结束时。
- 没有输出覆盖，portal 靠绑定通道的帧缓冲、让 `Minecraft.getMainRenderTarget()` 交出通道目标来改道（同 1.21.4）。
- 批量默认在关卡渲染结束时画；`flushBatch()` 能在那之前画（见下面的 **关卡钩子**）。
- 视图旋转在 `RenderSystem` 的 model-view 矩阵里，`ExitContext.poseStack()` 不带旋转（和更新的版本一样）。1.20.4 及更旧的不同，见 1.20.4。

**构建**
- Java 21（同 1.21.11）。common 仍用 ModDevGradle NeoForm `1.20.6-20240627.102356`，Fabric 用 Loom
  `mappings(loom.officialMojangMappings())`。
- **只做 Fabric**：Iris 在 1.20.6 没有 NeoForge 构建，`neoforge` 子项目删除。
- Fabric API `0.100.8+1.20.6`；Iris 1.7.2、Sodium 见 `gradle.properties`。
- Iris/Sodium 内嵌的库（jcpp、ANTLR、glsl-transformer）用 `nestedLibraries` 任务从它们自己的 jar 里取出来放进开发运行。

**包名和类**
- `Identifier` → `ResourceLocation`；`Identifier.fromNamespaceAndPath(a, b)` → `new ResourceLocation(a, b)`。
- 没有 JSpecify：`org.jspecify.annotations.Nullable` → `org.jetbrains.annotations.Nullable`。
- `CameraRenderState` → `net.minecraft.client.Camera`（位置 `getPosition()`）。
- `net.minecraft.client.renderer.rendertype.RenderTypes` → `net.minecraft.client.renderer.RenderType`。
- `TextureTarget(w, h, useDepth, Minecraft.ON_OSX)`；`RenderTarget.resize(w, h, Minecraft.ON_OSX)`。
- 没有 `ByteBufferBuilder`（1.21 起才有）：批次用 `new BufferBuilder(int)`，`VertexConsumer` 用 `vertex(...).endVertex()`。

**渲染通道**
- `RenderPipeline`/`RenderPass` 是 1.21.5 以后才有的。这里换回纯 OpenGL：`Gpu` 自己编译 `#version 330` 的程序、用
  `gl_VertexID` 画全屏三角形、手动管理 FBO 与 GL 状态；`STANDARD_DEPTH` 由 EXIT 在编译前加到着色器里。
- 没有 `GpuTextureView`，直接用 `RenderTarget` 的纹理 id。
- 没有输出覆盖：portal 改为绑定通道的 framebuffer，并用 `MinecraftMixin` 让 `getMainRenderTarget()` 在重定向期间返回
  通道目标（`MinecraftAccessor` 给 EXIT 自己读真字段）。

**管线**
- 全屏通道是 OpenGL 程序：`colorPipeline`/`depthPipeline`/`depthReplacePipeline`/`layerPipeline` 的语义与 26.3 一致。

**特征（提交）**
- 没有 `SubmitNodeCollector`/`SubmitNodeStorage`：批次改成 `MultiBufferSource`——`Exit.draw(MultiBufferSource, ...)`、
  `ExitContext.bufferSource()`、`Exit.batch(MultiBufferSource)`。
- 批次按 `RenderType` 攒；帧末先画不混合的类型，再画半透明的（保证实体不盖住更近的半透明）。半透明的提交另外录进第二个
  buffer，画的时候 colour 关、depth 开，写进通道的半透明深度目标，合并前用 `TranslucentDepth.fold` 取更近的。
- 删掉：gizmo（1.21.6 才有，连同 `SimpleGizmoCollector`/`DrawableGizmoPrimitives`/`Gizmos`）、Super Resolution、
  `StrictValidationMixin`，以及新渲染 API 的 mixin（`RenderPassMixin`、`PortalPassMixin`、`CompiledPipelineMixin`、
  `RenderPipelinesAccessor`、`FeatureRenderDispatcherAccessor`、`FeatureRenderDispatcherMixin`、`RenderTypeAccessor`）。

**缩放探针**
- 移植时缩放探针（`SceneScale`、`scale_probe.fsh`）和 Super Resolution 一起删了，理由是这里光影包总按场景分辨率渲染。
  但 Kappa 的 TAAU 这类光影包在自己的顶点着色器里缩放每个顶点，哪个版本都一样：场景只占深度缓冲的一角，深度种子、
  合并和通道混合都拿错位的深度比较。0.1.1 起探针又回来了（1.20.6 到 1.16.5），照 1.21.1 移植：探针四边形用
  `BufferBuilder.begin/end` 建，像 `FeatureDrawer` 那样只写深度地画；`SceneDepth` 照旧重采样场景深度。

**关卡钩子**
- `LevelRenderer.renderLevel(float, long, boolean, Camera, GameRenderer, LightTexture, Matrix4f 视图旋转, Matrix4f 投影)`。
  视图旋转与投影直接取参数（1.21 在本方法入口模型视图还是 identity，不能从 `RenderSystem` 读）。END 时合并。
- 没有帧图（1.21.2 才有），`ExitRuntime.beginPasses` 和 1.21.1 一样在 `renderLevel` 清空主目标之后调用。移植时漏了这个调用，
  `flushBatch()` 什么都不做，批量只在关卡结束时画，画在本该在它之后的东西（比如写深度的 Litematica overlay）后面；
  0.1.3 起改好了，1.20.4 到 1.16.5 相同。

**加载器事件**
- 没有 `LevelRenderEvents`（1.21.11 的）：用 `WorldRenderEvents.BEFORE_ENTITIES`，上下文里取 `consumers()`。

**Iris**
- Iris 1.7：没有 `Iris.isPackInUseQuick` → `IrisApi.getInstance().isShaderPackInUse()`。
- 没有 `ImmediateState.bypass`：EXIT 自己存 bypass，`IrisShaderOverrideMixin` 让 `shouldOverrideShaders` 在 bypass 时返回
  false、`getPhase` 返回 `NONE`。
- `sceneDepthTexture()` 返回 0（这个版本 Iris 用主深度）。
- 没有 skipExtension；混合用 `GlStateManagerMixin`：重定向期间把 alpha 因子换成 `(ONE, ONE_MINUS_SRC_ALPHA)`。

**开发运行**
- 运行目录 `run/fabric-1.20.6`；冒烟测试无光影与 BSL 光影各跑一次都 `RESULT: OK`；`runAuditClient` 每个 mixin 都应用。

## 1.20.4（相对 1.20.6）

**使用**
- Maven `…:0.1.5-1.20.4`，只有 Fabric。事件同 1.20.6。
- **pose stack 按 Minecraft 1.20.5 之前的约定**（0.1.5 起；0.1.4 还是旧的做法）：1.20.5 之前 Minecraft
  把视图旋转放在关卡的 pose stack 里，model-view 矩阵是单位矩阵。现在 `ExitContext.poseStack()` 一开始就带着视图旋转
  （就是关卡在相机位置的 pose stack），`ExitContext.modelView()` 是单位矩阵，批量用单位 model-view 矩阵画。
  portal 不动 model-view 矩阵，Minecraft 当时是什么就是什么：它在 debug 和 world-last 阶段会把视图旋转放进 model-view，
  Litematica 的选区框就靠这个，用单位 pose stack 画。
  往 `Exit.batch(...)` 提交、在绘制里画，都和交给关卡自己的 buffer source 一样直接用关卡的 pose stack，不用换算。
  以前 EXIT 要的是不带旋转的 pose stack、旋转由它自己加，于是用关卡 pose stack 交进来的实体和方块实体被转了两次。
  1.20.2 到 1.16.5 相同；1.20.5 起不变，那里旋转在 `RenderSystem` 的 model-view 矩阵里。

**构建**
- Java 17（1.20.5 起才是 21）。
- common 的 NeoForm 用 `1.20.4-20240627.114801`：`1.20.4-20231207.154220` 没有 ModDevGradle 需要的
  `neoform-dependencies` 能力变体，依赖解析会失败。
- Fabric API `0.97.3+1.20.4`；Iris 1.7.2、Sodium 见 `gradle.properties`。

**包名和类**
- `RenderSystem.getModelViewStack()` 是 `PoseStack`（1.20.5 起才是 `Matrix4fStack`）：用 `pushPose`/`popPose` 与
  `last().pose().set(...)`。
- `VertexConsumer.vertex`/`normal` 只收 `Matrix4f`/`Matrix3f`，没有 `PoseStack.Pose` 重载：写顶点时用
  `pose.pose()` / `pose.normal()`。

**关卡钩子**
- `LevelRenderer.renderLevel(PoseStack 视图旋转, float, long, boolean, Camera, GameRenderer, LightTexture, Matrix4f 投影)`：
  视图旋转是第一个 `PoseStack` 参数，投影是最后一个 `Matrix4f`。

其余同 1.20.6。

## 1.20.2（相对 1.20.4）

**使用**
- Maven `…:0.1.5-1.20.2`，只有 Fabric。事件和 API 同 1.20.4（pose stack 的约定也一样）。

**构建**
- NeoForm `1.20.2-20241215.201144`；Fabric API `0.91.6+1.20.2`；Iris 1.6.14、Sodium 见 `gradle.properties`。

**包名和类（Iris 换回 net.coderbot）**
- Iris 1.6.14 还在旧包名：`net.coderbot.iris.Iris`、`net.coderbot.iris.vertices.ImmediateState`、
  `net.coderbot.iris.pipeline.WorldRenderingPhase`。
- 管线类 `IrisRenderingPipeline` → `net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline`
  （`shouldOverrideShaders` / `getPhase` 仍在）。
- `IrisApi` 仍在 `net.irisshaders.iris.api.v0`（没有跟着搬）。

**开发运行**
- 冒烟测试的 Iris 阴影钩子目标改成 `net.coderbot.iris.pipeline.ShadowRenderer`（字段 `ACTIVE`、方法
  `renderShadows`）。

其余同 1.20.4。

## 1.20.1（相对 1.20.2）

**使用**
- Maven `…:0.1.5-1.20.1`，只有 Fabric。事件和 API 同 1.20.2。
- 批量只结束正在构建的 builder，和 1.20.2 一样。以前本帧没用到的渲染类型也会被结束，批量一提前画（`flushBatch()`）就抛
  `Not building!`。0.1.5 起，1.20 和 1.19.4 相同。

**构建**
- 变成**单个 Fabric Loom 工程**（同 LitematicaPass）：1.20.1 没有 ModDevGradle 能用的 NeoForm 构建，`common` 子工程根本
  建不起来。`common/src/main/java` 移进 `fabric/src/main/java`，`common/src/main/resources` 移进
  `fabric/src/main/resources`，`common/src/smoke` 移进 `fabric/src/smoke`；`common` 子工程和 build-logic 的 multiloader
  插件都删掉，`fabric/build.gradle` 直接是 fabric-loom-remap 工程（`minecraft(...)`、`loom.officialMojangMappings()`、
  fabric-loader、fabric-api、Iris/Sodium 用 `modCompileOnly` + `modLocalRuntime`、`nestedLibraries`、client /
  smokeClient / auditClient 三个运行配置、smoke 和 audit source set，以及展开 `fabric.mod.json` 和 `*.mixins.json` 的
  processResources），`neoforge` 插件也删掉。API 包名（`ml.mypals.exit.*`）不变。
- Fabric API `0.92.12+1.20.1`；Iris 1.7.6（Modrinth `YL57xq9U:s5eFLITc`）、Sodium `AANobbMI:OihdIimA`
  （`mc1.20.1-0.5.13`）。
- Sodium 0.5.13 拒绝开发运行里 LWJGL 的 snapshot 版本（它只认 3.3.1），开发运行要加
  `-Dsodium.checks.issue2561=false`。

**包名和类（Iris 又搬回 net.irisshaders）**
- Iris 1.7.6 用新包名：`net.irisshaders.iris.Iris`、`net.irisshaders.iris.vertices.ImmediateState`、
  `net.irisshaders.iris.pipeline.WorldRenderingPhase`。
- 管线类 `NewWorldRenderingPipeline` → `net.irisshaders.iris.pipeline.IrisRenderingPipeline`
  （`shouldOverrideShaders` / `getPhase` 仍在）。
- 冒烟测试的 Iris 阴影钩子目标改成 `net.irisshaders.iris.shadows.ShadowRenderer`（字段 `ACTIVE`、方法
  `renderShadows`）。
- 除这三个文件外一行源码都没改。

**开发运行**
- 运行目录 `run/fabric-1.20.1`；冒烟测试无光影和 BSL 光影各跑一次都是 `RESULT: OK`（BSL：2184 次改道绘制、1934 帧按
  深度混合、阴影回调里 EXIT 画了 0 次）；`runAuditClient` 每个 mixin 都应用成功。
- 无光影时画面里只有黄色盒子（红色和绿色提交给关卡自己的 buffer source，这里没画它们），有光影的那组是四色盒子加三张
  调试视图，和 `port/1.20.2` 的对应画面一致（截图在 `smoke-shots/1.20.1/fabric-none` 和 `fabric-bsl`）。

其余同 1.20.2。

## 1.20（相对 1.20.2）

**使用**
- Maven `…:0.1.5-1.20`，只有 Fabric。事件和 API 同 1.20.2。
- 代码和 1.20.2 相同，所以这个 jar 也能装在 1.19.4 上（`fabric.mod.json`：`>=1.19.4 <=1.20`）。

**构建**
- 变成**单个 Fabric Loom 工程**（同 LitematicaPass）：Minecraft 1.20.1 及更早没有 ModDevGradle 能用的 NeoForm 构建，
  `common` 子工程根本建不起来。`common/src/main/java` 移进 `fabric/src/main/java`，`common/src/main/resources` 移进
  `fabric/src/main/resources`，`common/src/smoke` 移进 `fabric/src/smoke`；`common` 子工程和 build-logic 的
  multiloader 插件都删掉，`fabric/build.gradle` 直接是 fabric-loom-remap 工程：`minecraft(...)`、
  `loom.officialMojangMappings()`、fabric-loader、fabric-api、Iris/Sodium 用 `modCompileOnly` + `modLocalRuntime`、
  `nestedLibraries`、client/smokeClient/auditClient 三个运行配置、smoke 和 audit source set，以及展开
  `fabric.mod.json` 和 `*.mixins.json` 的 processResources。API 包名（`ml.mypals.exit.*`）不变。
- Fabric API `0.83.0+1.20`；Iris 1.6.4（Modrinth `YL57xq9U:URWeWMAt`）、Sodium `AANobbMI:vgceLbdH`。
- `fabric.mod.json` 的 `minecraft` 从 `minecraft_version_range` 展开成 Fabric 自己的断言形式 `>=1.19.4 <=1.20`
  （代码和 1.20.2 相同，LitematicaPass 在 1.19.4 上验证过这段代码）。

**包名和类**
- 一行源码都没改：Iris 1.6.4 仍在旧包名（`net.coderbot.iris.Iris`、`net.coderbot.iris.vertices.ImmediateState`、
  `net.coderbot.iris.pipeline.WorldRenderingPhase`），管线类仍是
  `net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline`（`shouldOverrideShaders` / `getPhase` 都还在），
  冒烟测试的 Iris 阴影钩子仍是 `net.coderbot.iris.pipeline.ShadowRenderer`。

**开发运行**
- 运行目录 `run/fabric-1.20`；冒烟测试无光影和 BSL 光影各跑一次都是 `RESULT: OK`（BSL：2184 次改道绘制、1937 帧按深度
  混合、阴影回调里 EXIT 画了 0 次）；`runAuditClient` 每个 mixin 都应用成功。
- 无光影时画面里只有黄色盒子（红色和绿色提交给关卡自己的 buffer source，1.20 没画它们），有光影的那一组是四色盒子加
  三张调试视图，和 `port/1.20.2` 的对应画面一样（截图在 `smoke-shots/1.20/fabric-none` 和 `fabric-bsl`；冒烟测试每次
  随机选位置生成地形，所以是逐张目视对比，不是逐像素相同）。

其余同 1.20.2。

## 1.19.4（相对 1.20.2）

1.19.4 是 1.19.x 的最后一版，取代了原来的 `port/1.19.3`。

**使用**
- Maven `…:0.1.5-1.19.4`，只有 Fabric（Iris 1.6.11 没有 NeoForge 版）。事件和 API 同 1.20.2。

**构建**
- 变成**单个 Fabric Loom 工程**：只留 Fabric 后 `common` 子工程没有意义。`common/src/main/java` 移进
  `fabric/src/main/java`，`common/src/main/resources` 移进 `fabric/src/main/resources`，`common/src/smoke` 移进
  `fabric/src/smoke`；`common` 子工程和 build-logic 的 multiloader 插件都删掉，`fabric/build.gradle` 直接就是
  fabric-loom-remap 工程（`minecraft(...)`、`loom.officialMojangMappings()`、fabric-loader、fabric-api、Iris/Sodium 用
  `modCompileOnly` + `modLocalRuntime`、`nestedLibraries`、client / smokeClient / auditClient 三个运行配置、smoke 和
  audit source set，以及展开 `fabric.mod.json` 和 `*.mixins.json` 的 processResources），`neoforge` 插件也删掉。
  API 包名（`ml.mypals.exit.*`）不变。
- Fabric API `0.87.2+1.19.4`；Iris 1.6.11（Modrinth `YL57xq9U:wN6PuLPa`）、Sodium 0.4.10（`AANobbMI:b4hTi3mo`）。
  `minecraft_version_range` 是 `1.19.4`。

**顶点排序**
- 没有 `com.mojang.blaze3d.vertex.VertexSorting`，`RenderSystem.setProjectionMatrix` 只收矩阵：`ExitRuntime` 里
  portal 的投影保存/恢复和 `FeatureDrawer` 里的投影设置都不再传 `DISTANCE_TO_ORIGIN`（这个版本的深度排序由光影包
  自己做）。也还没有 `VertexBuffer.Usage`。

**Iris**
- Iris 1.6.11 的包名、管线类和阴影钩子和 1.20.2 一样：`net.coderbot.iris.*`、
  `net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline`、`net.coderbot.iris.pipeline.ShadowRenderer`；
  `IrisCompat` 和两个 Iris mixin 一行没改。

**开发运行**
- 运行目录 `run/fabric-1.19.4`；冒烟测试用 Complementary Unbound r5.9.3 跑是 `RESULT: OK`（964 次改道绘制、800 帧按深度混合、
  964 次阴影回调里 EXIT 画了 0 次）。

其余同 1.20.2。


## 1.18.2（相对 1.20.2）

**使用**
- Maven `…:0.1.5-1.18.2`，只有 Fabric。事件同 1.20.2（Fabric API 的 `WorldRenderEvents`，`BEFORE_ENTITIES` 的 `consumers()`）。
- API 里的 `Matrix4f` 是 `com.mojang.math.Matrix4f`：渲染路径还没有 JOML，`ExitContext.modelView()` 返回它，不是 `Matrix4fc`。
- 没有 `RenderType.debugQuads()`（1.19 才有）：半透明的东西用带自己纹理的渲染类型，比如 `RenderType.entityTranslucent(白色纹理)`。
  实体格式的每个顶点元素都要写。

这一支直接照 `port/1.20.2` 的代码改（1.20.1、1.20、1.19.4 三支是之后才补的），所以下面是相对 1.20.2 的差异。

**构建**
- Minecraft 1.18.2、Fabric API `0.77.0+1.18.2`、Fabric loader 0.19.5；Java 17。
- 1.20.1 及更早没有 NeoForm 产物，MultiLoader-Template 的分家（`build-logic`、`common`）删掉，`fabric` 是一个
  普通 Fabric Loom 项目。只有 Fabric：这个版本的 Iris 没有 NeoForge 版。

**包名和类**
- 渲染路径还没有 JOML：`com.mojang.math.Matrix4f` / `Matrix3f`。`ExitContext.modelView()` 返回 `Matrix4f`
  （不是 `Matrix4fc`）；`PoseStack` 的位姿矩阵用 `load()` / `multiply()`；`RenderSystem.setProjectionMatrix()`
  只有一个参数（没有 `VertexSorting`）。
- 文字是 `Component`：还没有 `Component.literal`，命令回显用 `new TextComponent(...)`。
- 1.19 才有 `RenderType.debugQuads()`：半透明的测试盒子改用带本模组白纹理的 `entityTranslucent`，两个测试盒子的
  渲染类型都是 entity 格式，写顶点时每个元素都要写。

**Iris**
- Iris 1.6.11 仍在 `net.coderbot.iris`：`ImmediateState`、`WorldRenderingPhase`，管线类是
  `net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline`（`shouldOverrideShaders` / `getPhase` 仍在），
  阴影是 `net.coderbot.iris.pipeline.ShadowRenderer`。

**开发运行**
- 客户端命令注册用 Fabric API v1 的 `ClientCommandManager.DISPATCHER`（1.19 前没有
  `ClientCommandRegistrationCallback`）。
- 冒烟测试：1.18 没有 `WorldCreationUiState`，建世界界面的名字和游戏模式由它自己保管（accessor 取 `nameEdit`、
  `modeButton`，切到 CREATIVE）；`CreateWorldScreen.openFresh()` 还没有，用 `createFresh(null)` 加 `setScreen()`；
  `ClientPacketListener.sendCommand()` 要 1.19 才有，命令按 `ServerboundChatPacket` 发；`/tp` 用 yaw/pitch 指定
  朝向；托管世界默认不给房主命令权限（建房界面也没有开作弊的开关），冒烟测试进世界后先把自己设成 op，每次运行开始
  先删掉旧世界目录。

其余同 1.20.2。


## 1.17.1（相对 1.18.2）

**使用**
- Maven `…:0.1.5-1.17.1`，只有 Fabric。事件和 API 同 1.18.2。
- 1.17 跑在 Java 16 上。只构建、测试了 1.17.1，`fabric.mod.json` 写的是 `>=1.17 <=1.17.1`。
- Iris 是 1.2.7（`net.coderbot.iris`）。
- Fabric API 的 mod id 还是 `fabric`（见下面的 **构建**），依赖 Fabric API 时写 `fabric`。

**构建**
- Minecraft 1.17.1、Fabric API `0.46.1+1.17`、Fabric loader 0.19.5；Java 16。构建 JVM 用 JDK 25（Loom 1.18.1
  要求），模组和开发客户端跑 JDK 16 toolchain。
- 单个 Fabric Loom 工程（形状同 1.18.2）：`common` 的内容在 `fabric` 里，`build-logic` 和多加载器插件删掉。
  只有 Fabric：这个版本的 Iris 没有 NeoForge 版。
- **Fabric API 在这个版本上的 mod id 还是 `fabric`**：`fabric-api` 这个 id（和 `provides: fabric`）要到
  0.76.0+1.18.2 才有。`fabric.mod.json` 里写 `fabric-api` 会让 Fabric Loader 直接拒绝启动：
  `HARD_DEP_NO_CANDIDATE emergency_exit ... {depends fabric-api @ [*]}`。要依赖 `fabric`。
- 这一代的 Fabric API 在 Maven 上是个空壳 jar（只有 `fabric.mod.json` 和图标，2.6 KB），真正的模块由它的 POM
  拉进来，启动日志里是 59 个模组。

**包名和类**
- 渲染路径还没有 JOML：`com.mojang.math.Matrix4f` / `Matrix3f`（同 1.18.2）。`PoseStack` 的位姿矩阵用
  `load()`；`RenderSystem.setProjectionMatrix()` 只有一个参数。
- `TextureTarget` 和 `RenderSystem.getModelViewStack()` / `applyModelViewMatrix()` / `getProjectionMatrix()` /
  `setProjectionMatrix()` 这一代都有了（1.16.5 没有）；`RenderType.format()` / `mode()` 也在，
  `VertexConsumer` 有 `defaultColor` / `unsetDefaultColor`。
- 1.17 没有 `RenderType.debugQuads()`（同 1.18.2）：半透明的测试盒子改用带本模组白纹理的 `entityTranslucent`，
  两个测试盒子的渲染类型都是 entity 格式，写顶点时每个元素都要写。
- 没有 gizmo（1.21.6 之前都没有）。
- 文字是 `Component`：还没有 `Component.literal`，命令回显用 `new TextComponent(...)`。

**Iris 1.2.7**
- 包名 `net.coderbot.iris`；管线类是 `net.coderbot.iris.pipeline.newshader.NewWorldRenderingPipeline`，阴影是
  `net.coderbot.iris.pipeline.ShadowRenderer`。
- **旁路开关**：Iris 1.2 没有独立的 bypass 开关，`IrisShaderOverrideMixin` 在
  `net.coderbot.iris.pipeline.PipelineManager.getPipelineNullable()` 的返回值上做 `@ModifyVariable`，旁路时返回
  null；`IrisCompat.bypass()` 读的是 EXIT 自己那个字段。
- **`skipExtension` 和 `isRenderingLevel` 都是空实现**：Iris 1.2 不会加宽关卡的顶点格式，没有这两个开关
  （1.16.5 的 Iris 1.4.5 反而有 `ImmediateState.isRenderingLevel`）。`IrisCompat.skipExtension()` /
  `isRenderingLevel()` 恒为 false，`setRenderingLevel()` 什么都不做。
- **场景深度**：1.2 的管线把场景深度放在自己的纹理里，不在主目标深度上。用 `LegacyIrisPipelineAccessor`
  （`@Mixin(targets = ...NewWorldRenderingPipeline)` + `@Accessor("renderTargets")`）取
  `RenderTargets.getDepthTexture()`；不同 1.17.1 构建返回的东西不一样（多数给 `DepthTexture` 对象，晚期的给纹理
  id），所以反射调 `getTextureId()` 兜底。取之前要先把旁路关掉（`IrisShaderOverrideMixin` 旁路时管线是 null）。
- **缩放探针**（0.1.1 起，见 1.20.6）：场景深度在 Iris 自己的纹理里，所以探针先把那张纹理同步进主深度再读，读完把不带探针
  四边形的深度放回两边。

**开发运行**
- 运行目录 `run/fabric-1.17.1`；无光影和 BSL 各跑一次都是 `RESULT: OK`（无光影：2213 次绘制、0 次改道；BSL：
  2202 次绘制全部改道、2202 个 portal 把通过移进通道、0 次不匹配、2202 帧按深度混合、阴影回调 2202 次而 EXIT
  在里面画了 0 次）。`runAuditClient` 每个 mixin 都应用（`GlStateManagerMixin` 三个、`LevelRendererMixin` 两个、
  `MinecraftMixin`、`IrisRenderingPipelineMixin` 两个、`IrisShaderOverrideMixin`）。
- 客户端命令注册用 Fabric API v1 的 `ClientCommandManager.DISPATCHER`（1.19 前没有
  `ClientCommandRegistrationCallback`）。
- 冒烟测试：1.17 没有 `WorldCreationUiState`，建世界界面的名字和游戏模式由它自己保管（accessor 取 `nameEdit`、
  `modeButton`；游戏模式是 `CycleButton`，1.16.5 是普通 `Button`）；`CreateWorldScreen.openFresh()` 还没有，用
  `CreateWorldScreen.create(null)` 加 `setScreen()`；`ClientPacketListener.sendCommand()` 要 1.19 才有，冒烟测试
  直接 `absMoveTo(...)` 摆放玩家和视角；截图用带 target 的 `Screenshot.grab(...)`；每次运行开始先删掉旧世界目录。
- **模组自己的资源在开发运行里看不见**（1.16.5 一样，这一支同样确认）：Fabric Loader 警告
  `getRootPath access for emergency_exit ... with multiple paths, returning only one`，这一代 Fabric API 的
  fabric-resource-loader 还调旧的 `getRootPath()`（只拿到 classes 目录），`assets/emergency_exit/**` 因此取不到，
  `DebugShapes` 的白纹理加载失败（`Failed to load texture: emergency_exit:textures/misc/white.png`），测试盒子画成
  黑色而不是四色。打包成 jar、或 1.18.2 及以后都没有这个问题；功能检查不受影响（`RESULT: OK`）。
- Sodium 0.3（`net.coderbot.batchedentityrendering`）把关卡的 buffer source 换成了自己的
  `FullyBufferedMultiBufferSource`，`Exit.draw` 拿到的 `passThrough` 就是它。

其余同 1.18.2。



## 1.16.5（相对 1.17.1）

**使用**
- Maven `…:0.1.5-1.16.5`，只有 Fabric。事件和 API 同 1.17.1。
- Fabric API 的 mod id 是 `fabric`（`fabric-api` 这个 id 是后来才有的），EXIT 的 jar 依赖 `fabric`，你的 mod 也要依赖 `fabric`。
- 固定功能管线，pose stack 的约定和 1.20.4 一样（从 0.1.5 起，见 1.20.4）：视图旋转在关卡的 pose stack 里。
  EXIT 画通道时（portal 和批量）把关卡的投影放回 GL 的投影矩阵栈；批量把 model-view 栈设成单位矩阵，portal 不动它。
  0.1.4 及以前两者都会把视图旋转乘到 model-view 栈上。

**构建**
- 单个 Fabric Loom 工程（形状同 1.17.1）：`common` 的内容在 `fabric` 里，`build-logic` 和多加载器插件都已经删掉。
- Fabric API `0.42.0+1.16`；Iris 1.4.5（Modrinth `YL57xq9U:1turazSM`）、Sodium `AANobbMI:YAGZ1cCS`；Java 16。
- **Fabric API 在这个版本上的 mod id 还是 `fabric`**：`fabric-api` 这个 id（和 `provides: fabric`）是后来的事。
  `fabric.mod.json` 里写 `fabric-api` 会让 Fabric Loader 直接拒绝启动：
  `HARD_DEP_NO_CANDIDATE emergency_exit ... {depends fabric-api @ [*]}`。要依赖 `fabric`。
- 这一代的 Fabric API 在 Maven 上是个空壳 jar（只有 `fabric.mod.json` 和图标），真正的模块（`fabric-command-api-v1`、
  `fabric-lifecycle-events-v1`、`fabric-rendering-v1` 等）由它的 POM 作为依赖拉进来，启动日志里是 56 个模组。
- 客户端命令仍是 Fabric API v1 的 `ClientCommandManager`（没有 `ClientCommandRegistrationCallback`）。

**渲染（没有核心着色器，固定功能管线）**
- **没有 `TextureTarget`：`RenderTarget` 自己就是带纹理的目标**，`new RenderTarget(w, h, true, Minecraft.ON_OSX)`。
- `RenderSystem` 还没有 `getModelViewStack` / `applyModelViewMatrix` / `getProjectionMatrix` / `setProjectionMatrix`：
  关卡的投影就在 GL 矩阵栈上，视图旋转在关卡的 pose stack 里。EXIT 自己画通道时（portal 和批次）用
  `RenderSystem.matrixMode(GL_PROJECTION) → pushMatrix() → loadIdentity() → multMatrix(投影)`，MODELVIEW 只
  `pushMatrix() → loadIdentity()`，画完 pop 回去（`GameRenderer.resetProjectionMatrix` 就是这个写法）。
  0.1.4 及以前 MODELVIEW 还会 `multMatrix(视图旋转)`，见 **使用**。`ExitRuntime` 的两个矩阵字段因此不再是
  `final`，`savedProjection` 也不需要了。
- `com.mojang.math.Matrix4f` 没有 `load(Matrix4f)`，只有拷贝构造（`new Matrix4f(other)`）。
- `GlStateManager` 没有 `_glGenVertexArrays` / `_glBindVertexArray` / `_getActiveTexture`：用
  `GL30.glGenVertexArrays()`、`GL30.glBindVertexArray(...)`、`GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)`。
- `RenderType.format()` / `mode()` 还在，`BufferBuilder.begin(int mode, VertexFormat)` 收 int，批次那段基本不动；
  但 `VertexConsumer` 还没有 `defaultColor` / `unsetDefaultColor`，`DualConsumer` 不再实现这两个。
- 消息是 `new TextComponent(...)`，日志走 Log4j（`LogManager.getLogger`）；`Entity.getEyePosition()` 要传 partial tick。

**Iris 1.4.5（固定功能时代的 Iris）**
- 包名 `net.coderbot.iris`（`Iris`、`net.coderbot.iris.vertices.ImmediateState`），`IrisApi` 仍在
  `net.irisshaders.iris.api.v0`。
- 管线是 `net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline`（没有 `NewWorldRenderingPipeline`）：
  `IrisRenderingPipelineMixin`（`beginHand` / `beginTranslucents`）、`LegacyIrisPipelineAccessor`
  （`@Mixin(targets = ...DeferredWorldRenderingPipeline)` + `@Accessor("renderTargets")`）、以及
  `IrisHooks.sceneDepthTexture()` 都指向它；`RenderTargets.getDepthTexture()` 直接返回纹理 id，不是 `DepthTexture`。
- 场景深度和 1.17.1 一样在 Iris 自己的纹理里，缩放探针也一样先同步、再放回不带探针四边形的深度。探针四边形按视图空间给出，
  用固定功能的矩阵栈画。
- **旁路开关换成 `setIsMainBound`**：把 `PipelineManager.getPipelineNullable()` 变成 null 对固定功能没用，
  Iris 只在它认为"主目标被绑定"时才绑自己的程序。所以 `IrisShaderOverrideMixin` 改成在
  `DeferredWorldRenderingPipeline.setIsMainBound` 的参数上做 `@ModifyVariable`，`IrisHooks.setBypass` 另外直接调
  `getRenderTargetStateListener().setIsMainBound(!bypass)`。
- `ImmediateState.isRenderingLevel` 这一代就有，所以 `isRenderingLevel` / `setRenderingLevel` 是真的起作用
  （1.17.1 那种空实现是特例）。
- 冒烟测试的阴影钩子仍是 `net.coderbot.iris.pipeline.ShadowRenderer`（字段 `ACTIVE`、方法 `renderShadows`）。

**开发运行**
- 运行目录 `run/fabric-1.16.5`；无光影和 BSL 各跑一次都是 `RESULT: OK`（无光影：2222 次绘制、0 次改道；
  BSL：2101 次绘制全部改道、2101 个 portal 把通过移进通道、0 次不匹配、1864 帧按深度混合、阴影回调里 EXIT 画了 0 次）。
  `runAuditClient` 每个 mixin 都应用（`GlStateManagerMixin` 三个、`LevelRendererMixin` 两个、`MinecraftMixin`、
  `IrisRenderingPipelineMixin` 两个、`IrisShaderOverrideMixin`）。
- **模组自己的资源在开发运行里看不见**（1.16.5 如此，1.17.1 也一样）：Fabric Loader 会警告
  `getRootPath access for emergency_exit ... with multiple paths, returning only one`，因为这一代 Fabric API 的
  fabric-resource-loader 还调旧的 `getRootPath()`（只拿到 classes 目录），`assets/emergency_exit/**` 就取不到。
  于是 `DebugShapes` 的 `white.png` 加载失败（`Failed to load texture: emergency_exit:textures/misc/white.png`），
  测试盒子画成黑色而不是四色；功能检查不受影响（打包成 jar、或 1.18.2 及以后，都没有这个问题）。
- 冒烟测试要按 1.16.5 的建世界界面改：没有 `WorldCreationUiState`，也没有 `CycleButton`（游戏模式是一个普通
  `Button`，按一下 生存→极限→创造，按钮标题就是当前模式）；飞行用 `player.abilities.flying`；截图用带
  width/height 的 `Screenshot.grab(...)`。

其余同 1.17.1。
