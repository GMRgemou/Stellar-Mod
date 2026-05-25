package com.luolian.stellarmod.server.block.entity;

import com.luolian.stellarmod.server.worldgen.dimensionline.tuning.StellarDimensionAttribute;
import com.luolian.stellarmod.server.worldgen.dimensionline.tuning.StellarDimensionTemplate;
import com.luolian.stellarmod.server.worldgen.dimensionline.tuning.StellarTuningResource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 传送门方块实体——维度科技线的核心运行时载体。
 * <p>
 * 每个传送门方块持有一个 {@link StellarDimensionTemplate}（锚定状态），
 * 玩家投入调谐资源后，效果写入模板，属性实时更新。
 * 最终通过稳定性检定决定链接到哪个维度。
 *
 * <h3>功能范围（第一版）</h3>
 * <ul>
 *   <li>持有 {@link StellarDimensionTemplate} 锚定状态</li>
 *   <li>NBT 持久化（区块卸载→加载恢复）</li>
 *   <li>应用调谐资源效果（查注册表 → 模板 commit）</li>
 *   <li>稳定性检定（委托给模板）</li>
 * </ul>
 * 物品槽位和 GUI 将在第三层实现。
 *
 * <h3>NBT 结构</h3>
 * <pre>{@code
 * {
 *   "Template": { ... StellarDimensionTemplate.toNBT() ... }
 * }
 * }</pre>
 *
 * @see StellarDimensionTemplate
 * @see StellarTuningResource
 */
public class PortalCoreBlockEntity extends BlockEntity {

    //NBT 键名
    private static final String TAG_TEMPLATE = "Template";

    /** 当前传送门的锚定状态（调谐快照），未锚定时为 null */
    @Nullable
    private StellarDimensionTemplate template;

    /**
     * @param pos   传送门方块在世界中的坐标
     * @param state 传送门方块的方块状态
     */
    public PortalCoreBlockEntity(BlockPos pos, BlockState state) {
        super(StellarBlockEntities.PORTAL_CORE_BE.get(), pos, state);
    }

    // ═══════════════════════════════════════════════════════════════
    // 模板管理
    // ═══════════════════════════════════════════════════════════════

    /** 获取当前锚定状态，未锚定时返回 {@code null} */
    @Nullable
    public StellarDimensionTemplate getTemplate() {
        return template;
    }

    /** 设置锚定状态（选定目标维度 + 初始化默认属性） */
    public void setTemplate(@Nullable StellarDimensionTemplate template) {
        this.template = template;
        setChanged();   //标记方块实体数据已变更，触发保存
    }

    /** 传送门是否已锚定目标维度 */
    public boolean isAnchored() {
        return template != null;
    }

    // ═══════════════════════════════════════════════════════════════
    // 调谐操作
    // ═══════════════════════════════════════════════════════════════

    /**
     * 将调谐资源投入传送门，效果立即应用到模板。
     * <p>
     * 调用前应确保 {@code template != null}（已锚定目标维度），
     * 否则操作无意义。
     *
     * @param resource 投入的调谐资源（含物品 ID 和效果数组）
     * @return 投入后的新属性值数组（供 GUI / 日志使用）
     * @throws IllegalStateException 如果尚未锚定目标维度
     */
    public int[] applyTuningResource(StellarTuningResource resource) {
        if (template == null) {
            throw new IllegalStateException("Cannot apply tuning resource before anchoring a target dimension");
        }
        template.commit(resource);
        setChanged();
        return template.getAttributes();
    }

    /**
     * 预览投入调谐资源后的属性值，不修改模板（供 GUI 使用）。
     *
     * @param resource 投入的调谐资源
     * @return 投入后的属性值数组（不修改模板）
     * @throws IllegalStateException 如果尚未锚定目标维度
     */
    public int[] previewTuning(StellarTuningResource resource) {
        if (template == null) {
            throw new IllegalStateException("Cannot preview tuning before anchoring a target dimension");
        }
        return template.preview(resource);
    }

    // ═══════════════════════════════════════════════════════════════
    // 检定委托
    // ═══════════════════════════════════════════════════════════════

    /**
     * 执行稳定性检定，返回链接结果。
     * <p>
     * 直接委托给 {@link StellarDimensionTemplate#checkStability()}，
     * PortalCoreBlockEntity 自身不做额外判断。
     *
     * @return 检定结果，未锚定时返回 {@code null}
     */
    @Nullable
    public StellarDimensionTemplate.LinkResult checkStability() {
        if (template == null) return null;
        return template.checkStability();
    }

    /** 快捷方法：传送门是否已通过稳定性检定 */
    public boolean isStable() {
        return template != null && template.isStable();
    }

    /**
     * 获取指定属性的当前值。
     *
     * @return 属性值，未锚定时返回 -1
     */
    public int getAttribute(StellarDimensionAttribute attr) {
        if (template == null) return -1;
        return template.get(attr);
    }

    // ═══════════════════════════════════════════════════════════════
    // NBT 持久化
    // ═══════════════════════════════════════════════════════════════

    /**
     * 将传送门方块实体的状态序列化到 NBT（区块保存时调用）。
     * <p>
     * {@code tag} 是调用方传入的同一个 {@link CompoundTag} 对象引用（非副本），
     * 父类和子类的写入都作用于同一个可变实例。因此顺序无关紧要——
     * {@code super.saveAdditional(tag)} 先写基础字段，再 {@code tag.put(...)}
     * 追加自定义字段，调用方最终拿到的 tag 包含全部数据。
     * <p>
     * 模板自身的序列化由 {@link StellarDimensionTemplate#toNBT()} 完成，
     * 此处仅将其嵌套在 {@code "Template"} 键下写入。
     */
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (template != null) {
            tag.put(TAG_TEMPLATE, template.toNBT());
        }
    }

    /**
     * 从 NBT 反序列化恢复传送门方块实体的状态（区块加载时调用）。
     */
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_TEMPLATE)) {
            template = StellarDimensionTemplate.fromNBT(tag.getCompound(TAG_TEMPLATE));
        } else {
            template = null;
        }
    }
}
