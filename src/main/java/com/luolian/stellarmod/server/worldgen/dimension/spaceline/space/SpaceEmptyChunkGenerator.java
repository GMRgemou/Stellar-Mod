package com.luolian.stellarmod.server.worldgen.dimension.spaceline.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 空区块生成器，用于星域维度。
 * 不生成任何地形、结构、生物或装饰——整个维度为纯虚空。
 * 配合 {@link SpaceDimensions} 中定义的 512 格高度使用。
 */
public class SpaceEmptyChunkGenerator extends ChunkGenerator {

    private static final int MIN_Y = 0;
    private static final int HEIGHT = 512;

    /**
     * Codec，用于数据包中对本生成器的序列化与反序列化。
     * 仅保留生物群系源信息，其余字段由构造器补全。
     */
    public static final Codec<SpaceEmptyChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FixedBiomeSource.CODEC.fieldOf("biome_source").forGetter(
                            gen -> (FixedBiomeSource) gen.getBiomeSource()
                    )
            ).apply(instance, SpaceEmptyChunkGenerator::new)
    );

    /**
     * 从 FixedBiomeSource 中提取唯一 Holder\<Biome\> 并委托给主构造器。
     */
    private SpaceEmptyChunkGenerator(FixedBiomeSource biomeSource) {
        this(extractBiome(biomeSource));
    }

    public SpaceEmptyChunkGenerator(Holder<Biome> biome) {
        super(new FixedBiomeSource(biome));
    }

    /**
     * 从给定的 FixedBiomeSource 中提取第一个（也是唯一的）生物群系 Holder。
     * @throws IllegalStateException 若生物群系源为空
     */
    private static Holder<Biome> extractBiome(FixedBiomeSource source) {
        return source.possibleBiomes().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("FixedBiomeSource must contain exactly one biome"));
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** 不进行地形雕刻 */
    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {}

    /** 不构建地表 */
    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
                             RandomState random, ChunkAccess chunk) {}

    /** 不生成自然生物 */
    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    /** 直接返回空区块，不进行噪声填充 */
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
                                                       StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    /** 所有位置的基础高度均为 minY */
    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return getMinY();
    }

    /** 返回全部为空气的噪声柱 */
    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            states[i] = Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(getMinY(), states);
    }

    /** 调试屏幕不追加额外信息 */
    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {}

    /** 无海平面，返回 minY */
    @Override
    public int getSeaLevel() {
        return getMinY();
    }
}
