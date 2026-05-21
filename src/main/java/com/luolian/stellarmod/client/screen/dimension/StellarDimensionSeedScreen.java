package com.luolian.stellarmod.client.screen.dimension;

import com.luolian.stellarmod.server.worldgen.dimensionline.StellarDimensionSeedHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * 维度种子设置子页面。
 * <p>
 * 在创建新世界时从 {@code CreateWorldScreen} 跳转至此，
 * 玩家可以选择使用世界种子、手动输入数字种子、或随机生成。
 * <p>
 * 字符串种子会被哈希为 {@code long}：先尝试解析为数字，失败则用 hashCode。
 */
public class StellarDimensionSeedScreen extends Screen {

    private static final int SCREEN_WIDTH = 300;
    private static final int SEED_INPUT_WIDTH = 180;
    private static final int BUTTON_WIDTH = 100;

    private final Screen parent;

    private EditBox seedInput;
    private String seedText = "";
    private boolean useWorldSeed = true;

    public StellarDimensionSeedScreen(Screen parent) {
        super(Component.translatable("stellarmod.screen.dimension_seed"));
        this.parent = parent;
    }

    // ═══════════════════════════════════════════════════════════════
    // 界面构建
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        int centerX = this.width / 2;

        //种子文本输入框
        seedInput = new EditBox(
                font,
                centerX - SEED_INPUT_WIDTH / 2, 80,
                SEED_INPUT_WIDTH, 20,
                Component.empty()
        );
        seedInput.setValue(seedText);
        //setResponder：每当输入框文字变化（键入/粘贴/删除）时触发，传入最新文字
        seedInput.setResponder(text -> {
            //实时同步文字到成员变量，供确认按钮读取
            seedText = text;
            //输入为空 → 视为"使用世界种子"，非空 → 视为"使用自定义种子"
            useWorldSeed = text.isEmpty();
        });
        //addRenderableWidget：将组件同时注册到渲染列表（每帧自动绘制）和子元素列表（接收交互事件）
        //不调用此方法则组件既不可见也不可交互
        this.addRenderableWidget(seedInput);

        //随机种子按钮
        this.addRenderableWidget(
                Button.builder(Component.translatable("stellarmod.screen.dimension_seed.random"), btn -> {
                    long randomSeed = new Random().nextLong();
                    seedText = String.valueOf(Math.abs(randomSeed));
                    seedInput.setValue(seedText);
                    useWorldSeed = false;
                }).pos(centerX - BUTTON_WIDTH / 2, 110).size(BUTTON_WIDTH, 20).build()
        );

        //使用世界种子按钮
        this.addRenderableWidget(
                Button.builder(Component.translatable("stellarmod.screen.dimension_seed.use_world"), btn -> {
                    seedText = "";
                    seedInput.setValue("");
                    useWorldSeed = true;
                }).pos(centerX - BUTTON_WIDTH / 2, 135).size(BUTTON_WIDTH, 20).build()
        );

        //确认按钮
        this.addRenderableWidget(
                Button.builder(Component.translatable("stellarmod.screen.dimension_seed.confirm"), btn -> {
                    applySeedAndClose();
                }).pos(centerX - BUTTON_WIDTH / 2, 170).size(BUTTON_WIDTH, 20).build()
        );

        //返回按钮
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_BACK, btn -> {
                    onClose();
                }).pos(centerX - BUTTON_WIDTH / 2, 195).size(BUTTON_WIDTH, 20).build()
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // 逻辑
    // ═══════════════════════════════════════════════════════════════

    /**
     * 将玩家输入的任意字符串解析为 Minecraft 可用的 {@code long} 型种子值。
     *
     * <h3>处理逻辑</h3>
     * <ol>
     *   <li>去除首尾空白</li>
     *   <li>空字符串 → 返回 {@code 0}</li>
     *   <li>纯数字（如 {@code "123"}）→ 直接 {@code Long.parseLong}</li>
     *   <li>非纯数字（如 {@code "my_world"}）→ 用 {@code hashCode} 转为 int，再乘大素数 {@code 31L}
     *       扩展到 long 范围，改善分布均匀度</li>
     * </ol>
     *
     * @param input 玩家输入的原始种子字符串
     * @return 解析后的 long 型种子值
     */
    private static long parseSeed(String input) {
        //去除首尾空白，避免纯空格被当作有效输入
        String trimmed = input.trim();
        //trim 后为空（仅含空白字符）→ 防御性返回 0
        if (trimmed.isEmpty()) return 0;
        try {
            //纯数字字符串 → 直接解析为 long
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            //文字种子：String.hashCode() 返回 int（仅 2^32 种可能），
            //乘以 long 型大素数 31L 将结果扩展到 long 范围（2^64），
            //降低不同文字种子产生相同 long 值的碰撞概率
            return (long) trimmed.hashCode() * 31L;
        }
    }

    /** 应用种子并返回上级界面 */
    private void applySeedAndClose() {
        if (useWorldSeed) {
            StellarDimensionSeedHolder.useWorldSeed();
        } else {
            StellarDimensionSeedHolder.setCustomSeed(parseSeed(seedText));
        }
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 渲染
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        //标题
        graphics.drawCenteredString(font, this.title, this.width / 2, 30, 0xFFFFFF);

        //当前状态提示
        String statusKey = useWorldSeed ? "stellarmod.screen.dimension_seed.status_world" : "stellarmod.screen.dimension_seed.status_custom";
        graphics.drawCenteredString(font, Component.translatable(statusKey), this.width / 2, 60, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
