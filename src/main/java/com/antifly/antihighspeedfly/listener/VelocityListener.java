package com.antifly.antihighspeedfly.listener;

import com.antifly.antihighspeedfly.AntiHighSpeedFly;
import com.antifly.antihighspeedfly.detect.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerVelocityEvent;

/**
 * 服务端主动施加的较强速度（击退 / TNT / 激流三叉戟 / 鞘翅弹射等）
 * 会带来短暂高速。记录时间戳，让移动检测在宽限期内跳过，
 * 避免把合法机制判定为外挂。
 */
public final class VelocityListener implements Listener {

    private static final double GRACE_SPEED_MS = 8.0;

    private final AntiHighSpeedFly plugin;

    public VelocityListener(AntiHighSpeedFly plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        double horizontalMs = Math.hypot(
                event.getVelocity().getX(), event.getVelocity().getZ()) * 20.0;
        if (horizontalMs > GRACE_SPEED_MS) {
            PlayerData d = plugin.getTracker().get(event.getPlayer().getUniqueId());
            d.lastServerVelocityNanos = System.nanoTime();
        }
    }
}
