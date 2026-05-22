package com.luolian.stellarmod.server.data.dimensionline;

import com.google.gson.*;
import com.luolian.stellarmod.server.worldgen.dimensionline.tuning.StellarDimensionAttribute;
import com.luolian.stellarmod.server.worldgen.dimensionline.tuning.StellarTuningResource;
import com.luolian.stellarmod.server.worldgen.dimensionline.tuning.StellarTuningResourceRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 调谐资源 JSON 加载器，负责在数据包重载时扫描并解析调谐资源定义。
 * <p>
 * 继承自 {@link SimpleJsonResourceReloadListener}，自动扫描所有数据包中
 * 调谐资源 JSON 文件，并解析为 {@link StellarTuningResource} 注册到 {@link StellarTuningResourceRegistry}。
 * <p>
 * 注册表以物品 ID 为主键——同一物品无论通过 JSON 还是代码注册，均通过
 * {@link StellarTuningResourceRegistry#getById(ResourceLocation)} 统一查询。
 *
 * <h3>JSON 文件格式</h3>
 * <pre>{@code
 * {
 *   "item": "minecraft:diamond",
 *   "effects": {
 *     "stability": 2,
 *     "vitality": -1,
 *     "resonance": 1
 *   }
 * }
 * }</pre>
 * <ul>
 *   <li>{@code item}：必填，物品的注册名（{@code modid:path}）</li>
 *   <li>{@code effects}：选填，按属性 {@code id} 键值对指定影响值。
 *       未列出的属性默认为 0（无影响），负数为减少，正数为增加</li>
 * </ul>
 *
 * <h3>注册键</h3>
 * 注册表以 JSON 中的 {@code item} 字段值（物品注册名，如 {@code minecraft:diamond}）为主键，
 * 而非文件路径。因此不同文件为同一物品定义调谐资源时，后者会覆盖前者。
 *
 * @see StellarTuningResource
 * @see StellarTuningResourceRegistry
 */
public class StellarTuningResourceLoader extends SimpleJsonResourceReloadListener {
    //LoggerFactory用于获取 Logger 实例，getLogger根据传入的类对象创建一个专属于该类的 Logger
    private static final Logger LOGGER = LoggerFactory.getLogger(StellarTuningResourceLoader.class);

    /** setPrettyPrinting 使日志/导出更易读；disableHtmlEscaping 防止特殊字符被转义 */
    //默认情况下，Gson 会对 HTML 敏感字符进行转义，防止 JSON 在 HTML 页面中直接嵌入时引发 XSS（跨站脚本）安全问题，但在此处不可能出现该问题，转义后可读性会变差，故而取消转义
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /**
     * 第一个参数为 Gson 实例，第二个参数 {@code "tuning_resources"} 为扫描的子目录名。
     * 加载器会扫描所有资源包中 {@code data/<ns>/stellarmod/tuning_resources/} 下的 JSON 文件。
     */
    public StellarTuningResourceLoader() {
        super(GSON, "tuning_resources");
    }

    /**
     * 在数据包重载时调用：清空注册表 → 遍历所有 JSON 文件 → 解析 → 注册。
     *
     * @param resourceMap 键为 JSON 文件的资源位置，值为解析后的 JSON 元素
     * @param manager     资源管理器（父类签名要求，当前未使用）
     * @param profiler    性能分析器（父类签名要求，当前未使用）
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resourceMap, ResourceManager manager, ProfilerFiller profiler) {
        //数据包重载时先清空旧数据，避免残留已删除文件的注册项
        StellarTuningResourceRegistry.clear();

        //Map.Entry 是 Map 接口内部的嵌套接口，专门代表一个键值对实体。
        //resourceMap.entrySet() 返回一个 Set<Map.Entry<...>>，包含了这个 Map 里每一个键值对的视图
        for (Map.Entry<ResourceLocation, JsonElement> entry : resourceMap.entrySet()) {
            //文件资源位置（如 stellarmod:diamond），对应 JSON 文件在数据包中的路径
            ResourceLocation fileId = entry.getKey();
            //JSON 文件内容，已由父类 SimpleJsonResourceReloadListener 完成解析
            JsonElement json = entry.getValue();
            try {
                //解析 JSON → StellarTuningResource，以物品 ID 为主键注册，后续通过 StellarTuningResourceRegistry.getById(itemId) 查询。
                //getAsJsonObject把 JsonElement 转换为 JsonObject。
                //JsonObject 像一个 Map，可以用 get("键名") 访问里面的字段，比如 get("name")
                StellarTuningResource resource = parseResource(fileId, json.getAsJsonObject());
                StellarTuningResourceRegistry.register(resource);
            } catch (Exception e) {
                //单个文件解析失败不影响其他文件加载，仅记录错误日志
                //异常被抛出时，通常会通过构造器传入一条消息，或者由底层库生成，getMessage获取的就是这条消息
                LOGGER.error("Failed to load tuning resource from {}: {}", fileId, e.getMessage());
            }
        }
        //加载完成后输出总数，便于调试时确认正确加载
        LOGGER.info("Loaded {} tuning resources", StellarTuningResourceRegistry.size());
    }

    /**
     * 将单个 JSON 对象解析为 {@link StellarTuningResource} 实例。
     *
     * @param fileId JSON 文件的资源标识，用于错误追踪
     * @param json   代表一个调谐资源定义的 JSON 对象
     * @return 解析后的调谐资源
     * @throws IllegalArgumentException 如果必填字段 {@code item} 缺失或无效
     */
    private StellarTuningResource parseResource(ResourceLocation fileId, JsonObject json) {
        //必填字段：物品 ID
        String itemId = GsonHelper.getAsString(json, "item");
        //GsonHelper.getAsString 不会返回 null（字段缺失会直接抛 JsonSyntaxException（Gson 库在解析 JSON 字符串时发现语法错误）），无需判空
        if (itemId.isEmpty()) {
            //IllegalArgumentException方法接收到了一个非法、不合适或不合逻辑的参数
            throw new IllegalArgumentException("Missing or empty 'item' field in " + fileId);
        }

        //验证物品 ID 格式合法
        if (ResourceLocation.tryParse(itemId) == null) {
            throw new IllegalArgumentException("Invalid item ID format '" + itemId + "' in " + fileId);
        }

        //解析效果数组（选填字段，缺失时全部属性为 0）
        int[] effects = new int[StellarDimensionAttribute.COUNT];
        if (json.has("effects")) {
            JsonObject effectsObj = json.getAsJsonObject("effects");
            for (StellarDimensionAttribute attr : StellarDimensionAttribute.values()) {
                if (effectsObj.has(attr.id())) {
                    //形参： JsonObject -> 代表当前属性所对应的效果数据 JSON 对象;String -> 当前属性的标识符字符串;
                    //int -> 默认值，如果 effectsObj 里没有 attr.id() 这个键，GsonHelper.getAsInt 就会返回这个默认值
                    effects[attr.index()] = GsonHelper.getAsInt(effectsObj, attr.id(), 0);
                }
            }
        }

        return new StellarTuningResource(itemId, effects);
    }
}
