# 与 LitematicaFastMove 配对打包

本分支输出 EXIT 0.1.5+1.21.4，保留上游 Iris 1.8 普通顶点数据与 VAO 扩展布局修复，并为修复后的产物提供独立版本号。
`plainFormat` 用于构建普通格式顶点；上传时使用渲染线程专用的 `plainUpload`，后者保存/关闭/恢复 `renderWithExtendedVertexFormat`。工作线程不修改这个全局布局开关。
portal 也保存并恢复该状态，嵌套调用保持进入前的值。调用方必须在渲染线程绑定、上传和解绑 VertexBuffer。

使用 JDK 25 运行 Gradle Wrapper，并提供 Java 21 toolchain：

```powershell
.\gradlew.bat :fabric:publishToMavenLocal --console=plain
```

LFM 的 `port/1.21.4` 使用 `exit_version=0.1.5+1.21.4`，通过本地 Maven 获取并将 Fabric jar 内嵌。
发布本地 Maven 后，在 LFM 分支执行 `gradlew.bat build` 和 `python scripts/verify-package.py`。
不要将 `common`、sources、dev jar 当作给玩家使用的 Fabric jar，也不要把不同 MC 版本的本地 Maven 依赖混用。

成品验证应使用普通 Fabric 客户端，检查关闭光影和开启 BSL 的多角度、覆盖层及移动画面；构建成功不等于覆盖全部第三方模组/光影组合。
完整配对步骤及成品核验见 [LFM 打包说明](https://github.com/hotpad100c/litematicafastmove/blob/port/1.21.4/BUILDING.zh-CN.md)。
