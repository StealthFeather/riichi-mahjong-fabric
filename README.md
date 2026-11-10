# Riichi Mahjong — Fabric 26.3 服务端移植

基于 [caxapexac/MinecraftRiichiMahjong](https://github.com/caxapexac/MinecraftRiichiMahjong) 的独立 Fabric 服务端移植，保留原项目麻将规则、3D 模型、材质和音效。仅服务端安装 Mod，玩家使用原版客户端并接受资源包。

当前正式版：`0.4.2+fabric.26.3.15`，包含 `26.3.14` 的 TP 返回显示恢复修复。适用于 Minecraft **26.3**、Fabric Loader **0.19.5**、Fabric API **0.163.0**、Java **25**。

本版增加四麻初级／中级／高级电将 AI、默认及各座位难度设置、存档恢复与离座接管。服务端需 Node.js 22+；固定版本 AI 源码与 MIT 许可已内嵌，无需 npm 安装或远程 AI 服务。三麻仍使用原 BOT。难度尚未通过大规模同牌山对战标定，详见 [四麻 AI 说明](riichi-mahjong-fabric/docs/26.3.15.md)。

`26.3.14` 修复玩家 TP 离开后返回看不见麻将桌的问题：同步玩家显示观察关系，区块卸载时清理旧显示，返回后恢复桌体、桌上公共牌及本人手牌；同时处理显示附件失效和异步区块加载的恢复。此次修复无需更新资源包。构建、9 组主回归、9 项通用服务器集成及 5 次实际 TP 往返检查通过；针对性验证详见 [26.3.14 变更与验证](riichi-mahjong-fabric/docs/26.3.14.md)。

## 下载与安装

前往 [26.3.15 Release](https://github.com/StealthFeather/riichi-mahjong-fabric/releases/tag/v0.4.2%2Bfabric.26.3.15) 下载：

- `riichi-mahjong-0.4.2+fabric.26.3.15.jar`：放入服务端 `mods/`，同时安装对应 Fabric API。Polymer 已内嵌。
- `TimoryResource-26.3.zip`：包含本版全部麻将模型和中文进度的服务器资源包，与 `26.3.14` 附件完全一致。已有包含 `26.3.8` 麻将资产的自定义资源包可继续使用；从 `26.3.6` 升级时须补充立直棒模型，或使用本版附件。
- Release 下方的 `Source code (zip/tar.gz)`：GitHub 按发布标签生成的完整仓库源码，含移植代码及固定版本的上游快照。
- `verification.json`：本版构建哈希、回归及服务器集成验证结果和测试范围。
- `SHA256SUMS.txt`：发布附件校验值。

请在停服并备份存档后安装，确保运行服务端的账号可以执行 Node.js 22+。客户端无需安装此 Mod 或 Node.js。可选的 Team Reborn Energy API 用于役发电机能源互联。

## 操作

管理员使用 `/mahjong give` 获取物品，`/mahjong` 查看操作。放置麻将桌后右键桌边或座位文字入座，也可使用 `/mahjong join`；潜行右键中央开局，四麻空位由所选难度的电将 AI 补齐，三麻仍使用原项目 BOT。

右键自己的单张手牌出牌。吃、碰、杠、立直、荣和、自摸等通过经验条上方提示和本人面前的按钮执行，相邻按钮留出 3 个字符宽的间距；过期操作自动移除，不再写入聊天历史。立直选中后绿色箭头标记合法弃牌。点击荣和或自摸直接声明；选择跳过可和牌机会（含抢杠）时，先显示“确认跳过”和“返回”，确认后才放弃。只有吃碰杠机会时，普通跳过仍单击。主动离座只使用 `/mahjong leave`，桌子和座位点击不再离座。离开后 BOT 暂代，普通右键桌子或 `/mahjong join` 可接回原座位，保留当前手牌、分数与轮次，原座位不会被其他玩家占用。扳手右键中央或 `/mahjong menu` 打开设置，支持雀魂、天凤、WRC 四麻及三麻预设。

牌桌显示与碰撞为以中心方块正中为中心的 2×2（0.5+1+0.5）。本人操作按钮平放在桌沿，点击范围与字体背景一致。缩小后的立直棒位于中央风盘上方。摸牌放在右手边，与其他牌空出 1/3 牌宽；出牌后保留原牌位的空槽，其余牌保持原位，10 tick（正常 20 TPS 下约 0.5 秒）后再合并理牌。流局结算公开所有听牌玩家的手牌，未听牌玩家仍保持暗牌。

三元牌按钮显示“中”“发”“白”，替代原有“3元”“2元”“1元”。成功弃牌播放 `minecraft:block.stone.place`，使用 `ambient` 类别、音量 1、音调 2。

`26.3.10` 修复本人手牌与公共牌背重叠造成的闪烁，包含最右侧普通手牌。手牌所属玩家变化或流局公开时，显示实体也会按新的可见范围重建。

桌上显示随庄家轮转的门风、当前出牌者；场次、分数、余牌及结算移至本人 BossBar。鸣牌按出牌者相对方位横放，加杠叠放；暗杠左右两张牌背朝上，中间两张显示牌面。普通荒牌流局结算按规则预设及听牌人数计算未听罚符。每桌一个隐身、NoAI 的虚拟潜影贝承担原版客户端碰撞；服务端玩家使用相同包围盒。完整牌局留在服务端，对局中的暗牌牌面只发送给所属玩家。

同时包含牌谱、预设练习牌谱、单人配对麻将、麻将祭坛、役发电机、自动点击器、物品、配方和进度。53 个麻将进度的标题与达成说明已汉化。

## 来源与许可

- 原作者与原项目：[caxapexac/MinecraftRiichiMahjong](https://github.com/caxapexac/MinecraftRiichiMahjong)。
- 固定上游提交：`fe469836ed461d44998819dd3004c8dea01f5be0`，版本 `0.4.2`。
- `riichi-mahjong-fabric/upstream/` 保留原始快照；移植代码位于 `riichi-mahjong-fabric/src/`，中文覆盖位于 `resource-overrides/`。
- 原项目 LGPL 2.1 及 Mahjong4j 许可随源码和发布包保留；参见 `LICENSE.txt`、`LICENSE_mahjong4j.txt` 与 `UPSTREAM-CREDITS.txt`。
- 四麻 AI 使用 MIT 许可的 [电将 majiang-ai](https://github.com/kobalab/majiang-ai) 1.3.0 和 [majiang-core](https://github.com/kobalab/majiang-core) 1.3.4，源码及许可证保存在 `riichi-mahjong-fabric/ai/`，发布包附带对应许可证。

## 构建与限制

详见 [移植说明](riichi-mahjong-fabric/PORTING.md) 和 [构建环境说明](BUILDING.md)。仓库提供完整源码；现有构建脚本仍使用 Timory 工作区准备的 Minecraft、Java 与 Polymer 依赖目录，未提供独立下载依赖的一键构建流程。

原版客户端界面由显示实体、interaction、ActionBar 提示和原版容器替代。客户端 FOV 调整、渲染编辑器等不能直接移植；自定义流体以水投影，单个潜影贝的桌下碰撞为近似。

`26.3.13` 通过 9 组主回归、9 项通用服务器集成检查及 8 项实际桌实体交互检查。原版客户端验证普通和密集缩放按钮布局各 3 次文本采样、7 个声音数据包窗口，并实际操作自摸“跳过→返回→跳过→确认”流程；截图已复核。以上为固定场景测试，未遍历整场多人牌局。测试机 OpenAL 音频设备不可用，声音只核对数据包参数，未验收扬声器播放效果。详见 [26.3.13 变更与验证](riichi-mahjong-fabric/docs/26.3.13.md)。此前验证保留在 [26.3.12 说明](riichi-mahjong-fabric/docs/26.3.12.md)。
