# Riichi Mahjong — Fabric 26.3 服务端移植

基于 [caxapexac/MinecraftRiichiMahjong](https://github.com/caxapexac/MinecraftRiichiMahjong) 的独立 Fabric 服务端移植，保留原项目麻将规则、3D 模型、材质和音效。仅服务端安装 Mod，玩家使用原版客户端并接受资源包。

当前版本：`0.4.2+fabric.26.3.7`。适用于 Minecraft **26.3**、Fabric Loader **0.19.5**、Fabric API **0.163.0**、Java **25**。

## 下载与安装

前往 [Releases](https://github.com/StealthFeather/riichi-mahjong-fabric/releases) 下载：

- `riichi-mahjong-0.4.2+fabric.26.3.7.jar`：放入服务端 `mods/`，同时安装对应 Fabric API。Polymer 已内嵌。
- `TimoryResource-26.3.zip`：已合并麻将内容的 TimoryResource，可配置为服务器资源包，客户端接受后显示模型和中文进度。
- `riichi-mahjong-fabric-sources.zip`：本版完整移植源码及固定版本的原始项目快照。
- `SHA256SUMS.txt`：发布附件校验值。

请在停服并备份存档后安装。客户端无需安装此 Mod。可选的 Team Reborn Energy API 用于役发电机能源互联。

## 操作

管理员使用 `/mahjong give` 获取物品，`/mahjong` 查看操作。放置麻将桌后右键桌边或座位文字入座，也可使用 `/mahjong join`；潜行右键中央开局，空位由原项目 BOT 补齐。

右键自己的单张手牌出牌。吃、碰、杠、立直、荣和、自摸等通过经验条上方提示和本人面前的按钮执行；过期操作自动移除，不再写入聊天历史。立直选中后绿色箭头标记合法弃牌。对局中 Shift 右键牌桌、本人手牌或按钮退出，BOT 接管当前手牌和分数；也可 `/mahjong leave`。扳手右键中央或 `/mahjong menu` 打开设置，支持雀魂、天凤、WRC 四麻及三麻预设。

牌桌显示与碰撞为以中心方块正中为中心的 2×2（0.5+1+0.5）。桌上显示随庄家轮转的门风、当前出牌者和立直棒；场次、分数、余牌及结算移至本人 BossBar。鸣牌按出牌者相对方位横放，加杠叠放。每桌一个隐身、NoAI 的虚拟潜影贝承担原版客户端碰撞；服务端玩家使用相同包围盒。完整牌局留在服务端，暗牌牌面只发送给所属玩家。

同时包含牌谱、预设练习牌谱、单人配对麻将、麻将祭坛、役发电机、自动点击器、物品、配方和进度。53 个麻将进度的标题与达成说明已汉化。

## 来源与许可

- 原作者与原项目：[caxapexac/MinecraftRiichiMahjong](https://github.com/caxapexac/MinecraftRiichiMahjong)。
- 固定上游提交：`fe469836ed461d44998819dd3004c8dea01f5be0`，版本 `0.4.2`。
- `riichi-mahjong-fabric/upstream/` 保留原始快照；移植代码位于 `riichi-mahjong-fabric/src/`，中文覆盖位于 `resource-overrides/`。
- 原项目 LGPL 2.1 及 Mahjong4j 许可随源码和发布包保留；参见 `LICENSE.txt`、`LICENSE_mahjong4j.txt` 与 `UPSTREAM-CREDITS.txt`。

## 构建与限制

详见 [移植说明](riichi-mahjong-fabric/PORTING.md) 和 [构建环境说明](BUILDING.md)。仓库提供完整源码；现有构建脚本仍使用 Timory 工作区准备的 Minecraft、Java 与 Polymer 依赖目录，未提供独立下载依赖的一键构建流程。

原版客户端界面由显示实体、interaction、ActionBar 提示和原版容器替代。客户端 FOV 调整、渲染编辑器等不能直接移植；自定义流体以水投影，单个潜影贝的桌下碰撞为近似。

此版本通过编译、定向规则、交互候选和坐标回归；完整联机牌局、网络协议与客户端视觉尚未实测。“一家立直后其他家无法立直”的原现象在纯规则场景未复现，已修正相关供托覆盖和旧操作状态，仍需客户端复核。详见 [本版变更与验证](riichi-mahjong-fabric/docs/26.3.7.md)。
