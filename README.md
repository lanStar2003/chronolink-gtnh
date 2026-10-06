# ChronoLink · 时枢网络

仅针对 **GTNH 2.8.4 / Minecraft 1.7.10 / GT5 5.09.51.482** 的非官方无线资源网络附加模组。

## 下载与当前状态

当前测试版：**0.1.0-alpha.2**，已完成云端构建并发布 GitHub Release。

**[下载模组 JAR](https://github.com/lanStar2003/chronolink-gtnh/releases/download/v0.1.0-alpha.2/chronolink-gtnh2.8.4-0.1.0-alpha.2.jar)** · **[Release 页面与校验文件](https://github.com/lanStar2003/chronolink-gtnh/releases/tag/v0.1.0-alpha.2)** · [全部 Releases](https://github.com/lanStar2003/chronolink-gtnh/releases)

**请移除 alpha.1 后再安装 alpha.2，不要同时保留两个版本。** alpha.1 已知存在 Forge 依赖版本声明错误，会误报缺少指定 gregtech 版本。修复没有要求升级或替换整合包的 GT。

alpha.2 的真实 Forge 依赖回归、完整编译、重混淆和 Release 下载校验已通过。**这仍不等于完成整个 GTNH 客户端/服务端启动、机器传输或存档安全测试。** 具体证据见 [修复构建记录](docs/HOTFIX_ALPHA2_STATUS.md)。

## 简单界面与第一版范围

使用原生游戏内单页配置，只有连通器与绑定器，不需要控制器、网页控制台或复杂拓扑编辑器。

- 物品：标准 `IInventory` / `ISidedInventory` 接口及物品、metadata、NBT 过滤。
- 流体：Forge 流体接口，含采用同接口的气态流体；不包含独立氧气协议。
- GT EU：原生电压、安培；未知额定电压默认不输出。
- RF：原生 RF 接口，EU/RF 独立记账，本版不进行相互转换。
- 玩家私有频道 1–64；只操作已加载端点，不强制加载区块；不限制无线距离。
- 轻量传输纹理与粒子；新节点默认暂停，修改配置后需重新启用。

**尚未实现**：激光、数据光缆、AE 网格/库存桥、IC2 独立电网、特殊深层库存和物流请求协议、公共共享网络。标准接口实现不能视为所有设备均已兼容。

## 使用

在来源和接收设备旁各放一个连通器，选择相同网络编号和资源类型。来源端设为“抽入网络”，接收端设为“送出网络”，确认接入面后启用。一个连通器只处理一种资源并连接一个相邻面。

EU 还须确认电压与安培。绑定器右键记录网络编号，潜行右键绑定另一个属于自己的空节点。节点有缓冲时，不能切换网络、方向或资源类型。

配方材料：铁锭、玻璃、圆石、木板。连通器每次合成 8 个，不需要电路；实际 NEI 配方及铁器阶段可达性仍须游戏测试。

## 云端构建与自动 Release

工作流：`.github/workflows/build.yml`，代码提交自动运行，也可手动运行。

1. 运行核心测试、Java 语法检查、资源与构建/发布脚本测试。
2. 使用真实 Forge/GT/CoFH 依赖完整编译并重混淆。
3. 从官方 GT 开发/运行 JAR 读取实际 `@Mod` 数据，用真实 Forge 解析器检验成品 JAR 的依赖声明，并检查 Java 8 字节码和打包范围。
4. 全部通过后上传 Actions 产物；主分支 push 成功构建的新版本自动发布对应 GitHub Release。
5. 发布后重新下载全部 Release 附件并比较哈希，保存发布回执。已发布的版本和标签不会被覆盖；下一次发版须更新版本号和对应发布说明。

Release 附件包含 JAR、`SHA256SUMS`、`BUILD_EVIDENCE.json`、`DEPENDENCY_CHECK.txt` 和安装前说明。不附带整合包依赖 JAR。

构建不需要玩家实例或个人 Token。构建任务保持只读；仅成功后的主分支发布任务使用 GitHub 自动提供的令牌取得必要的仓库写入权限。不接受 Minecraft EULA，不自动安装到游戏。

**不要安装 `-dev.jar`，不要把源码 ZIP 改名成 JAR。**

## 本地/云端开发

安装构建 JDK 21、Java 8 toolchain 和 Python 3.9+，执行 `bash Build-Linux.sh`，或 Windows 的 `Build-Windows.cmd`。也可用 Gradle 8.8 执行：

```text
gradle --no-daemon clean check reobfJar stageCloudArtifact
```

`.devcontainer/` 提供独立开发环境配置，不会自动启动或开启付费服务。

## 安全边界

先在独立测试世界验证；不要直接用于重要存档。第三方容器与不同区块的存档不是原子事务，任意崩溃或外部 API 异常不能保证绝不丢失或复制资源。异常返回会隔离节点，不能盲目重放或退款。

本仓库由所有者建立，导入时可见性为公开；未上传游戏文件、第三方依赖二进制、字体或个人凭据。
