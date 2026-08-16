package com.antifly.antihighspeedfly.detect;

import org.bukkit.Location;

/** 每个玩家一份的运行时检测状态。 */
public final class PlayerData {

    // ---------- 物理一致性模型状态 ----------

    /** 最近一次被服务端接受的水平速度 (blocks/tick)。 */
    public double prevVx = 0.0;
    public double prevVz = 0.0;

    /** 最近一次被服务端接受的垂直速度 (blocks/tick，用于鞘翅 3D 总速度检查)。 */
    public double prevVy = 0.0;

    /** 上一个 tick 的移动状态（用于检测落地/起跳/起飞/入水等状态跃迁）。 */
    public boolean lastOnGround = false;
    public boolean lastFlying = false;
    public boolean lastGliding = false;
    public boolean lastSwimming = false;

    /** 最近一次按键输入向量（来自 Paper PlayerInputEvent）。 */
    public double inputX = 0.0;
    public double inputZ = 0.0;

    /** 状态跃迁宽限截止时间 (nanoTime)。 */
    public long transitionGraceUntil = 0L;

    /** 鞘翅烟花喷射加速宽限截止时间 (nanoTime)。 */
    public long rocketBoostUntil = 0L;

    // ---------- 怀疑度系统 ----------

    /** 累积怀疑度：超过阈值触发 警告/回退/踢出。 */
    public double suspicion = 0.0;

    /** 连续违规移动数（抗单次卡顿误报）。 */
    public int violationStreak = 0;

    // ---------- 宽限与时间戳 ----------

    /** 最近一次传送时间戳，传送后宽限期内不检测。 */
    public long lastTeleportNanos = 0L;

    /** 最近一次服务端主动施加较强速度的时间戳（击退/TNT/激流等）。 */
    public long lastServerVelocityNanos = 0L;

    /** 上次回退位置的时间戳，防止连续回退。 */
    public long lastSetbackNanos = 0L;

    /** 上次向管理员发送警报的时间戳。 */
    public long lastAlertNanos = 0L;

    /** 最近一个服务端确认过的安全位置，用于回退。 */
    public Location lastSafe = null;

    /** /antifly test 调试模式：实时显示判定数据。 */
    public boolean verbose = false;
}
