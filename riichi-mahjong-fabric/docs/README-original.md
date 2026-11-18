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

日本立直麻将、3D 牌桌、私有手牌与原版客户端适配。当前版本：0.4.2+fabric.26.3.6。安装侧：**服务端**。

## 安装与操作

仅安装到 Fabric 服务端 mods/，同时安装 Fabric API；Polymer 已内嵌。玩家使用原版客户端并接受合并后的 TimoryResource。

`/mahjong` 查看操作，管理员 `/mahjong give` 领物品。右键桌边入座，潜行右键中央开局，右键自己的单张牌出牌；吃碰杠等使用本人按钮或私聊点击。

## 构建

在本项目目录执行：

```powershell
node project.cjs info
node project.cjs build
```

也可双击 `build.cmd`。在工作区根目录运行 `node scripts/project.cjs riichi-mahjong build` 等效。

Java 25、Fabric Loader 0.19.5、Fabric API 0.163.0+26.3。构建使用公共服务端依赖和 Polymer 0.18.7+26.3；能源互联可选 Team Reborn Energy API。

可用操作：`node project.cjs resources`。执行前可加 `--dry-run` 查看实际命令。

可用操作：`node project.cjs package`。执行前可加 `--dry-run` 查看实际命令。

已有打包入口的项目会保留原有发布校验条件；目录拆分不会自动运行验收脚本或跳过这些条件。

## 目录与依赖

本项目源码、配置模板及本地构建内容位于此目录。项目入口 `project.cjs` 根据公共 `../../projects/workspace.json` 调用现有工具。

- 公共 JDK 和依赖：`../../build/tools/`、公共构建缓存和 Minecraft 依赖。
- 公共脚本：`../../scripts/`。
- 统一材质包：`../../TimoryResource/`，发布时继续合并。
- 统一测试服与存档：`../../run/`。

这是工作区内按项目拆分的目录布局；单独复制本文件夹到其他电脑时，还需准备上述公共依赖。

## 发布文件

- [deploy/RiichiMahjong-0.4.2+fabric.26.3.6](../../deploy/RiichiMahjong-0.4.2+fabric.26.3.6/)

已有发布文件继续保存在公共 deploy/ 或原有项目 build/ 路径，避免已有下载路径、校验值和测试服配置变化。

## 维护说明

保留固定上游源码 upstream/ 与许可；中文覆盖位于 resource-overrides/。先执行 resources 再构建可重新生成合并资源。不要重新运行 prepare.cjs 或 adapt.cjs。完整牌局留在服务端，暗牌仅发送给所属玩家。GitHub 仓库：https://github.com/StealthFeather/riichi-mahjong-fabric 。

本次目录整理仅调整工程布局、路径和说明。玩法、视觉与协议验收由用户负责。
