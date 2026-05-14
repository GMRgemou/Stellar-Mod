package com.luolian.stellarmod.server.worldgen.biome.spaceline.space;

import com.luolian.stellarmod.StellarMod;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * 星域维度的生物群系注册。
 * <p>
 * 只定义一个 {@code space_biome}，用于整个星域维度：
 * <ul>
 *   <li>纯黑天空与雾气，模拟太空视觉</li>
 *   <li>无降水、无生物生成、无地形特征和雕刻器</li>
 *   <li>配合 {@code EmptyChunkGenerator} 构成纯虚空环境</li>
 * </ul>
 */
public class SpaceBiomes {
    public static final ResourceKey<Biome> SPACE_BIOME = ResourceKey.create(Registries.BIOME,
            StellarMod.location("space_biome"));

    /**
     * 数据生成阶段向注册表注入星域生物群系。
     */
    public static void bootstrap(BootstapContext<Biome> context) {
        context.register(SPACE_BIOME, createSpaceBiome(context));
    }

    /**
     * 构建并返回星域生物群系实例。
     * <ul>
     *   <li>生物生成：空（无自然生物）</li>
     *   <li>地形生成：空（不放置特征、不雕刻地形）</li>
     *   <li>视觉效果：黑色天空 (0x000000)、黑色雾气、深蓝水体，模拟太空</li>
     *   <li>气候：无降水，温度 0.8（影响草/树叶颜色，实际无植被因此无视觉影响）</li>
     * </ul>
     */
    private static Biome createSpaceBiome(BootstapContext<Biome> context) {
        HolderGetter<PlacedFeature> placedFeatureGetter = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> carverGetter = context.lookup(Registries.CONFIGURED_CARVER);

        MobSpawnSettings.Builder spawnBuilder = new MobSpawnSettings.Builder();

        BiomeGenerationSettings.Builder generationBuilder =
                new BiomeGenerationSettings.Builder(placedFeatureGetter, carverGetter);

        BiomeSpecialEffects.Builder effectsBuilder = new BiomeSpecialEffects.Builder()
                .skyColor(0x000000)
                .fogColor(0x000000)
                .waterColor(0x3f76e4)
                .waterFogColor(0x050533);

        return new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.8f)
                .downfall(0.0f)
                .specialEffects(effectsBuilder.build())
                .mobSpawnSettings(spawnBuilder.build())
                .generationSettings(generationBuilder.build())
                .build();
    }
}
