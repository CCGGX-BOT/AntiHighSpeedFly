package com.antifly.antihighspeedfly;

import com.antifly.antihighspeedfly.command.AntiFlyCommand;
import com.antifly.antihighspeedfly.config.ConfigManager;
import com.antifly.antihighspeedfly.detect.MovementTracker;
import com.antifly.antihighspeedfly.listener.InputListener;
import com.antifly.antihighspeedfly.listener.ItemUseListener;
import com.antifly.antihighspeedfly.listener.MoveListener;
import com.antifly.antihighspeedfly.listener.PlayerLifecycleListener;
import com.antifly.antihighspeedfly.listener.TeleportListener;
import com.antifly.antihighspeedfly.listener.VelocityListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * 反「高速飞行」外挂插件主类。
 *
 * <p>核心思路：不做普通限速、也不测速——用游戏自身的水平运动物理
 * （阻力/摩擦/输入加速度/目标速度）从玩家上一次被接受的速度出发预测
 * 本 tick 应有的位移，与实际位移对比残差；残差超过容差才判定违规。
 * 速度上限由物理模型自然推导，配置中只有「残差容差」这一模型误差参数。</p>
 */
public final class AntiHighSpeedFly extends JavaPlugin {

    private static AntiHighSpeedFly instance;

    private ConfigManager configManager;
    private MovementTracker tracker;

    public static AntiHighSpeedFly getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        configManager = new ConfigManager(this);
        tracker = new MovementTracker();

        getServer().getPluginManager().registerEvents(new MoveListener(this), this);
        getServer().getPluginManager().registerEvents(new InputListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemUseListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportListener(this), this);
        getServer().getPluginManager().registerEvents(new VelocityListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this), this);

        Objects.requireNonNull(getCommand("antifly"), "antifly 命令未注册").setExecutor(new AntiFlyCommand(this));

        // 怀疑度每秒自然衰减
        getServer().getScheduler().runTaskTimer(this, () -> tracker.decayAll(configManager.getDecayPerSecond()), 20L, 20L);

        getLogger().info("AntiHighSpeedFly 已启用 (Bukkit " + getServer().getBukkitVersion() + ")");
    }

    @Override
    public void onDisable() {
        getLogger().info("AntiHighSpeedFly 已禁用");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MovementTracker getTracker() {
        return tracker;
    }
}
