# 绿色桌布与黑色陶瓦麻将材质

基于用户提供的 `D:/Download/TimoryResource (7).zip`，原始 SHA-1 为 `d50a9fd4ca8821729b31faed576c09f1e0b1294f`。原 ZIP 不修改。

| 部位 | 材质 |
| --- | --- |
| 桌布 | 绿色混凝土粉末 `green_concrete_powder`，每世界方块 64 像素 |
| 牌背 | 黄绿色混凝土 `lime_concrete` |
| 牌面白底、牌壳白色部分 | 平滑石英块 `quartz_block_bottom` |
| 桌体侧边 | 去皮红树原木 `stripped_mangrove_log` |
| 底座 | 黑色陶瓦 `black_terracotta`，每世界方块 32 像素 |
| 桌面边框上表面 | 平滑石英块 `quartz_block_bottom` |

原版平滑石英块模型实际引用 `quartz_block_bottom`。使用本地 Minecraft 26.3 客户端 JAR 的原版 16×16 像素纹理；若用户包覆盖了对应原版纹理则优先使用用户包。

桌体重复铺设原版材质，UV 按当前 1.5 倍水平显示缩放计算。桌布为绿色混凝土粉末，密度由上一版每世界方块 32 像素翻倍至 64 像素；原版 16×16 纹理在 256×256 贴图上重复铺设。底座由石头改为黑色陶瓦，密度由 16 翻倍至 32 像素，使用 128×128 重复贴图。两者像素边长均为上一版的一半，不做插值。木边、石英边框保持每世界方块 16 像素。桌体几何、碰撞、中央风盘与字样保留。牌壳保留 32×32 分辨率和白/绿分界；各牌面保持用户包的 256×256 分辨率和原有文字像素，只替换浅色中性底色，白板改为完整石英底色。纹理缩放只用最近邻，不平滑插值。

可重复生成覆盖资源（需要 Python 和 Pillow）：

```powershell
python retile-materials.py --base-pack 'D:/Download/TimoryResource (7).zip' --vanilla-jar ../../build/kaleidoscope/client/client.jar --cloth-density 64 --base-density 32 --output build/materials-green-terracotta/overrides
```

可维护副本保存在 `resource-overrides/assets/riichi_mahjong/` 及 `src/main/resources/assets/riichi_mahjong/`。当前完整合并包为 `build/materials-green-terracotta/TimoryResource-green-terracotta-26.3.zip`；相对于上一版只改变桌布、底座两个 PNG，其他文件逐字节保留。生成校验报告位于该目录的 `verification.json` 和 `overrides/material-verification.json`。

`material-preview.png` 是依据模型和 UV 生成的材质示意，未代替实际游戏客户端验收。运行时部署及备份位置见工作区 `run/single-server/resource-pack-installation.json`。
