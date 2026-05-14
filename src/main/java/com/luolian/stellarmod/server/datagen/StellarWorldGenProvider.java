package com.luolian.stellarmod.server.datagen;

import com.luolian.stellarmod.StellarMod;
import com.luolian.stellarmod.server.worldgen.biome.spaceline.space.SpaceBiomes;
import com.luolian.stellarmod.server.worldgen.dimension.spaceline.space.SpaceDimensions;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class StellarWorldGenProvider extends DatapackBuiltinEntriesProvider {
    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.DIMENSION_TYPE, SpaceDimensions::bootstrapType)
            .add(Registries.BIOME, SpaceBiomes::bootstrap)
            .add(Registries.LEVEL_STEM, SpaceDimensions::bootstrapStem);

    public StellarWorldGenProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries) {
        super(packOutput, registries, BUILDER, Set.of(StellarMod.MOD_ID));
    }
}
