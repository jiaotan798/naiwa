# 奶蛙六端构建

请克隆完整仓库。各端共用主工程的玩法源码和资源，单独取一个端口目录无法构建。把 Node.js 22 或更新版本加入 `PATH`，再按表设置 `JAVA_HOME`。首次构建需要联网下载 Gradle 和依赖。 [模型采样](../docs/模型接入说明.md)

| Minecraft | 加载器 | 构建 JDK | 游戏 Java | Fabric API |
| --- | --- | ---: | ---: | --- |
| 1.21.1 | NeoForge 21.1.252 | 21 | 21 | — |
| 1.20.1 | Forge 47.4.23 | 17 | 17 | — |
| 1.20.1 | Fabric Loader 0.19.4 | 21 | 17 | 0.92.9+1.20.1 |
| 1.21.1 | Fabric Loader 0.19.4 | 21 | 21 | 0.116.7+1.21.1 |
| 26.1.2 | NeoForge 26.1.2.94 | 25 | 25 | — |
| 26.1.2 | Fabric Loader 0.19.4 | 25 | 25 | 0.149.0+26.1.2 |

从仓库根目录运行对应命令。Fabric 的 `build` 会运行服务端 GameTest，其中 Fabric 1.20.1 的测试还需要 Gradle 找到 JDK 17。必要时用 `-Porg.gradle.java.installations.paths=<JDK 17 路径>` 指定。

```powershell
.\mod\gradlew.bat -p mod build runGameTestServer
.\ports\forge-1.20.1\gradlew.bat -p ports/forge-1.20.1 build runGameTestServer -PgameTests
.\mod\gradlew.bat -p ports/fabric-1.20.1 build
.\mod\gradlew.bat -p ports/fabric-1.21.1 build
.\mod\gradlew.bat -p ports/neoforge-26.1.2 build runGameTestServer -PgameTests
.\mod\gradlew.bat -p ports/fabric-26.1.2 build
```

1.21.1 NeoForge 主端构建产物是 `mod/build/libs/naiwa-0.5.0.jar`。其余五端在各自的 `build/libs/` 下生成带加载器和 Minecraft 版本的 JAR。Release 下载页为方便选包，六个文件名都带加载器和 Minecraft 版本。Forge 1.20.1 的成品已内嵌 MixinExtras Forge 0.4.1。

[0.5.0 验证](../validation/0.5.0.md) · [0.4.9 历史验证](https://github.com/jiaotan798/naiwa/blob/v0.4.9/validation/0.4.9.md) · [模组设置](../docs/模组设置.md)
