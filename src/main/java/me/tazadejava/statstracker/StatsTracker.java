package me.tazadejava.statstracker;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Interface class to represent different types of stat trackers.
 */
public interface StatsTracker {

    void startTracking();
    boolean appendCurrentStatsToLog();
    List<Player> getPlayerList();

}
