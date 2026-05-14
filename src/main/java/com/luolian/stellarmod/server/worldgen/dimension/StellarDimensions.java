package com.luolian.stellarmod.server.worldgen.dimension;

import com.luolian.stellarmod.StellarMod;
import com.luolian.stellarmod.server.worldgen.biome.StellarBiomes;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.Level;

import java.util.OptionalLong;

/**
 * 星域维度的类型与 LevelStem 注册。
 * <p>
 * 主要配置决策：
 * <ul>
 *   <li><b>固定时间：</b>关闭昼夜更替（{@code fixedTime} 为空），太空无太阳轨迹</li>
 *   <li><b>无降水：</b>太空无大气层，无雨雪天气</li>
 *   <li><b>高度 512 格：</b>提供充足的虚空空间用于建筑或装置摆放</li>
 *   <li><b>末地燃烧标签：</b>使用 {@link BlockTags#INFINIBURN_END}，与虚空环境一致</li>
 *   <li><b>末地视觉效果：</b>使用 {@link BuiltinDimensionTypes#END_EFFECTS}，
 *       提供黑色天空和雾气渲染，贴合太空氛围</li>
 *   <li><b>无怪物生成：</b>亮度和怪物生成上限均为 0</li>
 *   <li><b>区块生成器：</b>使用 {@link EmptyChunkGenerator}，不生成任何地形</li>
 * </ul>
 */
public class StellarDimensions {
    /** 星域维度的 LevelStem 注册键 */
    public static final ResourceKey<LevelStem> SPACE_LEVEL_STEM_KEY = ResourceKey.create(Registries.LEVEL_STEM,
            StellarMod.location("space_dimension"));
    /** 星域维度的 Dimension（Level）注册键 */
    public static final ResourceKey<Level> SPACE_LEVEL_KEY = ResourceKey.create(Registries.DIMENSION,
            StellarMod.location("space_dimension"));
    /** 星域维度的 DimensionType 注册键 */
    public static final ResourceKey<DimensionType> SPACE_DIM_TYPE_KEY = ResourceKey.create(Registries.DIMENSION_TYPE,
            StellarMod.location("space_type"));

    /**
     * 注册星域维度的 {@link DimensionType}。
     * <p>
     * 各参数含义与设置理由见 {@link DimensionType} 构造器文档。
     */
    public static void bootstrapType(BootstapContext<DimensionType> context) {
        context.register(SPACE_DIM_TYPE_KEY, new DimensionType(
                OptionalLong.empty(),                  // fixedTime: 空 = 无昼夜循环
                false,                                  // hasSkylight
                false,                                  // hasCeiling
                false,                                  // ultraWarm
                false,                                  // natural
                1.0,                                    // coordinateScale
                false,                                  // bedWorks
                false,                                  // respawnAnchorWorks
                0,                                      // minY
                512,                                    // height: 逻辑高度
                512,                                    // logicalHeight: 渲染/传送高度上限
                BlockTags.INFINIBURN_END,              // infiniburn: 末地标签，适配虚空
                BuiltinDimensionTypes.END_EFFECTS,     // effectsLocation: 末地天空/雾渲染
                1.0f,                                   // ambientLight
                new DimensionType.MonsterSettings(false, false, ConstantInt.of(0), 0)));
    }

    /**
     * 注册星域维度的 {@link LevelStem}，绑定 DimensionType 与 EmptyChunkGenerator。
     * <p>
     * 使用 {@link StellarBiomes#SPACE_BIOME} 作为区块生成器的唯一生物群系源。
     */
    public static void bootstrapStem(BootstapContext<LevelStem> context) {
        HolderGetter<Biome> biomeRegistry = context.lookup(Registries.BIOME);
        HolderGetter<DimensionType> dimTypes = context.lookup(Registries.DIMENSION_TYPE);
        EmptyChunkGenerator emptyChunkGenerator = new EmptyChunkGenerator(
                biomeRegistry.getOrThrow(StellarBiomes.SPACE_BIOME)
        );

        LevelStem stem = new LevelStem(
                dimTypes.getOrThrow(StellarDimensions.SPACE_DIM_TYPE_KEY),
                emptyChunkGenerator
        );
        context.register(SPACE_LEVEL_STEM_KEY, stem);
    }
}
