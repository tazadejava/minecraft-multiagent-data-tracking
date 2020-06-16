package me.tazadejava.analyzer;

import com.google.gson.JsonObject;
import me.tazadejava.blockranges.BlockRange2D;
import me.tazadejava.mission.MalmoStatsTracker;
import me.tazadejava.mission.Mission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlayerAnalyzer {

    public static final Material VICTIM_BLOCK = Material.CHEST;

    private Player player;
    private Mission mission;

    //TODO: for other actions: entering room, exiting room, track coordinates
    //TODO: learn how to communicate with genesis to develop rules
        //TODO: still need to learn a bit more on how to maximize genesis usage to my advantage

    //the indices will represent the victim number
    private List<Block> victimBlocks;
    private Block currentVictimTarget;

    private String currentRoom;

    public PlayerAnalyzer(Player player, Mission mission) {
        this.player = player;
        this.mission = mission;

        victimBlocks = new ArrayList<>();

        currentRoom = null;
    }

    public void update(MalmoStatsTracker.LastStatsSnapshot lastStats) {
        analyzeVictimTarget(lastStats);
        analyzeBrokenBlocks(lastStats);
        analyzeRoom(lastStats);
    }

    private void analyzeVictimTarget(MalmoStatsTracker.LastStatsSnapshot lastStats) {
        JsonObject lineOfSight = (JsonObject) lastStats.lastPlayerValues.get("LineOfSight");

        if (lineOfSight.size() > 0 && lineOfSight.get("hitType").getAsString().equals("block")) {
            Location loc = new Location(player.getWorld(), lineOfSight.get("x").getAsDouble(), lineOfSight.get("y").getAsDouble(), lineOfSight.get("z").getAsDouble());
            Block block = loc.getBlock();

            if(currentVictimTarget != null && !currentVictimTarget.equals(block)) {
                Bukkit.broadcastMessage("Looked away from victim " + (victimBlocks.indexOf(currentVictimTarget)));
                currentVictimTarget = null;
            }

            if(block.getType() == VICTIM_BLOCK && !block.equals(currentVictimTarget)) {
                if(!victimBlocks.contains(block)) {
                    victimBlocks.add(block);
                }

                Bukkit.broadcastMessage("Looked at victim " + (victimBlocks.indexOf(block)));
                currentVictimTarget = block;
            }
        }
    }

    private void analyzeBrokenBlocks(MalmoStatsTracker.LastStatsSnapshot lastStats) {
        //TODO: analyze if a chest is broken; if so, then a victim was saved! find which victim it was via the victimBlocks list
    }

    private void analyzeRoom(MalmoStatsTracker.LastStatsSnapshot lastStats) {
        Location location = new Location(player.getWorld(), (double) lastStats.lastPlayerValues.get("XPos"), 0, (double) lastStats.lastPlayerValues.get("ZPos"));

        String currentRoomAnalysis = null;
        for(Map.Entry<String, BlockRange2D> entry : mission.getRooms().entrySet()) {
            if(entry.getValue().isInRange(location)) {
                currentRoomAnalysis = entry.getKey();
                break;
            }
        }

        if(currentRoom != null && !currentRoom.equals(currentRoomAnalysis)) {
            Bukkit.broadcastMessage("Exited room " + currentRoom);
            currentRoom = null;
        }

        if(currentRoomAnalysis != null && !currentRoomAnalysis.equals(currentRoom)) {
            Bukkit.broadcastMessage("Entered room " + currentRoomAnalysis);
            currentRoom = currentRoomAnalysis;
        }
    }

    public Player getPlayer() {
        return player;
    }
}
