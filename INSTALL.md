# 奶蛙 0.5.1 安装指南

按 Minecraft 版本和加载器选一份 JAR。Fabric 端还要装表里的 Fabric API。

| Minecraft | 加载器 | 游戏 Java | 前置模组 | JAR |
| --- | --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.23 | 17 | 无 | `naiwa-0.5.1-Forge-1.20.1.jar` |
| 1.20.1 | Fabric Loader 0.19.4 | 17 | Fabric API 0.92.9+1.20.1 | `naiwa-0.5.1-Fabric-1.20.1.jar` |
| 1.21.1 | NeoForge 21.1.252 | 21 | 无 | `naiwa-0.5.1-NeoForge-1.21.1.jar` |
| 1.21.1 | Fabric Loader 0.19.4 | 21 | Fabric API 0.116.7+1.21.1 | `naiwa-0.5.1-Fabric-1.21.1.jar` |
| 26.1.2 | NeoForge 26.1.2.94 | 25 | 无 | `naiwa-0.5.1-NeoForge-26.1.2.jar` |
| 26.1.2 | Fabric Loader 0.19.4 | 25 | Fabric API 0.149.0+26.1.2 | `naiwa-0.5.1-Fabric-26.1.2.jar` |

0.5.1 补充实测发现：Fabric 1.20.1 入服后可能触发专服崩溃；Forge 1.20.1 无光影时图腾飞升会使客户端崩溃；26.1.2 两个加载器开启本次测试的 Unbound／BSL 后奶蛙模型不可见。Forge 1.20.1 的 BSL 登录流程未完成。 [补充验证与已知问题](validation/0.5.1-ports.md)

先关闭游戏或服务器，把旧版奶蛙 JAR 移出实例的 `mods` 目录，再放入新版。联机时客户端和服务端都要安装匹配同一加载器和 Minecraft 版本的奶蛙 0.5.1。升级或切换加载器前先备份存档。

奶蛙通过整蛊等玩法事件出现，也可以用生成蛋召唤。0.5.1 沿用关闭群系自然生成的规则，存档里已有的奶蛙仍会保留。

[玩法与设置](https://github.com/jiaotan798/naiwa/tree/v0.5.1#玩法) · [玩家使用授权](https://github.com/jiaotan798/naiwa/blob/v0.5.1/PLAYER_USAGE.md)
