package com.antifly.antihighspeedfly.listener;

import com.antifly.antihighspeedfly.AntiHighSpeedFly;
import com.antifly.antihighspeedfly.detect.PlayerData;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 鞘翅烟花喷射（PlayerElytraBoostEvent）：使用烟花会产生一次突然的
 * 大幅加速（合法机制），记录宽限期让物理残差检测跳过这段加速窗口。
 */
public final class ItemUseListener implements Listener {

    private final AntiHighSpeedFly plugin;

    public ItemUseListener(AntiHighSpeedFly plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        PlayerData d = plugin.getTracker().get(event.getPlayer().getUniqueId());
        d.rocketBoostUntil = System.nanoTime()
                + plugin.getConfigManager().getRocketBoostMs() * 1_000_000L;
    }
}
