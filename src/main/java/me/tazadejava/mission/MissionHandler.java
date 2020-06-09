package me.tazadejava.mission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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

        BossBar countdown = Bukkit.createBossBar("Time left: " + duration + " second" + (duration == 1 ? "" : "s"), BarColor.BLUE, BarStyle.SEGMENTED_10);

        for(Player p : statsTracker.getPlayerList()) {
            countdown.addPlayer(p);
        }

        new BukkitRunnable() {

            int count = 0;

            @Override
            public void run() {
                statsTracker.appendCurrentStatsToLog();

                count++;
                if(count % 20 == 0) {
                    int secondsLeft = (duration - (count / 20));
                    countdown.setTitle("Time left: " + secondsLeft + " second" + (secondsLeft == 1 ? "" : "s"));
                    double progress = (double) secondsLeft / duration;
                    countdown.setProgress(progress);

                    if(progress <= .2 && countdown.getColor() == BarColor.BLUE) {
                        countdown.setColor(BarColor.RED);
                    }
                }
                if(count >= 20 * duration) {
                    countdown.removeAll();

                    endMission();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1L);

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
