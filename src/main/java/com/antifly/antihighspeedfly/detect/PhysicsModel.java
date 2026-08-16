package com.antifly.antihighspeedfly.detect;

import com.antifly.antihighspeedfly.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * 物理一致性预测模型 —— 完全没有「测速」逻辑。
 *
 * <p>思路：不比较任何「速度是否超过上限」，而是用游戏自身的水平运动物理
 * （阻力、地面摩擦、输入加速度、飞行/游泳目标速度）从玩家<b>上一次被服务端
 * 接受的水平速度</b>出发，<b>预测</b>本 tick 应有的速度向量；再让监听器把
 * 实际位移与预测对比，偏差（残差）超过容差才判定为物理上不可能的运动。</p>
 *
 * <p>速度的“上限”不是配置出来的数字，而是物理模型自然推导的：</p>
 * <ul>
 *   <li>空中水平速度每 tick 必须 ×0.91 衰减——持续保持 1 b/t 的恒定速度，
 *       残差即为 0.09 b/t，超过空中容差即违规；</li>
 *   <li>地面按输入驱动（approach 到目标速度）或按摩擦衰减（0.546/冰面 0.989）——
 *       恒定高于目标速度必然产生残差；</li>
 *   <li>游泳/鞘翅：低于目标速度时向目标接近，高于目标速度时只能按阻力衰减；</li>
 *   <li>创造飞行：速度必须向 flySpeed 属性推导的目标速度收敛。</li>
 * </ul>
 *
 * <p>合法玩法（加速跑、跳跃、冰面滑行、游泳、创造飞行、鞘翅+烟花）全部由
 * 模型本身覆盖，无需任何豁免表。</p>
 */
public final class PhysicsModel {

    // ---------- vanilla 水平运动物理常量 (blocks/tick) ----------

    /** 空中水平阻力：每 tick 速度 ×0.91。 */
    public static final double AIR_DRAG = 0.91;
    /** 水中水平阻力：每 tick 速度 ×0.80。 */
    public static final double WATER_DRAG = 0.80;
    /** 鞘翅水平阻力：每 tick 速度 ×0.99。 */
    public static final double GLIDE_DRAG = 0.99;
    /** 普通方块地面摩擦：每 tick 速度 ×0.546。 */
    public static final double GROUND_FRICTION = 0.546;
    /** 空中微量操控带来的最大附加速度 (b/t)。 */
    public static final double AIR_CONTROL = 0.03;

    /** 步行基准速度 4.317 m/s → 0.2159 b/t。 */
    private static final double WALK_MS = 4.317;
    /** 游泳基准速度 3.0 m/s → 0.15 b/t。 */
    private static final double SWIM_MS = 3.0;
    /** 默认飞行速度 10.89 m/s（flySpeed=0.05）→ 0.5445 b/t。 */
    private static final double FLY_MS = 10.89;
    /** 鞘翅 3D 总速度末端速度 80 m/s → 4.0 b/t（俯冲/烟花可达的物理极限，超过必须衰减）。 */
    public static final double GLIDE_TERMINAL = 80.0 / 20.0;
    /** 地面/游泳接近目标速度的速率。 */
    private static final double APPROACH_RATE = 0.35;
    /** 飞行接近目标速度的速率。 */
    private static final double FLY_APPROACH_RATE = 0.55;

    /** 二维向量。 */
    public record Vec2(double x, double z) {
    }

    private PhysicsModel() {
    }

    /**
     * 预测本 tick 玩家应有的水平速度向量。
     *
     * @param p   玩家
     * @param d   检测状态（含上一次被接受的速度与按键输入）
     * @param cfg 配置
     */
    public static Vec2 predict(Player p, PlayerData d, ConfigManager cfg) {
        double prevX = d.prevVx;
        double prevZ = d.prevVz;
        double prevSpeed = Math.hypot(prevX, prevZ);
        double inX = d.inputX;
        double inZ = d.inputZ;
        double inSpeed = Math.hypot(inX, inZ);
        boolean hasInput = inSpeed > 1e-4;

        // 预测方向：优先沿用上次被接受速度的方向，其次用按键输入方向
        double dirX;
        double dirZ;
        if (prevSpeed > 1e-4) {
            dirX = prevX / prevSpeed;
            dirZ = prevZ / prevSpeed;
        } else if (hasInput) {
            dirX = inX / inSpeed;
            dirZ = inZ / inSpeed;
        } else {
            return new Vec2(0.0, 0.0);
        }

        double mag;
        if (p.isFlying()) {
            // 创造/旁观飞行：向 flySpeed 属性推导的目标速度收敛
            mag = approach(prevSpeed, flyTarget(p), FLY_APPROACH_RATE);
        } else if (p.isGliding()) {
            // 鞘翅：速度向量每 tick 随视角旋转（俯冲/拉起），水平分量会随
            // 总速度的增长而增长，也会因旋转而减小——没有固定的“目标速度”。
            // 因此水平分量只按 0.99 阻力预测，不做“目标接近/强制衰减”假设；
            // 总速度超过末端速度的检查在监听器中单独执行（见 glideTerminalViolated）。
            mag = prevSpeed * GLIDE_DRAG;
        } else if (p.isSwimming() || p.isInWater()) {
            // 游泳：低于目标速度向目标接近，高于目标速度只能按阻力衰减（激流另有宽限）
            double target = swimTarget(p);
            mag = prevSpeed > target
                    ? prevSpeed * WATER_DRAG
                    : approach(prevSpeed, target, APPROACH_RATE);
        } else if (p.isOnGround()) {
            // 地面：有输入 → 向目标速度接近；无输入 → 按脚下方块摩擦衰减
            mag = hasInput
                    ? approach(prevSpeed, groundTarget(p), APPROACH_RATE)
                    : prevSpeed * groundFriction(p);
        } else {
            // 空中：没有输入加速度，只有阻力衰减 + 极小的空中操控
            mag = prevSpeed * AIR_DRAG + (hasInput ? AIR_CONTROL : 0.0);
        }

        return new Vec2(dirX * mag, dirZ * mag);
    }

    /** 本 tick 允许的物理残差容差 (b/t)，按移动状态取值。 */
    public static double tolerance(Player p, ConfigManager cfg) {
        if (p.isFlying()) return cfg.getFlyTolerance();
        if (p.isGliding()) return cfg.getGlideTolerance();
        if (p.isSwimming() || p.isInWater()) return cfg.getSwimTolerance();
        if (p.isOnGround()) return cfg.getGroundTolerance();
        return cfg.getAirTolerance();
    }

    /**
     * 鞘翅「末端速度」检查：3D 总速度已高于物理末端速度（80 m/s）时，
     * 本 tick 不允许保持或增长——必须按鞘翅阻力衰减。
     * （用于拦截恒定超高速的鞘翅飞行外挂；俯冲/烟花的合法加速都在末端速度以下，
     *   不会触发此规则）
     */
    public static boolean glideTerminalViolated(Player p, PlayerData d, double measuredTotal) {
        double prevTotal = totalSpeed(d);
        return p.isGliding()
                && prevTotal > GLIDE_TERMINAL
                && measuredTotal > prevTotal * GLIDE_DRAG + 0.05;
    }

    /** 该检查对应的“本 tick 允许的最大 3D 总速度”。 */
    public static double glideTerminalMaxNow(PlayerData d) {
        return totalSpeed(d) * GLIDE_DRAG + 0.05;
    }

    /** 上一次被接受速度的 3D 总速度 (b/t)。 */
    public static double totalSpeed(PlayerData d) {
        return Math.hypot(Math.hypot(d.prevVx, d.prevVz), d.prevVy);
    }

    // ---------- 目标速度（全部由属性/药水推导，非限速阈值） ----------

    /** 地面目标速度：步行基准 × 疾跑 × 速度药水。 */
    private static double groundTarget(Player p) {
        double base = WALK_MS / 20.0;
        if (p.isSprinting()) {
            base *= 1.3;
        }
        base *= 1.0 + 0.2 * speedAmplifier(p);
        return base;
    }

    /** 飞行目标速度：flySpeed 属性（支持插件改速与疾跑飞行 ×2）。 */
    private static double flyTarget(Player p) {
        double base = p.getFlySpeed() * (FLY_MS / 0.05) / 20.0; // 0.05 ↔ 10.89 m/s
        if (p.isSprinting()) {
            base *= 2.0;
        }
        return base;
    }

    /** 游泳目标速度：基准 3 m/s × 速度药水 × 海豚的恩惠。 */
    private static double swimTarget(Player p) {
        double base = SWIM_MS / 20.0 * (1.0 + 0.2 * speedAmplifier(p));
        if (p.getPotionEffect(PotionEffectType.DOLPHINS_GRACE) != null) {
            base *= 2.0;
        }
        return base;
    }

    /** 速度效果等级（amplifier 0 = 效果 I → 1 级）。 */
    private static int speedAmplifier(Player p) {
        var effect = p.getPotionEffect(PotionEffectType.SPEED);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    /** 脚下方块摩擦系数（冰面滑行、灵魂沙减速、史莱姆等）。 */
    private static double groundFriction(Player p) {
        Material m = p.getLocation().getBlock().getRelative(BlockFace.DOWN).getType();
        if (isIce(m)) return 0.989;
        if (m == Material.SOUL_SAND || m == Material.SOUL_SOIL) return 0.4;
        if (m == Material.SLIME_BLOCK || m == Material.HONEY_BLOCK) return 0.8;
        return GROUND_FRICTION;
    }

    private static boolean isIce(Material m) {
        return m == Material.ICE || m == Material.PACKED_ICE
                || m == Material.BLUE_ICE || m == Material.FROSTED_ICE;
    }

    /** 速度按速率向目标值接近（模拟输入加速度）。 */
    private static double approach(double from, double target, double rate) {
        return from + (target - from) * rate;
    }
}
