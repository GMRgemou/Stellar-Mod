package com.luolian.stellarmod.server.block.entity;

import com.luolian.stellarmod.StellarMod;
import com.luolian.stellarmod.server.block.StellarBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class StellarBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, StellarMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<CraftingAreaBlockEntity>> CRAFTING_AREA_BE =
            BLOCK_ENTITIES.register("crafting_area_be", () ->
                    BlockEntityType.Builder.of(CraftingAreaBlockEntity::new,
                            StellarBlocks.CRAFTING_AREA_BLOCK.get())
                            //build(null)：模组方块实体不受原版 DataFixer 管理，DataFixer 参数传 null，表示不需要原版的版本迁移数据修复
                            //原版的 DataFixer 系统是Minecraft用来保证存档向后兼容性的核心机制。简单来说，它就像一个内置的“存档升级工具”，
                            //当用新版本游戏打开旧版本存档时，它能自动将存档中的所有数据转换成新版本可识别的格式
                            .build(null));

    public static final RegistryObject<BlockEntityType<PortalCoreBlockEntity>> PORTAL_CORE_BE =
            BLOCK_ENTITIES.register("portal_core_be", () ->
                    BlockEntityType.Builder.of(PortalCoreBlockEntity::new,
                            StellarBlocks.PORTAL_CORE_BLOCK.get())
                            .build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}