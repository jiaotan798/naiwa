# 代码与素材来源说明

作者及版权署名：**jiaotan798**，不列共创者。主许可为 [All Rights Reserved](LICENSE)，具体玩家例外见 [PLAYER_USAGE](PLAYER_USAGE.md)。

## 项目素材

项目美术由作者使用 AI 工具制作，游戏内笑声由作者提供的音轨剪辑而来。

当前源码只保留构建和修改所需的素材：

| 内容 | 来源及识别依据 | 本次范围 |
| --- | --- | --- |
| 可编辑模型、内嵌贴图和动画 | [当前源模型和修形法线](assets/source/README.md)，52 骨、一张贴图、11 段动画 | 原模型和法线随源码，派生运行资源随源码及成品 |
| 采样网格、动画和实体贴图 | `assets/runtime/`，包含高模、低模和实体贴图 | 两个 `.nwm` 和一张实体贴图进入成品，校验 JSON 随源码 |
| 当前盾牌源贴图 | `assets/shield/naiwa-shield-v1.png`；SHA256 `d7eef39dab1048d7c1891ea34f6f2d7ac891cbd44179e80155754d91571dacb3` | 源图供修改及模型检查使用；相同正式贴图进入成品 |
| 物品与状态图标 | `scripts/make-icons.mjs` 中固定像素稿生成 | 运行图像随源码及成品 |
| 本体与盾牌笑声音效 | 由作者提供的音轨剪辑 | `laugh_full.ogg` 和 `laugh_short.ogg` 随成品分发。 [音效说明](docs/音效说明.md) |

保留素材的路径、大小和 SHA256 见[当前素材指纹](third-party/asset-sha256.json)。指纹用于识别文件，不替代许可文字。

## 网易参考与新增生产贴图

望远镜里的奶蛙图片 `textures/gui/scope_portrait.png` 和笑气粒子贴图 `textures/particle/gas_face.png` 来自网易版参考包 `naiwa-0.1.0.zip`，以原始图片字节随成品分发。烟雾使用 Minecraft 内置的 `generic_7` 纹理。第三方素材的权利归相应权利人。

## 上游工具

| 内容 | 来源与版本 | 许可与范围 |
| --- | --- | --- |
| Gradle Wrapper 脚本与 JAR | 工程 Wrapper 配置指向 Gradle 9.2.1；原脚本头与 JAR 内声明保持原样 | 原作者的 Apache-2.0 许可继续有效，[内嵌许可原文](third-party/gradle-wrapper-LICENSE.txt)随源码保留；Wrapper 不进入奶蛙成品 |
| NeoForge、ModDevGradle | NeoForge 21.1.252、插件 2.0.147，由构建配置指定 | 构建从上游仓库获取，不把完整依赖或共享工具提交到本仓库；各自上游声明继续适用，不改为项目 All Rights Reserved |
