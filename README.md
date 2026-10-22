# EXIT（应急出口）

[中文文档](README.zh-CN.md)

A library for Fabric that lets custom world rendering bypass shader packs (Iris). What you draw through EXIT goes into an
independent render target and is merged onto the frame by depth after the shader pack has shaded it, so the shader pack
neither replaces your pipelines nor swallows your drawing. It is the rendering core of
[LitematicaPass](https://github.com/hotpad100c/litematicapass), taken out of it.

Minecraft **1.17.1** only for now (mod id `emergency_exit`, package `ml.mypals.exit.api`). Each Minecraft version gets
its own git branch with plain code for that version. 1.17 runs on Java 16, and only 1.17.1 is built and run here
(`fabric.mod.json`: `>=1.17 <=1.17.1`).

### Loaders

One jar: `emergency_exit-fabric` (Maven `ml.mypals.exit:emergency_exit-fabric:0.1.1+1.17.1`). Iris is optional.
Without a shader pack nothing is routed. Iris has no NeoForge build for this Minecraft version, so this branch is
Fabric only. Iris here is 1.2.7, in `net.coderbot.iris`.

Call EXIT while the level renders, inside `LevelRenderer.renderLevel`. This version has no `SubmitNodeCollector`, so
the drawings and the batch are a `MultiBufferSource` and are drawn at the end of the level rendering.

| Purpose | Fabric |
|---|---|
| Submit to a batch | `WorldRenderEvents.BEFORE_ENTITIES` (the context's `consumers()`) |
| Drawings (recorded into the batch) | the same event, or `WorldRenderEvents.AFTER_ENTITIES` |
| A portal (drawn at once) | call it from the events, or at the end of the level rendering |

Before 1.21.5 there is no output override, so a portal moves what is drawn to the main target by binding the channel's
framebuffer and by handing the channel's target out of `Minecraft.getMainRenderTarget()` (see the rules).

* The world still hides your drawing: the channel's depth is seeded from the scene depth before translucent surfaces.
* Translucent surfaces of the scene (water, glass) are drawn by the shader pack after your drawing left the frame; the
  parts of it behind them are dimmed (to 50% by default) to read as "behind".
* The first-person hand stays in front: the depth before and after the hand is compared and your drawing is discarded
  where the hand is.
* Without a shader pack (or without Iris) nothing is routed: everything is drawn as usual.

![Pass.png](docs/Pass.png)

## Use

Call EXIT on the render thread while the level renders, for example from Fabric's `WorldRenderEvents` (see Loaders above).

```java
// 1. Drawing with a managed context: buffer source, camera-relative pose stack, camera. The submits are recorded into
//    the channel's batch.
WorldRenderEvents.BEFORE_ENTITIES.register(context ->
    Exit.draw(context.consumers(), exit -> {           // the buffer source is used as-is when nothing is routed
        PoseStack pose = exit.poseStack();
        Vec3 cam = exit.cameraPos();
        pose.translate(x - cam.x, y - cam.y, z - cam.z);
        // 1.17 has no RenderType.debugQuads(); any vanilla render type does. This one is the entity format, so every
        // element of the vertex has to be written.
        VertexConsumer buffer = exit.bufferSource().getBuffer(RenderType.entitySolid(myTexture));
        // ... write vertices with pose.last()
    }));

// 2. A portal, for code with its own render context: the shader pack's switches are set while the lambda runs and put
//    back after it. Draws that go to the main target draw into the channel's target instead.
channel.portal(() -> {                           // or Exit.portal(...) on the main channel
    myRenderer.render(Minecraft.getInstance().getMainRenderTarget());  // moved into the channel
    RenderTarget target = channel.redirect(fb);  // other targets: redirect yourself
    ...
});
Exit.portal(PortalOverrides.DEFAULT.withRenderingLevel(PortalOverrides.Switch.OFF), () -> { ... });

// Vertex buffers built by yourself, often on a worker thread: vanilla formats, without the pack's widening.
Exit.plainFormat(() -> buildMyVbo());

// 3. Batch: collected during the frame, drawn all at once at the end of the level rendering, together with the
//    drawings of 1: all solid submits first, then the translucent ones.
WorldRenderEvents.BEFORE_ENTITIES.register(context ->
    myRenderer.submit(Exit.batch(context.consumers())));
```

### Channels

`Exit.draw/portal/batch` use the shared **main channel** (`emergency_exit:main`, default options). A mod that needs other
options declares its own:

```java
ExitChannel xray = Exit.channel(new ResourceLocation("mymod", "xray"),
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
approximately. Translucent render types write no depth, so EXIT draws the translucent submits of its own drawings
(drawings, batches, and submits rendered through EXIT inside a portal) a second time with depth-only copies
of their pipelines, into a depth target of the channel; before merging, the nearer of both depths becomes the
channel's depth. Translucent content in passes you open yourself in a portal writes
no depth, keeps the seeded scene depth and counts as far away. Channels with `blendWithChannels(false)` are merged
over the layer by priority, then creation order. Each channel costs one RGBA8 + D32 target at the scene size, one more
D32 target once it draws translucent submits, and blending two or more costs two more.

### Rules

* **Thread and time.** Render thread, between the start and the end of `LevelRenderer.renderLevel` (except `portal` off
  the render thread and `plainFormat`, below). Outside it a drawing is dropped (logged once) and a portal does not
  route. A channel's target is seeded with the scene depth when it is first used in the frame, so route (portal or `redirect`) once the
  level's render passes run (as Litematica does), outside an open render pass, not before.
* **Portals.** `portal(overrides, body)` sets Iris's `bypass` (default on), `skipExtension` (default on, per thread)
  and `isRenderingLevel` (default left alone), each `KEEP`/`ON`/`OFF`, while `body` runs, and restores them after. It
  routes with a shader pack in use, outside its shadow pass, while the level renders; the switches are set whenever a
  pack is in use outside its shadow pass. While it routes, the channel's framebuffer is bound and
  `Minecraft.getMainRenderTarget()` hands out the channel's target (a mixin), so draws that go to the main target draw
  into the channel, for any mod. `redirect(yourTarget)` is for other targets. `redirect` (also the static
  `Exit.redirect`) throws outside a portal (or a `draw` lambda), off the render thread and inside a portal
  of another channel, instead of quietly handing your target back; code that also runs outside portals checks
  `Exit.isRouting()` first. (This Minecraft version has no `RenderSystem` output override and no render passes, so the
  portal moves draws by binding the framebuffer instead.)
* **Worker threads.** Off the render thread a portal only sets that thread's `skipExtension` (a `ThreadLocal`):
  `bypass` and `isRenderingLevel` are global render state and left alone, and `redirect` is not available. To only
  turn the widening off, use `Exit.plainFormat(Runnable)`, on any thread.
* **Nesting.** Portals nest on one channel at a time (a drawing inside one keeps its switches; one inside a
  drawing shares its collector). Opening another channel inside one throws.
* **Shadow pass.** EXIT draws nothing into the shader pack's shadow map, so its drawings cast no shadows: in the
  shadow pass a `draw` lambda and a `portal` body do not run, and a `batch` collector drops what it gets
  (`plainFormat` is not affected). Fabric's `WorldRenderEvents` do not fire in Iris's shadow pass anyway.
* **Order.** A `draw` lambda runs at once, but what it submits joins the channel's batch, wherever it is called (also
  inside an open render pass, or before the level's render passes start). The batch is rendered at the end of the
  level rendering, or earlier with `flushBatch()`, in one frame: solid submits first, then translucent ones, so a solid
  submit never covers a nearer translucent one. Portals draw at once, before the batch: in a portal, call
  `flushBatch()` before drawing translucent things, or the batch's solid submits cover them. To draw with passes of
  your own, use a portal.
* **Portals and batches.** A batch (and `draw`) hands EXIT submits. EXIT keeps them and decides the order when it
  renders them, so it draws all solid submits before all translucent ones and records the depth of the translucent
  ones. A portal wraps someone else's code: it opens its own passes with its own pipelines and draws into the
  channel's target at once. EXIT only sees the finished pixels.
* **Why a portal's translucent pixels cannot blend with a later batch.** Once a translucent surface is drawn into the
  target, it is already mixed with the colour behind it: the pixel holds only the result, and the surface's own colour
  and coverage are gone. A solid batch submit that belongs behind it would have to replace that colour behind, which
  is no longer possible. The depth does not help (translucent surfaces write none, and even then a pixel holds one
  colour). So the batch's solid submits can only cover it, or come before it: call `flushBatch()` in the portal before
  drawing translucent things (for Litematica, after the opaque terrain and before the translucent terrain).
* **Blending.** The merge treats the channel's colour as premultiplied alpha (vanilla's translucent pipelines work).
* **Own vertex buffers.** Iris widens vertex formats while it builds them; build them in `Exit.plainFormat(...)` (any
  thread: compiling usually runs on workers) when you draw them routed with vanilla pipelines.
* **Scene depth.** It is copied twice per frame (before the hand, before translucents) when a channel wants the frame:
  its `wantsFrame`, or by default "it drew in the previous frame". A channel that draws without it still works, with
  occlusion against the main depth at that moment for that frame.
* **API stability.** `ml.mypals.exit.api` is the API; `impl`, `compat` and `mixin` are not. The API names Minecraft
  types (`MultiBufferSource`, `PoseStack`, `RenderTarget`), so it is stable within one Minecraft version.

Debug: `/emergency_exit debug <color|target_depth|scene_depth|main_depth|translucent_depth|off> [channel]` shows a
channel's buffers instead of the frame (with a shader pack in use). `/emergency_exit debug shapes` turns three test boxes
on, 5 blocks in front of you, or off again: red = translucent drawing, green = batch (solid), yellow = a solid box on another channel behind the green one, blended by depth, blue = a pass on the main target in a
portal, moved into a channel that shows through the world.

## Development

The project is one Fabric Loom project. `fabric/src/main` holds all of the code (API, rendering, mixins); the entry
points, metadata, command registration and the event hooks of the debug shapes are in
`fabric/src/main/java/ml/mypals/exit/fabric`. `fabric/src/audit` and `fabric/src/smoke` are the development checks.
Loader-specific queries go through the `Platform` service.
(Minecraft 1.20.1 and older have no NeoForm build for ModDevGradle, so there is no `common` subproject: Loom remaps the
one project from Mojang names to intermediary. 1.17 additionally compiles at Java 16, so the build runs its own JDK 25
while the development client runs a JDK 16 toolchain.)

* `./gradlew build` builds the jar (`fabric/build/libs`).
* `./gradlew :fabric:runSmokeClient` creates a world, turns on the test shapes, takes screenshots and quits. The log
  ends with `RESULT: OK`. It runs in `run/fabric-1.17.1`. To test routing, put a shader pack into its `shaderpacks`
  folder and enable it in `config/iris.properties`.
* `./gradlew :fabric:runAuditClient` checks that every mixin applies (Fabric only).
* `./gradlew publishToMavenLocal` publishes the jar for other mods' development setups.

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
