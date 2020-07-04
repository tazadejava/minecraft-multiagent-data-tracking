package me.tazadejava.mission;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import me.tazadejava.actiontracker.Utils;
import me.tazadejava.blockranges.BlockRange2D;
import org.bukkit.Location;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

//holds data for a particular mission
public class Mission {

    private String missionID;
    private String missionName;

    private int duration;
    private Location playerSpawnLocation;
    private List<MissionRoom> rooms = new ArrayList<>();

    public Mission(String missionName) {
        this.missionName = missionName;

        missionID = UUID.randomUUID().toString() + "-" + LocalDateTime.now().toString();
        missionID = missionID.replaceAll("[^a-zA-Z0-9]", "");
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

    private void loadMissionFolderData(File dataFolder, Gson gson) {
        File missionFolder = new File(dataFolder.getAbsolutePath() + "/" + missionID + "/");
        if(!missionFolder.exists()) {
            return;
        }

        try {
            File roomsFile = new File(missionFolder.getAbsolutePath() + "/rooms.json");
            if(!roomsFile.exists()) {
                return;
            }

            FileReader reader = new FileReader(roomsFile);
            JsonReader jsonReader = new JsonReader(reader);

            JsonObject object = gson.fromJson(jsonReader, JsonObject.class);

            for(Map.Entry<String, JsonElement> entry : object.entrySet()) {
                rooms.add(new MissionRoom(entry.getKey(), playerSpawnLocation.getWorld(), entry.getValue().getAsJsonObject()));
            }

            reader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
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

        for(MissionRoom room : rooms) {
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

        rooms.add(room);
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
        for(MissionRoom room : rooms) {
            if(room.getRoomName().equals(roomName)) {
                return true;
            }
        }

        return false;
    }

    public List<MissionRoom> getRooms() {
        return rooms;
    }
}
