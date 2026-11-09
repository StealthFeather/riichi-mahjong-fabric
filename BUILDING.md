# 构建环境

本仓库保存 Fabric 服务端移植源码、固定上游源码和资源覆盖。现有源码与资源已准备好，**不要重新运行 `prepare.cjs`、`adapt.cjs` 或 `resources.cjs`**，这些首次导入脚本会覆盖当前源码、资源或版本元数据。

需要 Node.js、完整 JDK 25（含 `java`、`javac`、`jar`），以及已启动过一次的 Minecraft 26.3 / Fabric 0.19.x 服务端目录。服务端应已加载 Fabric API，使 `.fabric/processedMods` 内有拆分后的 API 依赖。Minecraft 运行文件和世界不包含在源码仓库中。

构建脚本共享 `riichi-mahjong-fabric/build-config.cjs`。JAR 文件名、发布目录名、校验记录和发布清单的版本统一读取 `src/main/resources/fabric.mod.json`，无需修改脚本中的版本常量。

## 配置本地构建环境

从仓库根目录运行 PowerShell，按实际位置修改以下示例路径。服务端使用独立测试目录：

```powershell
$env:JAVA_HOME = 'D:\Java\jdk-25'
$env:RIICHI_MC_SERVER = 'D:\Minecraft\mahjong-test\server'
$env:RIICHI_POLYMER_RUNTIME = Join-Path $PWD 'riichi-mahjong-fabric\build\deps\polymer'

# 只读已有模组，将其 bundled Polymer 和嵌套运行依赖提取到本仓库 build/deps。
node riichi-mahjong-fabric/prepare-local-deps.cjs 'D:\Minecraft\downloads\riichi-mahjong-0.4.2+fabric.26.3.9.jar'

node riichi-mahjong-fabric/build.cjs
node riichi-mahjong-fabric/test.cjs
```

提取命令也可读取旧版模组或备份文件。也可从可信的 Polymer bundled `0.18.7+26.3` 分发自行提取依赖，并将环境变量指向包含这些 JAR 的目录。`prepare-local-deps.cjs` 不运行导入脚本，不覆盖源码或资产。

`build.cjs` 编译移植代码、原规则库和 `src/rules/java` 中的共享规则兼容层；`rules-patches.cjs` 对驱动器和回合结算源码应用带匹配校验的补丁，保持上游快照不变。产物位于 `riichi-mahjong-fabric/build/libs/`。`test.cjs` 自动编译并执行 `src/test/java/com/riichimahjong/fabric/*Regression.java`，将通过结果与当前 JAR 的 SHA-256 绑定，写入 `build/verification.json`。这些回归检查不代替真实服务器及客户端验收，结果中的两项运行验收标志默认仍为 `false`。

## 合并资源包与发布附件

打包前先通过构建和回归检查，并准备包含 `pack.mcmeta` 的资源包基准。生产部署应使用当前完整 TimoryResource 基准；仅验证麻将时可使用独立测试服生成的 Polymer 包：

```powershell
$env:RIICHI_RESOURCE_PACK_BASELINE = 'D:\Minecraft\mahjong-test\server\polymer\resource_pack.zip'
node riichi-mahjong-fabric/package.cjs
```

输出默认位于 `deploy/RiichiMahjong-<元数据版本>/`，包含模组、合并资源包、源码、验证记录和 SHA-256。基准包提供其他命名空间，打包器用当前模组源码中的 `assets/riichi_mahjong` 覆盖麻将命名空间，并合并图集来源。没有额外资源工作区时，保留基准的 `pack.mcmeta`，使用固定上游的麻将图集；不会重新生成或覆盖源码资产。测试服生成的纯麻将包不得用来覆盖生产的完整 TimoryResource。

## 可配置路径与旧默认值

所有变量均为可选；不设置时兼容原 Timory 工作区布局。相对环境变量路径按调用进程的工作目录解析。

| 环境变量 | 用途 | 未设置时 |
| --- | --- | --- |
| `RIICHI_JAVA_HOME` / `JAVA_HOME` | JDK 根目录，前者优先 | `<仓库>/build/tools/jdk-25.0.4.1+1` |
| `RIICHI_MC_SERVER` | MC 26.3 服务端根目录 | `<仓库>/run/crop-26.3` |
| `RIICHI_POLYMER_RUNTIME` | bundled Polymer 与展开后的依赖目录，递归扫描 JAR | `<仓库>/build/kaleidoscope/runtime`；未显式设置时沿用原 classpath JSON |
| `RIICHI_POLYMER_JAR` | 嵌入新模组的 bundled JAR | `<Polymer目录>/polymer-bundled-0.18.7+26.3.jar` |
| `RIICHI_POLYMER_CLASSPATH_FILE` | Polymer 编译 classpath JSON 数组，设置后优先于目录扫描 | `<仓库>/build/kaleidoscope/classpath.json` |
| `RIICHI_RESOURCE_PACK_BASELINE` | 合并资源包基准 ZIP，仅打包需要 | `<仓库>/deploy/TimoryGameplay-2.7.18+26.3/TimoryResource-26.3.zip` |
| `RIICHI_RESOURCE_ROOT` | 可选额外资源工作区，提供图集及 `pack.mcmeta` | `<仓库>/TimoryResource` |
| `RIICHI_RELEASE_DIR` | 发布附件目录 | `<仓库>/deploy/RiichiMahjong-<版本>` |
| `RIICHI_CHANGELOG` | 本次发布说明 | `riichi-mahjong-fabric/docs/<fabric后的版本>.md` |

服务端 classpath 读取 `versions/26.3/server-26.3.jar`、`libraries/**/*.jar`、`.fabric/processedMods/**/*.jar`。构建产物、下载依赖、运行目录与本地环境变量无需提交。`install-local.cjs` 面向原工作区的测试服管理器，其他环境请直接按 README 安装发布附件。
