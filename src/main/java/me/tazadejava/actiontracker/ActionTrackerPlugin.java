package me.tazadejava.actiontracker;

import me.tazadejava.blockranges.SelectionWand;
import me.tazadejava.blockranges.SpecialItem;
import me.tazadejava.blockranges.SpecialItemEventListener;
import me.tazadejava.mission.MissionCommandHandler;
import me.tazadejava.mission.MissionEventListener;
import me.tazadejava.mission.MissionManager;
import me.tazadejava.mission.WorldManager;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;

//registers the EventListener and MissionCommandHandler into the server

/**
 * Main class that registers events and commands into the server.
 */
public class ActionTrackerPlugin extends JavaPlugin {

    private MissionManager missionManager;

    private MissionCommandHandler commandHandler;

    @EventHandler
    public void onEnable() {
        //make sure main folder is created
        if(!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        //handles multiple worlds being loaded into the server, since some missions span between multiple worlds
        WorldManager worldManager = new WorldManager(this);
        worldManager.loadWorlds();

        //registers the wand for use in manual room defining
        HashMap<String, SpecialItem> specialItems = new HashMap<>();
        specialItems.put("wand", new SelectionWand());
        getServer().getPluginManager().registerEvents(new SpecialItemEventListener(specialItems.values()), this);

        //registers the main mission event listener
        MissionEventListener listener;
        getServer().getPluginManager().registerEvents(listener = new MissionEventListener(), this);

        missionManager = new MissionManager(this, listener);

        //registers the main mission command
        getCommand("mission").setExecutor(commandHandler = new MissionCommandHandler(this, missionManager, specialItems, worldManager));
        getCommand("mission").setTabCompleter(commandHandler);
    }

    @EventHandler
    public void onDisable() {
        missionManager.onDisable();

        commandHandler.restoreBlocks(null);
    }
}
