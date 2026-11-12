# 《奶蛙》

> 奶蛙爱在你挖矿、钓鱼或开门时捣乱，被发现了还会捧腹大笑。**v0.5.1** 调整脸颊与颈部模型，降低模型绘制负担，并减少 1.21.1 版同屏多只奶蛙时的顶点提交。

## 下载与安装

从 [v0.5.1 Release](https://github.com/jiaotan798/naiwa/releases/tag/v0.5.1) 下载与你的 Minecraft 版本和加载器相符的 JAR。

| Minecraft | 加载器 | 安装位置 | 前置模组 |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.23 | 客户端及服务端 | 无 |
| 1.20.1 | Fabric Loader 0.19.4 | 客户端及服务端 | Fabric API 0.92.9+1.20.1 |
| 1.21.1 | NeoForge 21.1.252 | 客户端及服务端 | 无 |
| 1.21.1 | Fabric Loader 0.19.4 | 客户端及服务端 | Fabric API 0.116.7+1.21.1 |
| 26.1.2 | NeoForge 26.1.2.94 | 客户端及服务端 | 无 |
| 26.1.2 | Fabric Loader 0.19.4 | 客户端及服务端 | Fabric API 0.149.0+26.1.2 |

把 JAR 和所需前置模组放进实例的 `mods` 目录。联机时，客户端和服务端安装同一版本。 [Java 版本和升级步骤](INSTALL.md)

## 玩法

- **遇见奶蛙**。开门、开箱、驾船、照玻璃和挖钻石都有机会触发整蛊。创造模式也可以用生成蛋召唤。
- **收集笑气**。随身带着空玻璃瓶，奶蛙整蛊得逞后笑起来，就能自动收集笑气。把瓶子扔向怪物，它们会暂时笑到停手。带在自己身上，偶尔也会把你逗笑。
- **转化盾牌**。拿着普通盾等奶蛙在身边笑完，盾牌会变成奶蛙盾。每次攒满三次有效格挡，盾上的奶蛙就会探头大笑，把面前的目标击退。
- **用图腾保命**。笑气瓶和不死图腾可以合成奶蛙图腾。图腾保住你一命后，你会变成奶蛙，在金色圣光下飞升。恢复人形并落地后，奶蛙会到你面前大笑着嘲讽你。

[完整玩法](docs/完整玩法说明.md) · [模组设置](docs/模组设置.md) · [更新记录](CHANGELOG.md)

## 构建源码

构建 Minecraft 1.21.1 NeoForge 版时，使用 JDK 21，并把 Node.js 22 或更新版本加入 `PATH`。在仓库根目录运行：

```powershell
.\mod\gradlew.bat -p mod build
```

主端产物为 `mod/build/libs/naiwa-0.5.1.jar`。 [六端构建说明](ports/README.md)

## 反馈

在 QQ 搜索群号 **873511838**，或扫描二维码加入「mc模组问题反馈群」。你也可以在 GitHub 提交 Issue。请写明模组版本、Minecraft 版本、加载器和复现步骤。 [提交问题](https://github.com/jiaotan798/naiwa/issues)

<img src=".github/feedback-qq.png" alt="mc模组问题反馈群二维码，群号 873511838" width="220">

## 作者、许可与素材

作者 **jiaotan_**。 [bilibili](https://space.bilibili.com/409729840?) · [抖音](https://v.douyin.com/KDs5ZxJh5V8/)

奶蛙的原创内容保留全部权利（**All Rights Reserved**）。个人游玩、整合包、服务器和视频直播的使用范围见许可文件。 [LICENSE](LICENSE) · [玩家使用授权](PLAYER_USAGE.md) · [代码与素材来源](THIRD_PARTY_NOTICES.md)
