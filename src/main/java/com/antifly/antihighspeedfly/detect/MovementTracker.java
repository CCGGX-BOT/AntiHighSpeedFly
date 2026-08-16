package com.antifly.antihighspeedfly.detect;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 玩家检测数据跟踪表。 */
public final class MovementTracker {

    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

    public PlayerData get(UUID uuid) {
        return data.computeIfAbsent(uuid, k -> new PlayerData());
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
    }

    public void reset(UUID uuid) {
        PlayerData d = data.get(uuid);
        if (d != null) {
            d.suspicion = 0.0;
            d.violationStreak = 0;
            d.prevVx = 0.0;
            d.prevVz = 0.0;
            d.prevVy = 0.0;
        }
    }

    /** 每个游戏刻对全体玩家做怀疑度衰减（由主线程定时任务调用）。 */
    public void decayAll(double perSecond) {
        double perTick = perSecond / 20.0;
        for (PlayerData d : data.values()) {
            d.suspicion = Math.max(0.0, d.suspicion - perTick);
            if (d.suspicion <= 0.0) {
                d.violationStreak = 0;
            }
        }
    }

    public int size() {
        return data.size();
    }
}
