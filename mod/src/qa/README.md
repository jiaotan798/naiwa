# 客户端验收探针

在仓库根目录运行 `./scripts/gradle.ps1 qaJar`，可把探针编译为 `mod/build/qa/naiwa-qa.jar`。探针覆盖飞升、手部显示、望远镜、笑气粒子、整蛊、背包、音效和渲染采样。

完整运行需要独立专服、客户端、启动器和测试夹具。公开仓库提供探针源码和编译入口。验证服务端玩法可直接运行 `./scripts/gradle.ps1 build runGameTestServer`。 [0.5.0 验证](../../../validation/0.5.0.md)
