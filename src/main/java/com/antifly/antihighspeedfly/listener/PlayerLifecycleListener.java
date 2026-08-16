package com.antifly.antihighspeedfly.listener;

import com.antifly.antihighspeedfly.AntiHighSpeedFly;
import com.antifly.antihighspeedfly.detect.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** 玩家生命周期：进入/退出/重生时的数据维护。 */
public final class PlayerLifecycleListener implements Listener {

    private final AntiHighSpeedFly plugin;

    public PlayerLifecycleListener(AntiHighSpeedFly plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getTracker().get(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getTracker().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerData d = plugin.getTracker().get(event.getPlayer().getUniqueId());
        d.suspicion = 0.0;
        d.violationStreak = 0;
        d.prevVx = 0.0;
        d.prevVz = 0.0;
        d.prevVy = 0.0;
        d.lastSafe = event.getRespawnLocation().clone();
    }
}
