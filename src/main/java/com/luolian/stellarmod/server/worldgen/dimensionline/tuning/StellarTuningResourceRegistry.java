package com.luolian.stellarmod.server.worldgen.dimensionline.tuning;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * 调谐资源注册表——存储所有可投入传送门的调谐资源定义。
 * <p>
 * 以<b>物品注册名</b>为主键（如 {@code minecraft:diamond}），
 * 统一承载两种来源的调谐资源：
 * <ul>
 *   <li><b>JSON 附加：</b>通过 {@code StellarTuningResourceLoader} 从数据包中加载，
 *       为已有物品（原版/模组）附加调谐能力</li>
 *   <li><b>代码注册：</b>模组自身的调谐专用物品（类似工具核心的矩阵物品），
 *       在物品注册时同步调用 {@link #register(StellarTuningResource)} 注册其调谐效果</li>
 * </ul>
 * <p>
 * 同一物品 ID 后续注册会覆盖前者，以后加载的为准。
 *
 * <h3>设计参考</h3>
 * 与 {@code MaterialManager} 同模式——以物品 ID 为主键、clear/register/get 三层结构，
 * 确保数据包重载时旧数据完全清理再重新加载。
 *
 * @see StellarTuningResource
 */
public class StellarTuningResourceRegistry {

    /** 核心映射：物品注册名 → 调谐资源（LinkedHashMap 保持注册顺序） */
    private static final Map<ResourceLocation, StellarTuningResource> BY_ITEM = new LinkedHashMap<>();

    // ═══════════════════════════════════════════════════════════════
    // 生命周期（由 Loader 或代码调用）
    // ═══════════════════════════════════════════════════════════════

    /** 清空所有已注册的调谐资源（在数据包重载前调用） */
    public static void clear() {
        BY_ITEM.clear();
    }

    /**
     * 注册一个调谐资源，以其内部的 {@code itemId} 为键。
     * <p>
     * 如果同一物品 ID 已有资源，后者会覆盖前者。
     * 若 {@code itemId} 格式非法，静默丢弃（不注册也不抛异常）。
     *
     * @param resource 调谐资源实例（内含物品 ID 和效果数组）
     */
    public static void register(StellarTuningResource resource) {
        ResourceLocation itemId = ResourceLocation.tryParse(resource.itemId());
        if (itemId != null) {
            //本质上 key 和 value.itemId 是同一份数据的不同表示——key 是 ResourceLocation 形式（方便 HashMap.get() 用），value 里存的是原始 String 形式
            BY_ITEM.put(itemId, resource);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 查询（以物品 ID 或物品实例为入口）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 根据物品注册名获取其调谐资源。
     *
     * @param itemId 物品的注册名（如 {@code minecraft:diamond}）
     * @return 对应的调谐资源，未注册返回 {@code null}
     */
    public static StellarTuningResource getById(ResourceLocation itemId) {
        return BY_ITEM.get(itemId);
    }

    /**
     * 根据物品实例获取其调谐资源。
     * <p>
     * 内部通过 {@link ForgeRegistries#ITEMS#getKey(Object)} 反查物品注册名，
     * 适用于手持物品、物品栏等已有 Item 实例的场景。
     *
     * @param item 物品实例
     * @return 对应的调谐资源，未注册返回 {@code null}
     */
    public static StellarTuningResource getByItem(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null ? BY_ITEM.get(id) : null;
    }

    /**
     * 判断指定物品是否已注册调谐资源。
     *
     * @param itemId 物品的注册名
     */
    public static boolean contains(ResourceLocation itemId) {
        return BY_ITEM.containsKey(itemId);
    }

    /**
     * 判断指定物品实例是否已注册调谐资源。
     */
    public static boolean contains(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null && BY_ITEM.containsKey(id);
    }

    /**
     * 获取所有已注册的调谐资源。
     *
     * @return 全部资源的不可修改集合，保持注册顺序
     */
    public static Collection<StellarTuningResource> getAll() {
        return Collections.unmodifiableCollection(BY_ITEM.values());
    }

    /**
     * 获取所有已注册调谐资源的物品 ID 集合。
     *
     * @return 全部物品 ID 的不可修改集合
     */
    public static Set<ResourceLocation> getAllItemIds() {
        return Collections.unmodifiableSet(BY_ITEM.keySet());
    }

    /** 已注册的调谐资源总数 */
    public static int size() {
        return BY_ITEM.size();
    }

    /** 注册表是否为空 */
    public static boolean isEmpty() {
        return BY_ITEM.isEmpty();
    }
}
