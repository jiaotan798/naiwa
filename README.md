# 奶蛙

奶蛙会在你挖矿、钓鱼或开门时突然现身，整蛊得逞后捧腹大笑，带着空玻璃瓶还能收集它笑出来的笑气。**0.4.9** 加入笑气瓶破碎特效，瓶子碎开时会冒出淡黄色烟雾，飞出几枚奶蛙表情。

## 下载与安装

从 [0.4.9 Release](https://github.com/jiaotan798/naiwa/releases/tag/v0.4.9) 下载与游戏版本和加载器匹配的 JAR。

| Minecraft Java | 加载器 | 安装位置 | 前置模组 |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.23 | 客户端及服务端 | 无 |
| 1.20.1 | Fabric Loader 0.19.4 | 客户端及服务端 | Fabric API 0.92.9+1.20.1 |
| 1.21.1 | NeoForge 21.1.252 | 客户端及服务端 | 无 |
| 1.21.1 | Fabric Loader 0.19.4 | 客户端及服务端 | Fabric API 0.116.7+1.21.1 |
| 26.1.2 | NeoForge 26.1.2.94 | 客户端及服务端 | 无 |
| 26.1.2 | Fabric Loader 0.19.4 | 客户端及服务端 | Fabric API 0.149.0+26.1.2 |

把匹配的 JAR 和所需前置放进对应实例的 `mods` 目录。游戏 Java 版本、联机和升级步骤见[安装指南](https://github.com/jiaotan798/naiwa/releases/download/v0.4.9/INSTALL.md)。

## 玩法

- **遇见奶蛙**。开门、开箱、驾船、照玻璃和挖钻石等日常动作都有机会触发整蛊。在创造模式也可以用奶蛙生成蛋召唤。
- **收集笑气**。随身带着空玻璃瓶，奶蛙笑起来时会自动换给你一瓶笑气。把笑气瓶扔向怪物，可以让它们短暂停止追击。随身带着笑气瓶也可能漏气，把你自己逗笑。
- **转化盾牌**。拿着普通盾牌等奶蛙在身边笑完，就有机会得到奶蛙盾。累计三次有效格挡，奶蛙会探头大笑并击退面前的目标。
- **制作图腾**。用原版不死图腾和一瓶笑气合成奶蛙图腾。触发保命后会变成奶蛙飞升，恢复人形并落地时，奶蛙还会出现在面前大笑。

触发条件、物品效果和配置方法见[完整玩法说明](docs/完整玩法说明.md)与[模组设置](docs/模组设置.md)。各版本变更见[更新记录](CHANGELOG.md)。

## 构建源码

从仓库根目录运行以下命令构建 1.21.1 NeoForge 版。

```powershell
.\mod\gradlew.bat -p mod build
```

产物在 `mod/build/libs/`。构建环境、测试命令和其他版本的步骤见[多加载器构建说明](ports/README.md)。

## 反馈

遇到问题可在 [Issues](https://github.com/jiaotan798/naiwa/issues) 反馈，或搜索 QQ 群 **873511838**。请附上奶蛙版本、Minecraft 版本、加载器和复现步骤。

## 作者、许可与素材

作者为 [jiaotan798](https://github.com/jiaotan798)。[哔哩哔哩](https://space.bilibili.com/409729840) · [抖音](https://v.douyin.com/KDs5ZxJh5V8/)

项目原创内容保留全部权利。玩家安装、整合包、服务器和视频直播的使用范围见 [LICENSE](LICENSE) 与 [玩家使用授权](PLAYER_USAGE.md)。上游依赖及素材适用各自的许可。[代码与素材来源](THIRD_PARTY_NOTICES.md)
