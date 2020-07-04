package me.tazadejava.analyzer;

import com.google.gson.JsonObject;
import me.tazadejava.mission.MissionRoom;
import me.tazadejava.statstracker.EnhancedStatsTracker;
import me.tazadejava.mission.Mission;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

//for the most part, this class does not have to exist within the plugin:
//to export to a separate process:
// - save the json file continuously and have an external process check for changes, then perform similar actions to what is being done here, but search the JSON file instead of casting
public class PlayerAnalyzer {

    public static final Set<Material> VICTIM_BLOCKS = new HashSet<>(Arrays.asList(Material.PRISMARINE, Material.GOLD_BLOCK));

    private Player player;
    private Mission mission;

    //TODO: these are questions that will be asked to the player before any experiment starts, obtained via data beforehand
    private boolean isSpatiallyAware = true;

    //TODO: learn how to communicate with genesis to develop rules
        //TODO: still need to learn a bit more on how to maximize genesis usage to my advantage

    //the indices will represent the victim number
    private List<Location> victimBlocks;
    private Block currentVictimTarget;

    private String currentRoom;

    private EnhancedStatsTracker.LastStatsSnapshot lastLastStats;

    private List<String> firstLevelActions;

    public PlayerAnalyzer(Player player, Mission mission) {
        this.player = player;
        this.mission = mission;

        victimBlocks = new ArrayList<>();
        firstLevelActions = new ArrayList<>();

        currentRoom = null;
    }

    private void log(String action) {
        Bukkit.getLogger().info(action);
        firstLevelActions.add(action);

        //TODO temp: print to all players
        for(Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(action);
        }

        //TODO: pass data into genesis, so that genesis can understand data
    }

    public void update(EnhancedStatsTracker.LastStatsSnapshot lastStats) {
        if(lastLastStats == null || lastLastStats.lastPlayerValues == null) {
            lastLastStats = lastStats;
            return;
        }

        EnhancedStatsTracker.LastStatsSnapshot deltaStats = lastStats.calculateDeltaSnapshot(lastLastStats);

        analyzeVictimTarget(lastStats);
        analyzeBrokenBlocks(lastStats, deltaStats);
        analyzeRoom(lastStats);
        analyzeClickedBlocks(deltaStats);

        lastLastStats = lastStats;
    }

    private void analyzeVictimTarget(EnhancedStatsTracker.LastStatsSnapshot lastStats) {
        if(lastStats.lastPlayerValues.get("LineOfSight") instanceof String && ((String) lastStats.lastPlayerValues.get("LineOfSight")).isEmpty()) {
            currentVictimTarget = null;
            return;
        }

        JsonObject lineOfSight = (JsonObject) lastStats.lastPlayerValues.get("LineOfSight");

        if (lineOfSight.size() > 0 && lineOfSight.get("hitType").getAsString().equals("block")) {
            Location loc = new Location(player.getWorld(), lineOfSight.get("x").getAsDouble(), lineOfSight.get("y").getAsDouble(), lineOfSight.get("z").getAsDouble());
            Block block = loc.getBlock();

            if(currentVictimTarget != null && !currentVictimTarget.equals(block)) {
                log(player.getName() + " looked away from victim " + (victimBlocks.indexOf(currentVictimTarget.getLocation())) + ".");
                currentVictimTarget = null;
            }

            if(VICTIM_BLOCKS.contains(block.getType()) && !block.equals(currentVictimTarget)) {
                if(!victimBlocks.contains(block.getLocation())) {
                    victimBlocks.add(block.getLocation());
                }

                log(player.getName() + " looked at victim " + (victimBlocks.indexOf(block.getLocation())) + ".");
                currentVictimTarget = block;
            }
        }
    }

    private void analyzeBrokenBlocks(EnhancedStatsTracker.LastStatsSnapshot lastStats, EnhancedStatsTracker.LastStatsSnapshot deltaStats) {
        boolean savedVictim = false;
        for(Material victimMat : VICTIM_BLOCKS) {
            if(deltaStats.lastPlayerBlocksBroken.containsKey(victimMat.toString().toLowerCase())) {
                //ASSUMPTION: PLAYER CAN ONLY BREAK ONE BLOCK IN ONE TICK'S TIME (don't think this can be violated in any way)
                int victimNumber = victimBlocks.indexOf(deltaStats.lastPlayerBlocksBrokenLocations.get(0));
                log(player.getName() + " saved victim " + victimNumber + ".");

                savedVictim = true;
                break;
            }
        }

        if(!savedVictim && !deltaStats.lastPlayerBlocksBroken.isEmpty()) {
            for(String name : deltaStats.lastPlayerBlocksBroken.keySet()) {
                log(player.getName() + " broken a " + name + " block.");
            }
        }
    }

    private void analyzeRoom(EnhancedStatsTracker.LastStatsSnapshot lastStats) {
        Location location = new Location(player.getWorld(), (double) lastStats.lastPlayerValues.get("XPos"), 0, (double) lastStats.lastPlayerValues.get("ZPos"));

        String currentRoomAnalysis = null;
        for(MissionRoom room : mission.getRooms()) {
            if(room.getBounds().isInRange(location)) {
                currentRoomAnalysis = room.getRoomName();
                break;
            }
        }

        if(currentRoom != null && !currentRoom.equals(currentRoomAnalysis)) {
            if((boolean) lastStats.lastPlayerValues.get("IsSprinting")) {
                log(player.getName() + " ran out of the room named " + currentRoom + ".");
            } else {
                log(player.getName() + " walked out of the room named " + currentRoom + ".");
            }
            currentRoom = null;
        }

        if(currentRoomAnalysis != null && !currentRoomAnalysis.equals(currentRoom)) {
            if((boolean) lastStats.lastPlayerValues.get("IsSprinting")) {
                log(player.getName() + " ran into the room named " + currentRoomAnalysis + ".");
            } else {
                log(player.getName() + " walked into the room named " + currentRoomAnalysis + ".");
            }
            currentRoom = currentRoomAnalysis;
        }
    }

    private void analyzeClickedBlocks(EnhancedStatsTracker.LastStatsSnapshot deltaStats) {
        if(deltaStats.lastPlayerDoorsOpened != 0) {
            log(player.getName() + " opened a door.");
        }
        if(deltaStats.lastPlayerFiresExtinguished != 0) {
            log(player.getName() + " extinguished a fire.");
        }
    }

    public Player getPlayer() {
        return player;
    }
}
