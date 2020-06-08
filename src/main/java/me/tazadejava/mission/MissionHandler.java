package me.tazadejava.mission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

//handles the mission data tracking
public class MissionHandler {

    private JavaPlugin plugin;
    private MissionEventListener listener;

    private Gson gson;

    private StatsTracker statsTracker;
    private CommandSender missionInitiator;
    private boolean missionInProgress;
    private int duration;
    private JsonObject jsonLog;

    public MissionHandler(JavaPlugin plugin, MissionEventListener listener) {
        this.plugin = plugin;
        this.listener = listener;

        gson = new GsonBuilder().setPrettyPrinting().create();

        missionInProgress = false;

        initDataFolder();
    }

    private void initDataFolder() {
        File mainFolder = plugin.getDataFolder();
        File dataFolder = new File(mainFolder.getAbsolutePath() + "/rawData/");

        if(!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public boolean startMission(CommandSender missionInitiator, final int duration) {
        if(missionInProgress) {
            return false;
        }

        this.missionInitiator = missionInitiator;
        this.duration = duration;
        missionInProgress = true;
        jsonLog = new JsonObject();

        statsTracker = new StatsTracker(plugin, listener, jsonLog);
        statsTracker.startTracking();

        new BukkitRunnable() {

            int count = 0;

            @Override
            public void run() {
                statsTracker.appendCurrentStatsToLog();

                count++;
                if(count >= 20 * duration) {
                    endMission();
                    cancel();
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0, 1L);

        return true;
    }

    public void endMission() {
        saveLog();

        missionInProgress = false;
        jsonLog = null;

        missionInitiator.sendMessage("The mission has ended!");
    }

    public JsonObject getJsonLog() {
        return jsonLog;
    }

    private void saveLog() {
        File dataFolder = new File(plugin.getDataFolder().getAbsolutePath() + "/rawData/");
        File dataLog = new File(dataFolder.getAbsolutePath() + "/log.txt");

        try {
            if(!dataLog.exists()) {
                dataLog.createNewFile();
            } else {
                //TODO: CHANGE THIS TO DATESTAMPS INSTEAD
                dataLog.delete();
                dataLog.createNewFile();
            }

            FileWriter fileWriter = new FileWriter(dataLog);
            gson.toJson(jsonLog, fileWriter);
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
