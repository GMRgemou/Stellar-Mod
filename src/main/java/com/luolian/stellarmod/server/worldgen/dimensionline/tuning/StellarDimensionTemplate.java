package com.luolian.stellarmod.server.worldgen.dimensionline.tuning;

import com.luolian.stellarmod.StellarMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * 传送门锚定状态。
 * <p>
 * 当玩家建造传送门并选定目标维度后，通过投入调谐资源影响七个属性，
 * 最终进行稳定性检定来决定链接结果。
 * <p>
 * 七个属性表示"传送门连接质量"，而非维度生成参数：
 * <ul>
 *   <li>{@link StellarDimensionAttribute#STABILITY 稳定性} —— 检定核心，决定能否成功链接</li>
 *   <li>其余六个属性 —— 影响链接后的传送消耗、buff/debuff、事件频率等运行时效果</li>
 * </ul>
 *
 * <h3>稳定性检定规则</h3>
 * <ul>
 *   <li>稳定性 ≥ 默认值 (10) → {@link LinkResult#SUCCESS 成功连接选定维度}</li>
 *   <li>稳定性 ≥ 5 → {@link LinkResult#SAME_CATEGORY 回退到同类别的其他维度}</li>
 *   <li>稳定性 < 5 → {@link LinkResult#RANDOM 随机连接到任意维度}</li>
 * </ul>
 *
 * <h3>NBT 结构</h3>
 * <pre>{@code
 * {
 *   "id": "stellarmod:anchor_xxx",
 *   "sourceDimension": "minecraft:overworld",
 *   "targetDimension": "stellarmod:preset_chaos_01",
 *   "attributes": [I;10,5,5,5,0,10,10],
 *   "isPlayerCreated": false
 * }
 * }</pre>
 */
public class StellarDimensionTemplate {

    // ═══════════════════════════════════════════════════════════════
    // 稳定性检定的结果枚举
    // ═══════════════════════════════════════════════════════════════

    /** 稳定性检定的三种可能结果 */
    public enum LinkResult {
        /** 成功链接到玩家选定的维度 */
        SUCCESS,
        /** 稳定性不足但不太低：回退到与原目标同类别的其他维度 */
        SAME_CATEGORY,
        /** 稳定性极低：随机链接到任意维度 */
        RANDOM
    }

    /** 稳定性检定的成功阈值（≥ 此值即成功） */
    public static final int STABILITY_SUCCESS_THRESHOLD = 10;

    /** 稳定性检定的同类回退阈值（≥ 此值但不足成功时回退同类） */
    public static final int STABILITY_SAME_CATEGORY_THRESHOLD = 5;

    // ═══════════════════════════════════════════════════════════════
    // 实例字段
    // ═══════════════════════════════════════════════════════════════

    /** 锚定状态唯一标识 */
    private ResourceLocation id;

    /** 绑定源维度（通常是主世界） */
    private final ResourceLocation sourceDimension;

    /** 绑定的目标维度（预生成或自制） */
    private final ResourceLocation targetDimension;

    /** 目标维度的类别（仅预生成维度有值，自制维度为 null） */
    //标记可能为 null
    @Nullable
    private final StellarDimensionCategory targetCategory;

    /** 是否为玩家自制维度（不计入存档预生成上限） */
    private final boolean isPlayerCreated;

    /**
     * 七个连接质量属性的当前值。
     * 索引由 {@link StellarDimensionAttribute#index()} 决定。
     */
    //final 的是"引用"，不是"数组内容"
    private final int[] attributes;

    // ═══════════════════════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════════════════════

    /**
     * 为预生成维度创建锚定状态。
     */
    public StellarDimensionTemplate(ResourceLocation id,
                                    ResourceLocation sourceDimension,
                                    StellarPresetDimension target) {
        this.id = Objects.requireNonNull(id, "锚定 ID 不能为 null");
        this.sourceDimension = Objects.requireNonNull(sourceDimension, "源维度不能为 null");
        this.targetDimension = target.getId();  //存储了明确的预设维度 ID
        this.targetCategory = target.getCategory(); //存储类别，仅用于回退
        this.isPlayerCreated = false;
        this.attributes = StellarDimensionAttribute.createDefaultArray();
    }

    /**
     * 为自制维度创建锚定状态。
     */
    public StellarDimensionTemplate(ResourceLocation id,
                                    ResourceLocation sourceDimension,
                                    ResourceLocation targetDimension) {
        this.id = Objects.requireNonNull(id, "锚定 ID 不能为 null");
        this.sourceDimension = Objects.requireNonNull(sourceDimension, "源维度不能为 null");
        this.targetDimension = Objects.requireNonNull(targetDimension, "目标维度不能为 null");
        this.targetCategory = null;
        this.isPlayerCreated = true;
        this.attributes = StellarDimensionAttribute.createDefaultArray();
    }

    /**
     * 完整构造器（供 NBT 反序列化使用）。
     */
    private StellarDimensionTemplate(ResourceLocation id,
                                     ResourceLocation sourceDimension,
                                     ResourceLocation targetDimension,
                                     @Nullable StellarDimensionCategory targetCategory,
                                     boolean isPlayerCreated,
                                     int[] attributes) {
        this.id = Objects.requireNonNull(id);
        this.sourceDimension = Objects.requireNonNull(sourceDimension);
        this.targetDimension = Objects.requireNonNull(targetDimension);
        this.targetCategory = targetCategory;
        this.isPlayerCreated = isPlayerCreated;
        this.attributes = StellarDimensionAttribute.clampAll(attributes);
    }

    // ═══════════════════════════════════════════════════════════════
    // 稳定性检定
    // ═══════════════════════════════════════════════════════════════

    /**
     * 执行稳定性检定，返回链接结果。
     * <p>
     * 检定仅依赖 {@link StellarDimensionAttribute#STABILITY 稳定性} 属性值：
     * <ul>
     *   <li>≥ {@value #STABILITY_SUCCESS_THRESHOLD} → 成功</li>
     *   <li>≥ {@value #STABILITY_SAME_CATEGORY_THRESHOLD} → 同类回退</li>
     *   <li>&lt; {@value #STABILITY_SAME_CATEGORY_THRESHOLD} → 随机</li>
     * </ul>
     *
     * @return 检定结果
     */
    public LinkResult checkStability() {
        int stability = get(StellarDimensionAttribute.STABILITY);
        if (stability >= STABILITY_SUCCESS_THRESHOLD) {
            return LinkResult.SUCCESS;
        }
        if (stability >= STABILITY_SAME_CATEGORY_THRESHOLD) {
            return LinkResult.SAME_CATEGORY;
        }
        return LinkResult.RANDOM;
    }

    /**
     * 快捷方法：稳定性是否足够直接链接选定维度。
     */
    public boolean isStable() {
        return checkStability() == LinkResult.SUCCESS;
    }

    // ═══════════════════════════════════════════════════════════════
    // 属性读写
    // ═══════════════════════════════════════════════════════════════

    /** 获取指定属性的当前值 */
    public int get(StellarDimensionAttribute attr) {
        return attributes[attr.index()];
    }

    /** 设置指定属性的值（自动钳制） */
    public void set(StellarDimensionAttribute attr, int value) {
        attributes[attr.index()] = attr.clamp(value);
    }

    /** 对指定属性进行偏移（正数增加，负数减少），自动钳制 */
    public void adjust(StellarDimensionAttribute attr, int delta) {
        int idx = attr.index();
        attributes[idx] = attr.clamp(attributes[idx] + delta);
    }

    /** 将所有属性重置为默认值 */
    public void resetToDefaults() {
        for (StellarDimensionAttribute attr : StellarDimensionAttribute.values()) {
            attributes[attr.index()] = attr.defaultValue();
        }
    }

    /** 获取所有属性值的只读副本 */
    public int[] getAttributes() {
        return Arrays.copyOf(attributes, StellarDimensionAttribute.COUNT);
    }

    // ═══════════════════════════════════════════════════════════════
    // 阈值判定
    // ═══════════════════════════════════════════════════════════════

    /** 指定属性是否处于偏低状态（可能触发负面效果） */
    public boolean isLow(StellarDimensionAttribute attr) {
        return attr.isLow(get(attr));
    }

    /** 指定属性是否处于偏高状态（可能触发负面效果） */
    public boolean isHigh(StellarDimensionAttribute attr) {
        return attr.isHigh(get(attr));
    }

    /** 指定属性是否正好在默认值 */
    public boolean isNormal(StellarDimensionAttribute attr) {
        return attr.isNormal(get(attr));
    }

    /** 模板中是否有任何属性处于偏低或偏高状态 */
    public boolean hasAnyDeviation() {
        for (StellarDimensionAttribute attr : StellarDimensionAttribute.values()) {
            int v = get(attr);
            if (attr.isLow(v) || attr.isHigh(v)) return true;
        }
        return false;
    }

    /** 是否尚未投入任何调谐资源 */
    public boolean isUntuned() {
        for (StellarDimensionAttribute attr : StellarDimensionAttribute.values()) {
            if (!attr.isNormal(get(attr))) return false;
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // 调谐方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 预览投入调谐资源后的属性值，不修改模板自身（供 GUI 使用）。
     *
     * @param resource 投入的调谐资源
     * @return 投入后的属性值数组
     */
    public int[] preview(StellarTuningResource resource) {
        return resource.apply(attributes);
    }

    /**
     * 实际投入调谐资源，将计算后的属性值原地覆盖写入 {@code attributes}。
     * <p>
     * 使用 {@link System#arraycopy} 而非直接赋值 {@code attributes = adjusted}，
     * 因为 {@code attributes} 字段为 {@code final}，不允许替换引用，只能原地修改元素。
     * arraycopy 是 JVM native 方法，比手动 for 循环逐个赋值更高效。
     */
    public void commit(StellarTuningResource resource) {
        //将调谐资源的效果应用到属性数组的副本上（先不直接修改原数组）
        int[] adjusted = resource.apply(attributes);
        //形参含义：源数组 从源数组第0位开始读 目标数组 从目标数组第0位开始写 复制7个元素
        //把 adjusted 的全部 7 个属性值覆盖写入 attributes 对应位置
        System.arraycopy(adjusted, 0, attributes, 0, StellarDimensionAttribute.COUNT);
    }

    // ═══════════════════════════════════════════════════════════════
    // 元数据访问
    // ═══════════════════════════════════════════════════════════════

    public ResourceLocation getId() { return id; }
    public ResourceLocation getSourceDimension() { return sourceDimension; }
    public ResourceLocation getTargetDimension() { return targetDimension; }
    @Nullable public StellarDimensionCategory getTargetCategory() { return targetCategory; }
    public boolean isPlayerCreated() { return isPlayerCreated; }

    /** 修改锚定 ID（通常用于另存副本） */
    public void setId(ResourceLocation id) {
        this.id = Objects.requireNonNull(id);
    }

    // ═══════════════════════════════════════════════════════════════
    // NBT 序列化
    // ═══════════════════════════════════════════════════════════════

    /** 将锚定状态序列化为 NBT */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putString("sourceDimension", sourceDimension.toString());
        tag.putString("targetDimension", targetDimension.toString());
        if (targetCategory != null) {
            tag.putString("targetCategory", targetCategory.id());
        }
        tag.putBoolean("isPlayerCreated", isPlayerCreated);
        tag.put("attributes", new IntArrayTag(Arrays.copyOf(attributes, StellarDimensionAttribute.COUNT)));
        return tag;
    }

    /** 从 NBT 反序列化锚定状态 */
    public static StellarDimensionTemplate fromNBT(@NotNull CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        if (id == null) id = StellarMod.location("unknown");

        ResourceLocation source = ResourceLocation.tryParse(tag.getString("sourceDimension"));
        if (source == null) source = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

        ResourceLocation target = ResourceLocation.tryParse(tag.getString("targetDimension"));
        if (target == null) target = StellarMod.location("space_dimension");

        StellarDimensionCategory category = null;
        if (tag.contains("targetCategory")) {
            category = StellarDimensionCategory.byId(tag.getString("targetCategory"));
        }

        boolean isPlayerCreated = tag.getBoolean("isPlayerCreated");

        int[] attributes;
        if (tag.contains("attributes", Tag.TAG_INT_ARRAY)) {
            attributes = tag.getIntArray("attributes");
        } else {
            attributes = StellarDimensionAttribute.createDefaultArray();
        }

        return new StellarDimensionTemplate(id, source, target, category, isPlayerCreated, attributes);
    }

    // ═══════════════════════════════════════════════════════════════
    // equals / hashCode / toString
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StellarDimensionTemplate that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * 返回利于调试的可读字符串。
     * <p>
     * 格式：{@code StellarDimensionTemplate{id=..., category=..., stability=10, activity=5, ...}}
     * <p>
     * 使用 {@link StringBuilder} 而非 {@code +} 拼接，因为循环拼接 7 个属性时
     * {@code +} 每次都会创建新的中间 {@link String} 对象，{@code StringBuilder} 只维护一个内部缓冲区，效率更高。
     */
    @Override
    public String toString() {
        // 预估容量约 80 字符，StringBuilder 避免多次扩容
        StringBuilder sb = new StringBuilder("StellarDimensionTemplate{");
        sb.append("id=").append(id);
        // 预设维度显示类别 ID，自制维度 targetCategory 为 null → 显示"玩家自制"
        sb.append(", category=").append(targetCategory != null ? targetCategory.id() : "玩家自制");
        // 遍历全部 7 个属性，格式：attrName=attrValue
        for (StellarDimensionAttribute attr : StellarDimensionAttribute.values()) {
            sb.append(", ").append(attr.id()).append("=").append(get(attr));
        }
        sb.append('}');
        return sb.toString();
    }
}
