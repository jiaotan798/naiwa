# 代码与素材来源

我是奶蛙的作者，版权署名为 jiaotan798。项目的原创内容及玩家使用范围见两份许可文件。 [LICENSE](LICENSE) · [玩家使用授权](PLAYER_USAGE.md)

## 模型、贴图和音效

我用 AI 工具制作了项目美术，游戏里的笑声剪自我提供的音轨。

| 内容 | 文件与用途 |
| --- | --- |
| 可编辑模型和修形法线 | [模型源文件](assets/source/README.md)随源码提供。52 根骨骼、一张贴图和 11 段动画用于生成游戏资源。 |
| 游戏模型和实体贴图 | `assets/runtime/` 中的高模、低模和贴图进入成品。模型摘要和姿态对照供构建校验使用。 |
| 盾牌源图 | [naiwa-shield-v1.png](assets/shield/naiwa-shield-v1.png)用于维护盾面和把手的贴图。 |
| 物品与状态图标 | 由 `scripts/make-icons.mjs` 中的像素稿生成。 |
| 本体与盾牌笑声 | `laugh_full.ogg` 和 `laugh_short.ogg` 随成品分发。 [音效维护](docs/模型接入说明.md#音效) |

素材的路径、大小和 SHA256 集中记录在[素材指纹](third-party/asset-sha256.json)。

## 网易版参考素材

望远镜图片 `textures/gui/scope_portrait.png` 和笑气粒子贴图 `textures/particle/gas_face.png` 来自网易版参考包 `naiwa-0.1.0.zip`，以原始图片字节随成品分发。烟雾使用 Minecraft 自带的 `generic_7`。第三方素材的权利归相应权利人。

## 上游工具和依赖

主工程使用 Gradle 9.2.1 Wrapper，Forge 1.20.1 使用 Gradle 8.8 Wrapper。Gradle Wrapper 的原始 Apache-2.0 许可随源码保留。 [许可原文](third-party/gradle-wrapper-LICENSE.txt)

构建所需的加载器和插件由 Gradle 按工程配置获取。Forge 1.20.1 成品内嵌 MixinExtras Forge 0.4.1，相关上游声明随依赖保留。 [构建环境](ports/README.md)
