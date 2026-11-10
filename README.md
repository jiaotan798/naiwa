# 《奶蛙》

> 奶蛙会在你挖矿、钓鱼或开门时突然现身，整蛊得逞后捧腹大笑。**v0.5.0** 更新奶蛙的面部表情和游戏内简介，奶蛙会通过整蛊等玩法事件出现。

## 下载与安装

从 [v0.5.0 Release](https://github.com/jiaotan798/naiwa/releases/tag/v0.5.0) 下载与下表匹配的 `.jar`。

| Minecraft | 加载器 | 安装位置 | 前置模组 |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.23 | 客户端及服务端 | 无 |
| 1.20.1 | Fabric Loader 0.19.4 | 客户端及服务端 | Fabric API 0.92.9+1.20.1 |
| 1.21.1 | NeoForge 21.1.252 | 客户端及服务端 | 无 |
| 1.21.1 | Fabric Loader 0.19.4 | 客户端及服务端 | Fabric API 0.116.7+1.21.1 |
| 26.1.2 | NeoForge 26.1.2.94 | 客户端及服务端 | 无 |
| 26.1.2 | Fabric Loader 0.19.4 | 客户端及服务端 | Fabric API 0.149.0+26.1.2 |

把匹配的 JAR 和所需前置模组放进对应实例的 `mods` 目录。Java 版本、详细安装步骤及联机和升级要求见[安装指南](https://github.com/jiaotan798/naiwa/releases/download/v0.5.0/INSTALL.md)。

## 玩法

- **遇见奶蛙**。开门、开箱、驾船、照玻璃和挖钻石等日常动作都有机会触发整蛊。在创造模式也可以用奶蛙生成蛋召唤。
- **收集笑气**。随身带着空玻璃瓶，奶蛙笑起来时会自动换给你一瓶笑气。把笑气瓶扔向怪物，可以让它们短暂停止追击。随身带着笑气瓶也可能漏气，把你自己逗笑。
- **转化盾牌**。拿着普通盾牌等奶蛙在身边笑完，就有机会得到奶蛙盾。累计三次有效格挡，奶蛙会探头大笑并击退面前的目标。
- **制作图腾**。用原版不死图腾和一瓶笑气合成奶蛙图腾。触发保命后会变成奶蛙飞升，恢复人形并落地时，奶蛙还会出现在面前大笑。

触发条件和道具效果见[完整玩法说明](docs/完整玩法说明.md)，配置方法见[模组设置](docs/模组设置.md)。各版本变更见[更新记录](CHANGELOG.md)。

## 构建源码

```powershell
.\mod\gradlew.bat -p mod build
```

1.21.1 NeoForge 版的产物在 `mod/build/libs/`。构建环境及其他版本和加载器的步骤见[构建说明](ports/README.md)。

## 反馈

在 QQ 搜索群号 **873511838**，或扫描下方二维码加入「mc模组问题反馈群」。也可以在本仓库的 **[Issues](https://github.com/jiaotan798/naiwa/issues)** 提交问题。反馈时请附上模组版本、Minecraft 版本、加载器和复现步骤。

<img src=".github/feedback-qq.png" alt="mc模组问题反馈群二维码，群号 873511838" width="220">

## 作者、许可与素材

作者：jiaotan_。

- jiaotan_：[bilibili](https://space.bilibili.com/409729840?) · [抖音](https://v.douyin.com/KDs5ZxJh5V8/)

本模组的原创内容保留所有权利（**All Rights Reserved**）。玩家安装、整合包、服务器和视频直播的使用范围见 [LICENSE](LICENSE) 与[玩家使用授权](PLAYER_USAGE.md)。上游模板、依赖和第三方素材适用各自的许可，见[代码与素材来源](THIRD_PARTY_NOTICES.md)。
