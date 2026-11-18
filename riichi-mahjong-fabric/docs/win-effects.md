# 可替换胡牌特效

桌主在开局前打开牌桌设置，点击“胡牌特效”切换。默认是“唤魔尖牙”，也可以选择“直接摊牌”。选择按牌桌保存，三麻、四麻及川麻共用接口，无需增加客户端模组或材质。

## 第一套：唤魔尖牙

20 tick = 1 秒，全程 64 tick（3.2 秒）。

| 时间 | 表现 |
| --- | --- |
| 0–8 tick | 在实际胡牌来源处唤出原版尖牙，手牌保持竖立 |
| 8–18 tick | 尖牙咬合，将来源牌缩小并拉入桌面 |
| 18–24 tick | 来源牌消失，保留短暂停顿 |
| 24–44 tick | 胡牌张从赢家最右摸牌位下方浮到桌上，保持竖立 |
| 44–48 tick | 胡牌张停稳 |
| 48–60 tick | 赢家整排暗手牌及胡牌张一起推倒，落到现有贴桌面布局 |
| 60–64 tick | 保持最终姿态，然后开始番符/役种结算；川麻继续剩余玩家回合 |

自摸使用本人摸牌位；荣和使用实际可见牌河末张；抢杠/抢北使用来源玩家右侧持牌位。一炮多响只吞一次来源牌，在每位赢家处分别浮牌和推倒。副露保持原有公开布局。被吞的河牌只从显示中隐藏，牌河槽位保留，其余牌不会移动，规则数据和分数不变。

尖牙通过三个可缩放的 Polymer 物品展示实体组成牙床和双颚，沿用原版尖牙纹理和咬合动作；完整模型最大高度为一张竖立麻将牌的 8/3（当前约 0.35 格），为 beta.1 的两倍。原版尖牙实体没有缩放元数据，因此不再发送原始大小的尖牙实体。需要更新本版资源包中的 `win_fangs_base` 和 `win_fangs_jaw` 模型。展示实体不加入服务端世界，不造成伤害或仇恨，随牌桌显示清理。晚进入观看范围的玩家看到当前牌面帧；已过去的声音不会重放。

## 接口与职责

源码入口为 `src/main/java/com/riichimahjong/fabric/wineffect/`。

| 类型 | 职责 |
| --- | --- |
| `WinEffect` | 无状态定义：ID、显示名、时长、逐帧姿态、一次性事件、是否吞掉来源牌 |
| `WinEffects` | 注册和查询。未知存档 ID 回退默认，拒绝重复 ID 和超出范围的时长/事件 |
| `WinEffectContext` | 一次已接受胡牌的公开来源和赢家列表，保留赤五牌面身份；不包含规则驱动器、牌山或其他玩家暗牌 |
| `WinEffectFrame` | 来源牌、目标胡牌张的可见性/位移/缩放/倾倒程度，以及整手倾倒程度 |
| `WinEffectCue` | 在指定 tick 对来源或每位赢家触发实体/声音 |
| `WinEffectSession` | 一次播放的上下文、定义和 tick 计数，结束后由牌桌收尾 |
| `WinEffectTriggers` | 将日麻胡牌或川麻本次新增赢家转换为展示上下文 |
| `WinEffectRenderer` | 将姿态转换为 Minecraft/Polymer 显示并清理临时实体 |

位移单位是世界方块，Y 向上；`scale` 是现有麻将牌尺寸的倍率；`flip`/`handReveal` 从 0（竖立）到 1（面朝上平放）。来源姿态以来源当前朝向为基准。渲染器根据桌子朝向计算各座位位置，保持胡牌张右侧 1/3 牌宽的间隔，并在倾倒时按模型支撑点调整高度。

目前实体事件支持 `minecraft:evoker_fangs`；声音支持原版声音注册 ID。新增其他种类实体/粒子时，在渲染适配层添加对应事件支持，无需修改麻将规则。

## 添加新特效

实现 `WinEffect`，在服务器初始化时调用一次 `WinEffects.register(...)`。注册完成后牌桌菜单自动列入。时长限制为 1–200 tick，每个事件必须在播放时长内；自定义特效应确保最后一帧与正常摊牌布局一致。

```java
public final class QuickReveal implements WinEffect {
    public String id() { return "my_mod:quick_reveal"; }
    public String displayName() { return "快速摊牌"; }
    public int durationTicks() { return 12; }
    public boolean consumesSource() { return false; }
    public WinEffectFrame frame(WinEffectContext context, int tick) {
        double reveal = Math.clamp(tick / 10.0, 0, 1);
        return new WinEffectFrame(WinEffectFrame.TilePose.HIDDEN,
            new WinEffectFrame.TilePose(true, 0, 0, 0, 1, reveal), reveal);
    }
}
// 在 ModInitializer 中注册：
WinEffects.register(new QuickReveal());
```

特效必须是展示逻辑，不修改手牌、分数或发放奖励。现有 `MahjongWinEffects` 继续负责奖励、成就和结算声音，仅在原结算阶段执行一次。

## 生命周期与验证

胡牌规则先提交，再启动显示。动画期间暂停驱动器推进和玩家动作；川麻驱动器在一次新增胡牌后停止本帧推进，避免已经进入下一个摸打流程。后续继续血战时仅为新赢家播放，最终结算不重播旧胡牌。

每手结束或开新手会清理相应临时状态。卸载/移除终止动画，读档收敛到最终布局，不从半段重放；来源河牌的隐藏槽位和最终赢家布局随存档保存。重置/结束对局清空本手特效状态，保留牌桌特效选项。

执行 `node project.cjs build` 和 `node test.cjs`。`WinEffectRegression` 检查时间线和注册契约；`WinEffectTriggersRegression` 检查日麻/川麻事件、同帧 BOT 胡牌、多人胡牌和牌面身份。Fabric 启动探针只验证运行时实体/包和存档行为；客户端的真实尖牙大小、咬合时机及视觉连贯性仍需进服检查。
