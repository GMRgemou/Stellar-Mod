package com.luolian.stellarmod.server.worldgen.dimensionline.tuning;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.Objects;

/**
 * 传送门调谐资源。
 * <p>
 * 每种调谐资源对应一种可投入传送门的物品，投入后会对维度的七个属性
 * 施加不同的影响向量——某些属性上升，某些下降。
 * <p>
 * 玩家无法通过单一资源将所有属性拉满，必须组合多种资源互相牵制，
 * 这就是调谐系统的核心博弈。
 *
 * @param itemId  物品的注册名（{@code modid:path}）
 * @param effects 对七个属性的影响值数组，索引为 {@link StellarDimensionAttribute#index()}
 */
public record StellarTuningResource(
        String itemId,
        int[] effects
) {

    /**
     * 传送门调谐资源<br>
     * 此处采用 Record 紧凑构造器：在字段自动赋值前进行校验和预处理。
     * <ul>
     *   <li>校验 {@code itemId} 非空，为 null 则立即抛出 NullPointerException</li>
     *   <li>补全效果数组到 7 个元素——调用方可只传有影响的几个属性，
     *       其余位置自动补 0（表示无影响）</li>
     * </ul>
     */
    public StellarTuningResource {
        Objects.requireNonNull(itemId, "itemId 不能为 null");
        //传入数组不足 7 位时扩容，新增元素自动初始化为 0
        if (effects.length < StellarDimensionAttribute.COUNT) {
            //Arrays.copyOf(original, newLength) 会复制原数组，并根据 newLength 调整大小：
            //如果 newLength > 原长度 → 多余位置用默认值（如 null、0，此处为0）填充。
            //如果 newLength < 原长度 → 截断
            effects = Arrays.copyOf(effects, StellarDimensionAttribute.COUNT);
        }
    }

    /**
     * 获取该资源对指定属性的影响值。正数表示增加，负数表示减少，0 表示无影响。
     */
    public int getEffect(StellarDimensionAttribute attr) {
        return effects[attr.index()];
    }

    /**
     * 该资源是否对任何属性产生非零影响。
     */
    public boolean hasAnyEffect() {
        for (int e : effects) {
            if (e != 0) return true;
        }
        return false;
    }

    /**
     * 计算将该资源投入当前属性值后的结果数组。
     * <p>
     * 对七个属性逐一执行：当前值 + 材料影响值 → 钳制到 {@code [min, max]}。
     * 返回全新数组，不修改入参（适合 GUI 预览）。
     *
     * @param current 当前属性值数组
     * @return 投入后的新属性值数组（不影响原数组）
     */
    public int[] apply(int[] current) {
        int[] adjusted = Arrays.copyOf(current, StellarDimensionAttribute.COUNT);
        for (StellarDimensionAttribute attr : StellarDimensionAttribute.values()) {
            int idx = attr.index();
            int newValue = adjusted[idx] + getEffect(attr);
            adjusted[idx] = attr.clamp(newValue);
        }
        return adjusted;
    }

    /**
     * 计算将该资源投入当前属性值后，指定属性的变化量。
     *
     * @param current 当前属性值
     * @param attr    目标属性
     * @return 投入后的属性值
     */
    public int applyToAttribute(int current, StellarDimensionAttribute attr) {
        return attr.clamp(current + getEffect(attr));
    }

    /**
     * 尝试从 Forge 物品注册表中解析物品。
     *
     * @return 注册表中的物品实例，若未找到则返回 {@code null}
     */
    public Item resolveItem() {
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null) return null;
        return ForgeRegistries.ITEMS.getValue(loc);
    }

    // ═══════════════════════════════════════════════════════════════
    // 工厂方法
    // ═══════════════════════════════════════════════════════════════

    /** 快捷工厂：从物品 ID 和效果数组创建调谐资源 */
    public static StellarTuningResource of(String itemId, int... effects) {
        return new StellarTuningResource(itemId, effects);
    }
}
