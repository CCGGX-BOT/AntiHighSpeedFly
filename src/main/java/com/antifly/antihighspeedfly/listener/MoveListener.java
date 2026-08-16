package com.antifly.antihighspeedfly.listener;

import com.antifly.antihighspeedfly.AntiHighSpeedFly;
import com.antifly.antihighspeedfly.config.ConfigManager;
import com.antifly.antihighspeedfly.detect.PlayerData;
import com.antifly.antihighspeedfly.detect.PhysicsModel;
import com.antifly.antihighspeedfly.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Locale;

/**
 * 核心检测监听器：物理一致性（残差）检测。
 *
 * <p>每个移动事件：用物理模型从玩家上一次被接受的速度预测本 tick 应有的
 * 水平速度，与实际位移对比得到残差；残差超过容差 → 违规，按超出比例累计
 * 怀疑度，梯度处罚：警告 → 取消移动(拉回) → 回退到最近安全位置 → 踢出。</p>
 */
public final class MoveListener implements Listener {

    /** 传送后的宽限期，避免误判传送后的首个移动包。 */
    private static final long TELEPORT_GRACE_NANOS = 200_000_000L;
    /** 服务端主动施加较强速度（击退/TNT/激流等）后的初始宽限期。 */
    private static final long VELOCITY_GRACE_NANOS = 300_000_000L;

    private final AntiHighSpeedFly plugin;

    public MoveListener(AntiHighSpeedFly plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        ConfigManager cfg = plugin.getConfigManager();

        if (p.hasPermission("antihighspeedfly.bypass")) return;
        if (!cfg.isEnabled() || !cfg.isWorldEnabled(p.getWorld())) return;
        if (p.isDead() || !p.isOnline()) return;

        PlayerData d = plugin.getTracker().get(p.getUniqueId());
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || !from.getWorld().equals(to.getWorld())) return;

        // 坐骑：速度由坐骑实体决定，不适用玩家物理模型
        if (p.isInsideVehicle()) {
            d.prevVx = 0.0;
            d.prevVz = 0.0;
            d.prevVy = 0.0;
            d.lastSafe = from.clone();
            return;
        }

        long now = System.nanoTime();

        // 状态跃迁（落地/起跳/起飞/入水）→ 短宽限，避免模型切换的瞬时误差误报
        boolean stateChanged = p.isOnGround() != d.lastOnGround
                || p.isFlying() != d.lastFlying
                || p.isGliding() != d.lastGliding
                || p.isSwimming() != d.lastSwimming;
        if (stateChanged) {
            d.transitionGraceUntil = now + cfg.getTransitionGraceMs() * 1_000_000L;
        }

        boolean grace = now < d.transitionGraceUntil
                || now < d.rocketBoostUntil
                || now - d.lastTeleportNanos < TELEPORT_GRACE_NANOS
                || now - d.lastServerVelocityNanos < VELOCITY_GRACE_NANOS;
        if (grace) {
            acceptMove(d, from, to, p);
            return;
        }

        // 恶意包防御：非有限坐标直接重罚
        if (!Double.isFinite(to.getX()) || !Double.isFinite(to.getY()) || !Double.isFinite(to.getZ())) {
            d.suspicion += 100.0;
            punish(p, d, event, cfg, Double.POSITIVE_INFINITY);
            return;
        }

        double mvx = to.getX() - from.getX();
        double mvz = to.getZ() - from.getZ();
        double mvy = to.getY() - from.getY();
        double measured = Math.hypot(mvx, mvz);
        double measuredTotal = Math.hypot(Math.hypot(mvx, mvz), mvy);

        // ---- 物理预测 vs 实际位移 → 残差 ----
        PhysicsModel.Vec2 predicted = PhysicsModel.predict(p, d, cfg);
        double residual = Math.hypot(mvx - predicted.x(), mvz - predicted.z());

        double tolerance = PhysicsModel.tolerance(p, cfg);
        // 服务端刚施加过较强速度：短时间内容差放大，吸收击退/激流的衰减期
        if (now - d.lastServerVelocityNanos < cfg.getVelocityRecentMs() * 1_000_000L) {
            tolerance *= cfg.getVelocityRecentMultiplier();
        }

        double over = residual / tolerance - 1.0;

        // 鞘翅末端速度规则：3D 总速度高于物理末端(80 m/s)后不允许保持或增长。
        // 俯冲/拉起的合法加速都在末端速度以下，不会触发。
        if (PhysicsModel.glideTerminalViolated(p, d, measuredTotal)) {
            double terminalOver = (measuredTotal - PhysicsModel.glideTerminalMaxNow(d)) / tolerance;
            over = Math.max(over, terminalOver);
        }

        boolean flagged = over > 0.0;
        if (flagged) {
            d.violationStreak++;
            d.suspicion += over;
            if (punish(p, d, event, cfg, measured * 20.0)) {
                return; // 已取消本次移动（拉回/回退），不采信该速度
            }
            acceptMove(d, from, to, p);
        } else {
            d.violationStreak = 0;
            d.suspicion = Math.max(0.0, d.suspicion - 0.5);
            acceptMove(d, from, to, p);
        }

        // 调试模式：实时面板
        if (d.verbose) {
            p.sendActionBar(Component.text(Msg.color(String.format(Locale.ROOT,
                    "&7[HSF] &fres &e%.3f &7/ tol &a%.3f &7/ over &c%.2f &7/ sus &6%.1f &7/ n &b%d",
                    residual, tolerance, over, d.suspicion, d.violationStreak))));
        }
    }

    /** 采信本次移动：更新物理模型状态并记录安全位置。 */
    private void acceptMove(PlayerData d, Location from, Location to, Player p) {
        d.prevVx = to.getX() - from.getX();
        d.prevVz = to.getZ() - from.getZ();
        d.prevVy = to.getY() - from.getY();
        d.lastOnGround = p.isOnGround();
        d.lastFlying = p.isFlying();
        d.lastGliding = p.isGliding();
        d.lastSwimming = p.isSwimming();
        d.lastSafe = from.clone();
    }

    /**
     * 按怀疑度梯度执行处罚。
     *
     * @return 本次移动是否已被取消（true 时调用方不得采信该速度）
     */
    private boolean punish(Player p, PlayerData d, PlayerMoveEvent event, ConfigManager cfg, double speedMs) {
        double s = d.suspicion;
        long now = System.nanoTime();

        if (s >= cfg.getKickAt() && cfg.isKickEnabled()) {
            event.setCancelled(true);
            p.kickPlayer(Msg.color(cfg.msg("kick")));
            return true;
        }

        if (s >= cfg.getSetbackAt()) {
            if (now - d.lastSetbackNanos >= cfg.getSetbackCooldownMs() * 1_000_000L) {
                d.lastSetbackNanos = now;
                event.setCancelled(true);
                Location setback = d.lastSafe != null ? d.lastSafe : p.getLocation();
                p.teleportAsync(setback);
                p.sendActionBar(Component.text(Msg.color(cfg.msg("setback"))));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.9f);
                alertStaff(p, cfg.msg("setback"), s, speedMs, d);
                return true;
            }
        }

        if (s >= cfg.getWarnAt() && cfg.isCancelOnWarn()) {
            event.setCancelled(true);
            return true;
        }

        return false;
    }

    /** 向有 antihighspeedfly.alert 权限的玩家广播疑似违规信息（带冷却）。 */
    private void alertStaff(Player p, String msg, double suspicion, double speed, PlayerData d) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isNotifyStaff()) return;
        long now = System.nanoTime();
        if (now - d.lastAlertNanos < cfg.getAlertCooldownMs() * 1_000_000L) return;
        d.lastAlertNanos = now;

        Location loc = p.getLocation();
        String message = Msg.color(String.format(Locale.ROOT,
                "&e[AntiHSF] &f%s &7疑似物理异常高速 &8(速度 &f%.1f &8m/s &7· 怀疑度 &c%.1f&8) &7位置 &f%s %d,%d,%d",
                p.getName(), speed, suspicion,
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

        plugin.getServer().getOnlinePlayers().stream()
                .filter(pl -> pl.hasPermission("antihighspeedfly.alert"))
                .forEach(pl -> pl.sendMessage(message));
        plugin.getLogger().warning(Msg.stripColor(message));
    }
}
