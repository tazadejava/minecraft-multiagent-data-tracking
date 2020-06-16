package me.tazadejava.mission;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tazadejava.actiontracker.Utils;
import me.tazadejava.blockranges.BlockRange2D;
import org.bukkit.Location;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

//holds data for a particular mission
public class Mission {

    private String missionID;
    private String missionName;

    private int duration;
    private Location playerSpawnLocation;
    private HashMap<String, BlockRange2D> rooms = new HashMap<>();

    public Mission(String missionName) {
        this.missionName = missionName;

        missionID = UUID.randomUUID().toString() + "-" + LocalDateTime.now().toString();
        missionID = missionID.replaceAll("[^a-zA-Z0-9]", "");
    }

    public Mission(String id, JsonObject details) {
        missionID = id;

        missionName = details.get("name").getAsString();

        if (details.has("duration")) {
            duration = details.get("duration").getAsInt();
        }
        if (details.has("location")) {
            playerSpawnLocation = Utils.getLocation(details.getAsJsonObject("location"));
        }
        if (details.has("rooms")) {
            JsonObject roomsObject = details.getAsJsonObject("rooms");

            for(Map.Entry<String, JsonElement> entry : roomsObject.entrySet()) {
                rooms.put(entry.getKey(), new BlockRange2D(roomsObject.getAsJsonObject(entry.getKey())));
            }
        }
    }

    public void save(JsonObject main) {
        JsonObject details = new JsonObject();
        details.addProperty("name", missionName);
        if (hasDuration()) {
            details.addProperty("duration", duration);
        }
        if (hasPlayerSpawnLocation()) {
            details.add("location", Utils.saveLocation(playerSpawnLocation));
        }
        if(!rooms.isEmpty()) {
            JsonObject roomsObject = new JsonObject();

            for(Map.Entry<String, BlockRange2D> entry : rooms.entrySet()) {
                roomsObject.add(entry.getKey(), entry.getValue().save());
            }

            details.add("rooms", roomsObject);
        }

        main.add(missionID, details);
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

    public void addRoom(String roomName, BlockRange2D range) {
        if(hasRoom(roomName)) {
            return;
        }

        rooms.put(roomName, range);
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

    public HashMap<String, BlockRange2D> getRooms() {
        return rooms;
    }
}
