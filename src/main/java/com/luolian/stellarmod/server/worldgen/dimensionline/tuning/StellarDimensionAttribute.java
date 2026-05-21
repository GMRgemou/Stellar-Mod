package com.luolian.stellarmod.server.worldgen.dimensionline.tuning;

import net.minecraft.network.chat.Component;

/**
 * 维度传送门调谐系统的七个核心属性。
 * <p>
 * 每个属性有固定的取值范围和默认值，供调谐 GUI 展示滑条、
 * 资源投入影响计算、以及维度生成参数推导使用。
 * <p>
 * 属性设计中不存在"纯正面"或"纯负面"属性——
 * 每个属性过低或过高都会带来不同的后果，玩家需要寻找平衡点。
 */
public enum StellarDimensionAttribute {

    /** 稳定性：锚定是否牢固。过低→崩塌/弹出；过高→固化/不可再调 */
    STABILITY(0, "stability", 0, 20, 10),

    /** 活性：绑定区域的生物活跃度。过低→死寂无生物；过高→狂暴入侵 */
    VITALITY(1, "vitality", 0, 20, 5),

    /** 共振度：两维度之间的能量共鸣强度。过低→传送消耗大/受伤；过高→边界模糊/内容泄漏 */
    RESONANCE(2, "resonance", 0, 20, 5),

    /** 扩张性：绑定区域对周边空间的挤压能力。过低→空间裂隙干扰传送；过高→挤压其他维度/触发跨维度事件 */
    EXPANSIVENESS(3, "expansiveness", 1, 20, 5),

    /** 亲和性：玩家与绑定区域的适应性。过低→持续 debuff；过高→强力 buff（伴随副作用） */
    AFFINITY(4, "affinity", 0, 10, 0),

    /**
     * 时间流速：绑定区域相对主世界的时间倍率。
     * 存储为定点整数，实际值 = {@code value / 10.0}，范围 0.1x ~ 10.0x。
     */
    TIME_RATE(5, "time_rate", 1, 100, 10),

    /** 法则强度：原版物理规则在绑定区域的有效程度。过低→物理异常；过高→规则过强（重力增加等） */
    LAW_STRENGTH(6, "law_strength", 0, 10, 10);

    // ═══════════════════════════════════════════════════════════════
    // 实例字段
    // ═══════════════════════════════════════════════════════════════

    /** 稳定索引，不依赖 {@link #ordinal()}（因为其顺序敏感、不可读、与外部存储不兼容），便于序列化和数组寻址 */
    private final int index;

    /** 翻译键后缀，完整键为 {@code stellarmod.attribute.<id>} */
    private final String id;

    /** 属性允许的最小值 */
    private final int min;

    /** 属性允许的最大值 */
    private final int max;

    /** 默认值，即传送门开启前的初始状态 */
    private final int defaultValue;

    StellarDimensionAttribute(int index, String id, int min, int max, int defaultValue) {
        this.index = index;
        this.id = id;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
    }

    // ═══════════════════════════════════════════════════════════════
    // 通用访问
    // ═══════════════════════════════════════════════════════════════

    public int index() { return index; }
    public String id() { return id; }
    public int min() { return min; }
    public int max() { return max; }
    public int defaultValue() { return defaultValue; }

    /** 属性值是否在其合法范围内 */
    public boolean isValid(int value) {
        return value >= min && value <= max;
    }

    /** 将给定值钳制到属性的合法范围内 */
    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }

    /** 当前值是否低于正常区间（偏低，可能触发负面效果） */
    public boolean isLow(int value) {
        return value < defaultValue;
    }

    /** 当前值是否高于正常区间（偏高，可能触发负面效果） */
    public boolean isHigh(int value) {
        return value > defaultValue;
    }

    /** 当前值是否正好在默认值（正常状态） */
    public boolean isNormal(int value) {
        return value == defaultValue;
    }

    /** 获取用于 GUI 显示的翻译组件 */
    public Component getDisplayName() {
        return Component.translatable("stellarmod.attribute." + id);
    }

    /** 获取用于 Tooltip 描述的翻译组件 */
    public Component getDescription() {
        return Component.translatable("stellarmod.attribute." + id + ".desc");
    }

    // ═══════════════════════════════════════════════════════════════
    // 静态工具
    // ═══════════════════════════════════════════════════════════════

    /** 属性总数 */
    public static final int COUNT = values().length;

    /** 根据稳定索引查找属性 */
    public static StellarDimensionAttribute byIndex(int index) {
        for (StellarDimensionAttribute attr : values()) {
            if (attr.index == index) return attr;
        }
        //运行时异常，表示向方法传递了一个不合法或不适当的参数
        throw new IllegalArgumentException("无效的属性索引: " + index);
    }

    /** 根据字符串 ID 查找属性，找不到返回 {@code null} */
    public static StellarDimensionAttribute byId(String id) {
        for (StellarDimensionAttribute attr : values()) {
            if (attr.id.equals(id)) return attr;
        }
        return null;
    }

    /** 创建一个以默认值填充的属性值数组（用于初始模板） */
    public static int[] createDefaultArray() {
        int[] arr = new int[COUNT];
        for (StellarDimensionAttribute attr : values()) {
            arr[attr.index] = attr.defaultValue;
        }
        return arr;
    }

    /**
     * 将一个可能不完整或越界的属性值数组规范化。
     * <p>
     * 处理两种情况：
     * <ul>
     *   <li><b>数组过短：</b>输入数组长度不足 7 时，缺失的属性位置以默认值填充</li>
     *   <li><b>数值越界：</b>每个属性值通过 {@link #clamp(int)} 钳制到 {@code [min, max]} 范围内</li>
     * </ul>
     * 始终返回一个长度 = {@link #COUNT} 的全新数组，不修改入参。
     *
     * @param values 原始属性值数组，可任意长度（通常来自 NBT 反序列化或外部输入）
     * @return 规范化后的属性值数组，长度固定为 7，所有值均在合法范围内
     */
    public static int[] clampAll(int[] values) {
        int[] clamped = new int[COUNT];                              //确保返回值长度 = 7
        for (StellarDimensionAttribute attr : values()) {            //遍历全部七个属性
            //数组够长→取对应索引的值；不够长→用该属性的默认值兜底
            int v = values.length > attr.index ? values[attr.index] : attr.defaultValue;
            clamped[attr.index] = attr.clamp(v);                     //钳制到 [min, max]
        }
        return clamped;
    }
}
