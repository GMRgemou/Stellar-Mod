package com.luolian.stellarmod.server.worldgen.dimensionline;

import com.luolian.stellarmod.StellarMod;
import com.luolian.stellarmod.server.worldgen.dimensionline.tuning.StellarDimensionCategory;
import com.luolian.stellarmod.server.worldgen.dimensionline.tuning.StellarPresetDimension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 预生成维度池——管理一个存档中所有预生成维度的持久化数据。
 * <p>
 * 在存档首次创建时，使用维度种子<b>确定性</b>生成全部预生成维度，
 * 每类别随机生成 2~4 个（由种子决定），同一种子始终产出完全相同的维度列表。
 * <p>
 * 维度未被发现前对玩家不可见，需通过研究设备逐步解锁。
 *
 * <h3>挂载位置</h3>
 * 与 {@link StellarDimensionSeedData} 一致，挂载在主世界的 {@code DimensionDataStorage}，
 * 物理文件位于 {@code <存档>/data/stellarmod_preset_dimension_pool.dat}。
 *
 * <h3>NBT 结构</h3>
 * <pre>{@code
 * {
 *   "dimensionSeed": 1234567890,
 *   "generated": true,
 *   "dimensions": [
 *     { "id": "stellarmod:preset_chaos_01", "category": "chaos", "discovered": false, "generationSeed": ... },
 *     ...共 10~20 个（每类 2~4）
 *   ]
 * }
 * }</pre>
 *
 * <h3>重要约定</h3>
 * 所有静态方法中的 {@code level} 必须为主世界（overworld）的 {@link ServerLevel}，
 * 原因同 {@link StellarDimensionSeedData}——数据挂载在主世界存储上。
 */
public class StellarPresetDimensionPool extends SavedData {

    private static final String DATA_NAME = StellarMod.MOD_ID + "_preset_dimension_pool";

    /** 每类别最少预生成数量 */
    private static final int MIN_PER_CATEGORY = 2;
    /** 每类别最多预生成数量 */
    private static final int MAX_PER_CATEGORY = 4;

    /** 预生成维度列表（存档创建时确定，不可增减） */
    private final List<StellarPresetDimension> dimensions = new ArrayList<>();

    /** 使用的维度种子（快照，便于调试） */
    private long dimensionSeed;

    /** 是否已生成（防止重复生成） */
    private boolean generated;

    // ═══════════════════════════════════════════════════════════════
    // 构造 / 工厂
    // ═══════════════════════════════════════════════════════════════

    /**
     * 私有空构造器，实例一律通过工厂方法创建。
     * <p>
     * 该类有两条互斥的初始化路径——首次创建时走 {@link #create(long)} 用种子生成维度列表，
     * 已有存档加载时走 {@link #fromNBT(CompoundTag)} 从持久化数据还原。
     * 空构造器交由各工厂方法按各自数据源填充字段，比带参构造器更清晰。
     */
    private StellarPresetDimensionPool() {}

    /**
     * 使用维度种子确定性生成全部预生成维度。
     * <p>
     * 首先用种子决定每类别的数量（{@value #MIN_PER_CATEGORY}~{@value #MAX_PER_CATEGORY} 个），
     * 再按同一 Random 序列生成维度属性。调用顺序固定，相同种子始终产出完全相同的维度列表。
     * ID 格式：{@code stellarmod:preset_<category>_<全局序号>}。
     *
     * @param dimensionSeed 维度种子（来自 {@link StellarDimensionSeedData}）
     * @return 已生成完毕的池实例，标记为 dirty 等待持久化
     */
    public static StellarPresetDimensionPool create(long dimensionSeed) {
        StellarPresetDimensionPool pool = new StellarPresetDimensionPool();
        pool.dimensionSeed = dimensionSeed;
        pool.generated = true;

        //使用维度种子的确定性随机序列，同一种子 → 相同维度列表
        Random random = new Random(dimensionSeed);

        //第一轮：确定每类别的数量（2~4）——先消费随机序列
        //values() 返回所有枚举常量数组，等价于：new StellarDimensionCategory[] { CHAOS, ADVENTURE, MINING, VAST_WORLD, VOID }
        StellarDimensionCategory[] categories = StellarDimensionCategory.values();
        int[] counts = new int[categories.length];
        for (int i = 0; i < categories.length; i++) {
            //random.nextInt返回0到bound-1的伪随机int数
            counts[i] = MIN_PER_CATEGORY + random.nextInt(MAX_PER_CATEGORY - MIN_PER_CATEGORY + 1);
        }

        //第二轮：按已确定的数量生成维度——同一种子、相同 Random 实例、相同消费顺序，确定性
        int globalIndex = 1;
        for (int i = 0; i < categories.length; i++) {
            for (int j = 0; j < counts[i]; j++) {
                //String.format("%02d", globalIndex)将整数 globalIndex 格式化为一个至少两位的十进制字符串，不足两位时前面补零
                //% → 格式说明符的开始；0 → 不足宽度时用零填充；2 → 输出至少占 2 个字符的位置；d → 十进制整数
                ResourceLocation id = StellarMod.location(
                        "preset_" + categories[i].id() + "_" + String.format("%02d", globalIndex)
                );
                pool.dimensions.add(StellarPresetDimension.generate(id, categories[i], random));
                globalIndex++;
            }
        }

        //标记为脏，确保新生成的维度列表在下次保存周期写入磁盘
        pool.setDirty();
        return pool;
    }

    /**
     * 获取或初始化预生成维度池。
     * <p>
     * 如果存档中已有池则直接返回，否则使用传入的维度种子创建新池。
     * 这里遵循了{@code computeIfAbsent} 的首次创建逻辑，
     * 确保新实例在下次自动保存周期写入磁盘。
     *
     * @param level         必须为主世界（overworld）的 ServerLevel
     * @param dimensionSeed 维度种子
     * @return 已存在或新创建的维度池
     */
    public static StellarPresetDimensionPool getOrCreate(ServerLevel level, long dimensionSeed) {
        return level.getDataStorage().computeIfAbsent(
                tag -> fromNBT(tag),                                    //已有数据 → NBT 反序列化
                () -> create(dimensionSeed),                            // 首次创建 → 确定性生成
                DATA_NAME
        );
    }

    /**
     * 从世界存档中读取预生成维度池。
     *
     * @param level 必须为主世界（overworld）的 ServerLevel
     * @return 已存储的池，若不存在则返回 {@code null}
     */
    @Nullable
    public static StellarPresetDimensionPool get(ServerLevel level) {
        return level.getDataStorage().get(
                tag -> fromNBT(tag),
                DATA_NAME
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // 查询
    // ═══════════════════════════════════════════════════════════════

    /** 获取所有预生成维度的只读列表 */
    public List<StellarPresetDimension> getAll() {
        //返回一个不可修改的视图列表，修改操作会导致抛出 UnsupportedOperationException，注意不可修改仅是本视图，若其他位置改变列表值，此处也会被修改
        return Collections.unmodifiableList(dimensions);
    }

    /** 按类别筛选预生成维度 */
    public List<StellarPresetDimension> getByCategory(StellarDimensionCategory category) {
        List<StellarPresetDimension> result = new ArrayList<>();
        for (StellarPresetDimension dim : dimensions) {
            if (dim.getCategory() == category) {
                result.add(dim);
            }
        }
        return result;
    }

    /** 获取所有尚未被发现的维度 */
    public List<StellarPresetDimension> getUndiscovered() {
        List<StellarPresetDimension> result = new ArrayList<>();
        for (StellarPresetDimension dim : dimensions) {
            if (!dim.isDiscovered()) {
                result.add(dim);
            }
        }
        return result;
    }

    /** 获取所有已被发现的维度 */
    public List<StellarPresetDimension> getDiscovered() {
        List<StellarPresetDimension> result = new ArrayList<>();
        for (StellarPresetDimension dim : dimensions) {
            if (dim.isDiscovered()) {
                result.add(dim);
            }
        }
        return result;
    }

    /**
     * 按 ID 精确查找预生成维度。
     *
     * @param id 维度 ID
     * @return 找到的维度，未找到返回 {@link Optional#empty()}
     */
    public Optional<StellarPresetDimension> getById(ResourceLocation id) {
        for (StellarPresetDimension dim : dimensions) {
            if (dim.getId().equals(id)) {
                //Optional.of(dim) 表达的设计意图是：值的缺失不是正常情况，dim 绝不能为 null，如果为null抛出NullPointerException
                return Optional.of(dim);
            }
        }
        //Optional.empty() 不包含任何对象（内部引用为 null），但本身不是 null，是一个单例（Optional 内部维护了一个 EMPTY 常量），每次调用都返回同一个空实例
        //清晰地表达“找不到”的意图，比直接返回 null 更安全，因为调用方无法忽略它的空状态检查，直接调用 get() 会抛出 NoSuchElementException，
        //所以必须显式处理空状态，这也是使用 Optional 的核心目的
        return Optional.empty();
    }

    // ═══════════════════════════════════════════════════════════════
    // 修改
    // ═══════════════════════════════════════════════════════════════

    /**
     * 将指定维度标记为已发现（不可逆）。
     *
     * @param id 要发现的维度 ID
     * @return 是否成功找到并标记（false 表示 ID 不存在或已发现）
     */
    public boolean discover(ResourceLocation id) {
        for (StellarPresetDimension dim : dimensions) {
            if (dim.getId().equals(id)) {
                if (dim.isDiscovered()) return false;   //已发现，无需操作
                dim.markDiscovered();
                setDirty();                             //标记持久化数据需要保存
                return true;
            }
        }
        return false;                                   //ID 不存在
    }

    // ═══════════════════════════════════════════════════════════════
    // 访问器
    // ═══════════════════════════════════════════════════════════════

    public long getDimensionSeed() { return dimensionSeed; }
    public boolean isGenerated() { return generated; }
    public int size() { return dimensions.size(); }

    // ═══════════════════════════════════════════════════════════════
    // NBT 序列化
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从 NBT 反序列化预生成维度池。
     */
    private static StellarPresetDimensionPool fromNBT(CompoundTag tag) {
        StellarPresetDimensionPool pool = new StellarPresetDimensionPool();
        pool.dimensionSeed = tag.getLong("dimensionSeed");
        pool.generated = tag.getBoolean("generated");

        ListTag list = tag.getList("dimensions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag dimTag = list.getCompound(i);
            pool.dimensions.add(StellarPresetDimension.fromNBT(dimTag));
        }

        return pool;
    }

    /**
     * 将预生成维度池序列化为 NBT。
     */
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putLong("dimensionSeed", dimensionSeed);
        tag.putBoolean("generated", generated);

        ListTag list = new ListTag();
        for (StellarPresetDimension dim : dimensions) {
            list.add(dim.toNBT());
        }
        tag.put("dimensions", list);

        return tag;
    }

    // ═══════════════════════════════════════════════════════════════
    // equals / hashCode / toString
    // ═══════════════════════════════════════════════════════════════

    @Override
    public String toString() {
        return "PresetDimensionPool{seed=" + dimensionSeed +
                ", generated=" + generated +
                ", count=" + dimensions.size() +
                ", discovered=" + getDiscovered().size() + "/" + dimensions.size() + "}";
    }
}
