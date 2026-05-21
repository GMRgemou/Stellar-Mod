package com.luolian.stellarmod.server.worldgen.dimensionline;

import com.luolian.stellarmod.StellarMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 维度种子持久化数据。
 * <p>
 * 存储在世界存档目录中，记录该存档的维度生成种子。
 * 维度种子用于所有预生成维度（{@code PresetDimension}）的元数据生成，
 * 确保同一存档的维度池始终一致。
 *
 * <h3>挂载位置</h3>
 * 数据挂载在<b>主世界的 {@code DimensionDataStorage}</b> 上，物理文件位于：
 * <pre>{@code <存档>/data/stellarmod_dimension_seed.dat}</pre>
 * 而非各自定义维度的 {@code dimensions/<dim>/data/} 目录下。
 * <p>
 * <b>原因：</b>
 * <ul>
 *   <li>维度种子是<b>世界级</b>概念（决定整个存档的维度池），不是某个维度的私有数据</li>
 *   <li>主世界必定存在，地狱/末地可能被禁用，自定义维度可能未生成——只有 overworld 永远在线</li>
 *   <li>原版中许多世界级数据（如袭击进度）也挂载在 overworld 的存储上，此为 Minecraft 惯例</li>
 * </ul>
 *
 * <h3>重要约定</h3>
 * <b>所有方法的 {@code level} 参数必须传入主世界（overworld）的 {@code ServerLevel}。</b>
 * 每个维度的 {@code DimensionDataStorage} 是隔离的——在 overworld 上存储的数据，
 * 在其他维度上 {@code get()} 拿不到。如果误传自定义维度的 level：
 * <ul>
 *   <li>{@code get()} 会因为找不到数据而返回 {@code null}</li>
 *   <li>{@code getOrCreate()} 会用错误的维度种子创建不一致的副本</li>
 * </ul>
 *
 * <h3>NBT 结构</h3>
 * <pre>{@code
 * {
 *   "dimension_seed": 1234567890,
 *   "has_custom_seed": true
 * }
 * }</pre>
 */
public class StellarDimensionSeedData extends SavedData {

    private static final String DATA_NAME = StellarMod.MOD_ID + "_dimension_seed";

    private long dimensionSeed;
    private boolean hasCustomSeed;

    // ═══════════════════════════════════════════════════════════════
    // 构造 / 工厂
    // ═══════════════════════════════════════════════════════════════

    private StellarDimensionSeedData(long seed, boolean hasCustomSeed) {
        this.dimensionSeed = seed;
        this.hasCustomSeed = hasCustomSeed;
    }

    /**
     * 初始化维度种子数据（首次加载时调用）。
     *
     * @param level         必须为主世界（overworld）的 ServerLevel，数据将挂载在 overworld 的 DimensionDataStorage 上
     * @param hasCustomSeed 玩家是否在创建世界时设定了自定义种子
     * @param customSeed    自定义种子值（仅 hasCustomSeed 为 true 时有效）
     */
    public static void initialize(ServerLevel level, boolean hasCustomSeed, long customSeed) {
        long seed = hasCustomSeed ? customSeed : level.getSeed();
        StellarDimensionSeedData data = new StellarDimensionSeedData(seed, hasCustomSeed);
        //标记数据已修改（标记数据为脏数据），通知世界存档系统"这份数据需要在下次保存周期写入磁盘"。不调用的话数据只存在于内存，服务器关闭/世界卸载后就丢失了喵
        data.setDirty();
        /*
            level是ServerLevel，代表服务器端的一个维度，内部持有一个DimensionDataStorage实例，管理附加的自定义存档数据
            getDataStorage()返回DimensionDataStorage，一个针对当前维度的键值存储管理器，其负责：
                用字符串标识（key）关联一个 SavedData 实例；
                在存档保存时，把所有“脏”的 SavedData 序列化成 NBT 并写入独立的 .dat 文件；
                提供读取接口（get、computeIfAbsent），在需要时自动从磁盘加载数据
            set(DATA_NAME, data)将 data（这里是新建的 StellarDimensionSeedData 实例）以 DATA_NAME 为键，存入内部 Map。
                此后，这个维度存储管理器就认识了这份数据，知道在保存文件时需要处理它
                如果之前已经存在相同 DATA_NAME 的数据，它会被替换（旧数据不再被追踪）
            set 方法只是把对象放进 Map，并不会自动标记为“需要保存”。所以需要配合 setDirty() 才有意义。
         */
        level.getDataStorage().set(DATA_NAME, data);
    }

    /**
     * 从世界存档中读取维度种子数据。
     *
     * @param level 必须为主世界（overworld）的 ServerLevel，其他维度无法读到 overworld 上挂载的数据
     * @return 已存储的数据，若不存在则返回 {@code null}
     */
    public static StellarDimensionSeedData get(ServerLevel level) {
        return level.getDataStorage().get(
                /*
                    注：T是实际要获取的数据类型
                    get 方法期望一个 Function<CompoundTag, StellarDimensionSeedData>，编译器知道参数必定是 CompoundTag，所以可以省略，完整体如下：
                    (CompoundTag tag) -> {
                       return StellarDimensionSeedData.load(tag);
                    }
                    这个方法实际上接受了 一个能将 CompoundTag 转换为数据的函数对象 和 一个 String，相当于把逻辑（菜谱）带在身上，需要时再调用（看怎么炒菜）
                 */
                tag -> load(tag),
                DATA_NAME
        );
    }

    /**
     * 获取或初始化维度种子数据。
     * <p>
     * 如果存档中已有数据（磁盘上存在 {@code .dat} 文件）则反序列化后返回；
     * 否则用当前世界的种子创建新实例。
     *
     * @param level 必须为主世界（overworld）的 ServerLevel，误传其他维度会创建不一致的副本（见类注释）
     *
     * <p>
     * 注意：首次创建时会调用 {@link #setDirty()} 标记数据，
     * 确保新实例在下一个自动保存周期被写入磁盘。
     * （{@code computeIfAbsent} 内部的 {@code cache.put} 仅注册到内存缓存，
     * 不会自动标记脏——若不在 supplier （下面那个lambda表达式里）中手动标记，则新数据要等到世界完整保存时才会落盘）
     */
    public static StellarDimensionSeedData getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                tag -> load(tag),                                  //已有数据 → NBT 反序列化
                () -> {
                    //首次创建：用世界种子构造默认实例，并标记为脏以触发持久化
                    StellarDimensionSeedData data = new StellarDimensionSeedData(level.getSeed(), false);
                    data.setDirty();
                    return data;
                },
                DATA_NAME
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // 访问器
    // ═══════════════════════════════════════════════════════════════

    public long getDimensionSeed() { return dimensionSeed; }
    public boolean hasCustomSeed() { return hasCustomSeed; }

    // ═══════════════════════════════════════════════════════════════
    // NBT 序列化
    // ═══════════════════════════════════════════════════════════════

    private static StellarDimensionSeedData load(CompoundTag tag) {
        long seed = tag.getLong("dimension_seed");
        boolean hasCustom = tag.getBoolean("has_custom_seed");
        return new StellarDimensionSeedData(seed, hasCustom);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putLong("dimension_seed", dimensionSeed);
        tag.putBoolean("has_custom_seed", hasCustomSeed);
        return tag;
    }

    @Override
    public String toString() {
        return "DimensionSeedData{seed=" + dimensionSeed + ", custom=" + hasCustomSeed + "}";
    }
}
