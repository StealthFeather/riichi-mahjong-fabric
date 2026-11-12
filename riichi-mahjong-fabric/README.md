# Riichi Mahjong

<!-- timory-resource-repository:start -->
## 材质包统一维护

材质包独立维护于 [StealthFeather/Timory-ResourcePack](https://github.com/StealthFeather/Timory-ResourcePack)。GitHub 独立仓库是统一维护来源；主工作区 `TimoryResource/` 是现有构建脚本的兼容工作目录，独立检出位于 `projects/timory-resource-pack/`。

**每次涉及材质包的设计、修改或生成（含贴图、模型、物品定义、语言、图集、动画和元数据），必须先从 GitHub 同步最新内容，再开始修改。** 在 Timory 主工作区根目录执行：

```powershell
git -C projects/timory-resource-pack fetch origin
git -C projects/timory-resource-pack pull --ff-only origin main
```

首次使用先克隆该仓库；本地有未提交改动或分支分叉时先保留并合并，解决冲突后再修改。同步后比较并合并独立检出与 `TimoryResource/` 的差异；资源生成脚本同样遵循此顺序。保留其他项目资源、图集条目与许可证，禁止用旧快照或单项目包覆盖共享内容。验证后的材质包改动需提交并推送回独立仓库，具体流程见其 [README](https://github.com/StealthFeather/Timory-ResourcePack#readme)。
<!-- timory-resource-repository:end -->

日本立直麻将、3D 牌桌、私有手牌与原版客户端适配。当前本地版本：**0.4.2+fabric.26.3.19**，仅安装到 Fabric 服务端。

已同步 [GitHub 独立仓库](https://github.com/StealthFeather/riichi-mahjong-fabric) main 提交 `decaf7b762d68dfad8ef52bfc47c982c0c3b94e0`（26.3.15），保留四麻三级 AI、TP 返回显示恢复、跳过和牌确认及手牌空槽延迟整理。

## 3×3 牌桌

牌桌显示、方块选择轮廓及客户端/服务端玩家碰撞均为以中心方块正中为中心的 **3×3 格**。桌体高度保持不变。所有麻将牌的长宽高、排列间距及相对桌面高度同步放大 **1.5 倍**；手牌、牌山、弃牌、副露、拔北牌与王牌使用相同比例。

手牌及空槽的点击范围、桌沿操作按钮的文字和命中区域、座位与门风文字、立直棒同步调整。摸牌仍与上一张牌留出 1/3 牌宽的间隔；自摸、荣和的和牌张在结算摊牌时也放在最右摸牌位，并保留相同间隔。竖立手牌与摊开手牌按模型底面贴齐桌面，本人手牌、公共牌背和点击区域共用对应位置。单人配对麻将保持原尺寸。

## 安装与操作

需要 Minecraft 26.3、Fabric Loader 0.19.5、Fabric API 0.163.0+26.3、Java 25；四麻 AI 需要服务端可执行 Node.js 22+。Polymer 和固定版本 AI 源码已内嵌。玩家使用原版客户端并接受包含麻将资产的 TimoryResource，本次尺寸修改无需新增材质。

停服后将构建的 `riichi-mahjong-0.4.2+fabric.26.3.19.jar` 替换服务端旧版 JAR，再正常开服。部署时保留当前 TimoryResource 材质包；测试服安装记录位于工作区 `run/single-server/mahjong-installation.json`。

`/mahjong` 查看操作，管理员 `/mahjong give` 领物品。右键桌边入座，潜行右键中央开局，右键自己的牌出牌；吃碰杠、立直与胡牌使用本人桌沿按钮。跳过可和牌机会需要确认。主动离座使用 `/mahjong leave`；离座后 BOT 暂代，右键桌子或 `/mahjong join` 可接回原座位。

扳手右键中央或 `/mahjong menu` 打开设置，支持四麻/三麻规则预设；四麻可选择初级、中级或高级 AI。完整暗牌仅保存在服务端，牌面只发送给所属玩家。

牌的交互体积按模型实际显示边界生成，手牌及单人配对麻将的宽、高、厚度均与显示对齐。四麻全部 AI 难度与三麻机器人每次决策随机等待 0.5–1.5 秒。

## 构建与验证

在本项目目录执行：

```powershell
node project.cjs build
node test.cjs
```

也可双击 `build.cmd`。构建器兼容当前 Timory 工作区与独立源码归档，公共 JDK、Minecraft 和 Polymer 依赖默认位于工作区根目录。配置方式见 [BUILDING.md](BUILDING.md)。**不要重新运行 `prepare.cjs`、`adapt.cjs` 或 `resources.cjs`**，以免覆盖已移植内容及版本元数据。

JAR 位于 `build/libs/`，回归结果和 JAR SHA-256 位于 `build/verification.json`。15 组 Java 回归及 10 项 Node AI 测试通过；本版未进行真实客户端视觉或完整联机验收。变更见 [26.3.19 说明](docs/26.3.19.md)。

可选打包入口为 `node project.cjs package`，默认输出至工作区 `deploy/`；用 `RIICHI_RELEASE_DIR` 可指定其他目录。`install-local.cjs` 仅供正常停服后的主测试服安装，构建与打包不会自动部署。

## 来源与许可

原项目为 [caxapexac/MinecraftRiichiMahjong](https://github.com/caxapexac/MinecraftRiichiMahjong)，固定提交 `fe469836ed461d44998819dd3004c8dea01f5be0`。完整快照和许可证保存在 `upstream/`，移植说明见 [PORTING.md](PORTING.md)。四麻 AI 使用 MIT 许可的 majiang-ai 1.3.0 和 majiang-core 1.3.4，源码及许可位于 `ai/`。
