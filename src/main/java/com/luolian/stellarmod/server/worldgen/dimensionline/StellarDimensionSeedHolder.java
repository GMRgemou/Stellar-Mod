package com.luolian.stellarmod.server.worldgen.dimensionline;

/**
 * 维度种子桥接器。
 * <p>
 * 负责将客户端 {@code StellarDimensionSeedScreen} 设置的种子传递到
 * 服务端 {@code WorldEvent.Load} 时消费，解决单人游戏中
 * 两个阶段的种子传输问题。
 * <p>
 * 流程：
 * <ol>
 *   <li>客户端 GUI → {@link #setCustomSeed(long)} 或 {@link #useWorldSeed()}</li>
 *   <li>服务端 WorldEvent.Load → {@link #resolveSeed(long)} 取走并清零</li>
 * </ol>
 */
public class StellarDimensionSeedHolder {

    private static boolean useCustomSeed = false;
    private static long customSeed = 0;

    /**
     * 设定自定义维度种子。
     */
    public static void setCustomSeed(long seed) {
        useCustomSeed = true;
        customSeed = seed;
    }

    /**
     * 标记为使用世界种子。
     */
    public static void useWorldSeed() {
        useCustomSeed = false;
    }

    /**
     * 解析最终使用的种子。
     * 如果用户设定了自定义种子则返回它，否则返回传入的世界种子。
     * 调用后自动清空自定义状态，防止下次创建世界时残留。
     */
    public static long resolveSeed(long worldSeed) {
        if (useCustomSeed) {
            useCustomSeed = false;
            return customSeed;
        }
        return worldSeed;
    }

    /** 玩家是否设定了自定义维度种子 */
    public static boolean hasCustomSeed() {
        return useCustomSeed;
    }
}
