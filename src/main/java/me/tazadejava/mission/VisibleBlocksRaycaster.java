package me.tazadejava.mission;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

//get the blocks that the player can see
//inspired by algorithm that Essie wrote in python
public class VisibleBlocksRaycaster {

    class RaycastBounds {

        private final float FOV_HORIZONTAL_ANGLE_HALF = (float) Math.toRadians(45);//35 //increased angle for better accuracy
        private final float FOV_VERTICAL_ANGLE_HALF = (float) Math.toRadians(53);//43 //increased angle for better accuracy

        private Vector playerLocation, playerDirection;
        private double angle;

        public RaycastBounds(Player p) {
            playerLocation = p.getEyeLocation().toVector();
            playerDirection = p.getEyeLocation().getDirection();
            angle = Math.toRadians(50);
        }

        public boolean isInBounds(Location loc) {
            Vector vecToLoc = loc.toVector().subtract(playerLocation);
            return vecToLoc.angle(playerDirection) <= angle;
            //TODO: for more fine bound-checking, compare the horizontal and vertical angles separately
        }
    }

    private Set<Material> transparentBlocks;

    private int[] deltaDirs = new int[] {
            1, 0, 0,
            -1, 0, 0,
            0, 1, 0,
            0, -1, 0,
            0, 0, 1,
            0, 0, -1};

    public VisibleBlocksRaycaster() {
        transparentBlocks = new HashSet<>();
        transparentBlocks.addAll(Arrays.asList(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR, Material.GLASS, Material.GLASS_PANE));
    }

    private boolean isTransparent(Material material) {
        if(transparentBlocks.contains(material)) {
            return true;
        }
        if(material.toString().contains("GLASS")) {
            return true;
        }

        return false;
    }

    public List<Block> getVisibleBlocks(Player p) {
        Set<Block> blocks = new HashSet<>();

        RaycastBounds bounds = new RaycastBounds(p);

        //to check which blocks are being seen as an estimate: need to raycast the current target block, then do other raycasts towards left/right and up/down to capture all possible in sight; breadth search from each to find the surrounding blocks
        //to check if block is visible to player: if block is surrounded by untransparent blocks, then no. otherwise, if the transparent block is one that faces the player, then maybe

        int maxDistance = 10;
        int maxDistanceSquared = (int) Math.pow(maxDistance, 2);

        List<Block> lineOfSight = p.getLineOfSight(transparentBlocks, maxDistance);

        if(!lineOfSight.isEmpty() && lineOfSight.get(lineOfSight.size() - 1).getType() != Material.AIR) {
            Location startLocation = lineOfSight.get(lineOfSight.size() - 2).getLocation();
            //center the location to be fairly raycasted by all angles
            startLocation.add(0.5, 0.5, 0.5);
            LinkedList<Location> openBlocksList = new LinkedList<>();
            Set<Location> closedBlocksList = new HashSet<>();

            openBlocksList.add(startLocation);
            closedBlocksList.add(startLocation);

            int openCount = 0;
            while (!openBlocksList.isEmpty()) {
                openCount++;
                Location loc = openBlocksList.poll();

                for (int i = 0; i < 6; i++) {
                    Location deltaLoc = loc.clone().add(deltaDirs[i * 3], deltaDirs[i * 3 + 1], deltaDirs[i * 3 + 2]);
                    Block deltaBlock = deltaLoc.getBlock();

                    if(!bounds.isInBounds(deltaLoc)) {
                        if(deltaBlock.getType() != Material.AIR) {
                            deltaBlock.setType(Material.RED_WOOL);
                        }
                        continue;
                    }
                    if(deltaLoc.distanceSquared(startLocation) > maxDistanceSquared) {
                        if(deltaBlock.getType() != Material.AIR) {
                            deltaBlock.setType(Material.RED_WOOL);
                        }
                        continue;
                    }

                    if(deltaBlock.getType() == Material.AIR) {
                        if(!closedBlocksList.contains(deltaLoc)) {
                            openBlocksList.add(deltaLoc);
                            closedBlocksList.add(deltaLoc);
                        }
                    } else {
//                        if(!blocks.contains(deltaBlock)) {
                        blocks.add(deltaBlock);
//                        }
                    }
                }
            }

//            Bukkit.broadcastMessage("" + closedBlocksList.size() + " RAN " + openCount + " TIMES");
        }

        //TODO: TEMP JUST DO TARGET BLOCK
        blocks.clear();
        blocks.add(lineOfSight.get(lineOfSight.size() - 1));

        Material pick = (new Material[] {Material.GREEN_STAINED_GLASS, Material.BLACK_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.WHITE_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.PURPLE_STAINED_GLASS})[(int) (Math.random() * 6)];

        //run one last loop to make sure the blocks found are not behind other blocks in player's view
        List<Block> finalBlocks = new ArrayList<>();
        Location startLoc = p.getEyeLocation();
//        Location startLoc = p.getLocation().getBlock().getLocation().add(0.5, 0.5, 0.5);
//        Location startLoc = p.getEyeLocation();
        for(Block block : blocks) {

//            Bukkit.broadcastMessage(dx + " " + dy + " " + dz);

            RayTraceResult result = p.getWorld().rayTraceBlocks(startLoc, block.getLocation().add(.5, .5, .5).toVector().subtract(startLoc.toVector()), maxDistance, FluidCollisionMode.SOURCE_ONLY, true);

            if(result != null && result.getHitBlock() != null && result.getHitBlock().equals(block)) {
                finalBlocks.add(block);

                if(lineOfSight.get(lineOfSight.size() - 1).equals(block)) {
                    block.setType(Material.YELLOW_STAINED_GLASS);
                } else {
                    block.setType(pick);
                }
            } else {
                if(result.getHitBlock() != null) {
                    Bukkit.broadcastMessage("WRONG: " + result.getHitBlock().getLocation().toVector().subtract(block.getLocation().toVector()));
                }
                //try again with alternative location
//                Location secondStartLoc = p.getLocation().getBlock().getLocation().add(0.5, 0.5, 0.5);
//
//                RayTraceResult secondResult = p.getWorld().rayTraceBlocks(secondStartLoc, block.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(startLoc.toVector()), maxDistance, FluidCollisionMode.SOURCE_ONLY, true);
//
//                if(secondResult != null && secondResult.getHitBlock() != null && secondResult.getHitBlock().equals(block)) {
//                    finalBlocks.add(block);
//
//                    if (lineOfSight.get(lineOfSight.size() - 1).equals(block)) {
//                        block.setType(Material.YELLOW_STAINED_GLASS);
//                    } else {
//                        block.setType(pick);
//                    }
//                }
            }
        }

        return finalBlocks;
    }
}
