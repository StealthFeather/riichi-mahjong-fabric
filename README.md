# Riichi Mahjong — Fabric 26.3 服务端移植

基于 [caxapexac/MinecraftRiichiMahjong](https://github.com/caxapexac/MinecraftRiichiMahjong) 的独立 Fabric 服务端移植，保留原项目麻将规则、3D 模型、材质和音效。仅服务端安装 Mod，玩家使用原版客户端并接受资源包。

当前版本：`0.4.2+fabric.26.3.12`。适用于 Minecraft **26.3**、Fabric Loader **0.19.5**、Fabric API **0.163.0**、Java **25**。

## 下载与安装

前往 [最新版 Release](https://github.com/StealthFeather/riichi-mahjong-fabric/releases/tag/v0.4.2%2Bfabric.26.3.12) 下载：

- `riichi-mahjong-0.4.2+fabric.26.3.12.jar`：放入服务端 `mods/`，同时安装对应 Fabric API。Polymer 已内嵌。
- `TimoryResource-26.3.zip`：包含本版全部麻将模型和中文进度的服务器资源包。已有包含 `26.3.8` 麻将资产的自定义资源包可继续使用；从 `26.3.6` 升级时须补充立直棒模型，或使用本版附件。
- Release 下方的 `Source code (zip/tar.gz)`：GitHub 按发布标签生成的完整仓库源码，含移植代码及固定版本的上游快照。
- `verification.json`：本版构建哈希、回归、服务器集成及原版客户端测试结果。
- `SHA256SUMS.txt`：发布附件校验值。

请在停服并备份存档后安装。客户端无需安装此 Mod。可选的 Team Reborn Energy API 用于役发电机能源互联。

## 操作

管理员使用 `/mahjong give` 获取物品，`/mahjong` 查看操作。放置麻将桌后右键桌边或座位文字入座，也可使用 `/mahjong join`；潜行右键中央开局，空位由原项目 BOT 补齐。

右键自己的单张手牌出牌。吃、碰、杠、立直、荣和、自摸等通过经验条上方提示和本人面前的按钮执行；过期操作自动移除，不再写入聊天历史。立直选中后绿色箭头标记合法弃牌。对局中只有 Shift 右键命中桌子才退出，命中牌或本人按钮不离座；也可 `/mahjong leave`。离开后 BOT 暂代，普通右键桌子或 `/mahjong join` 可接回原座位，保留当前手牌、分数与轮次，原座位不会被其他玩家占用。扳手右键中央或 `/mahjong menu` 打开设置，支持雀魂、天凤、WRC 四麻及三麻预设。

牌桌显示与碰撞为以中心方块正中为中心的 2×2（0.5+1+0.5）。本人操作按钮平放在桌沿，点击范围与字体背景一致。缩小后的立直棒位于中央风盘上方。摸牌放在右手边，与其他牌空出 1/3 牌宽；出牌后保留原牌位的空槽，其余牌保持原位，10 tick（正常 20 TPS 下约 0.5 秒）后再合并理牌。流局结算公开所有听牌玩家的手牌，未听牌玩家仍保持暗牌。

`26.3.10` 修复本人手牌与公共牌背重叠造成的闪烁，包含最右侧普通手牌。手牌所属玩家变化或流局公开时，显示实体也会按新的可见范围重建。

桌上显示随庄家轮转的门风、当前出牌者；场次、分数、余牌及结算移至本人 BossBar。鸣牌按出牌者相对方位横放，加杠叠放；暗杠左右两张牌背朝上，中间两张显示牌面。普通荒牌流局结算按规则预设及听牌人数计算未听罚符。每桌一个隐身、NoAI 的虚拟潜影贝承担原版客户端碰撞；服务端玩家使用相同包围盒。完整牌局留在服务端，对局中的暗牌牌面只发送给所属玩家。

同时包含牌谱、预设练习牌谱、单人配对麻将、麻将祭坛、役发电机、自动点击器、物品、配方和进度。53 个麻将进度的标题与达成说明已汉化。

## 来源与许可

- 原作者与原项目：[caxapexac/MinecraftRiichiMahjong](https://github.com/caxapexac/MinecraftRiichiMahjong)。
- 固定上游提交：`fe469836ed461d44998819dd3004c8dea01f5be0`，版本 `0.4.2`。
- `riichi-mahjong-fabric/upstream/` 保留原始快照；移植代码位于 `riichi-mahjong-fabric/src/`，中文覆盖位于 `resource-overrides/`。
- 原项目 LGPL 2.1 及 Mahjong4j 许可随源码和发布包保留；参见 `LICENSE.txt`、`LICENSE_mahjong4j.txt` 与 `UPSTREAM-CREDITS.txt`。

## 构建与限制

详见 [移植说明](riichi-mahjong-fabric/PORTING.md) 和 [构建环境说明](BUILDING.md)。仓库提供完整源码；现有构建脚本仍使用 Timory 工作区准备的 Minecraft、Java 与 Polymer 依赖目录，未提供独立下载依赖的一键构建流程。

原版客户端界面由显示实体、interaction、ActionBar 提示和原版容器替代。客户端 FOV 调整、渲染编辑器等不能直接移植；自定义流体以水投影，单个潜影贝的桌下碰撞为近似。

`26.3.12` 通过 7 组回归、9 项真实服务器集成检查，以及四麻 1/2/3 人听牌、三麻 1/2 人听牌的 5 个真实流局结算场景。原版客户端的 3 次实体采样和截图复核确认四个座位共 16 张暗杠牌的两端牌背、中间牌面及摆放方向。以上为固定场景测试，未遍历整场多人牌局；详见 [本版变更与验证](riichi-mahjong-fabric/docs/26.3.12.md)。此前验证保留在 [26.3.11 说明](riichi-mahjong-fabric/docs/26.3.11.md) 和 [26.3.10 说明](riichi-mahjong-fabric/docs/26.3.10.md)。
