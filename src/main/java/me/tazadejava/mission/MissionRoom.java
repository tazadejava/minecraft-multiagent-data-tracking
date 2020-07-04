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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class MissionRoom {

    private String roomName;
    private BlockRange2D bounds;
    private Set<Location> visibleRoomBlocks;

    private boolean isScanningRoomBlocks;
    private Runnable afterTimerEnds;

    public MissionRoom(String roomName, BlockRange2D bounds) {
        this.roomName = roomName;
        this.bounds = bounds;
    }

    public MissionRoom(String roomName, World world, JsonObject data) {
        this.roomName = roomName;

        bounds = new BlockRange2D(data.getAsJsonObject("bounds"));

        JsonArray blocks = data.getAsJsonArray("blocks");

        visibleRoomBlocks = new HashSet<>();
        for(JsonElement element : blocks) {
            String[] loc = element.getAsString().split(" ");
            visibleRoomBlocks.add(new Location(world, Integer.parseInt(loc[0]), Integer.parseInt(loc[1]), Integer.parseInt(loc[2])));
        }
    }

    public void save(JsonObject mainObject) {
        JsonObject roomData = new JsonObject();

        roomData.add("bounds", bounds.save());

        JsonArray blocksArray = new JsonArray();

        for(Location loc : visibleRoomBlocks) {
            blocksArray.add(loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
        }

        roomData.add("blocks", blocksArray);

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

                Block[] blocks = raycaster.getVisibleBlocks(player, lastBlockState.keySet());
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
}
