package com.antifly.antihighspeedfly.util;

import org.bukkit.ChatColor;

/** 消息颜色工具。 */
public final class Msg {

    private Msg() {
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String stripColor(String s) {
        return ChatColor.stripColor(s);
    }
}
