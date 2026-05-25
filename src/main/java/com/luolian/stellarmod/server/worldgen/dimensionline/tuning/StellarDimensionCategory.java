package com.luolian.stellarmod.server.worldgen.dimensionline.tuning;

import net.minecraft.network.chat.Component;

/**
 * 预生成维度的类别。
 * <p>
 * 共 3 个类别，每个存档预生成维度池中的每个维度都属于一个类别，
 * 类别在维度被发现时固定，用于稳定性检定失败时的同类回退匹配。
 *
 * <h3>生成数量</h3>
 * <ul>
 *   <li><b>混沌 (CHAOS)</b>：1 个——后续扩展为"大维度内含最多 100 个子维度"</li>
 *   <li><b>冒险世界 (ADVENTURE)</b>：2~4 个——含原大世界内容，探索与战斗主题</li>
 *   <li><b>采矿 (MINING)</b>：1 个——资源丰富，矿石密度高</li>
 * </ul>
 */
public enum StellarDimensionCategory {

    /** 混沌类：高风险高回报，属性极端，事件频繁。含虚空内容，后续扩展为混沌大维度内含子维度 */
    CHAOS("chaos"),

    /** 冒险世界类：合并了原大世界类，注重探索与战斗，Boss/地牢/遗迹/建筑综合主题 */
    ADVENTURE("adventure"),

    /** 采矿类：资源丰富，矿石密度高，地形适合开采 */
    MINING("mining");

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
