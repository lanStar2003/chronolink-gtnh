# 首次云端构建结果

记录日期：2026-10-06。

**GitHub Actions 完整构建成功。没有执行 GTNH 游戏启动或实际机器传输测试。**

## 可核验记录

- 仓库：lanStar2003/chronolink-gtnh，上传时为公开仓库；未更改可见性。
- 构建源码提交：`9b016232f185881565bb9979d2f3fd4e5dc75024`。
- 工作流：`Build GTNH 2.8.4`，运行编号 1，run ID `37476927693`，attempt 1。
- 运行页面：https://github.com/lanStar2003/chronolink-gtnh/actions/runs/37476927693
- Core job：`112314643944`，成功。
- Forge compile / reobfuscation job：`112314766291`，成功。
- Gradle 原始结论：`BUILD SUCCESSFUL in 2m 4s`，29 actionable tasks，28 executed、1 up-to-date。

## 实际通过

| 检查 | 结果 |
|---|---|
| 生产核心测试 | 45 项、124091 次断言通过 |
| 构建脚本测试 | 14 项通过 |
| 资源静态检查 | 23 项通过 |
| Java 语法检查 | 21 个 Java 文件通过 |
| 真实 Forge / GT / CoFH 依赖完整编译 | 通过 |
| reobfJar | 通过 |
| JAR 内容和 Java 8 字节码检查 | 通过 |
| 下载后 ZIP / JAR 哈希及 CRC 复核 | 通过 |
| PNG 资源解码 | 4 张纹理正常解码 |
| SRG 命名抽查 | TileConnector 的 updateEntity、NBT、inventory 等方法已呈 SRG 名称 |
| GTNH 客户端 / 独立服务端启动 | 未执行 |
| 实际机器传输、存档和性能回归 | 未执行 |

SRG 抽查不是整个游戏的链接或运行验证。

## 产物

模组文件：`chronolink-gtnh2.8.4-0.1.0-alpha.1.jar`，58164 bytes。

SHA-256：

```text
75674cc0fa0291ed3694725c1e3f54d8a5223642ab6f3862a652949b07b0be1e
```

Actions artifact：`chronolink-gtnh2.8.4-37476927693-1`，ID `11419358315`。包含 JAR、SHA256SUMS、BUILD_EVIDENCE.json 和安装前说明。下载的归档 SHA-256 为 `0ea91541f4e6ccb8f16f37db442fd9b9a7b88aacc8c3900b8f32f48e87a35dff`。

本次 artifact 的到期时间是 2026-10-20T14:14:31Z；没有自动发布 Release。后续重新构建会产生对应提交的新产物，不能混用提交身份。

## 范围和使用边界

目标仅 GTNH 2.8.4。保持简单原生单页面板、连通器和绑定器；目前为 alpha 测试构建。物品、Forge 流体及采用同接口的气态流体、原生 GT EU 和 RF 的代码已编译，不代表所有第三方设备验证通过。

EU/RF 独立记账，不互相转换。激光、数据光缆、AE、独立氧气与专用深层物流等仍未实现。

先放入独立测试实例的 mods 目录，在新测试世界验证；不要直接打开重要存档。不要安装 -dev.jar，不要把源码 ZIP 当作模组。游戏验收项目见 INGAME_SMOKE_TEST.md。
