package me.tazadejava.actiontracker;

import me.tazadejava.blockranges.SelectionWand;
import me.tazadejava.blockranges.SpecialItem;
import me.tazadejava.blockranges.SpecialItemEventListener;
import me.tazadejava.mission.MissionEventListener;
import me.tazadejava.mission.MissionCommandHandler;
import me.tazadejava.mission.MissionHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;

//registers the EventListener and MissionCommandHandler into the server
public class ActionTrackerPlugin extends JavaPlugin {

    private MissionHandler missionHandler;

    @EventHandler
    public void onEnable() {
        HashMap<String, SpecialItem> specialItems = new HashMap<>();
        specialItems.put("wand", new SelectionWand());

        MissionEventListener listener;
        getServer().getPluginManager().registerEvents(listener = new MissionEventListener(), this);
        missionHandler = new MissionHandler(this, listener);

        getServer().getPluginManager().registerEvents(new SpecialItemEventListener(specialItems.values()), this);

        MissionCommandHandler commandHandler;
        getCommand("mission").setExecutor(commandHandler = new MissionCommandHandler(this, missionHandler, specialItems));
        getCommand("mission").setTabCompleter(commandHandler);
    }

    @EventHandler
    public void onDisable() {
        missionHandler.onDisable();
    }
}
