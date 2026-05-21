package com.luolian.stellarmod.client.listener;

import com.luolian.stellarmod.StellarMod;
import com.luolian.stellarmod.client.screen.dimension.StellarDimensionSeedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端事件监听器。
 * <p>
 * 处理仅客户端侧的事件，如在世界创建界面注入自定义按钮。
 */
@Mod.EventBusSubscriber(modid = StellarMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class StellarClientListener {

    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    /** 按钮相对屏幕底部的位置（距底 60 像素） */
    private static final int BUTTON_BOTTOM_OFFSET = 60;

    /**
     * 世界创建界面初始化后，注入维度种子设置按钮。
     * 按钮位于屏幕中下部，点击后打开 {@link StellarDimensionSeedScreen}。
     *
     * @param event 屏幕初始化完成事件，携带已初始化的屏幕实例，
     *              此处用于获取 {@link CreateWorldScreen} 以注入自定义按钮
     */
    @SubscribeEvent
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen)) return;

        int x = (screen.width - BUTTON_WIDTH) / 2;
        int y = screen.height - BUTTON_BOTTOM_OFFSET;

        event.addListener(
                Button.builder(
                        Component.translatable("stellarmod.screen.dimension_seed.button"),
                        btn -> Minecraft.getInstance().setScreen(
                        new StellarDimensionSeedScreen(screen)
                )
                ).pos(x, y)
                 .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                 .build()
        );
    }
}
