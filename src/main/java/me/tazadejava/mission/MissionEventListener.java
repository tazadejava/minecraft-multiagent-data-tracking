package me.tazadejava.mission;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

//tracks player data while the mission is active
public class MissionEventListener implements Listener {

    private MissionManager missionManager;

    private HashMap<Player, Integer> doorsOpened;
    private HashMap<Player, Integer> firesExtinguished;
    private HashMap<Player, HashMap<String, Integer>> blocksBroken;
    private HashMap<Player, List<Location>> blocksBrokenLocations;

    public MissionEventListener() {
        doorsOpened = new HashMap<>();
        firesExtinguished = new HashMap<>();
        blocksBroken = new HashMap<>();
        blocksBrokenLocations = new HashMap<>();
    }

    public void setMissionManager(MissionManager missionManager) {
        this.missionManager = missionManager;
    }

    public void initMission(List<Player> players) {
        doorsOpened.clear();
        firesExtinguished.clear();
        blocksBroken.clear();
        blocksBrokenLocations.clear();

        for(Player p : players) {
            doorsOpened.put(p, 0);
            firesExtinguished.put(p, 0);
            blocksBroken.put(p, new HashMap<>());
            blocksBrokenLocations.put(p, new ArrayList<>());
        }
    }

    //simulate the XP from breaking blocks
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if(missionManager.getCurrentMission() != null && missionManager.isMissionInProgress(missionManager.getCurrentMission())) {
            if(event.getBlock().getType() == Material.GOLD_BLOCK) {
                event.getPlayer().setLevel(event.getPlayer().getLevel() + 25);
            } else if(event.getBlock().getType() == Material.PRISMARINE) {
                event.getPlayer().setLevel(event.getPlayer().getLevel() + 10);
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if(missionManager.getCurrentMission() != null && missionManager.isMissionInProgress(missionManager.getCurrentMission())) {
            event.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if(blocksBroken.containsKey(p)) {
            HashMap<String, Integer> blockStats = blocksBroken.get(p);

            String blockName = event.getBlock().getType().toString().toLowerCase();

            if(!blockStats.containsKey(blockName)) {
                blockStats.put(blockName, 1);
            } else {
                blockStats.put(blockName, blockStats.get(blockName) + 1);
            }

            blocksBrokenLocations.get(p).add(event.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if(event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if(block.getLocation().add(0, 1, 0).getBlock().getType() == Material.FIRE) {
                if(firesExtinguished.containsKey(p)) {
                    firesExtinguished.put(p, firesExtinguished.get(p) + 1);
                }
            }
        }
        if(event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (doorsOpened.containsKey(p)) {
                Block block = event.getClickedBlock();

                if(block.getBlockData().getMaterial().toString().endsWith("_DOOR")) {
                    Door door = (Door) block.getBlockData();
                    if(!door.isOpen()) {
                        doorsOpened.put(p, doorsOpened.get(p) + 1);
                    }
                }
            }
        }
    }

    public int getDoorsOpened(Player p) {
        return doorsOpened.getOrDefault(p, -1);
    }

    public int getFiresExtinguished(Player p) {
        return firesExtinguished.getOrDefault(p, -1);
    }

    public HashMap<String, Integer> getBlocksBroken(Player p) {
        return blocksBroken.getOrDefault(p, null);
    }

    public List<Location> getBlocksBrokenLocations(Player p) {
        return blocksBrokenLocations.getOrDefault(p, null);
    }
}
