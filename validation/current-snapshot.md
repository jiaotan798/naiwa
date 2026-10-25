# 当前版本源码快照

## 作者确认

- 目标为 jiaotan798/naiwa，Private。
- 作者及版权署名使用 jiaotan798，不列共创者。
- 主许可仍为 All Rights Reserved，现行 PLAYER_USAGE 例外条款不变。
- 保留游戏实际使用的音效，按作者指示不再将其列为本轮待确认项。
- 只交付当前版本，不上传旧 Git 历史；作者已明确授权上传此快照并发布 Release，继续保持 Private。

## 文件范围

保留代码、测试、文档、Gradle Wrapper、构建配置、当前 bbmodel、盾牌源贴图、运行模型与检查清单、生产贴图与 OGG，以及模型采样、图标生成和现有音效检查脚本。

排除 assets/reference 整个目录、盾牌参考 UV 图、生成提示词及依赖原始参考媒体的截取脚本；历史素材、缓存、存档及个人配置也不纳入。原开发项目中的素材不删除，导出目录中移出的文件仅存于本地备份。

当前上传分支名为 upload-current，只有一次无父提交的初始提交。旧 main 和完整 Git bundle 仅为本地档案，未来不推送旧 main、不用 --all 或 --mirror。源码 ZIP 由当前快照导出，不含 .git、历史提交或排除素材。

## 验证与边界

业务源码、测试及生产资源与原开发源逐文件一致；源模型及运行资源摘要保持原值。当前版权变更仅为 jiaotan798 署名，PLAYER_USAGE 没有变化。原正式成品 JAR 不变，SHA256 为 `759e1b1dda348e6995447a8807509341ee7b23184b5afbd4b6d1fb8de0dc1972`。

当前精简快照已从仅含本分支的本地隔离克隆执行 `scripts/gradle.ps1 build --offline --console=plain`，构建及模型检查通过，报告 `BUILD SUCCESSFUL in 22s`。克隆中不存在参考目录，证明构建不依赖被排除文件。复用了本机 Gradle 依赖及素材缓存，未做空白缓存首次联网构建。

此前 98 项 GameTest 结果见[0.4.4 验证](0.4.4.md)，本轮不重复运行游戏测试或客户端。不声明 Linux/macOS、空白缓存首次联网、模型重新采样、客户端、联机及光影已通过新验收。

本地最终提交号、源码 ZIP 摘要与完整日志写入原开发项目的 GitHub 发布说明及忽略的 .verification/，不向源码提交中写入自身最终哈希。
