# 代码与素材来源说明

作者及版权署名：**jiaotan798**，不列共创者。主许可为 [All Rights Reserved](LICENSE)，具体玩家例外见 [PLAYER_USAGE](PLAYER_USAGE.md)。

## 项目素材

作者已确认本项目美术由本人使用 AI 生成；游戏实际使用的音效也按作者明确指示保留，不再列为本轮待确认项。本文件记录项目所有者的交付决定，不将其说明改写成已独立核验的“公有领域”或“无主作品”法律结论。

当前源码只保留构建和修改所需的素材：

| 内容 | 来源及识别依据 | 本次范围 |
| --- | --- | --- |
| 可编辑模型、内嵌贴图和动画 | `assets/source/nailong_origin_zero.bbmodel`；SHA256 `530ab823e6f66065e52b68d9d5a24b765640945cac8e6fca12a590098c7291b9`；38 骨、六张贴图、11 段动画 | 原模型随源码；派生运行资源随源码及成品 |
| 采样网格、动画和实体贴图 | `assets/runtime/`，清单核对源文件与产物摘要 | `.nwm` 和六张实体贴图进入成品；校验 JSON 只随源码 |
| 当前盾牌源贴图 | `assets/shield/naiwa-shield-v1.png`；SHA256 `d7eef39dab1048d7c1891ea34f6f2d7ac891cbd44179e80155754d91571dacb3` | 源图供修改及模型检查使用；相同正式贴图进入成品 |
| 物品与状态图标 | `scripts/make-icons.mjs` 中固定像素稿生成 | 运行图像随源码及成品 |
| 本体与盾牌笑声音效 | `mod/src/main/resources/assets/naiwa/sounds/laugh_full.ogg`、`laugh_short.ogg`；由作者提供给项目的音轨处理所得 | 按作者指示保留正式 OGG；技术参数见[音效说明](docs/音效说明.md) |

保留素材的路径、大小和 SHA256 见[当前素材指纹](third-party/asset-sha256.json)。指纹用于识别文件，不替代许可文字。

参考图片、参考视频、原始参考音频、生成提示词及历史素材不随当前快照交付；它们只在原开发项目或本地备份中保留。依赖这些原始媒体的重新截取脚本也不随本快照提供。

## 网易参考与新增生产贴图

0.4.7～0.4.9的视觉与玩法参考来自用户提供的naiwa-0.1.0.zip；较新包SHA256为81a7f1e9d33e23d98337785a55d167807d74a9566ca577230d29d685607ad34f。原参考包、Python脚本与媒体不随源码交付，也未作为指令执行。

实际进入成品的新增图片是 textures/gui/scope_portrait.png 与 textures/particle/gas_face.png，均按用户提供包的原字节保留。烟雾引用Minecraft内置generic_7，不复制游戏纹理；所需权利仍归原权利人。上述用户提供来源不等于独立核实了第三方权属或公有领域状态，项目许可不扩张第三方权利。指纹列于当前素材清单。

## 上游工具

| 内容 | 来源与版本 | 许可与范围 |
| --- | --- | --- |
| Gradle Wrapper 脚本与 JAR | 工程 Wrapper 配置指向 Gradle 9.2.1；原脚本头与 JAR 内声明保持原样 | 原作者的 Apache-2.0 许可继续有效，[内嵌许可原文](third-party/gradle-wrapper-LICENSE.txt)随源码保留；Wrapper 不进入奶蛙成品 |
| NeoForge、ModDevGradle | NeoForge 21.1.252、插件 2.0.147，由构建配置指定 | 构建从上游仓库获取，不把完整依赖或共享工具提交到本仓库；各自上游声明继续适用，不改为项目 All Rights Reserved |

本次没有复制其他作品的署名或模板许可，也不授予 Minecraft 商标或其他第三方权利。玩家授权和项目主许可的适用范围保持现行条款。
