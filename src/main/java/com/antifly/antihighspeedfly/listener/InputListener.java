package com.antifly.antihighspeedfly.listener;

import com.antifly.antihighspeedfly.AntiHighSpeedFly;
import com.antifly.antihighspeedfly.detect.PlayerData;
import org.bukkit.Input;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;

/**
 * 记录玩家每 tick 的真实按键输入（Paper/Bukkit PlayerInputEvent）。
 * 物理模型据此区分「有输入(主动加速)」与「无输入(惯性滑行/衰减)」，
 * 让地面/空中模型可以精确预测合法速度。
 */
public final class InputListener implements Listener {

    private final AntiHighSpeedFly plugin;

    public InputListener(AntiHighSpeedFly plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInput(PlayerInputEvent event) {
        PlayerData d = plugin.getTracker().get(event.getPlayer().getUniqueId());
        Input input = event.getInput();
        d.inputX = (input.isLeft() ? 1.0 : 0.0) - (input.isRight() ? 1.0 : 0.0);
        d.inputZ = (input.isForward() ? 1.0 : 0.0) - (input.isBackward() ? 1.0 : 0.0);
    }
}
