package com.luolian.stellarmod.server.block.custom;

import com.luolian.stellarmod.server.block.entity.PortalCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 传送门方块——维度科技线的核心交互方块。
 * <p>
 * 每个传送门方块持有一个 {@link PortalCoreBlockEntity}，作为调谐状态和锚定信息
 * 的运行时载体。右键交互和 GUI 将在后续层实现。
 *
 * <h3>后续扩展</h3>
 * <ul>
 *   <li>多方块结构检测——传送门框架成型校验</li>
 *   <li>右键 GUI——打开调谐界面</li>
 *   <li>外观渲染——根据调谐状态改变纹理/动画</li>
 * </ul>
 *
 * @see PortalCoreBlockEntity
 */
public class PortalCoreBlock extends Block implements EntityBlock {

    public PortalCoreBlock(Properties properties) {
        super(properties);
    }

    // ═══════════════════════════════════════════════════════════════
    // EntityBlock 实现
    // ═══════════════════════════════════════════════════════════════

    /**
     * 为传送门方块创建对应的方块实体。
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalCoreBlockEntity(pos, state);
    }

    /**
     * 方块实体 tick 调度器。当前 PortalCoreBlockEntity 无常驻 tick 逻辑，
     * 返回 {@code null} 以跳过每帧调用，节省性能。
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        //当前 PortalCoreBlockEntity 无常驻 tick 逻辑，后续如需动画/粒子特效可在此接入
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // 交互
    // ═══════════════════════════════════════════════════════════════

    /**
     * 右键交互入口。
     * <p>
     * 当前为占位实现——仅返回成功标记，不打开 GUI。
     * 第三层调谐 GUI 实现后将通过 {@code NetworkHooks.openScreen} 打开界面。
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        //后续：从 level 获取 PortalCoreBlockEntity，打开调谐 GUI
        return InteractionResult.CONSUME;
    }

    // ═══════════════════════════════════════════════════════════════
    // 生命周期
    // ═══════════════════════════════════════════════════════════════

    /**
     * 方块被移除时，丢弃方块实体内物品并移除实体。
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            //后续：若有物品槽位，在此处 Containers.dropContents()
            level.removeBlockEntity(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
