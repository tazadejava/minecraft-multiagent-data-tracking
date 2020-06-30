package me.tazadejava.mission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import me.tazadejava.analyzer.PlayerAnalyzer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//handles the mission data tracking
public class MissionHandler {

    private JavaPlugin plugin;
    private MissionEventListener listener;

    private Gson gson;

    //clause: key will always be lowercase.
    private HashMap<String, Mission> missions;

    private Mission currentMission;
    private StatsTracker statsTracker;
    private List<PlayerAnalyzer> playerAnalyzers;
    private CommandSender missionInitiator;
    private boolean missionInProgress;
    private JsonObject jsonLog;

    private BossBar countdown;

    public MissionHandler(JavaPlugin plugin, MissionEventListener listener) {
        this.plugin = plugin;
        this.listener = listener;

        gson = new GsonBuilder().setPrettyPrinting().create();

        missions = new HashMap<>();

        missionInProgress = false;

        initDataFolder();

        loadData();
    }

    private void loadData() {
        File missionsFile = new File(plugin.getDataFolder().getAbsolutePath() + "/missions.json");

        try {
            if(!missionsFile.exists()) {
                return;
            }

            FileReader reader = new FileReader(missionsFile);
            JsonReader jsonReader = new JsonReader(reader);

            JsonObject object = gson.fromJson(jsonReader, JsonObject.class);

            for(Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String id = entry.getKey();
                Mission mission = new Mission(id, object.getAsJsonObject(id));
                missions.put(mission.getMissionName().toLowerCase(), mission);
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveData() {
        JsonObject head = new JsonObject();
        for(Mission mission : missions.values()) {
            mission.save(head);
        }

        try {
            File missionsFile = new File(plugin.getDataFolder().getAbsolutePath() + "/missions.json");

            if(!missionsFile.exists()) {
                missionsFile.createNewFile();
            }

            FileWriter writer = new FileWriter(missionsFile);
            gson.toJson(head, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initDataFolder() {
        File mainFolder = plugin.getDataFolder();
        File dataFolder = new File(mainFolder.getAbsolutePath() + "/rawData/");

        if(!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public boolean createMission(String missionName) {
        if(missions.containsKey(missionName.toLowerCase())) {
            return false;
        }

        missions.put(missionName.toLowerCase(), new Mission(missionName));
        saveData();
        return true;
    }

    public boolean startMission(CommandSender missionInitiator, Mission mission) {
        if(missionInProgress) {
            return false;
        }

        this.missionInitiator = missionInitiator;
        this.currentMission = currentMission;
        missionInProgress = true;
        jsonLog = new JsonObject();

        statsTracker = new MalmoStatsTracker(plugin, listener, jsonLog);
        statsTracker.startTracking();

        playerAnalyzers = new ArrayList<>();

        int duration = mission.getDuration();

        countdown = Bukkit.createBossBar("Time left: " + duration + " second" + (duration == 1 ? "" : "s"), BarColor.BLUE, BarStyle.SEGMENTED_10);

        for(Player p : statsTracker.getPlayerList()) {
            countdown.addPlayer(p);

            playerAnalyzers.add(new PlayerAnalyzer(p, mission));

            p.teleport(mission.getPlayerSpawnLocation());
        }

        new BukkitRunnable() {

            int count = 0;

            @Override
            public void run() {
                if(!missionInProgress) {
                    cancel();
                    return;
                }

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
                    endMission(true);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if(!missionInProgress) {
                    cancel();
                    return;
                }

                for(PlayerAnalyzer analyzer : playerAnalyzers) {
                    analyzer.update(((MalmoStatsTracker) statsTracker).getLastStatsSnapshot(analyzer.getPlayer()));
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 1L);

        return true;
    }

    public boolean abortMission() {
        if(!missionInProgress) {
            return false;
        }

        endMission(false);
        return true;
    }

    public boolean doesMissionExist(String mission) {
        return missions.containsKey(mission.toLowerCase());
    }

    public Mission getMission(String missionName) {
        return missions.get(missionName.toLowerCase());
    }

    public boolean isMissionInProgress(Mission mission) {
        return missionInProgress && currentMission == mission;
    }

    private void endMission(boolean saveLog) {
        if(saveLog) {
            saveLog();
        }

        missionInProgress = false;
        jsonLog = null;
        countdown.removeAll();

        missionInitiator.sendMessage("The mission has ended!");
    }

    public JsonObject getJsonLog() {
        return jsonLog;
    }

    public Collection<Mission> getAllMissions() {
        if(missions.isEmpty()) {
            return null;
        }

        return missions.values();
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

    public void onDisable() {
        if(missionInProgress) {
            abortMission();
        }
    }
}
