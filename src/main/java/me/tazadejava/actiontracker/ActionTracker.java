package me.tazadejava.actiontracker;

import me.tazadejava.mission.MissionEventListener;
import me.tazadejava.mission.MissionCommandHandler;
import me.tazadejava.mission.MissionHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

//registers the EventListener and MissionCommandHandler into the server
public class ActionTracker extends JavaPlugin {

    @EventHandler
    public void onEnable() {
        MissionEventListener listener;
        getServer().getPluginManager().registerEvents(listener = new MissionEventListener(), this);
        MissionHandler missionHandler = new MissionHandler(this, listener);
        getCommand("mission").setExecutor(new MissionCommandHandler(missionHandler));
    }

    @EventHandler
    public void onDisable() {

    }
}
