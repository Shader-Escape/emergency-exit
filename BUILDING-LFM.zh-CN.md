# 与 LitematicaFastMove 配对打包

本分支输出 EXIT 0.1.1+1.21.1，修复 Iris 1.8 普通顶点数据与 VAO 扩展布局不一致的问题。
`plainFormat` 在渲染线程保存/关闭/恢复 `renderWithExtendedVertexFormat`；工作线程只改变线程局部的扩展开关。
portal 也保存并恢复该状态，嵌套调用保持进入前的值。调用方必须在渲染线程绑定、上传和解绑 VertexBuffer。

使用 JDK 25 运行 Gradle Wrapper，并提供 Java 21 toolchain：

```powershell
.\gradlew.bat :fabric:publishToMavenLocal --console=plain
```

LFM 的 `port/1.21.1` 使用 `exit_version=0.1.1+1.21.1`，通过本地 Maven 获取并将 Fabric jar 内嵌。
发布本地 Maven 后，在 LFM 分支执行 `gradlew.bat build` 和 `python scripts/verify-package.py`。
不要将 `common`、sources、dev jar 当作给玩家使用的 Fabric jar，也不要把不同 MC 版本的本地 Maven 依赖混用。

本轮实际普通 Fabric 客户端已检查关闭光影和开启 BSL 的多角度、覆盖层及移动画面；这不覆盖全部第三方模组/光影组合。
完整配对步骤及成品核验见 [LFM 打包说明](https://github.com/hotpad100c/litematicafastmove/blob/port/1.21.1/BUILDING.zh-CN.md)。
