# 独立验收探针

本目录保留当前QA源码，正式JAR不包含探针。可运行 scripts/gradle.ps1 qaJar 编译，输出 mod/build/qa/naiwa-qa.jar。

原开发环境用独立专用服和两个隐藏、静音客户端执行TCP专项；启动器依赖原工作区共享运行库，不随源码交付。当前探针包括飞升、手部恢复、望远镜、笑气粒子、整蛊、背包、声音与自然场景。它们需要对应的离线账号、文件屏障和专用夹具，不能直接装入玩家实例代替实际游玩。

普通独立验证使用 scripts/gradle.ps1 build runGameTestServer；当前108项服务端测试与本版实机覆盖见[验证说明](../../../validation/0.4.9.md)。
