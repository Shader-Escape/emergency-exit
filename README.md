# EXIT（应急出口）

[中文文档](README.zh-CN.md)

A Fabric library that lets custom world rendering bypass shader packs (Iris). What you draw through EXIT goes into an
independent render target and is merged onto the frame by depth after the shader pack has shaded it, so the shader pack
neither replaces your pipelines nor swallows your drawing. It is the rendering core of
[LitematicaPass](https://github.com/hotpad100c/litematicapass), taken out of it.

Minecraft **26.3** only for now (mod id `emergency_exit`, package `ml.mypals.exit.api`). Each Minecraft version gets
its own git branch with plain code for that version.

* The world still hides your drawing: the channel's depth is seeded from the scene depth before translucent surfaces.
* Translucent surfaces of the scene (water, glass) are drawn by the shader pack after your drawing left the frame; the
  parts of it behind them are dimmed (to 50% by default) to read as "behind".
* The first-person hand stays in front: the depth before and after the hand is compared and your drawing is discarded
  where the hand is.
* Without a shader pack (or without Iris) nothing is routed: everything is drawn as usual.

![Pass.png](docs/Pass.png)

## Use

Call EXIT on the render thread while the level renders, for example from Fabric's `LevelRenderEvents`.

```java
// 1. Immediate drawing with a managed context: collector, camera-relative pose stack, camera.
LevelRenderEvents.AFTER_SOLID_FEATURES.register(context ->
    Exit.draw(context.submitNodeCollector(), exit -> {   // the collector is used as-is when nothing is routed
        PoseStack pose = exit.poseStack();
        Vec3 cam = exit.cameraPos();
        pose.translate(x - cam.x, y - cam.y, z - cam.z);
        exit.collector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, buffer) -> { ... });
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

// 3. Batch: collected during the frame, drawn all at once at the end of the level rendering.
LevelRenderEvents.COLLECT_SUBMITS.register(context ->
    myRenderer.submit(Exit.batch(context.submitNodeCollector())));
```

### Channels

`Exit.draw/portal/batch` use the shared **main channel** (`emergency_exit:main`, default options). A mod that needs other
options declares its own:

```java
ExitChannel xray = Exit.channel(Identifier.fromNamespaceAndPath("mymod", "xray"),
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
approximately; only content that writes depth sorts (translucent render types that write none, such as vanilla's
`debugQuads`, keep the seeded scene depth and count as far away). Channels with `blendWithChannels(false)` are merged
over the layer by priority, then creation order. Each channel costs one RGBA8 + D32 target at the scene size, and
blending two or more costs two more.

### Rules

* **Thread and time.** Render thread, between the start and the end of `LevelRenderer.render` (except `portal` off
  the render thread and `plainFormat`, below). Outside it a drawing is dropped (logged once) and a portal does not
  route. A channel's target is seeded with the scene depth when it is first used in the frame, so route (portal or `redirect`) once the
  level's render passes run (as Litematica does), outside an open render pass, not before.
* **Portals.** `portal(overrides, body)` sets Iris's `bypass` (default on), `skipExtension` (default on, per thread)
  and `isRenderingLevel` (default left alone), each `KEEP`/`ON`/`OFF`, while `body` runs, and restores them after. It
  routes with a shader pack in use, outside its shadow pass, while the level renders; the switches are set whenever a
  pack is in use outside its shadow pass. While it routes, EXIT stands in for the output override of 1.21.6–26.2
  (gone in 26.3) with a mixin: every render pass **opened** in the portal with the main target as its colour or depth
  attachment draws into the channel's target instead (its render area kept within the channel, for Super Resolution),
  for any mod. Not moved, so use `channel.redirect(yourTarget)` there: other targets, a pass already open when the
  portal began (one handed to you by an event), and clears and copies of the main target. Gizmos added on the render
  thread while the portal routes (vanilla `Gizmos.cuboid`, `Gizmos.line` and so on) go to the channel too and are
  drawn with its batch, with their persistence, fading and always-on-top settings kept. `redirect` (also
  the static `Exit.redirect`) throws outside a portal (or a `draw` lambda), off the render thread and inside a portal
  of another channel, instead of quietly handing your target back; code that also runs outside portals checks
  `Exit.isRouting()` first.
* **Worker threads.** Off the render thread a portal only sets that thread's `skipExtension` (a `ThreadLocal`):
  `bypass` and `isRenderingLevel` are global render state and left alone, and `redirect` is not available. To only
  turn the widening off, use `Exit.plainFormat(Runnable)`, on any thread.
* **Nesting.** Portals nest on one channel at a time (an immediate drawing inside one keeps its switches; one inside an
  immediate drawing shares its collector). Opening another channel inside one throws.
* **Shadow pass.** EXIT draws nothing into the shader pack's shadow map, so its drawings cast no shadows: in the
  shadow pass a `draw` lambda and a `portal` body do not run, and a `batch` collector drops what it gets
  (`plainFormat` is not affected). Fabric's `LevelRenderEvents` do not fire in Iris's shadow pass anyway.
* **Open render passes.** 26.3 cannot open a pass inside another, and some places run inside the main pass (Fabric's
  terrain and feature events such as `AFTER_SOLID_FEATURES`). An immediate drawing there still runs at once, but its
  submits join the channel's batch (no `ExitContext.target()`/`openPass` then). The same happens before the level's
  render passes start (`COLLECT_SUBMITS`, `BEFORE_GIZMOS`): the scene depth is still last frame's. `END_MAIN` is a
  place where immediate drawings render at once.
* **Order.** Immediate drawings outside a pass render when the lambda returns; batches at the end of the level rendering, after them,
  or earlier with `flushBatch()`. Passes you open with `ExitContext.openPass` come before that drawing's submits.
* **Blending.** The merge treats the channel's colour as premultiplied alpha (vanilla's translucent pipelines work).
* **Own vertex buffers.** Iris widens vertex formats while it builds them; build them in `Exit.plainFormat(...)` (any
  thread: compiling usually runs on workers) when you draw them routed with vanilla pipelines.
* **Scene depth.** It is copied twice per frame (before the hand, before translucents) when a channel wants the frame:
  its `wantsFrame`, or by default "it drew in the previous frame". A channel that draws without it still works, with
  occlusion against the main depth at that moment for that frame.
* **API stability.** `ml.mypals.exit.api` is the API; `impl`, `compat` and `mixin` are not. The API names Minecraft
  types (`SubmitNodeCollector`, `PoseStack`, `RenderTarget`), so it is stable within one Minecraft version.

Debug: `/emergency_exit debug <color|target_depth|scene_depth|main_depth|translucent_depth|off> [channel]` shows a
channel's buffers instead of the frame (with a shader pack in use). `/emergency_exit debug shapes` turns three test boxes
on, 5 blocks in front of you, or off again: red = immediate drawing, green = batch (solid), yellow = a solid box on another channel behind the green one, blended by depth, blue = a pass on the main target in a
portal, moved into a channel that shows through the world, with a cyan gizmo outline added in the same portal.

## Development

* `./gradlew build` builds the library.
* `./gradlew runSmokeClient` creates a world, turns on the test shapes of `/emergency_exit debug shapes`, takes
  screenshots into `run/screenshots/` and quits; the log ends with `RESULT: OK`.
* `./gradlew runAuditClient` checks that every mixin applies.
* `./gradlew runSmokeClient -Pexit.superResolution` does the same with the Super Resolution mod loaded (in the
  development runs only), to check the stretched merge. It needs a shader pack SR supports (for example Sundial Lite;
  SR stays off with BSL) and `upscale_algo = "fsr2"` in `run/config/super_resolution/config.toml` (the other
  algorithms do not upscale properly there). The smoke test also saves `*-presented.png`, the window's front buffer:
  what the player sees (with SR the main target is not the presented frame).

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
