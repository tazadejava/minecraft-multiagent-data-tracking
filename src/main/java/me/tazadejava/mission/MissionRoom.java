package me.tazadejava.mission;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tazadejava.blockranges.BlockRange2D;
import me.tazadejava.statstracker.PreciseVisibleBlocksRaycaster;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class MissionRoom {

    private String roomName;
    private BlockRange2D bounds;
    @Deprecated
    private Set<Location> visibleRoomBlocks; //the recommendation system is not realistically capable of obtaining a list of visible room blocks before the player enters the room. thus, it has been discontinued

    private Set<Location> entranceExitLocations = new HashSet<>();

    private boolean isScanningRoomBlocks;
    private Runnable afterTimerEnds;

    public MissionRoom(String roomName, BlockRange2D bounds) {
        this.roomName = roomName;
        this.bounds = bounds;
    }

    public MissionRoom(String roomName, World world, JsonObject data) {
        this.roomName = roomName;

        bounds = new BlockRange2D(data.getAsJsonObject("bounds"));

        if(data.has("blocks")) {
            JsonArray blocks = data.getAsJsonArray("blocks");
            visibleRoomBlocks = new HashSet<>();
            for (JsonElement element : blocks) {
                String[] loc = element.getAsString().split(" ");
                visibleRoomBlocks.add(new Location(world, Integer.parseInt(loc[0]), Integer.parseInt(loc[1]), Integer.parseInt(loc[2])));
            }
        }

        if(data.has("entranceExitLocations")) {
            JsonArray locations = data.getAsJsonArray("entranceExitLocations");
            for(JsonElement locString : locations) {
                String[] loc = locString.getAsString().split(" ");
                entranceExitLocations.add(new Location(world, Integer.parseInt(loc[0]), Integer.parseInt(loc[1]), Integer.parseInt(loc[2])));
            }
        }
    }

    public void save(JsonObject mainObject) {
        JsonObject roomData = new JsonObject();

        roomData.add("bounds", bounds.save());

//        JsonArray blocksArray = new JsonArray();

//        for(Location loc : visibleRoomBlocks) {
//            blocksArray.add(loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
//        }

//        roomData.add("blocks", blocksArray);

        JsonArray entranceExitArray = new JsonArray();

        for(Location loc : entranceExitLocations) {
            entranceExitArray.add(loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
        }

        roomData.add("entranceExitLocations", entranceExitArray);

        mainObject.add(roomName, roomData);
    }

    public void beginScanningRoomBlocks(JavaPlugin plugin, Player player, int yLowerBound, int yUpperBound) {
        HashMap<Location, BlockState> lastBlockState = new HashMap<>();
        PreciseVisibleBlocksRaycaster raycaster = new PreciseVisibleBlocksRaycaster(true, true, false, yLowerBound, yUpperBound);
        BlockData transformMaterial = Bukkit.getServer().createBlockData(Material.GLASS);

        isScanningRoomBlocks = true;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isScanningRoomBlocks) {
                    if(!lastBlockState.isEmpty()) {
                        for(Location loc : lastBlockState.keySet()) {
                            Block block = loc.getBlock();
                            BlockState state = lastBlockState.get(loc);

                            player.sendBlockChange(block.getLocation(), state.getBlockData());
                        }
                    }

                    visibleRoomBlocks = lastBlockState.keySet();
                    player.sendMessage("Captured " + visibleRoomBlocks.size() + " blocks in room " + roomName + ".");

                    if(afterTimerEnds != null) {
                        afterTimerEnds.run();
                        afterTimerEnds = null;
                    }

                    cancel();
                    return;
                }

                Set<Block> blocks = raycaster.getVisibleBlocks(player, lastBlockState.keySet());
                for (Block block : blocks) {
                    if(!bounds.isInRange(block.getLocation())) {
                        continue;
                    }

                    lastBlockState.put(block.getLocation(), block.getState());
                    player.sendBlockChange(block.getLocation(), transformMaterial);
                }
            }
        }.runTaskTimer(plugin, 0, 4L);
    }

    public void endScanningRoomBlocks(Runnable afterTimerEnds) {
        isScanningRoomBlocks = false;
        this.afterTimerEnds = afterTimerEnds;
    }

    public BlockRange2D getBounds() {
        return bounds;
    }

    public String getRoomName() {
        return roomName;
    }

    public Set<Location> getEntranceExitLocations() {
        return entranceExitLocations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MissionRoom that = (MissionRoom) o;
        return Objects.equals(roomName, that.roomName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomName);
    }
}
