package me.tazadejava.mission;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import me.tazadejava.actiontracker.Utils;
import org.bukkit.Location;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.*;

//holds data for a particular mission
public class Mission {

    private String missionID;
    private String missionName;

    private int duration;
    private Location playerSpawnLocation;

    private HashMap<String, MissionRoom> rooms = new HashMap<>();
    private HashMap<String, Location> decisionPoints = new HashMap<>();

    private MissionGraph originalGraph, currentMissionGraph;

    /**
     * Import from a CSV file constructor
     * @param missionName
     * @param missionID
     * @param dataFolder
     */
    public Mission(String missionName, String missionID, File dataFolder, Location playerSpawnLocation) {
        this.missionName = missionName;
        this.missionID = missionID;
        this.playerSpawnLocation = playerSpawnLocation;

        //todo: make this an argument instead of setting for the player
        duration = 1000;

        loadMissionFolderData(dataFolder, new Gson());
    }

    public Mission(String missionName) {
        this.missionName = missionName;

        missionID = UUID.randomUUID().toString() + "-" + LocalDateTime.now().toString();
        missionID = missionID.replaceAll("[^a-zA-Z0-9]", "");

        originalGraph = new MissionGraph(this);
    }

    public Mission(String id, File dataFolder, Gson gson, JsonObject details) {
        missionID = id;

        missionName = details.get("name").getAsString();

        if (details.has("duration")) {
            duration = details.get("duration").getAsInt();
        }
        if (details.has("location")) {
            playerSpawnLocation = Utils.getLocation(details.getAsJsonObject("location"));
        }

        loadMissionFolderData(dataFolder, gson);
    }

    public void startMission() {
        currentMissionGraph = originalGraph.clone();
    }

    private void loadMissionFolderData(File dataFolder, Gson gson) {
        File missionFolder = new File(dataFolder.getAbsolutePath() + "/" + missionID + "/");
        if(!missionFolder.exists()) {
            return;
        }

        try {
            File roomsFile = new File(missionFolder.getAbsolutePath() + "/rooms.json");
            if(roomsFile.exists()) {
                FileReader reader = new FileReader(roomsFile);
                JsonReader jsonReader = new JsonReader(reader);

                JsonObject object = gson.fromJson(jsonReader, JsonObject.class);

                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    rooms.put(entry.getKey(), new MissionRoom(entry.getKey(), playerSpawnLocation.getWorld(), entry.getValue().getAsJsonObject()));
                }

                reader.close();
            }

            File decisionPointsFile = new File(missionFolder.getAbsolutePath() + "/decisionGraph.json");
            if(decisionPointsFile.exists()) {
                FileReader reader = new FileReader(decisionPointsFile);
                JsonReader jsonReader = new JsonReader(reader);

                JsonObject main = gson.fromJson(jsonReader, JsonObject.class);

                JsonObject decisionPointsObj = main.getAsJsonObject("decisionPoints");
                for(Map.Entry<String, JsonElement> entry : decisionPointsObj.entrySet()) {
                    String point = entry.getValue().getAsString();
                    String[] split = point.split(" ");
                    decisionPoints.put(entry.getKey(), new Location(playerSpawnLocation.getWorld(), Double.parseDouble(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2])));
                }

                originalGraph = new MissionGraph(this, main.getAsJsonObject("graphData"), playerSpawnLocation.getWorld());

                reader.close();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteMissionFolderData(File dataFolder) {
        try {
            File missionFolder = new File(dataFolder.getAbsolutePath() + "/" + missionID + "/");
            if(missionFolder.isDirectory() && missionFolder.exists()) {
                Files.walkFileTree(missionFolder.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveMissionFolderData(File dataFolder, Gson gson) {
        //create mission folder
        File missionFolder = new File(dataFolder.getAbsolutePath() + "/" + missionID + "/");
        if(!missionFolder.exists()) {
            missionFolder.mkdirs();
        }

        //save room data

        JsonObject roomObject = new JsonObject();

        for(MissionRoom room : rooms.values()) {
            room.save(roomObject);
        }

        try {
            File roomsFile = new File(missionFolder.getAbsolutePath() + "/rooms.json");
            if(!roomsFile.exists()) {
                roomsFile.createNewFile();
            }

            FileWriter writer = new FileWriter(roomsFile);
            gson.toJson(roomObject, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //save decision point data

        if(!decisionPoints.isEmpty()) {
            JsonObject main = new JsonObject();

            JsonObject decisionPointsObj = new JsonObject();
            for(Map.Entry<String, Location> entry : decisionPoints.entrySet()) {
                Location loc = entry.getValue();
                decisionPointsObj.addProperty(entry.getKey(), loc.getX() + " " + loc.getY() + " " + loc.getZ());
            }

            main.add("decisionPoints", decisionPointsObj);

            main.add("graphData", originalGraph.save());

            try {
                File decisionPointsFile = new File(missionFolder.getAbsolutePath() + "/decisionGraph.json");
                if(!decisionPointsFile.exists()) {
                    decisionPointsFile.createNewFile();
                }

                FileWriter writer = new FileWriter(decisionPointsFile);
                gson.toJson(main, writer);
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void save(File dataFolder, Gson gson, JsonObject main) {
        JsonObject details = new JsonObject();
        details.addProperty("name", missionName);
        if (hasDuration()) {
            details.addProperty("duration", duration);
        }
        if (hasPlayerSpawnLocation()) {
            details.add("location", Utils.saveLocation(playerSpawnLocation));
        }

        if(!decisionPoints.isEmpty()) {
            JsonObject decisionPointsObj = new JsonObject();
            for(Map.Entry<String, Location> entry : decisionPoints.entrySet()) {
                Location loc = entry.getValue();
                decisionPointsObj.addProperty(entry.getKey(), loc.getX() + " " + loc.getY() + " " + loc.getZ());
            }
            details.add("decisionPoints", decisionPointsObj);
        }

        main.add(missionID, details);

        saveMissionFolderData(dataFolder, gson);
    }

    public void setMissionName(String missionName) {
        this.missionName = missionName;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setPlayerSpawnLocation(Location loc) {
        playerSpawnLocation = loc;
    }

    public void addRoom(MissionRoom room) {
        if(hasRoom(room.getRoomName())) {
            return;
        }

        rooms.put(room.getRoomName(), room);
    }

    public boolean hasDuration() {
        return duration != 0;
    }

    public boolean hasPlayerSpawnLocation() {
        return playerSpawnLocation != null;
    }

    public boolean canRunMission() {
        return hasDuration() && hasPlayerSpawnLocation();
    }

    public String getMissionName() {
        return missionName;
    }

    public int getDuration() {
        return duration;
    }

    public Location getPlayerSpawnLocation() {
        return playerSpawnLocation;
    }

    public String getMissionID() {
        return missionID;
    }

    public boolean hasRoom(String roomName) {
        return rooms.containsKey(roomName);
    }

    public boolean hasDecisionPoint(String name) {
        return decisionPoints.containsKey(name);
    }

    public void addDecisionPoint(String name, Location loc) {
        decisionPoints.put(name, loc);
    }

    public HashMap<String, Location> getDecisionPoints() {
        return decisionPoints;
    }

    public MissionRoom getRoom(String name) {
        return rooms.getOrDefault(name, null);
    }

    public Collection<MissionRoom> getRooms() {
        return rooms.values();
    }

    public MissionGraph getMissionGraph() {
        return currentMissionGraph;
    }

    public MissionGraph getOriginalMissionGraph() {
        return originalGraph;
    }
}
