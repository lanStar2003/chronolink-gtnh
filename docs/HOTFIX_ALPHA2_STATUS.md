# alpha.2：启动依赖修复与 Release 结果

记录日期：2026-10-06。

## 实际结果

- 构建源码提交：`abfbdf35f029192d28b1a85844d24802042a3075`。
- GitHub Actions 运行：`37487488773`，attempt 1；核心、完整构建和发布三个 job 均成功。
- 运行页面：https://github.com/lanStar2003/chronolink-gtnh/actions/runs/37487488773
- Gradle：`BUILD SUCCESSFUL in 29s`；27 actionable tasks，26 executed，1 up-to-date。
- 已发布 Release：`v0.1.0-alpha.2`，Release ID `404882988`，标记为 prerelease，不是 draft。
- Release：https://github.com/lanStar2003/chronolink-gtnh/releases/tag/v0.1.0-alpha.2
- JAR：https://github.com/lanStar2003/chronolink-gtnh/releases/download/v0.1.0-alpha.2/chronolink-gtnh2.8.4-0.1.0-alpha.2.jar

## 已定位并修复的问题

官方 GT5-Unofficial 5.09.51.482 成品 JAR 的 `gregtech` ID 向 Forge 登记的版本是 `MC1710`，同一 JAR 的 `gregtech_nh` ID 登记的版本才是 `5.09.51.482`。

alpha.1 将数字构建号限制写在 `gregtech` 上，因此 Forge 会在启动依赖检查时拒绝正确的 2.8.4 环境。

alpha.2 的成品 JAR 声明：

```text
required-after:gregtech;required-after:gregtech_nh@[5.09.51.482];required-after:CoFHCore
```

仍然精确限定对应 GT 构建，没有要求用户升级 GT，没有声明兼容后续整合包版本。没有反射依赖被上游裁剪的生成类。

## 真正执行的检查

| 项目 | 结果 |
|---|---|
| 生产核心测试 | 45 项通过，124091 次断言 |
| 真实 Forge / 官方 GT 开发与运行 JAR 依赖回归 | 20 项通过 |
| 回归中的旧版缺陷复现 | 旧 `gregtech@[5.09.51.482]` 被实际 Forge 解析器拒绝 |
| 新版依赖校验 | 成品 JAR 接受正确 `gregtech` 与 `gregtech_nh` 版本，拒绝相邻错误构建号 |
| 构建与发布脚本测试 | 20 项通过 |
| 资源静态检查 | 25 项通过 |
| Java 语法检查 | 22 个源码文件通过 |
| 完整编译、重混淆、Java 8 与打包检查 | 通过 |
| 发布工作流重新下载 Release 附件并核验 | 通过，5 个附件 |
| 本地复核 Actions ZIP / JAR CRC、SHA-256、成品注解 | 通过；JAR 27 个类均为 Java 8 字节码 |
| 完整 GTNH 客户端/服务端启动、世界加载、设备传输 | 未执行 |

本轮中间方案在真实 JAR 回归中失败，被阻止发布；修正为使用 `gregtech_nh` 后才通过并发布。没有把失败的中间产物作为 Release 提供。

## 文件校验

JAR 大小：58409 bytes。

SHA-256：

```text
a966293e1dfbde8b535785f410de5778cb37ed957564fd6ed3ca7ed3f99c4e3c
```

Release 附件：模组 JAR、SHA256SUMS、BUILD_EVIDENCE.json、DEPENDENCY_CHECK.txt、READ_BEFORE_INSTALL.txt。

## 更新方式

退出游戏，移除 alpha.1 的 ChronoLink JAR，再放入 alpha.2；不要保留两个 ChronoLink 版本，不要替换整合包里的 GregTech，不需要删除配置或存档。先用独立测试世界验证。

修复仅针对本次启动依赖错误及发布流程；简单单页面板和现有功能范围保持不变，激光、数据光缆、AE 等高级协议仍未实现。
