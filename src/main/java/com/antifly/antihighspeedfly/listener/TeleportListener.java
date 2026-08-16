package com.antifly.antihighspeedfly.listener;

import com.antifly.antihighspeedfly.AntiHighSpeedFly;
import com.antifly.antihighspeedfly.detect.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * 传送监听：任何传送都会进入宽限期，避免把传送误判为高速移动；
 * 服务端/插件传送后的位置视为可信安全参考点。
 */
public final class TeleportListener implements Listener {

    private final AntiHighSpeedFly plugin;

    public TeleportListener(AntiHighSpeedFly plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerData d = plugin.getTracker().get(event.getPlayer().getUniqueId());
        d.lastTeleportNanos = System.nanoTime();
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            d.lastSafe = event.getTo().clone();
        }
    }
}
