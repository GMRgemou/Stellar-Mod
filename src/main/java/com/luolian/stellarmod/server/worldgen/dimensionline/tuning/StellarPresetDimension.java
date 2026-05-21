package com.luolian.stellarmod.server.worldgen.dimensionline.tuning;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Random;

/**
 * 预生成维度——存档创建时生成的维度元数据。
 * <p>
 * 每个预生成维度在存档创建时即确定其类别、生成种子和固定变量，
 * 玩家通过研究设备逐步发现这些维度，然后选择锚定。
 * <p>
 * 与"自制维度"不同：预生成维度的元数据不可被玩家修改，
 * 且每个存档中预生成维度的数量有上限。
 *
 * <h3>NBT 结构</h3>
 * <pre>{@code
 * {
 *   "id": "stellarmod:preset_chaos_01",
 *   "category": "chaos",
 *   "discovered": true,
 *   "generationSeed": 1234567890
 * }
 * }</pre>
 */
public class StellarPresetDimension {

    /** 预生成维度唯一标识 */
    private final ResourceLocation id;

    /** 维度类别，创建时固定，不可修改 */
    private final StellarDimensionCategory category;

    /** 是否已被玩家发现 */
    private boolean discovered;

    /**
     * 维度生成种子，存档创建时随机生成。
     * 用于后续 ChunkGenerator 决定地形、生物群系等——同一存档同一维度始终相同。
     */
    private final long generationSeed;

    // ═══════════════════════════════════════════════════════════════
    // 构造
    // ═══════════════════════════════════════════════════════════════

    /**
     * @param id       预生成维度唯一 ID
     * @param category 维度类别（不可修改）
     * @param seed     生成种子
     */
    public StellarPresetDimension(ResourceLocation id, StellarDimensionCategory category, long seed) {
        this.id = Objects.requireNonNull(id, "预生成维度 ID 不能为 null");
        this.category = Objects.requireNonNull(category, "维度类别不能为 null");
        this.generationSeed = seed;
        this.discovered = false;
    }

    /**
     * 为指定类别生成一个新维度，种子随机。
     *
     * @param id       维度 ID
     * @param category 维度类别
     * @param random   随机数生成器
     */
    public static StellarPresetDimension generate(ResourceLocation id, StellarDimensionCategory category, Random random) {
        return new StellarPresetDimension(id, category, random.nextLong());
    }

    // ═══════════════════════════════════════════════════════════════
    // 访问器
    // ═══════════════════════════════════════════════════════════════

    public ResourceLocation getId() { return id; }
    public StellarDimensionCategory getCategory() { return category; }
    public long getGenerationSeed() { return generationSeed; }
    public boolean isDiscovered() { return discovered; }

    /** 标记为已发现（不可逆） */
    public void markDiscovered() {
        this.discovered = true;
    }

    // ═══════════════════════════════════════════════════════════════
    // NBT 序列化
    // ═══════════════════════════════════════════════════════════════

    /** 序列化为 NBT（存档持久化） */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putString("category", category.id());
        tag.putBoolean("discovered", discovered);
        tag.putLong("generationSeed", generationSeed);
        return tag;
    }

    /** 从 NBT 反序列化 */
    public static StellarPresetDimension fromNBT(@NotNull CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        if (id == null) throw new IllegalArgumentException("NBT 中缺少有效的 'id' 字段");

        StellarDimensionCategory category = StellarDimensionCategory.byId(tag.getString("category"));
        if (category == null) throw new IllegalArgumentException("未知的维度类别: " + tag.getString("category"));

        long seed = tag.getLong("generationSeed");
        StellarPresetDimension dim = new StellarPresetDimension(id, category, seed);
        dim.discovered = tag.getBoolean("discovered");
        return dim;
    }

    // ═══════════════════════════════════════════════════════════════
    // equals / hashCode / toString
    // ═══════════════════════════════════════════════════════════════

    /**
     * 基于 {@code id} 判断相等性。
     * <ol>
     *   <li>{@code this == o} —— 引用相同直接返回 {@code true}（短路优化）</li>
     *   <li>{@code instanceof StellarPresetDimension} —— 类型不匹配或为 null 则返回 {@code false}</li>
     *   <li>{@code id.equals(that.id)} —— 同一 ID 视为同一预设维度</li>
     * </ol>
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                          //同一个对象引用
        if (!(o instanceof StellarPresetDimension that)) return false; //类型检查（隐含 null 检查）
        return id.equals(that.id);                           //核心标识字段比较
    }

    /**
     * 委托给 {@code id.hashCode()}。
     * <p>
     * 必须与 {@link #equals(Object)} 保持一致：
     * {@code a.equals(b) → a.hashCode() == b.hashCode()}。
     * 违反此契约会导致 {@link java.util.HashMap}、{@link java.util.HashSet}
     * 等基于哈希的集合行为异常（如 put 后 get 不到）。
     *
     * <h3>"put 后 get 不到" 是什么现象？</h3>
     * HashMap 内部查找分两步：<b>先用 hashCode 定位桶，再用 equals 验证</b>。
     * 如果 hashCode 不同，直接去了错误的桶，equals 根本没机会被调用。
     *
     * <pre>{@code
     * // 场景：从 NBT 加载出「同一个维度」的新对象，尝试从 Map 中取回数据
     *
     * var dim1 = new StellarPresetDimension(id, CHAOS, 12345L);  // 放入 Map 的那个
     * var dim2 = StellarPresetDimension.fromNBT(savedTag);        // 从 NBT 加载的新对象
     *
     * // dim1.equals(dim2) == true   ← id 相同，equals 判定相等
     * // dim1.hashCode()  → 71982345 ← 如果 hashCode 继承自 Object（内存地址）
     * // dim2.hashCode()  → 12837641 ← 不同对象的地址哈希完全不一样
     *
     * HashMap<String, Dimension> map = new HashMap<>();
     * map.put(dim1, "混沌之门");           // dim1 存入 bucket[71982345 % 16] = bucket[7]
     * String result = map.get(dim2);       // dim2 去 bucket[12837641 % 16] = bucket[3] 找
     *
     * //dim2.hashCode() % 16 = 3，去这个桶找，遍历桶内链表，逐个用 equals 比较...
     *            ├──────────────┤
     *            │  bucket[7]   │ ← dim1 实际存放在这里 (71982345 % 16 = 7)
     *            ├──────────────┤   bucket[3] 里根本没有 dim1！→ 返回 null
     * }</pre>
     *
     * <p>
     * <b>解决方案：equals 用什么字段比较，hashCode 就用什么字段计算。</b><br>
     * 此处委托给 {@code id.hashCode()}——{@link ResourceLocation} 是值类型，
     * 相同命名空间和路径一定产出相同的 hashCode，两个方法永远同步。
     */
    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * 原toString方法会返回对象的字符串表示形式 类的全限定名 + '@' + 对象哈希码的无符号十六进制表示<br>
     * 此处重写为返回利于调试的可读字符串，格式：
     * {@code PresetDimension{id=..., category=..., discovered=...}}
     */
    @Override
    public String toString() {
        return "PresetDimension{id=" + id + ", category=" + category.id() + ", discovered=" + discovered + '}';
    }
}
