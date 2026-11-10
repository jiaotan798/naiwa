# 奶蛙多加载器构建

各端共用 [1.21.1 主工程](../README.md#构建源码)的玩法源码、模型、音效和配方。请克隆完整仓库。首次构建需要联网，安装 Node.js 22 或更新版本并加入 `PATH`，然后将 `JAVA_HOME` 指向目标的构建 JDK。需要重新采样模型时，见[模型接入说明](../docs/模型接入说明.md)。

| Minecraft | 加载器 | 构建 JDK / 游戏 Java | Fabric API |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.252 | 21 / 21 | — |
| 1.20.1 | Forge 47.4.23 | 17 / 17 | — |
| 1.20.1 | Fabric Loader 0.19.4 | 21 / 17 | 0.92.9+1.20.1 |
| 1.21.1 | Fabric Loader 0.19.4 | 21 / 21 | 0.116.7+1.21.1 |
| 26.1.2 | NeoForge 26.1.2.94 | 25 / 25 | — |
| 26.1.2 | Fabric Loader 0.19.4 | 25 / 25 | 0.149.0+26.1.2 |

从仓库根目录按目标选择命令。Fabric 1.20.1 的服务端测试还需要 Gradle 能找到 JDK 17，可用 `-Porg.gradle.java.installations.paths=<JDK 17 路径>` 指定。

```powershell
.\mod\gradlew.bat -p mod build runGameTestServer
.\ports\forge-1.20.1\gradlew.bat -p ports/forge-1.20.1 build runGameTestServer -PgameTests
.\mod\gradlew.bat -p ports/fabric-1.20.1 build
.\mod\gradlew.bat -p ports/fabric-1.21.1 build
.\mod\gradlew.bat -p ports/neoforge-26.1.2 build runGameTestServer -PgameTests
.\mod\gradlew.bat -p ports/fabric-26.1.2 build
```

Fabric 的 `build` 包含服务端 GameTest。成品位于目标的 `build/libs/`，选择文件名带目标加载器和 Minecraft 版本的 JAR。联机两端安装同一目标的奶蛙，Fabric 两端还须安装对应 Fabric API。Forge 1.20.1 使用自己的 Gradle 8.8 Wrapper，并随成品嵌入 MixinExtras Forge 0.4.1；上游声明随该依赖保留。

Fabric 的三项整蛊设置保存在世界的 `serverconfig/naiwa-server.json`，修改后重新进入世界或重启服务器生效。切换加载器前应备份存档。开发阶段服务端测试覆盖格挡和破盾冷却、图腾保命与合成返瓶、笑气限制及受伤解除；后台客户端检查覆盖资源加载与泛光初始化。十种整蛊、完整飞升、双客户端联机与 Iris 组合仍待逐端实机复验。

## 源码验证

按版本查看 [0.5.0 验证记录](../validation/0.5.0.md)及[历史验证](../validation/0.4.9.md)。
