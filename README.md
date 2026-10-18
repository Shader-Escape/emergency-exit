# EXIT（应急出口）

[中文文档](README.zh-CN.md)

A library for Fabric and NeoForge that lets custom world rendering bypass shader packs (Iris). What you draw through EXIT goes into an
independent render target and is merged onto the frame by depth after the shader pack has shaded it, so the shader pack
neither replaces your pipelines nor swallows your drawing. It is the rendering core of
[LitematicaPass](https://github.com/hotpad100c/litematicapass), taken out of it.

This branch is for Minecraft **1.21.8** (mod id `emergency_exit`, package `ml.mypals.exit.api`). Each Minecraft
version gets its own git branch with plain code for that version; `master` is 26.3.

### Loaders

Both loaders share one API, with one jar each: `emergency_exit-fabric` and `emergency_exit-neoforge` (Maven
`ml.mypals.exit:emergency_exit-<loader>:0.1.0+1.21.8`). Iris is optional. Without a shader pack nothing is routed.

Call EXIT while the level renders, inside `LevelRenderer.renderLevel`.

| Purpose | Fabric | NeoForge |
|---|---|---|
| Draw into a batch | `WorldRenderEvents.BEFORE_ENTITIES` (`context.consumers()`) | `RenderLevelStageEvent.AfterOpaqueBlocks` and others, without a buffer source to pass through: `Exit.batch()` |
| Drawings (recorded into the batch) | `WorldRenderEvents.BEFORE_ENTITIES`, `AFTER_ENTITIES`, `LAST` and others | `RenderLevelStageEvent.AfterOpaqueBlocks` and others |
| Portals | `WorldRenderEvents.BEFORE_ENTITIES`, `AFTER_ENTITIES`, `LAST` | `RenderLevelStageEvent.AfterOpaqueBlocks` and others |

1.21.8 has no `SubmitNodeCollector`: drawings and batches take a `MultiBufferSource` (see API stability below). The
level keeps no render pass open while it renders (each render type opens its own pass when it is drawn), so portals
work in the events inside the main pass.

The events of NeoForge for 1.21.8 hand out no buffer source, so there is nothing to pass through: use `Exit.batch()`
and `Exit.draw(null, ...)`. Without a shader pack the batch then renders into the main target at the end of the level
rendering.

Fabric's `WorldRenderEvents.END` and NeoForge's `RenderLevelStageEvent.AfterLevel` fire at the end of the level
rendering, where EXIT merges the frame, so drawings there may come after the merge and be dropped. Use `LAST`.

* The world still hides your drawing: the channel's depth is seeded from the scene depth before translucent surfaces.
* Translucent surfaces of the scene (water, glass) are drawn by the shader pack after your drawing left the frame; the
  parts of it behind them are dimmed (to 50% by default) to read as "behind".
* The first-person hand stays in front: the depth before and after the hand is compared and your drawing is discarded
  where the hand is.
* Without a shader pack (or without Iris) nothing is routed: everything is drawn as usual.

![Pass.png](docs/Pass.png)

## Use

Call EXIT on the render thread while the level renders, for example from Fabric's `WorldRenderEvents` or NeoForge's render events (see Loaders above).

```java
// 1. Drawing with a managed context: buffer source, camera-relative pose stack, camera. What is drawn into the
//    buffer source is recorded into the channel's batch.
WorldRenderEvents.BEFORE_ENTITIES.register(context ->
    Exit.draw(context.consumers(), exit -> {   // the level's buffer source is used as-is when nothing is routed
        PoseStack pose = exit.poseStack();
        Vec3 cam = exit.cameraPos();
        pose.translate(x - cam.x, y - cam.y, z - cam.z);
        VertexConsumer buffer = exit.bufferSource().getBuffer(RenderType.debugQuads());
        buffer.addVertex(pose.last(), 0, 0, 0).setColor(0xC0FF3030); ...
    }));

// 2. A portal, for code with its own render context: the shader pack's switches are set while the lambda
//    runs and put back after it. Render passes opened in it on the main target draw into the channel's target.
channel.portal(() -> {                           // or Exit.portal(...) on the main channel
    myRenderer.render(Minecraft.getInstance().gameRenderer.mainRenderTarget());  // moved into the channel
    RenderTarget target = channel.redirect(fb);  // other targets: redirect yourself
    ...
});
Exit.portal(PortalOverrides.DEFAULT.withRenderingLevel(PortalOverrides.Switch.OFF), () -> { ... });

// Vertex buffers built by yourself, often on a worker thread: vanilla formats, without the pack's widening.
Exit.plainFormat(() -> buildMyVbo());

// 3. Batch: collected during the frame, drawn all at once at the end of the level rendering, together with the
//    drawings of 1: all solid render types first, then the translucent ones.
WorldRenderEvents.BEFORE_ENTITIES.register(context ->
    myRenderer.render(Exit.batch(context.consumers())));
```

### Channels

`Exit.draw/portal/batch` use the shared **main channel** (`emergency_exit:main`, default options). A mod that needs other
options declares its own:

```java
ExitChannel xray = Exit.channel(ResourceLocation.fromNamespaceAndPath("mymod", "xray"),
    ExitOptions.builder()
        .occludedByWorld(false)          // show through the world
        .hideUnderHand(true)
        .dimBehindTranslucent(0.5F)      // 1 = not dimmed
        .priority(10)                    // merged after (over) lower priorities
        .blendWithChannels(true)         // blended with the other channels by depth (default)
        .wantsFrame(() -> myMod.enabled())
        .build());
xray.draw(...); xray.portal(...); xray.redirect(fb); xray.batch(); xray.flushBatch();
```

Drawings of one channel hide each other by depth (one target). Channels that blend with each other
(`blendWithChannels`, the default) are joined one by one, in merge order, into an accumulated layer: per pixel, the
nearer one is drawn over the other with their alpha (the hand and the dimming behind translucent surfaces are applied
as they join), and the layer is then merged onto the frame; priority only decides where they are at the same depth.
The layer keeps one depth per pixel, so two channels blend exactly and three or more overlapping at one pixel
approximately. Translucent render types write no depth, so EXIT draws the translucent render types of its own
drawings (drawings, batches, and batches rendered through EXIT inside a portal) a second time with depth-only copies
of their pipelines, into a depth target of the channel; before merging, the nearer of both depths becomes the
channel's depth. Translucent content in passes you open yourself in a portal writes
no depth, keeps the seeded scene depth and counts as far away. Channels with `blendWithChannels(false)` are merged
over the layer by priority, then creation order. Each channel costs one RGBA8 + D32 target at the scene size, one more
D32 target once it draws translucent render types, and blending two or more costs two more.

### Rules

* **Thread and time.** Render thread, between the start and the end of `LevelRenderer.renderLevel` (except `portal` off
  the render thread and `plainFormat`, below). Outside it a drawing is dropped (logged once) and a portal does not
  route. A channel's target is seeded with the scene depth when it is first used in the frame, so route (portal or `redirect`) once the
  level's render passes run (as Litematica does), outside an open render pass, not before.
* **Portals.** `portal(overrides, body)` sets Iris's `bypass` (default on), `skipExtension` (default on, per thread; it does nothing on 1.21.8, see
  `PortalOverrides`) and `isRenderingLevel` (default left alone), each `KEEP`/`ON`/`OFF`, while `body` runs, and restores them after. It
  routes with a shader pack in use, outside its shadow pass, while the level renders; the switches are set whenever a
  pack is in use outside its shadow pass. While it routes, a mixin moves every render pass **opened** in the portal
  with the main target as its colour or depth attachment into the channel's target, for any mod. (1.21.8 also has
  `RenderSystem.outputColorTextureOverride`, which only moves render types; the mixin also moves passes opened directly.) Not moved, so use `channel.redirect(yourTarget)` there: other targets, a pass already open when the
  portal began (one handed to you by an event), and clears and copies of the main target. `redirect` (also
  the static `Exit.redirect`) throws outside a portal (or a `draw` lambda), off the render thread and inside a portal
  of another channel, instead of quietly handing your target back; code that also runs outside portals checks
  `Exit.isRouting()` first.
* **Worker threads.** Off the render thread a portal only sets that thread's `skipExtension` (a `ThreadLocal`):
  `bypass` and `isRenderingLevel` are global render state and left alone, and `redirect` is not available. To only
  turn the widening off, use `Exit.plainFormat(Runnable)`, on any thread.
* **Nesting.** Portals nest on one channel at a time (a drawing inside one keeps its switches; one inside a
  drawing shares its buffer source). Opening another channel inside one throws.
* **Shadow pass.** EXIT draws nothing into the shader pack's shadow map, so its drawings cast no shadows: in the
  shadow pass a `draw` lambda and a `portal` body do not run, and a `batch` buffer source drops what it gets
  (`plainFormat` is not affected). Fabric's `WorldRenderEvents` do not fire in Iris's shadow pass anyway.
* **Order.** A `draw` lambda runs at once, but what it draws into the buffer source joins the channel's batch,
  wherever it is called (also before the level's render passes start). The batch keeps one buffer per render type and
  draws nothing while it is filled; do not end its batches yourself. It is rendered at the end of the level rendering,
  or earlier with `flushBatch()`, in one frame: solid render types first, then translucent ones (those whose pipeline
  blends), so a solid drawing never covers a nearer translucent one. Portals draw at once, before the batch: in a
  portal, call `flushBatch()` before drawing translucent things, or the batch's solid drawings cover them. To draw with
  passes of your own, use a portal.
* **Portals and batches.** A batch (and `draw`) hands EXIT vertices, kept per render type. EXIT decides the order when
  it renders them, so it draws all solid render types before all translucent ones and records the depth of the
  translucent ones. A portal wraps someone else's code: it opens its own passes with its own pipelines and draws into the
  channel's target at once. EXIT only sees the finished pixels.
* **Why a portal's translucent pixels cannot blend with a later batch.** Once a translucent surface is drawn into the
  target, it is already mixed with the colour behind it: the pixel holds only the result, and the surface's own colour
  and coverage are gone. A solid batch drawing that belongs behind it would have to replace that colour behind, which
  is no longer possible. The depth does not help (translucent surfaces write none, and even then a pixel holds one
  colour). So the batch's solid drawings can only cover it, or come before it: call `flushBatch()` in the portal before
  drawing translucent things (for Litematica, after the opaque terrain and before the translucent terrain).
* **Blending.** The merge treats the channel's colour as premultiplied alpha (vanilla's translucent pipelines work).
* **Own vertex buffers.** Iris widens vertex formats while it builds them; build them in `Exit.plainFormat(...)` (any
  thread: compiling usually runs on workers) when you draw them routed with vanilla pipelines.
* **Scene depth.** It is copied twice per frame (before the hand, before translucents) when a channel wants the frame:
  its `wantsFrame`, or by default "it drew in the previous frame". A channel that draws without it still works, with
  occlusion against the main depth at that moment for that frame.
* **API stability.** `ml.mypals.exit.api` is the API; `impl`, `compat` and `mixin` are not. The API names Minecraft
  types (`MultiBufferSource`, `Camera`, `PoseStack`, `RenderTarget`), so it is stable within one Minecraft version.
  1.21.8 has no `SubmitNodeCollector` and no camera render state: where 26.3 takes or hands out a
  `SubmitNodeCollector` this branch uses a `MultiBufferSource` (`Exit.draw(MultiBufferSource, ...)`,
  `ExitContext.bufferSource()`, `Exit.batch(MultiBufferSource)`), and `ExitContext.camera()` is the `Camera`.

Debug: `/emergency_exit debug <color|target_depth|scene_depth|main_depth|translucent_depth|off> [channel]` shows a
channel's buffers instead of the frame (with a shader pack in use). `/emergency_exit debug shapes` turns three test boxes
on, 5 blocks in front of you, or off again: red = translucent drawing, green = batch (solid), yellow = a solid box on another channel behind the green one, blended by depth, blue = a pass on the main target in a
portal, moved into a channel that shows through the world. 1.21.8 has no gizmos, so there is no cyan outline.

## Development

The project has three subprojects, laid out like MultiLoader-Template. `common` holds all loader-independent code
(API, rendering, mixins). `fabric` and `neoforge` hold the entry points, metadata, command registration and the
event hooks of the debug shapes. Loader-specific queries go through the `Platform` service.

* `./gradlew build` builds both jars (`fabric/build/libs`, `neoforge/build/libs`).
* `./gradlew :fabric:runSmokeClient` and `./gradlew :neoforge:runSmokeClient` create a world, turn on the test shapes
  and run one phase without shaders, then one with each shader pack in the run's `shaderpacks` folder (switched through
  Iris, so put BSL, Kappa and so on there). Each phase takes screenshots
  (`screenshots/emergency_exit-smoke-<version>-<loader>-<pack>-<view>.png`) and checks what EXIT did; then the game
  quits. The log ends with `RESULT: OK`. `-Demergency_exit.smoke.packs=none,BSL.zip` picks the phases instead. They run
  in `run/fabric-1.21.8` and `run/neoforge-1.21.8`.
* `./gradlew :fabric:runAuditClient` checks that every mixin applies (Fabric only).
* The smoke test also saves `*-presented.png`, the window's front buffer, which is what the player sees. EXIT still
  follows the render size of the Super Resolution mod when it is present, but SR has no build for 1.21.8, so the
  development runs do not load it.
* NeoForge's development runs turn on the Blaze3D validation layer, which Iris does not start with: set
  `enableB3DValidationLayer = false` in `run/neoforge-1.21.8/config/neoforge-client.toml`.
* `./gradlew publishToMavenLocal` publishes both jars for other mods' development setups.

## LitematicaPass on EXIT (plan)

LitematicaPass then depends on EXIT for 26.3 (built with `publishToMavenLocal`, nested with `include`), and keeps its
own copy of the pipeline for the older versions until EXIT is ported to them:

| LitematicaPass now (26.3)                                    | On EXIT                                                                              |
|--------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `SchematicTarget` + `SceneDepth` + `DepthSeed` + `Composite` | a channel `litematicapass:schematic`, `wantsFrame` = Litematica's rendering toggle    |
| `piecewiseDrawBlockLayerGroup` wrap                          | `channel.portal(() -> original.call(channel.redirect(fb), group))`                   |
| entities into its own storage, drawn after the opaque group  | `channel.batch(queue)` in `piecewiseRenderEntities`; `channel.flushBatch()` after the opaque group |
| overlay and `RenderHandler` boxes: `begin/end`               | `channel.portal(...)` around them                                                    |
| malilib `RenderUtils.mainTarget()` → `targetFor`             | `Exit.isRouting() ? Exit.redirect(original) : original`                              |
| `ChunkRendererSchematicVboMixin.plainFormat`                 | `Exit.plainFormat(...)`                                                              |
| `LegacyTerrainFixUniformMixin`: `!SchematicTarget.isActive()` | `!Exit.isActive()`                                                                   |
| `/litematicapass debug`                                      | `/emergency_exit debug ... litematicapass:schematic`                                 |
| `LevelRendererMixin`, `IrisRenderingPipelineMixin`           | in EXIT                                                                              |
