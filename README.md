# Timory Mahjong

Timory Mahjong 0.5.6 正式版是 Minecraft 26.3 Fabric 服务端麻将模组。玩家使用原版客户端并接受 TimoryResource 资源包。

生存模式下，麻将 Mod 物品只保留麻将桌的合成配方。麻将祭坛保留产出原版物品的转换配方。

支持雀魂、天凤、WRC、川麻、公式战和 M-League，可选择东风场或南风场。换三张、赖子和血战到底可独立开启。四位玩家还可在「玩法设置」各自同意，为下一场开启抽血模式。玩家可在牌桌菜单入座、观战和调整对局设置，输入 `/mahjong stats` 查看个人对局统计。

右键中心风盘，进入「个人设置」，点击“我的出牌方式”，可在右键直接出牌和二次确认之间切换。日麻吃、碰后和川麻碰后，暗杠或加杠须等自己再次摸牌。

使用牌谱物品可查看已完成的对局记录、回放牌局及分析。对局统计和回放按玩家权限显示；公式战积分暂不设升段。

雀魂、天凤和 M-League 的标准四人东风场和半庄使用本地 Mortal 模型打牌，牌谱回放也按该模型的合法弃牌权重评分。服务器需安装模型和推理程序。详见 [Mortal 安装与适用范围](riichi-mahjong-fabric/docs/0.5.4.md)。

服务端需要 Minecraft 26.3、Fabric API、Java 25 及 Node.js 22+；玩家无需安装客户端 Mod。

详细规则、统计、构建和许可信息见 [项目说明](riichi-mahjong-fabric/README.md)、[0.5.6 正式版更新说明](riichi-mahjong-fabric/docs/0.5.6.md) 和 [构建说明](riichi-mahjong-fabric/BUILDING.md)。
