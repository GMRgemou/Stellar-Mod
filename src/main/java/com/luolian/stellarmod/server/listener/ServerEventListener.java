package com.luolian.stellarmod.server.listener;

import com.luolian.stellarmod.StellarMod;
import com.luolian.stellarmod.api.toolcore.StellarMatrixEffect;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.luolian.stellarmod.server.data.toolcore.StellarMatrixRegistry;
import com.luolian.stellarmod.server.item.custom.toolcore.ToolCoreItem;
import com.luolian.stellarmod.server.item.custom.toolcore.ToolCoreNBT;
import com.luolian.stellarmod.server.worldgen.dimension.spaceline.space.SpaceDimensions;
import com.luolian.stellarmod.server.worldgen.dimensionline.StellarDimensionSeedData;
import com.luolian.stellarmod.server.worldgen.dimensionline.StellarDimensionSeedHolder;
import com.luolian.stellarmod.server.worldgen.dimensionline.StellarPresetDimensionPool;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = StellarMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(SpaceDimensions.SPACE_LEVEL_KEY)) return;

        StructureSpawnData data = level.getDataStorage().computeIfAbsent(
                StructureSpawnData::load,
                StructureSpawnData::new,
                StellarMod.MOD_ID + "_kjs_spawned"
        );

        if (!data.hasSpawned) {
            StructureTemplateManager templateManager = level.getStructureManager();
            Optional<StructureTemplate> templateOpt = templateManager.get(StellarMod.location("space_station"));

            if (templateOpt.isPresent()) {
                StructureTemplate template = templateOpt.get();
                BlockPos pos = new BlockPos(0, 60, 0);
                StructurePlaceSettings settings = new StructurePlaceSettings();
                template.placeInWorld(level, pos, pos, settings, level.getRandom(), 2);

                data.hasSpawned = true;
                data.setDirty();
            }
        }
    }

    /**
     * 主世界首次加载时，将玩家设定的维度种子从临时 Holder 持久化到世界存档。
     *
     * <h3>触发时机</h3>
     * {@link LevelEvent.Load} 在每个维度加载时均会触发（客户端+服务端），
     * 此处通过两层过滤确保只在"服务端主世界"执行一次。
     *
     * <h3>数据流</h3>
     * <pre>
     * StellarDimensionSeedScreen（GUI 选择种子）
     *   → StellarDimensionSeedHolder（客户端静态临时持有）
     *     → onOverworldLoad（本方法，服务端事件）
     *       → StellarDimensionSeedData.initialize（种子持久化到 .dat）
     *       → StellarPresetDimensionPool.getOrCreate（使用同一份种子生成维度池）
     *         → 之后所有维度生成通过 getOrCreate() 读取
     * </pre>
     *
     * <h3>Holder 读取顺序说明</h3>
     * 必须先调用 {@code hasCustomSeed()} 再调用 {@code resolveSeed()}。
     * 因为 {@code resolveSeed()} 内部会清空 Holder 状态（标记重置），
     * 顺序颠倒则 {@code hasCustomSeed()} 永远返回 {@code false}。
     *
     * @param event 维度加载事件，携带已加载的 Level 实例
     */
    @SubscribeEvent
    public static void onOverworldLoad(LevelEvent.Load event) {
        //过滤 1：仅服务端维度（客户端 Level 非 ServerLevel，不处理）
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        //过滤 2：仅主世界（地狱/末地/自定义维度不触发初始化）
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        //检查是否已有持久化数据——非 null 说明是已有存档重载，跳过
        StellarDimensionSeedData existing = StellarDimensionSeedData.get(level);
        if (existing != null) return;

        //首次创建世界：从临时 Holder 读取玩家在 GUI 中的选择
        //注意：必须先用 hasCustomSeed() 读标记，再用 resolveSeed() 取值；
        //resolveSeed() 会清空 Holder，顺序颠倒则 hasCustomSeed() 永远为 false
        boolean isCustom = StellarDimensionSeedHolder.hasCustomSeed();
        long seed = StellarDimensionSeedHolder.resolveSeed(level.getSeed());
        //将种子持久化到世界存档的 .dat 文件中
        StellarDimensionSeedData.initialize(level, isCustom, seed);

        //维度种子初始化完成后，使用同一份种子生成预生成维度池
        //先通过 get 取回刚持久化的种子数据，确保池使用完全一致的种子值
        StellarDimensionSeedData seedData = StellarDimensionSeedData.get(level);
        if (seedData != null) {
            long dimensionSeed = seedData.getDimensionSeed();
            StellarPresetDimensionPool.getOrCreate(level, dimensionSeed);
        } else {
            //理论上不会到达——initialize() 刚执行完毕，get() 必然取回非 null 数据
            //若到达此处，说明 DimensionDataStorage 内部出现了异常状态（如并发修改或缓存不一致）
            LOGGER.warn(
                    "Unexpected null StellarDimensionSeedData right after initialization. " +
                            "Falling back to level seed for dimension pool generation."
            );
            StellarPresetDimensionPool.getOrCreate(level, level.getSeed());
        }
    }

    /**
     * 玩家每 tick 事件：遍历物品栏中的工具核心，触发所有已启用的矩阵效果。
     * 先在效果应用前重置生存玩家的飞行权限，防止关闭矩阵后残留飞行能力。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;

        //重置生存玩家的飞行权限，由活跃的矩阵效果重新授权
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
        }

        for (ItemStack stack : player.getInventory().items) {
            if (!(stack.getItem() instanceof ToolCoreItem)) continue;
            for (String id : ToolCoreNBT.getAttachedMatrixEffects(stack)) {
                if (!ToolCoreNBT.isMatrixEnabled(stack, id)) continue;
                int activeLevel = ToolCoreNBT.getMatrixActiveLevel(stack, id);
                if (activeLevel <= 0) continue;
                StellarMatrixEffect effect = StellarMatrixRegistry.get(id);
                if (effect != null) {
                    effect.onPlayerTick(player, activeLevel);
                }
            }
        }
    }

    public static class StructureSpawnData extends SavedData {
        public boolean hasSpawned = false;

        public StructureSpawnData() {}

        public static StructureSpawnData load(CompoundTag tag) {
            StructureSpawnData data = new StructureSpawnData();
            data.hasSpawned = tag.getBoolean("hasSpawned");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("hasSpawned", hasSpawned);
            return tag;
        }
    }
}
