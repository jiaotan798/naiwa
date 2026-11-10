# 独立验收探针

在仓库根目录运行 `./scripts/gradle.ps1 qaJar` 编译独立验收探针，产物为 `mod/build/qa/naiwa-qa.jar`。

探针覆盖飞升、手部恢复、望远镜、笑气粒子、整蛊、背包、音效和渲染采样。运行探针需要独立专服、客户端及配套测试夹具。

常规源码验证使用 `./scripts/gradle.ps1 build runGameTestServer`。 [0.5.0 验证记录](../../../validation/0.5.0.md)
