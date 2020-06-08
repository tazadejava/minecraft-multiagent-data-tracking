package me.tazadejava.mission;

import org.bukkit.block.Block;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.List;

//tracks player data while the mission is active
public class MissionEventListener implements Listener {

    private HashMap<Player, Integer> doorsOpened;
    private HashMap<Player, HashMap<String, Integer>> blocksBroken;

    public MissionEventListener() {
        doorsOpened = new HashMap<>();
        blocksBroken = new HashMap<>();
    }

    public void initMission(List<Player> players) {
        doorsOpened.clear();
        blocksBroken.clear();

        for(Player p : players) {
            doorsOpened.put(p, 0);
            blocksBroken.put(p, new HashMap<>());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if(blocksBroken.containsKey(p)) {
            HashMap<String, Integer> blockStats = blocksBroken.get(p);

            String blockName = event.getBlock().getBlockData().getAsString();

            if(!blockStats.containsKey(blockName)) {
                blockStats.put(blockName, 1);
            } else {
                blockStats.put(blockName, blockStats.get(blockName) + 1);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if(event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player p = event.getPlayer();
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

    public HashMap<String, Integer> getBlocksBroken(Player p) {
        return blocksBroken.getOrDefault(p, null);
    }
}
