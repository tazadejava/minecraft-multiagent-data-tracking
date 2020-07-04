package me.tazadejava.statstracker;

import org.bukkit.entity.Player;

import java.util.List;

public interface StatsTracker {

    void startTracking();
    boolean appendCurrentStatsToLog();
    List<Player> getPlayerList();

}
