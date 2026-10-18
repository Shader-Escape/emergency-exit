# 版本差异记录（ChangeLog）

EXIT 每个 Minecraft 版本一个 git 分支，分支里只有该版本的代码。本文件只在 `master` 上维护，
按版本从新到旧，记下每个版本和**上一个更新的版本**相比，Minecraft、加载器和 Iris 的哪些差异影响到 EXIT。
移植到更旧的版本时，从最接近的分支开出，照着这里改。

| Minecraft | 分支 | Java | 映射 | Iris（开发运行） |
|---|---|---|---|---|
| 26.3 | `master` | 25 | 无混淆 | 1.11.x |
| 26.2 | `port/26.2` | 25 | 无混淆 | 1.11.4 |
| 26.1.2 | `port/26.1.2` | 25 | 无混淆 | 1.11.4（26.1） |
| 1.21.11 | `port/1.21.11` | 21 | 混淆，Mojang 映射 | 1.10.8 |
| 1.20.6 | `port/1.20.6` | 21 | 混淆，Mojang 映射 | 1.7.2 |
| 1.20.4 | `port/1.20.4` | 17 | 混淆，Mojang 映射 | 1.7.2 |

## 26.2（相对 26.3）

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
- 没有 `ImmediateState.skipExtension`：Iris 直接把加宽后的顶点格式当原版格式交出。EXIT 里这个开关什么都不做
  （`IrisCompat.hasSkipExtension()` 为 false）。
- 画特征时 `isRenderingLevel` 仍要设为 false（和 26.3 一样）。LitematicaPass 对实体设 true，但在 EXIT 里那样
  `entitySolid` 会画不出来。

**开发运行**
- `StrictValidationMixin` 的目标是 `GlRenderPass` 和 `VulkanRenderPass`。
- 运行目录按版本分开：`run/<加载器>-<版本>`。共用目录里的模组（Mod Menu、Super Resolution）只支持 26.3。

## 26.1.2（相对 26.2）

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
- Iris 同 26.2：没有 `skipExtension`。

## 1.21.11（相对 26.1.2）

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
- 同样没有 `skipExtension`。

**开发运行**
- NeoForge 开发环境默认开着 Blaze3D 校验层，Iris 1.10.8 启动时会崩（`ValidationGpuDevice` 不能转成 `GlDevice`）。
  在 `run/neoforge-1.21.11/config/neoforge-client.toml` 里设 `enableB3DValidationLayer = false`。

## 1.20.6（相对 1.21.11）

这一节把 1.21.11 到 1.20.6 之间的差异一起记下（1.21.10、1.21.8、1.21.5、1.21.4、1.21.3、1.21.1 都跳过了，直接从
`port/1.21.11` 开出并一次性改到 1.20.6）。

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
- 删掉：gizmo（1.21.6 才有，连同 `SimpleGizmoCollector`/`DrawableGizmoPrimitives`/`Gizmos`）、Super Resolution 与缩放
  探针（`SceneScale`、`scale_probe.fsh`、`RenderTypeAccessor`）、`StrictValidationMixin`，以及新渲染 API 的 mixin
  （`RenderPassMixin`、`PortalPassMixin`、`CompiledPipelineMixin`、`RenderPipelinesAccessor`、
  `FeatureRenderDispatcherAccessor`、`FeatureRenderDispatcherMixin`、`RenderTypeAccessor`）。

**关卡钩子**
- `LevelRenderer.renderLevel(float, long, boolean, Camera, GameRenderer, LightTexture, Matrix4f 视图旋转, Matrix4f 投影)`。
  视图旋转与投影直接取参数（1.21 在本方法入口模型视图还是 identity，不能从 `RenderSystem` 读）。END 时合并。

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
