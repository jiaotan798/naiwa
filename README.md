# 奶蛙

Minecraft Java 1.21.1 模组，使用 NeoForge 21.1.252、Java 21，模组 ID 为 `naiwa`，当前版本 **0.4.4**。

作者与版权署名：**jiaotan798**。不列共创者。

仓库为 [jiaotan798/naiwa](https://github.com/jiaotan798/naiwa)，保持 **Private**。作者已明确授权上传当前版本并发布 0.4.4 Release；仅仓库获授权用户可访问。

本仓库按私有源码备份与协作准备，许可为 [All Rights Reserved](LICENSE)。正式成品允许玩家下载、安装和个人游玩；免费整合包、收费服务器及收益视频直播的条件见[玩家使用授权](PLAYER_USAGE.md)。仓库访问权限不等于源码或素材的对外分发、修改或商用授权。

## 玩法与安装

奶蛙会在探索、采矿和劳作时现身整蛊玩家，成功后捧腹大笑。已实现玻璃镜像、门后变脸、树干躲猫、矿洞摸鱼同事、钓鱼抢书、船尾偷渡、苦力怕代班、假钻石钓人、箱中探头和望远镜贴脸十种整蛊。

- 奶蛙盾：持普通盾遇见奶蛙后，经完整大笑原位转化；每三次成功格挡释放大笑与击退，保留原盾适用数据。
- 笑气瓶：随身空瓶在成功大笑期间自动换取一瓶笑气；可投掷控制普通怪物，也有携带漏气副作用。
- 奶蛙图腾：原版不死图腾与笑气瓶无序合成，返还一个空瓶。真实保命后变身奶蛙，升高六格、悬停六秒、缓降三秒，恢复人形并实际落地后再出现奶蛙嘲笑。
- 0.4.4：修正玻璃镜像靠墙或沿墙移动便提前消失的问题。奶蛙近墙时保留身体净空，其他实心障碍仍会中止演出。

客户端与服务器均安装同版 `naiwa-0.4.4.jar`，每个实例仅保留一个奶蛙版本。无需 YSM 或其他动画前置。成品作为独立 [Release 附件](https://github.com/jiaotan798/naiwa/releases)分发，不提交进源码历史。

[完整玩法](docs/完整玩法说明.md) · [盾牌规则](docs/盾牌玩法说明.md) · [模组设置](docs/模组设置.md) · [更新记录](CHANGELOG.md) · [0.4.4 验证](validation/0.4.4.md)

## 独立构建与测试

安装 JDK 21 和 Node.js 22 或更新版本，设置 `JAVA_HOME` 并将 Java、Node 加入 `PATH`。首次构建需要联网下载 Gradle 和 NeoForge 依赖。仓库已保留 Gradle Wrapper、当前源模型及采样资源，普通构建无需安装 Blockbench。

从仓库根目录运行：

```powershell
& .\scripts\gradle.ps1 build runGameTestServer
```

也可直接运行 Wrapper：

```powershell
& .\mod\gradlew.bat -p .\mod build runGameTestServer
```

Linux／macOS 使用：

```bash
./mod/gradlew -p mod build runGameTestServer
```

JAR 输出至 `mod/build/libs/`。`build` 包含模型来源校验与断言检查，`runGameTestServer` 在开发测试目录运行 98 项游戏测试；不要将开发测试目录作为实际游玩存档。

本仓库不携带开发机运行时、缓存、存档、整合包或自动安装到个人游戏实例的脚本。[QA 源码说明](mod/src/qa/README.md)中区分了服务端测试与客户端验收范围。

## 模型与素材

- [当前可编辑模型](assets/source/nailong_origin_zero.bbmodel)：保留网格、38 根骨骼、六张内嵌贴图及 11 段动画。
- `assets/runtime/`：已采样的游戏资源、摘要清单及独立姿态对照，不要手工修改生成文件。
- `assets/shield/naiwa-shield-v1.png`：当前盾牌源贴图，也是模型检查的对照原图。
- `scripts/`：模型采样、物品图标生成及音效起拍校验工具。

按作者确认，本快照不包含参考图片、参考视频、原始参考音频、生成提示词和历史素材；保留的模型、源贴图与运行资源用于构建和后续修改。游戏实际使用的 OGG 音效继续保留，技术参数见[音效说明](docs/音效说明.md)。

生成方法与骨骼约束见[模型接入说明](docs/模型接入说明.md)。修改 Java 逻辑后运行完整构建与 GameTest；修改模型时先重新采样，再运行上述检查。

## 许可与验证边界

根据项目所有者本次选择，项目原创部分保留全部权利，详见 [LICENSE](LICENSE)及[玩家使用授权](PLAYER_USAGE.md)。允许署名并保留许可的免费整合包收录；收费服务器可用，但不得售卖奶蛙专属内容或权益；视频与直播可正常展示游玩并保留收益。出售模组、修改版或素材，以及其他未经明确允许的代码或素材商业使用，需另行书面授权。第三方素材、Gradle Wrapper 等工具的权利与许可分别归其原权利人，不因本声明变更。

代码和素材的来源及实际纳入范围见 [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md)。作者已确认本人 AI 美术及保留项目音效的使用安排，不再将音效授权列为本轮待确认项。本次只交付当前版本，上传分支只有一次初始提交，不携带此前本地 Git 历史；旧历史及排除素材仅留在本地备份中。当前检查见[快照核对记录](validation/current-snapshot.md)。

原项目 0.4.4 已通过模型检查和全部 98 项 GameTest，原始服务端日志随验证记录保留。0.4.4 未进行客户端画面、光影或公网联机复测；0.4.3 的历史画面记录不能作为本版新实测声明。当前仓库副本的独立构建验证结果另见[上传准备记录](validation/github-preparation.md)。
