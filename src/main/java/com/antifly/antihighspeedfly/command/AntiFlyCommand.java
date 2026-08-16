package com.antifly.antihighspeedfly.command;

import com.antifly.antihighspeedfly.AntiHighSpeedFly;
import com.antifly.antihighspeedfly.config.ConfigManager;
import com.antifly.antihighspeedfly.detect.PlayerData;
import com.antifly.antihighspeedfly.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /antifly 管理命令。 */
public final class AntiFlyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS =
            List.of("reload", "toggle", "info", "reset", "test");

    private final AntiHighSpeedFly plugin;

    public AntiFlyCommand(AntiHighSpeedFly plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("antihighspeedfly.admin")) {
            sender.sendMessage(Msg.color("&c你没有权限使用此命令"));
            return true;
        }

        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.getConfigManager().reload();
                sender.sendMessage(Msg.color("&a配置已重载"));
            }
            case "toggle" -> {
                boolean now = !plugin.getConfig().getBoolean("enabled", true);
                plugin.getConfig().set("enabled", now);
                plugin.saveConfig();
                plugin.getConfigManager().reload();
                sender.sendMessage(Msg.color(now ? "&a检测已启用" : "&c检测已禁用"));
            }
            case "info" -> {
                ConfigManager cfg = plugin.getConfigManager();
                sender.sendMessage(Msg.color("&e===== AntiHighSpeedFly ====="));
                sender.sendMessage(Msg.color("&7启用状态: &f" + (cfg.isEnabled() ? "&a是" : "&c否")));
                sender.sendMessage(Msg.color("&7跟踪玩家数: &f" + plugin.getTracker().size()));
                sender.sendMessage(Msg.color("&7检测世界: &f" + (cfg.getEnabledWorlds().isEmpty()
                        ? "所有世界" : String.join(", ", cfg.getEnabledWorlds()))));
                sender.sendMessage(Msg.color("&7物理残差容差(地面/空中/飞行/游泳/鞘翅): &f"
                        + cfg.getGroundTolerance() + " / " + cfg.getAirTolerance() + " / "
                        + cfg.getFlyTolerance() + " / " + cfg.getSwimTolerance() + " / "
                        + cfg.getGlideTolerance()));
                sender.sendMessage(Msg.color("&7怀疑度阈值(警告/回退/踢出): &f"
                        + cfg.getWarnAt() + " / " + cfg.getSetbackAt() + " / " + cfg.getKickAt()));
            }
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(Msg.color("&c用法: /antifly reset <玩家>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Msg.color("&c玩家不在线"));
                    return true;
                }
                plugin.getTracker().reset(target.getUniqueId());
                sender.sendMessage(Msg.color("&a已重置 &f" + target.getName() + " &a的检测数据"));
            }
            case "test" -> {
                if (args.length < 2) {
                    sender.sendMessage(Msg.color("&c用法: /antifly test <玩家>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Msg.color("&c玩家不在线"));
                    return true;
                }
                PlayerData d = plugin.getTracker().get(target.getUniqueId());
                d.verbose = !d.verbose;
                sender.sendMessage(Msg.color("&a已" + (d.verbose ? "开启" : "关闭") + " &f" + target.getName()
                        + " &a的实时调试面板"));
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Msg.color(
                "&e===== AntiHighSpeedFly 命令 =====\n" +
                "&e/antifly reload &7- 重载配置\n" +
                "&e/antifly toggle &7- 启用/禁用检测\n" +
                "&e/antifly info &7- 查看运行状态\n" +
                "&e/antifly reset <玩家> &7- 重置某玩家的怀疑度\n" +
                "&e/antifly test <玩家> &7- 开关某玩家的实时调试面板"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String s : SUB_COMMANDS) {
                if (s.startsWith(prefix)) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("test"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        }
        return out;
    }
}
