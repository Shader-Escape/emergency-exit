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
| 1.21.10 | `port/1.21.10` | 21 | 混淆，Mojang 映射 | 1.9.7 |
| 1.21.8 | `port/1.21.8` | 21 | 混淆，Mojang 映射 | 1.9.6 |
| 1.21.5 | `port/1.21.5` | 21 | 混淆，Mojang 映射 | 1.8.11 |
| 1.21.4 | `port/1.21.4` | 21 | 混淆，Mojang 映射 | 1.8.8 |
| 1.21.3 | `port/1.21.3` | 21 | 混淆，Mojang 映射 | 1.8.1 |
| 1.21.1 | `port/1.21.1` | 21 | 混淆，Mojang 映射 | 1.8.8 |

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

## 1.21.10（相对 1.21.11）

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
- Iris 1.9.7 有 `ImmediateState.skipExtension`，但和 1.10.8 一样把加宽后的格式当原版格式交出。EXIT 照 LitematicaPass 不动它
  （`IrisCompat.hasSkipExtension()` 为 false）。
- 没有 `registerShadowRenderCallback`，冒烟测试仍用 `ShadowRendererMixin`。

**开发运行**
- NeoForge 开发运行同样要在 `run/neoforge-1.21.10/config/neoforge-client.toml` 里设 `enableB3DValidationLayer = false`。
- 冒烟测试一次跑完：先不开光影，再对 `shaderpacks` 里每个光影包各跑一轮（通过 Iris 的配置和 `Iris.reload()` 切换），
  截图名里带上光影包名。

## 1.21.8（相对 1.21.10）

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
- Iris 1.9.6 和 1.9.7 一样有 `ImmediateState.skipExtension`，EXIT 照旧不动它。
- 没有 `registerShadowRenderCallback`，冒烟测试仍用 `ShadowRendererMixin`。

**开发运行**
- NeoForge 开发运行同样要在 `run/neoforge-1.21.8/config/neoforge-client.toml` 里设 `enableB3DValidationLayer = false`。
- 冒烟测试另外检查 EXIT 的每条管线都能编译（`precompilePipeline(...).isValid()`）。没有这一项时，全屏着色器缺失只会
  在日志里报错，计数照样通过。

## 1.21.5（相对 1.21.8）

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
  `isRenderingLevel` 开着时，原版管线（`getVertexFormat()`，也就是 `RenderType.format()`）也换成加宽的格式。
  批量开始缓冲时两个都要设（`skipExtension` 开、`isRenderingLevel` 关）。只关 `isRenderingLevel` 时，实体方块（绿、黄）
  在光影下完全画不出来，计数却照样通过。
- 所以 1.21.5 上 `skipExtension` 是真的开关：`IrisCompat.hasSkipExtension()` 为 true，portal 和 `plainFormat` 都会设它，
  和 26.3 一样（1.21.6 到 26.2 的 Iris 把加宽格式当原版格式交出，EXIT 在那些版本不动它）。
- Iris 1.8 只要它想替换着色器（不管 bypass），就锁住它不认识的原版程序的颜色和深度写入。`IrisShaderOverrideMixin`
  让 `shouldOverrideShaders` 在 bypass 时返回 false（和 LitematicaPass 一样）。
- 没有 `registerShadowRenderCallback`，冒烟测试仍用 `ShadowRendererMixin`。

**开发运行**
- NeoForge 开发运行同样要在 `run/neoforge-1.21.5/config/neoforge-client.toml` 里设 `enableB3DValidationLayer = false`。
- 冒烟测试另外读呈现帧中心附近的像素，绿色方块不在就判失败（计数只说明调用过，上面的问题就是这样漏掉的）。

## 1.21.4（相对 1.21.5）

**构建**
- NeoForm `1.21.4-20241203.161809`，NeoForge `21.4.161`，Fabric API `0.119.4+1.21.4`，Iris 1.8.8、Sodium 0.6.13。

**纯 OpenGL**
- 没有 `RenderPipeline`、`RenderPass`、`GpuTexture`：所有绘制都画进当前绑定的帧缓冲，混合、写入掩码由渲染类型自己设。
  `Gpu` 照 LitematicaPass 换成纯 GL：自己编译程序（同样的片元着色器，顶点着色器用 `gl_VertexID` 画一个全屏三角形），
  画完放回帧缓冲、视口、程序、顶点数组、纹理、掩码、深度测试和混合。四种全屏通道保留：颜色（预乘混合或替换）、深度（LEQUAL）、
  替换深度、整层（用 `GL_ALWAYS`，不用先清成远处）。编译失败的程序只记一次日志，冒烟测试会列出来。
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
