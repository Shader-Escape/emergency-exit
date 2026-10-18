# EXIT（应急出口）

[中文文档](README.zh-CN.md)

EXIT is a library for Fabric and NeoForge that lets custom world rendering bypass shader packs (Iris). What you draw through EXIT goes into an independent render target, and after the shader pack has shaded the whole frame it is merged back by depth. The shader pack neither replaces your render pipeline nor swallows your drawing.

It was split out of the rendering core of [LitematicaPass](https://github.com/hotpad100c/litematicapass) with the help of AI. I know some people object to AI, but this was the only migration I could use to reach my goal quickly. I know what I'm doing, I use this library myself, and I can make sure it won't eat your pet's stone. This is not AI slop, and it is not wish coding. If you run into any trouble, I'm glad to help.

Mod id `emergency_exit`, API package `ml.mypals.exit.api`. Each Minecraft version has its own git branch, and each branch holds only that version's code. This README is maintained only on `master`. It covers what all versions share, and the examples use 26.3. Where another version differs (events, API types, missing features), it is written in that version's section of [ChangeLog.md](ChangeLog.md), under **Use**. Each branch carries only a short README that points here.

### Versions

| Minecraft | Branch | Maven version | Drawings and batches take | Notes |
|---|---|---|---|---|
| 26.3 | `master` | `0.1.0+26.3` | `SubmitNodeCollector` | |
| 26.2 | `port/26.2` | `0.1.0+26.2` | `SubmitNodeCollector` | |
| 26.1.2 | `port/26.1.2` | `0.1.0+26.1.2` | `SubmitNodeCollector` | |
| 1.21.11 | `port/1.21.11` | `0.1.0+1.21.11` | `SubmitNodeCollector` | NeoForge has no event that hands out the level's collector |
| 1.21.10 | `port/1.21.10` | `0.1.0+1.21.10` | `SubmitNodeCollector` | `ResourceLocation`; no gizmos |
| 1.21.8 | `port/1.21.8` | `0.1.0+1.21.8` | `MultiBufferSource` | `ExitContext.camera()` is the `Camera` |
| 1.21.5 | `port/1.21.5` | `0.1.0+1.21.5` | `MultiBufferSource` | |
| 1.21.4 | `port/1.21.4` | `0.1.0+1.21.4` | `MultiBufferSource` | plain OpenGL: portals bind the channel's target |
| 1.21.3 | `port/1.21.3` | `0.1.0+1.21.3` | `MultiBufferSource` | |
| 1.21.1 | `port/1.21.1` | `0.1.0+1.21.1` | `MultiBufferSource` | |

### Loaders

Both loaders share one API, with one jar each: `emergency_exit-fabric` and `emergency_exit-neoforge` (Maven `ml.mypals.exit:emergency_exit-<loader>:<Maven version>`, see Versions). Iris is optional. Without a shader pack, nothing is routed.

Call EXIT while the level renders, inside `LevelRenderer.render`. The places that work on 26.3 (other versions: ChangeLog, **Use**):

| Purpose | Fabric | NeoForge |
|---|---|---|
| Submit to a batch | `LevelRenderEvents.COLLECT_SUBMITS` | `SubmitCustomGeometryEvent` |
| Drawings (recorded into the batch) | `LevelRenderEvents.AFTER_SOLID_FEATURES`, `END_MAIN` and others | `RenderLevelStageEvent.AfterOpaqueFeatures` and others |
| After the main pass, outside any pass (portals) | `LevelRenderEvents.END_MAIN` | no matching event |

NeoForge's `RenderLevelStageEvent.AfterLevel` fires after level rendering has ended. By then EXIT has already merged the frame, so drawings made there are dropped.

## Basic concepts

| Term | Meaning |
|---|---|
| **Channel** (`ExitChannel`) | A set of drawings that share one target and one set of merge options (`ExitOptions`). Drawings in the same channel hide each other by depth. Different channels also blend by depth by default (see "Channel blending"). A channel with `blendWithChannels` turned off is layered on top by priority instead. |
| **Main channel** | The shared default channel `emergency_exit:main`, used by `Exit.draw/portal/batch`. When you need different options (for example, showing through the world), declare your own channel with `Exit.channel(id, options)`. |
| **Channel target** | Each channel gets one scene-sized colour and depth target (RGBA8 + D32). It is cleared the first time the frame uses it, and seeded with the scene depth. |
| **Routing** (`routed`) | A drawing is moved from the main target into the channel target and merged back later. This only happens when a shader pack is on, the frame is not in the shadow pass, and the level is rendering. `Exit.isActive()` says whether routing would happen right now. |
| **Scene depth** | The main depth that EXIT copies twice: once before the shader pack draws the hand, and once before it draws translucent surfaces. It seeds the depth of channel targets (so the world occludes drawings), and it is used during merging to find the hand and the translucent surfaces. |
| **Merge** (`composite`) | At the end of level rendering, the channel targets are drawn back onto the main target by depth. Pixels under the hand are dropped, and parts behind translucent surfaces are dimmed. |
| **Channel blending** (`blendWithChannels`) | Before merging, the blending channels are joined one by one into an accumulated layer. Per pixel, depths are compared and the nearer one is drawn over the farther one (blended by the two alphas). The layer is then merged onto the frame as a single layer. |
| **Drawing** (`draw`) | Gives a managed context (`SubmitNodeCollector`, a camera-relative `PoseStack`, the camera) to a lambda, which runs at once. What it submits goes into the channel's batch. |
| **`RenderPass`** | One Minecraft draw batch. Only one can be open at a time. It is unrelated to EXIT channels. |
| **Batch** (`batch`) | A collector you can submit to at any point in the frame. At the end of level rendering it is drawn, together with what `draw` recorded, into the channel target in one go: solids first, then translucents. Use `flushBatch()` to draw earlier. |
| **Pass-through collector** | The vanilla collector passed to drawing methods (it may be `null`). When nothing is routed, drawing goes straight to it. |
| **bypass** | Iris's switch `ImmediateState.bypass`. When it is on, the shader pack no longer replaces the vanilla pipeline with its own programs. Routed drawing happens while it is on. |
| **portal** | A defined interval during which Iris's switches (bypass, vertex format widening, `isRenderingLevel`) are set according to `PortalOverrides`, then restored. For code that already has its own render context. Inside the interval, render passes opened on the main target are moved into the channel target. |
| **Redirect** (`redirect`) | Inside a portal, swaps the target you meant to draw into for the channel target (it returns the target unchanged when nothing is routed). Only needed where portal routing does not reach: targets other than the main one, or render passes that were already open before the portal began. Calling it outside a portal throws. |
| **Vertex format widening** | When Iris builds vertex buffers, it adds the extra data the shader pack needs to the vanilla vertex format (`skipExtension` turns this off, one switch per thread). For your own buffers that you draw routed with vanilla pipelines, build them with `Exit.plainFormat`. |
| **Shadow pass** | The pass in which the shader pack renders the shadow map from the sun's view. EXIT draws nothing here: `draw` lambdas and `portal` bodies don't run, and `batch` contents are dropped. |

## What it does

* **The world still hides your drawing.** The channel target's depth is seeded from the scene depth captured before translucent surfaces are drawn, so things behind walls are not visible.
* **Parts behind translucent surfaces are dimmed.** Water, glass and other translucent surfaces are drawn by the shader pack after your content has left the frame, so the two cannot blend. Parts behind them are dimmed (50% by default), so they read as "behind".
* **The first-person hand always stays in front.** The depth before and after the hand is compared to find the pixels the hand covers, and your drawing is discarded there.
* **Without a shader pack (or without Iris), nothing is routed.** Everything is drawn the way vanilla draws it.

![Pass.png](docs/Pass.png)

### Steps in each frame

1. `LevelRenderer.render` starts: EXIT records the frame's camera (view rotation and position) and the level's projection matrix.
2. Before the shader pack draws the hand, and before it draws translucent surfaces, the scene depth is copied once each (only when some channel draws in this frame).
3. Your drawing goes into the channel target. The channel target is cleared the first time it is used in the frame, and seeded with the scene depth.
4. `LevelRenderer.render` ends: each channel's batch is drawn. Blending channels are merged by depth into one layer, which is merged onto the main target. The other channels are layered on top by priority.

## Quick start

Every call must be made on the render thread while level rendering is in progress, for example from Fabric's `LevelRenderEvents` or NeoForge's render events (see "Loaders" above).

### 1. Drawing (managed context)

```java
LevelRenderEvents.END_MAIN.register(context ->
    Exit.draw(context.submitNodeCollector(), exit -> {
        PoseStack pose = exit.poseStack();  
        Vec3 cam = exit.cameraPos();
        pose.translate(x - cam.x, y - cam.y, z - cam.z);
        exit.collector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, buffer) -> { ... });
    }));
```

The lambda runs at once. What it submits joins the channel's batch and is drawn at the end of level rendering, together with the batch (see "Order").
The first argument is the pass-through collector (it may be `null`). When nothing is routed (for example, no shader pack), drawing goes straight to it.
> `draw()` and `batch()` draw at the same time. `draw()` only gives you a managed context (the camera and a camera-relative `PoseStack`). To open your own `RenderPass`, use a portal.

| Method | Meaning |
|---|---|
| `collector()` | The `SubmitNodeCollector` you submit drawings to |
| `poseStack()` | A `PoseStack` at the camera position (camera-relative, no rotation) |
| `cameraPos()` / `camera()` | The camera position / this frame's `CameraRenderState` |
| `partialTick()` | The camera entity's partial tick for this frame |
| `modelView()` | The view rotation matrix, the model-view EXIT uses when drawing submits |
| `routed()` | Whether this drawing bypassed the shader pack |

### 2. Portal (for code that has its own render context)

```java
channel.portal(() -> {    // or on the main channel: Exit.portal(...)
    myRenderer.render(Minecraft.getInstance().gameRenderer.mainRenderTarget());  // render passes opened on the main target draw into the channel target automatically
    RenderTarget target = channel.redirect(fb);  // other targets need redirecting yourself
    ...
});

// Set the three Iris switches covered by PortalOverrides. The original values are restored when the portal ends
Exit.portal(PortalOverrides.DEFAULT.withRenderingLevel(PortalOverrides.Switch.OFF), () -> { ... });
```

`portal` runs its lambda at once. While the lambda runs, Iris's switches are set according to the `PortalOverrides`, and they are restored when it returns (or throws).

| Switch | Default | Meaning |
|---|---|---|
| `bypass` | `ON` | Iris's `ImmediateState.bypass`: don't replace the vanilla pipeline with the shader pack's programs |
| `skipExtension` | `ON` | Iris's `ImmediateState.skipExtension`, per thread. Vertex buffers built now keep the vanilla vertex format, without widening |
| `renderingLevel` | `KEEP` | Iris's `ImmediateState.isRenderingLevel`: while it is on, Iris widens the vertex format the submit pipeline writes |

**Notes**

* **Portal output behaviour:** 26.3 has no global output-target override (the `RenderSystem.outputColorTextureOverride` of 1.21.6–26.2 has been removed). EXIT uses a mixin to imitate it. While a portal is routing, **newly opened** render passes that use the main target as a colour or depth attachment are switched to the channel target, and their render area is limited to the channel target's size, to work with Super Resolution. So code that opens its own render pass on the main target needs no change. (How routing works differs by version. See ChangeLog: before 1.21.5 it binds the channel's target and makes `getMainRenderTarget()` return it.) These cases are not routed automatically, so use `channel.redirect(your target)` for them (or the static `Exit.redirect(original)` when you have no channel object):
  * drawing into a target other than the main target;
  * drawing into a render pass that was already open before the portal began, for example one an event handed to you, because its attachments were fixed when the pass opened;
  * clears and copies of the main target (`clear*`, `copyTextureToTexture`).

  The replacement is global: during a portal, any render pass that any mod opens on the main target is routed.
* **Gizmos (1.21.11 and later):** during portal routing, gizmos added on the render thread with vanilla `Gizmos` (`Gizmos.cuboid`, `Gizmos.line` and so on) go into that channel. They are drawn with the channel's batch at the end of level rendering, into the channel target, instead of by vanilla. Persistence, fading and always-on-top still apply.
* **Only valid inside the interval:** `redirect` can only be called inside a portal (or a `draw` lambda). Calling it outside the interval, off the render thread, or inside another channel's portal throws `IllegalStateException`. Code that may run both inside and outside an interval should check `Exit.isRouting()` or `Exit.inPortal()` first.
* **When routing happens:** on the render thread, with a shader pack on, outside the shadow pass and while the level renders, a new render pass on the main target is switched to the channel target, and `redirect` returns the channel target. Otherwise everything stays as it is. The switches are set whenever a shader pack is on and it is not the shadow pass (also outside level rendering). In the shadow pass the lambda does not run.
* **Nesting:** portals can nest, but only on one channel at a time. Drawings inside them (which must be on the same channel) keep the switches the portal set.
* **Other threads (for things like compiling VBOs):** calling `portal` off the render thread only sets the **current thread's** `skipExtension`. `bypass` and `renderingLevel` are global render state, so they are not set. `redirect` is not available either. If you only want to turn off the format widening, `Exit.plainFormat(Runnable)` is probably the better choice.

```java
// Build your own vertex buffer on a worker thread (later drawn with vanilla pipelines, routed)
Exit.plainFormat(() -> buildMyVbo());
```

### 3. Batch

```java
LevelRenderEvents.COLLECT_SUBMITS.register(context ->
    myRenderer.submit(Exit.batch(context.submitNodeCollector())));
```

`batch()` returns a `SubmitNodeCollector`. What is submitted to it during the frame is drawn into the channel target in one go at the end of level rendering, together with what `draw` recorded: all solid entities first, then all translucent ones. To draw earlier (for example, in a portal before translucent drawing), call `flushBatch()`, and make sure you know what you are doing. Don't reuse the collector: get a new one every frame.

## Channels

`Exit.draw/portal/batch` use the shared **main channel** (`emergency_exit:main`, default options). A mod that needs other options declares its own channel:

```java
ExitChannel xray = Exit.channel(Identifier.fromNamespaceAndPath("mymod", "xray"),
    ExitOptions.builder()
        .occludedByWorld(false)                     // show through the world
        .hideUnderHand(true)                        // keep the hand in front
        .dimBehindTranslucent(0.5F)                 // brightness behind translucent surfaces, 1 = not dimmed
        .priority(10)                               // merge order (larger merges later)
        .blendWithChannels(true)                    // blend with other channels by depth
        .wantsFrame(() -> myMod.enabled())          // whether it draws this frame
        .build());
xray.draw(...); xray.portal(...); xray.redirect(fb); xray.batch(); xray.flushBatch();
```

* Calling it again with the same id returns the same channel. With different options it throws (looking for trouble, are we?).
* Drawings in the same channel share one target and hide each other by depth.
* **Channel blending** (`blendWithChannels`, on by default): when two or more blending channels drew something this frame, EXIT joins them one by one, in merge order, into an accumulated layer. Per pixel it compares depths: a nearer channel is drawn over the accumulated content, a farther one underneath (both blended with premultiplied alpha). The hand is dropped, and dimming behind translucent surfaces is applied as each channel joins. The layer is then merged onto the frame as one layer. Priority only decides the order when depths are equal.
  * The accumulated layer keeps one depth per pixel. Two channels blend exactly. Three or more overlapping at the same pixel is approximate.
  * Translucent render types don't write depth. For EXIT's own drawings (drawings, batches, and submits rendered through EXIT inside a portal), EXIT draws the translucent submits a second time, using depth-only copies of their pipelines, into a depth target of that channel. Before merging, the nearer of the two depths becomes the channel's depth, so translucent content also sorts correctly against other channels. Translucent content in render passes you open yourself inside a portal still has no depth of its own. When it is compared, it uses the seeded scene depth and counts as very far away.
  * Channels with `blendWithChannels` turned off don't take part in blending. They are drawn over the accumulated layer afterwards, by priority, then by creation order (for example, a marker that should always show on top).
* Each channel takes one scene-sized RGBA8 + D32 target. Once it draws translucent submits, it takes one more D32 target. Blending two or more channels takes two more scene-sized layers.

| Option | Default | Meaning |
|---|---|---|
| `occludedByWorld` | `true` | The world (before translucent surfaces) occludes the channel's content |
| `hideUnderHand` | `true` | The first-person hand stays in front |
| `dimBehindTranslucent` | `0.5` | Brightness behind the scene's translucent surfaces (0..1) |
| `priority` | `0` | Merge order. Between blending channels it only matters when depths are equal |
| `blendWithChannels` | `true` | Blend by depth with the other blending channels. If off, the channel is laid over the blend result by priority |
| `wantsFrame` | `null` | Asked before the scene depth copy whether this frame will draw. `null` means "it drew last frame" |

> **Tip:** `occludedByWorld(false)` only lets a channel show through the **world**. It does not show through other EXIT channels. As long as `blendWithChannels` stays on (the default), it is still compared by depth with the other blending channels, and when it is behind them it is still covered. This is what happens to the blue block in the debug shapes when it sits behind the green one. To keep it on top of all EXIT content, also set `blendWithChannels(false)`, and it will be laid over the blend result according to `priority`.

## Rules and limits

* **Thread and timing.** Except for `portal` (off the render thread, only the format widening is turned off) and `plainFormat`, these can only be called on the render thread, between the start and the end of `LevelRenderer.render`. Outside that window a drawing is dropped (logged once), and a portal doesn't route. A channel target is seeded from the scene depth the first time the frame uses it. So routed portals (and `redirect`) must run after the level's render passes have started, and outside any open `RenderPass`, as Litematica does. Not earlier.
* **Nesting.** Portals can nest, but only on one channel at a time. A drawing called inside a drawing shares the outer collector. Opening another channel inside a channel's portal throws an exception.
* **Shadow pass.** EXIT draws nothing into the shader pack's shadow map, so content EXIT draws casts no shadow. In the shadow pass the `draw` lambda and the `portal` interval don't run, and the collector returned by `batch` drops whatever it receives (`plainFormat` is not affected). Fabric's `LevelRenderEvents` don't fire in Iris's shadow pass anyway.
* **Order.** A `draw` lambda runs at once, but what it submits joins the channel's batch wherever it is called, whether that is inside the main render pass or before level render passes start. The batch is drawn at the end of level rendering (or at `flushBatch()`) in one frame: all solid entities first, then all translucents, so a solid never covers a nearer translucent. Portals draw immediately, before the batch. In a portal, call `flushBatch()` before drawing translucent things, or the batch's solids will cover them. To draw with your own render passes, use a portal.
* **Portals and batches.** A batch (and `draw`) hands EXIT submits. EXIT keeps them and decides the order when it draws them, so it can draw all solids before all translucents, and it records the depth of the translucents. A portal wraps someone else's code: that code opens its own passes, picks its own pipelines, and draws into the channel target at once. EXIT only sees the finished pixels.
* **Why a portal's translucent pixels cannot blend with a later batch.** Once a translucent surface is drawn into the target, it is already mixed with the colour behind it. The pixel holds only the result, and the surface's own colour and coverage are gone. If a batch's solid submit should go behind it, the colour behind would have to be replaced, which is no longer possible. Depth doesn't help either: translucents write none, and even if they did, a pixel holds one colour. So the batch's solids can only cover the translucent surface, or be drawn before it. Call `flushBatch()` in the portal before drawing translucent things (for Litematica, after the opaque terrain and before the translucent terrain).
* **Blending.** When merging, the channel's colour is treated as premultiplied alpha (vanilla's translucent pipelines work).
* **Your own vertex buffers.** Iris widens vertex formats when it builds them. Build them inside `Exit.plainFormat(...)` (on any thread, since compiling usually happens on workers) when you draw them routed with vanilla pipelines.
* **Scene depth copies.** When a channel wants the frame (its `wantsFrame`, or by default "it drew in the previous frame"), the scene depth is copied twice per frame: before the hand, and before translucents. A channel that draws without a copy still works. It uses the main depth at that moment for occlusion in that frame.
* **API stability.** `ml.mypals.exit.api` is the API; `impl`, `compat` and `mixin` are not. The API uses Minecraft types (`SubmitNodeCollector`, `PoseStack`, `RenderTarget`), so it is only stable within one Minecraft version. Where an older version lacks a type, that branch uses a type with the same job (see the Versions table).

Other utility methods: `Exit.isActive()` (whether routing would happen right now: a shader pack is on, not in the shadow pass, level rendering), `Exit.isRouting()` (whether a routing portal is active right now), `Exit.inPortal()` (whether we are inside a portal right now), and `Exit.plainFormat(Runnable)`.

## Debugging

These are debugging features, to help you confirm whether something wrong is happening.

* `/emergency_exit debug <color|target_depth|scene_depth|main_depth|translucent_depth|off> [channel]`: shows a channel's merged buffer (default: the main channel) instead of the frame (requires a shader pack).
  * `color`: the channel target's colour (grey background)
  * `target_depth`: the channel target's depth (seeded from the scene depth)
  * `scene_depth`: the scene depth before translucent surfaces (the seed source)
  * `main_depth`: the final main depth
  * `translucent_depth`: the depth of the translucent surfaces only
* `/emergency_exit debug shapes`: spawns three test blocks 5 blocks in front of you. Run it again to turn them off:
  * Red (right): a translucent drawing on the main channel (Fabric's `AFTER_SOLID_FEATURES`, NeoForge's `AfterOpaqueFeatures`), recorded into the batch. From the side it is in front of the green one, and green shows faintly through it.
  * Green (centre): the main channel's batch (Fabric's `COLLECT_SUBMITS`, NeoForge's `SubmitCustomGeometryEvent`), opaque, writes depth.
  * Yellow (centre, behind and to the upper right of green): a drawing on another channel (at the end of level rendering), opaque, writes depth. It is blended with the main channel by depth, and green is in front where they overlap.
  * Blue (left): a pass opened directly on the main target inside a portal (also drawn at the end of level rendering, without a `redirect` call). The portal moves it automatically into a separate channel that shows through the world. The cyan wireframe outside it is a gizmo added in the same portal.

## Development

The project has three subprojects. `common` holds all loader-independent code (API, rendering and mixins). `fabric` and `neoforge` hold the entry points, metadata, command registration and the event hooks for the debug blocks. The structure comes from MultiLoader-Template. Loader-specific features are provided through the `Platform` service.

* `./gradlew build` builds both loaders' jars (`fabric/build/libs`, `neoforge/build/libs`).
* `./gradlew :fabric:runSmokeClient` and `./gradlew :neoforge:runSmokeClient` create a world, open the `/emergency_exit debug shapes` test blocks, and run one pass without shaders. Then they run one pass for each shader pack in the run's `shaderpacks` folder (switched through Iris, so put BSL, Kappa, Sundial Lite and so on there). Each pass takes screenshots (`screenshots/emergency_exit-smoke-<version>-<loader>-<pack>-<view>.png`) and checks EXIT's counters, then the game quits. The log ends with `RESULT: OK`. `-Demergency_exit.smoke.packs=none,BSL.zip` picks which passes run. The run directories are `run/fabric` and `run/neoforge` (on the port branches, `run/<loader>-<version>`).
* `./gradlew :fabric:runAuditClient` checks that every mixin applies (Fabric only).
* `./gradlew :fabric:runSmokeClient -Pexit.superResolution` (on the versions where development runs can load SR, see ChangeLog) does the same with the Super Resolution mod loaded (only in development runs). It checks the stretched merge in the passes for packs SR supports (for example Sundial Lite; SR stays off with BSL), and sets `upscale_algo = "fsr2"` in `run/fabric/config/super_resolution/config.toml` (the other algorithms don't upscale properly in this environment). The smoke test also saves `*-presented.png`, the window's front buffer, which is what the player actually sees. When SR is in use, the main target is not the final picture.
* `./gradlew publishToMavenLocal` publishes both jars to the local Maven repository for other mods' development setups.
