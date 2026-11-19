# 可替换胡牌特效

右键中心风盘，进入「个人设置」选择自己的胡牌效果。默认是「唤魔尖牙」，另有「末影人搬牌」「监守者尖啸」和「直接摊牌」。选择按玩家保存，适用于三麻、四麻及川麻。

## 荣和

三种动画的前 12 tick（0.6 秒）都用于推倒赢家的暗手牌。这时点炮牌仍在打出者的牌河原格，赢家右侧摸切位留空。自第 14 tick 起播放所选演出，最终把同一牌面的荣和牌平放在摸切位，保留与手牌之间的间隔。抢杠或抢北时，起点取自被抢玩家的持牌位。

| 荣和特效 | 动作 |
| --- | --- |
| 末影人搬牌 · 2.5 秒 | 末影人瞬移到牌河，抱起点炮牌，带牌瞬移到赢家右侧，放下后消失。 |
| 唤魔尖牙 · 3.3 秒 | 唤魔师在摸切位举手施法，牌河原格的尖牙合拢吞牌。唤魔师消失后，荣和牌从摸切位正下方浮出。 |
| 监守者尖啸 · 4.2 秒 | 监守者面向赢家，从点炮牌中心靠后一张牌宽处竖直爬出，蓄力 34 tick 后发动尖啸。声波命中点炮牌的同一 tick，牌沿弧线飞向摸切位。 |

一炮多响共用同一张点炮牌。牌河原格只播放第一位启用动画的赢家所选的来源动作，手牌端按各自设置播放。每位赢家的荣和牌落在自己的摸切位。搬走的河牌只在显示中隐藏，河牌槽位保留，副露和其余弃牌保持原位。三种生物使用缩放的原版实体，尖牙和声波由微缩展示实体组成，随动画清理。

## 自摸

选用任一实体动画时，自摸沿用 64 tick 的尖牙演出。尖牙在本人的摸牌位吞牌，和牌张从赢家最右摸牌位下方浮起，随后整手推倒。「直接摊牌」可同时跳过自摸和荣和演出。

## 接口与职责

源码入口为 `src/main/java/com/riichimahjong/fabric/wineffect/`。

| 类型 | 职责 |
| --- | --- |
| `WinEffect` | 无状态定义：ID、显示名、时长、逐帧姿态、一次性事件、是否吞掉来源牌 |
| `WinEffects` | 注册和查询。未知存档 ID 回退默认，拒绝重复 ID 和超出范围的时长/事件 |
| `WinEffectContext` | 一次已接受胡牌的公开来源和赢家列表，保留赤五牌面身份；不包含规则驱动器、牌山或其他玩家暗牌 |
| `WinEffectFrame` | 来源牌与目标牌的姿态、整手倾倒程度、两牌位之间的飞行进度，以及当前可见的实体和动作 |
| `WinEffectCue` | 在指定 tick 对来源或每位赢家触发实体/声音 |
| `WinEffectSession` | 一次播放的上下文、定义和 tick 计数，结束后由牌桌收尾 |
| `WinEffectTriggers` | 将日麻胡牌或川麻本次新增赢家转换为展示上下文 |
| `WinEffectRenderer` | 将姿态转换为 Minecraft/Polymer 显示并清理临时实体 |

位移单位是世界方块，Y 向上。`scale` 是现有麻将牌尺寸的倍率，`flip` 和 `handReveal` 从 0（竖立）到 1（面朝上平放）。`travel` 从 0（来源牌位）到 1（赢家摸切位）。渲染器按桌子朝向定位，在摸切位与左侧邻牌之间保留 1/3 牌宽的间隔，推倒时按模型支撑点调整高度。

荣和的实体由 `WinEffectFrame.actors` 描述，渲染器按当前帧创建、更新和移除。声音通过 `WinEffectCue` 播放一次。自摸沿用 `minecraft:evoker_fangs` 一次性实体事件。

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

胡牌规则先提交，再启动显示。动画期间暂停驱动器推进和玩家动作。血战到底在整手结束后按和牌顺序播放，保留打出时记录的来源牌位。

每手结束或开新手会清理相应临时状态。卸载/移除终止动画，读档收敛到最终布局，不从半段重放；来源河牌的隐藏槽位和最终赢家布局随存档保存。重置/结束对局清空本手特效状态，保留牌桌特效选项。

执行 `node project.cjs build` 和 `node test.cjs`。`RonWinEffectRegression` 检查三种荣和动画的动作顺序与落点。`WinEffectRegression` 检查自摸时间线和注册契约。`WinEffectTriggersRegression` 检查日麻与川麻事件、同帧 BOT 胡牌、多人胡牌和牌面身份。Fabric 启动探针检查运行时实体、网络包和存档行为。
