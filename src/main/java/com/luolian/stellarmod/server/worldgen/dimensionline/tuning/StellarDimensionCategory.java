package com.luolian.stellarmod.server.worldgen.dimensionline.tuning;

import net.minecraft.network.chat.Component;

/**
 * 预生成维度的类别。
 * <p>
 * 每个存档预生成维度池中的每个维度都属于一个类别，
 * 类别在维度被发现时固定，用于稳定性检定失败时的同类回退匹配。
 * <p>
 * 每类维度至少保证预生成一个，确保玩家不会因随机数运衰而无法体验某类内容。
 */
public enum StellarDimensionCategory {

    /** 混沌类：高风险高回报，属性极端，事件频繁 */
    CHAOS("chaos"),

    /** 冒险类：注重探索与战斗，多 Boss/地牢/挑战 */
    ADVENTURE("adventure"),

    /** 挖矿类：资源丰富，矿石密度高，地形适合开采 */
    MINING("mining"),

    /** 探索类：广袤空间，隐藏废墟/遗迹，叙事驱动 */
    EXPLORATION("exploration"),

    /** 结构类：充满自然/人造建筑，建筑党天堂 */
    STRUCTURE("structure"),

    /** 大世界类：模拟主世界规模，综合型，面积广阔 */
    VAST_WORLD("vast_world");

    private final String id;

    StellarDimensionCategory(String id) {
        this.id = id;
    }

    public String id() { return id; }

    /** 获取用于 GUI 显示的翻译组件 */
    public Component getDisplayName() {
        return Component.translatable("stellarmod.category." + id);
    }

    /** 获取类别描述的翻译组件 */
    public Component getDescription() {
        return Component.translatable("stellarmod.category." + id + ".desc");
    }

    /** 根据字符串 ID 查找类别，找不到返回 {@code null} */
    public static StellarDimensionCategory byId(String id) {
        for (StellarDimensionCategory cat : values()) {
            if (cat.id.equals(id)) return cat;
        }
        return null;
    }
}
