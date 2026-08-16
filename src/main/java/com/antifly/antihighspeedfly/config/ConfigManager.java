package com.antifly.antihighspeedfly.config;

import com.antifly.antihighspeedfly.AntiHighSpeedFly;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** 配置读取与重载。 */
public final class ConfigManager {

    private final AntiHighSpeedFly plugin;
    private FileConfiguration cfg;

    public ConfigManager(AntiHighSpeedFly plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.cfg = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }

    public boolean isEnabled() {
        return cfg.getBoolean("enabled", true);
    }

    public boolean isWorldEnabled(World world) {
        List<String> worlds = getEnabledWorlds();
        return worlds.isEmpty() || worlds.contains(world.getName());
    }

    public List<String> getEnabledWorlds() {
        return cfg.getStringList("worlds");
    }

    // ---------- 物理一致性模型（残差容差，非速度上限） ----------

    public double getGroundTolerance() {
        return cfg.getDouble("physics.residual-tolerance-ground", 0.10);
    }

    public double getAirTolerance() {
        return cfg.getDouble("physics.residual-tolerance-air", 0.08);
    }

    public double getFlyTolerance() {
        return cfg.getDouble("physics.residual-tolerance-fly", 0.12);
    }

    public double getSwimTolerance() {
        return cfg.getDouble("physics.residual-tolerance-swim", 0.10);
    }

    public double getGlideTolerance() {
        return cfg.getDouble("physics.residual-tolerance-glide", 0.12);
    }

    public long getTransitionGraceMs() {
        return cfg.getLong("physics.transition-grace-ms", 150);
    }

    public long getRocketBoostMs() {
        return cfg.getLong("physics.rocket-boost-ms", 400);
    }

    public long getVelocityRecentMs() {
        return cfg.getLong("physics.velocity-recent-ms", 1000);
    }

    public double getVelocityRecentMultiplier() {
        return cfg.getDouble("physics.velocity-recent-multiplier", 1.6);
    }

    // ---------- 怀疑度系统 ----------

    public int getConsecutiveRequired() {
        return cfg.getInt("suspicion.require-consecutive-violations", 2);
    }

    public double getWarnAt() {
        return cfg.getDouble("suspicion.warn-at", 5.0);
    }

    public double getSetbackAt() {
        return cfg.getDouble("suspicion.setback-at", 15.0);
    }

    public double getKickAt() {
        return cfg.getDouble("suspicion.kick-at", 40.0);
    }

    public long getSetbackCooldownMs() {
        return cfg.getLong("suspicion.setback-cooldown-ms", 1500);
    }

    public boolean isCancelOnWarn() {
        return cfg.getBoolean("suspicion.cancel-on-warn", true);
    }

    public double getDecayPerSecond() {
        return cfg.getDouble("suspicion.decay-per-second", 0.4);
    }

    // ---------- 惩罚 / 警报 ----------

    public boolean isKickEnabled() {
        return cfg.getBoolean("punishments.kick-enabled", true);
    }

    public boolean isNotifyStaff() {
        return cfg.getBoolean("alerts.notify-staff", true);
    }

    public long getAlertCooldownMs() {
        return cfg.getLong("alerts.cooldown-ms", 5000);
    }

    public String msg(String key) {
        return cfg.getString("messages." + key, "");
    }
}
