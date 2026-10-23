# How It Works
[https://shader-escape.github.io/emergency-exit/render-scale/#bsl](https://shader-escape.github.io/emergency-exit/render-scale/#bsl)

# EXIT (Emergency Exit)

[中文](README.zh-CN.md)

## What is this

Emergency Exit is a rendering library. It gives mods an easy way to get their custom rendering out of the grip of shader packs loaded by Iris.

The library starts from Minecraft 26.3 and is ported down to the common major versions as far back as 1.16.5, on both Fabric and NeoForge.

## Adding it to your mod

[JitPack](https://jitpack.io/#Shader-Escape/emergency-exit) builds EXIT from this repository. Add its repository and depend on the artifact for your loader and Minecraft version:

```groovy
repositories {
    exclusiveContent {
        forRepository { maven { url = 'https://jitpack.io' } }
        filter { includeGroup 'com.github.Shader-Escape.emergency-exit' }
    }
}

dependencies {
    // Fabric, Minecraft 1.21.11 and older (Loom with remapping)
    modImplementation('com.github.Shader-Escape.emergency-exit:emergency_exit-fabric:0.1.4-1.21.11') { transitive = false }
    include('com.github.Shader-Escape.emergency-exit:emergency_exit-fabric:0.1.4-1.21.11')

    // Fabric, Minecraft 26.1 and newer (Loom without remapping)
    implementation('com.github.Shader-Escape.emergency-exit:emergency_exit-fabric:0.1.4-26.3') { transitive = false }
    include('com.github.Shader-Escape.emergency-exit:emergency_exit-fabric:0.1.4-26.3')

    // NeoForge, Minecraft 1.21.1 and newer
    implementation('com.github.Shader-Escape.emergency-exit:emergency_exit-neoforge:0.1.4-26.3')
}
```

`include` nests EXIT in your jar, so players don't install it separately. `transitive = false` keeps EXIT's own Fabric Loader and Fabric API versions out of your build. On NeoForge, nest it with `jarJar` or ask players to install EXIT.

The version is `<EXIT version>-<Minecraft version>`, one per Minecraft version (JitPack does not accept the `+` that the mod itself uses, as in `0.1.4+1.21.11`):

| Minecraft | Version | Artifacts |
|---|---|---|
| 1.16.5, 1.17.1, 1.18.2, 1.19.3, 1.20, 1.20.1 | `0.1.4-<mc>` | `emergency_exit-fabric` |
| 1.20.2, 1.20.4, 1.20.6 | `0.1.4-<mc>` | `emergency_exit-fabric`, `emergency_exit-common` |
| 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2, 26.3 | `0.1.4-<mc>` | `emergency_exit-fabric`, `emergency_exit-neoforge`, `emergency_exit-common` |

The Fabric and NeoForge jars carry the whole library. `emergency_exit-common` is for the common module of a multi-loader project. For the newest commit of a Minecraft version, use `port~<mc>-SNAPSHOT` (built from the `port/<mc>` branch), during development only.

## Calling the API

Every API must be called on the render thread while the level is rendering. The table lists the events you can use on 26.3. For other versions, see **Usage** under that version in [ChangeLog.md](ChangeLog.md).

| API | Fabric | NeoForge |
|---|---|---|
| `batch` | `LevelRenderEvents.COLLECT_SUBMITS` | `SubmitCustomGeometryEvent` |
| `draw` | `LevelRenderEvents.AFTER_SOLID_FEATURES`, `END_MAIN`, etc. | `RenderLevelStageEvent.AfterOpaqueFeatures`, etc. |
| `portal` | `LevelRenderEvents.END_MAIN` | No suitable event |

> NeoForge's `RenderLevelStageEvent.AfterLevel` comes too late. Anything drawn in it is dropped.

## draw: draw something somewhere in the world

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

Good for:
- When you have no suitable renderer at hand and need to improvise.
- Very simple custom rendering such as selection boxes, highlighted blocks, path lines and markers.

> Note the `SubmitNodeCollector` passed in the example below. When no shader pack is on, your drawing is handed to it, so it renders in the same order and at the same time as **vanilla** would. If you don't pass one, your drawing still renders, but it is put off until just before the level rendering ends, and its order and timing are not guaranteed.

```java
LevelRenderEvents.AFTER_SOLID_FEATURES.register(context ->
    Exit.draw(context.submitNodeCollector(), exit -> 
    { /* draw your stuff */ }
 ));
```

`exit` provides:

| Method | Returns |
|---|---|
| `collector()` | Where to submit |
| `poseStack()` | A `PoseStack` with its origin at the camera and no rotation. Translate by `world position - cameraPos()` to reach the target |
| `cameraPos()` | The camera position |
| `camera()` | This frame's `CameraRenderState` |
| `partialTick()` | The camera entity's partial tick this frame, for interpolation |
| `modelView()` | The view rotation matrix |
| `routed()` | Whether this drawing bypassed the shader pack |

## batch: move existing submit code over as a whole

```java
LevelRenderEvents.COLLECT_SUBMITS.register(context ->
    myRenderer.submit(Exit.batch(context.submitNodeCollector())));
```

Good for:
- You already have a renderer that submits to a `SubmitNodeCollector`, and you only want its output to bypass the shader pack.
- You want to add shader protection to an existing renderer in an existing mod.

As for `draw` and `batch` themselves, what they draw goes into the same batch and is drawn at the same time. The only difference is that `draw` gives you some extra context.

## portal: code that opens its own RenderPass

```java
LevelRenderEvents.END_MAIN.register(context ->
    Exit.portal(() -> {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        mySchematicRenderer.render(main);  
        // opens RenderPasses on main inside, with its own pipelines and vertex buffers
    }));
```

Good for:
- Rendering code that, for one reason or another, shouldn't go through `SubmitNodeCollector`: schematic rendering (Litematica-style), terrain-like rendering that compiles its own vertex buffers, custom pipelines and shaders.

Shapes added inside a `portal` with vanilla `Gizmos` (`Gizmos.cuboid`, `Gizmos.line` and so on, available since 1.21.11) also bypass the shader pack. Their duration, fade-out and always-on-top settings still work.

```java
Exit.portal(() -> Gizmos.cuboid(box, GizmoStyle.stroke(0xFF30FFFF, 3)));
```

> Note: the lambda runs right away. While a portal is open, every RenderPass newly opened on the main target by **any mod** is moved, not just yours.

### Translucency

In most cases a portal draws on the spot, while the batch (`draw`, `batch`) is only drawn when the level rendering ends. Left alone, the solid content in the batch then covers translucent content drawn in the portal, even when it is farther away. If you need to (and know what you're doing), call `flushBatch()` in the portal before drawing translucent content, so the collected batch is drawn first:

```java
Exit.portal(() -> {
    renderSolid(main);
    Exit.main().flushBatch();     // draw what's already in the batch now
    renderTranslucent(main);
});
```

`flushBatch()` does nothing inside an open RenderPass.

### Adjusting Iris's switches

A portal temporarily sets three Iris switches and restores them when it ends (or throws). You usually don't need to change the defaults:

| Switch | Default | When on |
|---|---|---|
| `bypass` | `ON` | The shader pack does not replace vanilla pipelines |
| `skipExtension` | `ON` | Vertex buffers built at this point keep the vanilla vertex format (current thread only) |
| `renderingLevel` | `KEEP` | Iris adds the data the shader pack needs to vertices written by the submit pipeline |

```java
Exit.portal(
   PortalOverrides.DEFAULT.withRenderingLevel(
  PortalOverrides.Switch.OFF), () -> { ... }
);
```

`Switch` has three values: `ON`, `OFF` and `KEEP` (leave it as it is).

## redirect: targets a portal can't reach

A portal only moves "newly opened RenderPasses that draw into the main target". For anything else, swap the target yourself with `redirect`:

- drawing into targets other than the main target;
- clearing or copying the main target (`clear*`, `copyTextureToTexture`, etc.).

```java
Exit.portal(() -> {
    RenderTarget out = Exit.redirect(main);  
    // EXIT's target while bypassing the shader pack, otherwise main itself
    RenderSystem.getDevice().createCommandEncoder()
        .clearDepthTexture(out.getDepthTexture(), 1.0); 
        // clears only the depth of EXIT's target, leaving the real main target alone
    renderOnTop(out);
});
```

Use `channel.redirect(target)` when you can reach the channel object. When you can't, use the static `Exit.redirect(target)`, which picks the channel whose portal is currently open.

`redirect` can only be called inside a portal or a `draw` lambda. Calling it outside them, on another thread, or inside another channel's portal throws `IllegalStateException`. If the same code runs both inside and outside portals, check first:

```java
RenderTarget out = Exit.isRouting() ? Exit.redirect(main) : main;
```

## plainFormat: build vertex buffers on a worker thread

```java
// compile thread
Exit.plainFormat(() -> buildMyVertexBuffer());
```
You may need to build vertex buffers off the render thread for various reasons, such as rendering a large number of blocks. However, Iris adds extra data to the vertex buffers it builds, and drawing them later with vanilla pipelines in a portal goes wrong.
So build your buffers inside `plainFormat` to keep them in the vanilla format. It works on any thread at any time, and also runs during the shadow pass.

> Calling `portal` off the render thread has the same effect, but of the three switches it can only turn off format widening for the current thread. The other switches have no effect, and nothing can be redirected.

### plainUpload: upload them outside a portal

```java
// render thread, e.g. a queue of finished meshes drained each frame
Exit.plainUpload(() -> {
    vertexBuffer.bind();
    vertexBuffer.upload(meshData);
    VertexBuffer.unbind();
});
```
On Minecraft 1.21.1 to 1.21.4, Iris 1.8 also widens the *layout* a vertex buffer gets when it is uploaded (`VertexFormat.setupBufferState`), and the buffer keeps it. A buffer built with `plainFormat` but uploaded with that layout is drawn as scattered triangles while a shader pack is in use.
Portals turn that off themselves while they turn format widening off, so uploads inside a portal are fine. Wrap uploads that happen anywhere else in `plainUpload`, on the render thread. Only buffers built with `plainFormat` belong in it: the others are in Iris's widened format and need its layout.
On every other Minecraft version `plainUpload` just runs the code, so the same calls work everywhere.

## Channels: different display for different content

`Exit.draw/batch/portal` above all use the shared main channel. For effects such as seeing through blocks, you can declare your own channel:

```java
ExitChannel xray = Exit.channel(Identifier.fromNamespaceAndPath("mymod", "xray"),
    ExitOptions.builder()
        .occludedByWorld(false)    // show through blocks
        .build());

xray.draw(exit -> { ... });
xray.batch(collector);
xray.portal(() -> { ... });
xray.redirect(target);
xray.flushBatch();
```

Some common setups:

```java
// See-through: highlight ores, entities, hidden blocks
ExitOptions.builder().occludedByWorld(false).build();

// Always on top: markers nothing can cover, including other EXIT channels
ExitOptions.builder().occludedByWorld(false).blendWithChannels(false).priority(100).build();

// Only work while the feature is on, skipping needless cost
ExitOptions.builder().wantsFrame(() -> MyConfig.enabled).build();
```

| Option | Default | Description |
|---|---|---|
| `occludedByWorld` | `true` | Hidden by blocks. When `false`, shows through blocks |
| `hideUnderHand` | `true` | The first-person hand stays in front |
| `dimBehindTranslucent` | `0.5` | Brightness behind translucent blocks, 0 to 1. `1` means no dimming |
| `priority` | `0` | Higher numbers are drawn on top. Between two channels that both have `blendWithChannels` on, it only matters at equal depth |
| `blendWithChannels` | `true` | Hide and be hidden by other channels by distance. When `false`, drawn over all blending channels |
| `wantsFrame` | `null` | Whether it will draw this frame. `null` means "it drew last frame, so it will" |

How channels cover each other:

* Content in the same channel covers each other by distance.
* Channels with `blendWithChannels` on also cover each other by distance, nearer in front. `occludedByWorld(false)` only shows through blocks, not through nearer content in other channels.
* Channels with `blendWithChannels` off are drawn over all blending channels, ordered among themselves by `priority`, with the earlier-created one below on a tie.

Calling `Exit.channel` a second time with the same id returns the same channel. Asking with different options than the first time counts as looking for trouble*.

## Status queries

| Method | `true` when |
|---|---|
| `Exit.isActive()` | What's drawn now bypasses the shader pack (shader pack on, level rendering, not in the shadow pass) |
| `Exit.isRouting()` | Inside a portal that is currently bypassing the shader pack |
| `Exit.inPortal()` | Inside a portal or a `draw` lambda |

Off the render thread, `isRouting()` and `inPortal()` are always `false`.

## Limitations

* **When to call.** Only on the render thread, while the level is rendering (except `plainFormat`, `portal` off the render thread, and `plainUpload`, which only needs the render thread). Outside that, `draw` and `batch` content is dropped (with one warning in the log), and a portal still runs but doesn't bypass the shader pack.
* **Where to open a portal.** A portal must be called **after the level has started rendering**, for example in Fabric's `END_MAIN`, and not inside an open RenderPass. A RenderPass that was already open before the portal started is not moved, and what's drawn in it is still processed by the shader pack.
* **No shadows.** While the shader pack draws shadows, `draw` and `portal` lambdas don't run, and `batch` content is dropped.
* **Translucency in a portal.** See "Translucency" above first. Also, translucent content you draw in your own RenderPass inside a portal is treated as very far away when compared with other channels, so it is easily covered. Translucent content submitted through `draw` or `batch` doesn't have this problem.
* **More than two channels.** When three or more blending channels interleave front to back on the same pixel, occlusion may be wrong. Two channels are always correct.
* **Nesting.** Portals can nest, and `draw` can call `draw` again, but only on one channel at a time. Opening another channel inside one channel's portal throws.
* **Video memory.** Each channel takes an extra color + depth texture the size of the screen, plus one more depth texture once it has drawn translucent content. With two or more blending channels, two more on top. Don't create a channel per object.
* **`wantsFrame` returned `false` but you drew anyway.** The content still shows, but occlusion by blocks may be off for that frame.
* **Color.** Content is composited onto the frame as premultiplied alpha. Vanilla translucent render types are fine. Custom pipelines must output premultiplied-alpha color.
* **The API is only stable within one Minecraft version.** It uses Minecraft types (`SubmitNodeCollector`, `PoseStack`, `RenderTarget`); on older versions some are replaced by types that serve the same purpose, see the ChangeLog. Only use the `ml.mypals.exit.api` package; `impl`, `compat` and `mixin` may change at any time.

## Debugging

* `/emergency_exit debug shapes`: spawns four test blocks 5 blocks in front of your view. Run it again to remove them.
  * Red (right): a translucent block drawn with `draw` on the main channel.
  * Green (middle): a solid block from the main channel's `batch`.
  * Yellow (behind green, up and to the right): a solid block drawn with `draw` on another channel. It and the green block cover each other by distance.
  * Blue (left): a block drawn by opening a RenderPass directly on the main target inside a see-through channel's `portal`. The cyan outline around it is a Gizmo added in the same portal.
* `/emergency_exit debug <color|target_depth|scene_depth|main_depth|translucent_depth|off> [channel]`: needs a shader pack on. Replaces the screen with a buffer of a channel (the main channel by default), so you can see whether your content was actually drawn and what is covering it. `color` is the color drawn in the channel, `off` restores the normal view, and the others are various depths.

## Contributors

* [hotpad100c](https://github.com/hotpad100c) (Ryan100C): author.
* [wwwzzzxxx](https://github.com/wwwzzzxxx): ports to Minecraft 1.16.5 through 1.20.6.
