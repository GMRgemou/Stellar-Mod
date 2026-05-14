package com.luolian.stellarmod.server.worldgen.portal.spaceline.space;

import com.luolian.stellarmod.server.block.StellarBlocks;
import com.luolian.stellarmod.server.block.custom.DimensionBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.ITeleporter;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * 星域维度传送器。
 * 处理实体在主世界与星域维度之间的传送逻辑：
 * <ul>
 *   <li>传送到星域时：先查找已有传送方块，未找到则在安全位置下方放置新的传送方块</li>
 *   <li>返回主世界时：在目标坐标附近查找安全的站立位置</li>
 * </ul>
 */
public class SpaceTeleporter implements ITeleporter {
    private final BlockPos targetPos;
    private final boolean insideDimension;

    /**
     * @param pos       目标维度中期望的传送点坐标
     * @param insideDim {@code true} 表示传送到星域维度内部，
     *                  {@code false} 表示从星域返回主世界
     */
    public SpaceTeleporter(BlockPos pos, boolean insideDim) {
        this.targetPos = pos;
        this.insideDimension = insideDim;
    }

    /**
     * 在目标维度中放置实体并处理传送逻辑。
     * <ul>
     *   <li>先尝试查找维度中已有的传送方块</li>
     *   <li>找不到则进行安全位置搜索（上下各 50 格）</li>
     *   <li>传入星域时若没有已有传送方块，自动在脚下放置一个</li>
     * </ul>
     */
    @Override
    public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destinationWorld,
                              float yaw, Function<Boolean, Entity> repositionEntity) {
        entity = repositionEntity.apply(false);

        BlockPos finalPos = null;
        boolean foundExistingPortal = false;

        // 传送到星域时，先尝试查找已有的传送方块
        if (insideDimension) {
            BlockPos existingPortalPos = findExistingPortal(destinationWorld, targetPos);
            if (existingPortalPos != null) {
                finalPos = existingPortalPos.above(); // 站在方块上方
                foundExistingPortal = true;
            }
        }

        // 未找到已有传送方块，执行安全位置搜索
        if (finalPos == null) {
            int baseY = insideDimension ? targetPos.getY() : 61;
            BlockPos startPos = new BlockPos(targetPos.getX(), baseY, targetPos.getZ());
            finalPos = findSafePos(destinationWorld, startPos);
        }

        entity.setPos(finalPos.getX(), finalPos.getY(), finalPos.getZ());

        // 传送到星域且未找到已有传送方块时，在脚下放置新的传送方块
        if (insideDimension && !foundExistingPortal) {
            placePortalIfNeeded(destinationWorld, finalPos);
        }

        return entity;
    }

    /**
     * 在目标维度中以 center 为中心 21×5×21 范围内查找已有的传送方块。
     *
     * @param level  目标维度
     * @param center 搜索中心坐标
     * @return 找到的第一个传送方块位置，若无则返回 {@code null}
     */
    @Nullable
    private BlockPos findExistingPortal(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-10, -2, -10),
                center.offset(10, 2, 10))) {
            if (level.getBlockState(pos).getBlock() instanceof DimensionBlock) {
                return pos.immutable();
            }
        }
        return null;
    }

    /**
     * 从起始位置向上、下各搜索 50 格，寻找安全的实体站立位置。
     * 优先检查起始位置，然后向上搜索，最后向下搜索。
     *
     * @param level    目标维度
     * @param startPos 起始搜索位置
     * @return 找到的安全位置；若 100 格内均无安全点，返回起始位置作为兜底
     */
    private BlockPos findSafePos(ServerLevel level, BlockPos startPos) {
        BlockPos.MutableBlockPos mutable = startPos.mutable();

        // 优先检查起始位置
        if (isSafeStandingPos(level, mutable)) {
            return mutable.immutable();
        }

        // 向上搜索 50 格
        for (int i = 1; i <= 50; i++) {
            mutable.move(0, 1, 0);
            if (isSafeStandingPos(level, mutable)) {
                return mutable.immutable();
            }
        }

        // 重置，向下搜索 50 格
        mutable.set(startPos);
        for (int i = 1; i <= 50; i++) {
            mutable.move(0, -1, 0);
            if (isSafeStandingPos(level, mutable)) {
                return mutable.immutable();
            }
        }

        // 兜底：返回起始位置
        return startPos;
    }

    /**
     * 判断指定位置是否适合实体站立：
     * <ul>
     *   <li>脚下方块为实心固体（非空气、非流体、不可替换）</li>
     *   <li>站立点和头顶均为空气或可被水替换的方块</li>
     * </ul>
     */
    private boolean isSafeStandingPos(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockPos above = pos.above();

        // 脚下方块必须是固体
        BlockState belowState = level.getBlockState(below);
        if (belowState.isAir() || belowState.canBeReplaced(Fluids.WATER) || !belowState.isSolid()) {
            return false;
        }

        // 站立点和头顶必须是空气或可替换方块
        BlockState standingState = level.getBlockState(pos);
        BlockState aboveState = level.getBlockState(above);
        return (standingState.isAir() || standingState.canBeReplaced(Fluids.WATER))
                && (aboveState.isAir() || aboveState.canBeReplaced(Fluids.WATER));
    }

    /**
     * 在安全位置下方放置传送方块。
     * 放置前对附近 21×5×21 范围做双重检查，避免重复放置。
     */
    private void placePortalIfNeeded(ServerLevel level, BlockPos safePos) {
        boolean exists = BlockPos.betweenClosedStream(
                safePos.offset(-10, -2, -10),
                safePos.offset(10, 2, 10)
        ).anyMatch(p -> level.getBlockState(p).getBlock() instanceof DimensionBlock);

        if (!exists) {
            BlockPos placePos = safePos.below();
            level.setBlock(placePos, StellarBlocks.DIMENSION_BLOCK.get().defaultBlockState(), 3);
        }
    }
}
