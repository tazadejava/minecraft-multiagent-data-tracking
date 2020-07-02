package me.tazadejava.mission;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import java.util.*;

//get the blocks that the player can see
//inspired by algorithm that Essie wrote in python
//OUTDATED, since it uses a rough draft algorithm that is quite computationally intensive and also does not capture all blocks
@Deprecated
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

    //current status of this method:
    //IT works... kinda. it will capture MOST of the blocks that are visible to the player, and it will not
    //capture the blocks hidden from the player. however, sometimes it does not capture ALL the blocks visible
    //to the player, and further debugging is necessary to make this fixed. SEE:
    //CHANGEBLOCKS = true
    //THEN DETERMINE HOW BLOCKS HIDDEN IN THE RAY SHOULD BE HANDLED
    //possibly the initial block algo should be revised. perhaps the final blocks algo should be. who knows?
    public List<Block> getVisibleBlocks(Player p) {
        boolean CHANGEBLOCKS = false;
        boolean DEBUG = false;

        List<Block> blocks = new ArrayList<>();

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

            while (!openBlocksList.isEmpty()) {
                Location loc = openBlocksList.poll();

                for (int i = 0; i < 6; i++) {
                    Location deltaLoc = loc.clone().add(deltaDirs[i * 3], deltaDirs[i * 3 + 1], deltaDirs[i * 3 + 2]);
                    Block deltaBlock = deltaLoc.getBlock();

                    if(!bounds.isInBounds(deltaLoc)) {
                        if(CHANGEBLOCKS && deltaBlock.getType() != Material.AIR) {
                            deltaBlock.setType(Material.RED_WOOL);
                        }
                        continue;
                    }
                    if(deltaLoc.distanceSquared(startLocation) > maxDistanceSquared) {
                        if(CHANGEBLOCKS && deltaBlock.getType() != Material.AIR) {
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
                        if(!blocks.contains(deltaBlock)) {
                            blocks.add(deltaBlock);
                        }
                    }
                }
            }

//            Bukkit.broadcastMessage("" + closedBlocksList.size() + " RAN " + openCount + " TIMES");
        }

        Material pick = (new Material[] {Material.GREEN_STAINED_GLASS, Material.BLACK_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.WHITE_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.PURPLE_STAINED_GLASS})[(int) (Math.random() * 6)];

        //TODO: TEMP ONLY TARGET BLOCK
        if(DEBUG) {
            blocks.clear();
            blocks.add(lineOfSight.get(lineOfSight.size() - 1));
        }

        //run one last loop to make sure the blocks found are not behind other blocks in player's view

        List<Block> finalBlocks = new ArrayList<>();
        Location startLoc = p.getEyeLocation().getBlock().getLocation().add(0.5,0.5,0.5);

        HashMap<Location, Double> distances = new HashMap<>();

        for(Block b : blocks) {
            distances.put(b.getLocation(), b.getLocation().distanceSquared(startLoc));
        }

        //sort the blocks first to identify blocks behind visible ones
        Collections.sort(blocks, new Comparator<Block>() {
            @Override
            public int compare(Block o1, Block o2) {
                return Double.compare(distances.get(o1.getLocation()), distances.get(o2.getLocation()));
            }
        });

        Set<Location> hiddenBlocks = new HashSet<>();
        boolean foundStone = false;
        for(Block block : blocks) {
            if(hiddenBlocks.contains(block.getLocation())) {
                continue;
            }

            Material originalMat = block.getType();

            BlockIterator inBetweenBlocks = new BlockIterator(p.getWorld(), startLoc.toVector(), block.getLocation().add(0.5,0.5,0.5).toVector().subtract(startLoc.toVector()), 0, maxDistance);

            boolean foundBlock = false;
            List<String> mats = new ArrayList<>();
            int matindex = 0;
            Block nextInBetweenBlock = null;
            Block lastBlock;
            while(inBetweenBlocks.hasNext()) {
                lastBlock = nextInBetweenBlock;
                nextInBetweenBlock = inBetweenBlocks.next();

                if(nextInBetweenBlock.equals(block)) {
                    mats.add(ChatColor.RED + nextInBetweenBlock.getType().toString() + ChatColor.WHITE);
                } else {
                    mats.add(nextInBetweenBlock.getType().toString());
                }
                matindex++;

                if(transparentBlocks.contains(nextInBetweenBlock.getType())) {
                    mats.set(matindex - 1, ChatColor.BLUE + nextInBetweenBlock.getType().toString() + ChatColor.WHITE);
                    continue;
                }
//                if(hiddenBlocks.contains(nextInBetweenBlock.getLocation())) {
//                    mats.set(matindex - 1, ChatColor.LIGHT_PURPLE + nextInBetweenBlock.getType().toString() + ChatColor.WHITE);
//                    continue;
//                }

                if(!foundBlock && block.equals(nextInBetweenBlock)) {
                    foundBlock = true;
                    finalBlocks.add(block);

                    mats.set(matindex - 1, ChatColor.GOLD + nextInBetweenBlock.getType().toString() + ChatColor.WHITE);
                    if(lineOfSight.get(lineOfSight.size() - 1).equals(block)) {
                        if(CHANGEBLOCKS) block.setType(Material.YELLOW_STAINED_GLASS);
                    } else {
                        if(CHANGEBLOCKS) block.setType(pick);
                    }
                } else {
                    //if the target block has not yet been found, and there is a block in the way
                    //if this block has a perpendicular air block, we can pretend it is air as well
                    //to determine perpendicular: check last block, and make sure the distance
                    //from that block to this relative block > 1
                    boolean hasAdjacentCloseAirBlock = false;
                    double dist = nextInBetweenBlock.getLocation().distanceSquared(startLoc);
                    for (int i = 0; i < 6; i++) {
                        Block relativeBlock = nextInBetweenBlock.getLocation().add(deltaDirs[i * 3], deltaDirs[i * 3 + 1], deltaDirs[i * 3 + 2]).getBlock();

                        if (transparentBlocks.contains(relativeBlock.getType())) {
                            double relativeDist = relativeBlock.getLocation().distanceSquared(startLoc);
                            double lastRelativeDist = relativeBlock.getLocation().distanceSquared(lastBlock.getLocation());

//                                if (dist - relativeDist >= 0 || (int) Math.abs(dist - relativeDist) <= 1) {
//                                if (dist - relativeDist >= 0 && lastRelativeDist >= 1) {
                            if (dist - relativeDist >= 0 && !lastBlock.equals(relativeBlock)) {
                                mats.set(matindex - 1, "" + ChatColor.GREEN + relativeBlock
                                        .getType() + "(" + mats.get(matindex - 1) + ")" + ChatColor.WHITE);
                                hasAdjacentCloseAirBlock = true;
                                break;
                            }
                        }
                    }

                    if(!hasAdjacentCloseAirBlock) {
                        if(foundBlock) {
                            mats.set(matindex - 1, ChatColor.RED + nextInBetweenBlock.getType().toString() + ChatColor.WHITE);
                            hiddenBlocks.add(nextInBetweenBlock.getLocation());
                        } else {
                            mats.set(matindex - 1, ChatColor.DARK_RED + nextInBetweenBlock.getType().toString() + ChatColor.WHITE);
                            hiddenBlocks.add(block.getLocation());
                            hiddenBlocks.add(nextInBetweenBlock.getLocation());
                            foundBlock = true;
                        }
                    }

//                    if(foundBlock) {
//                        mats.set(matindex - 1, ChatColor.RED + nextInBetweenBlock.getType().toString() + ChatColor.WHITE);
//                        hiddenBlocks.add(nextInBetweenBlock.getLocation());
//                    } else if(lastBlock != null) {
//                        //if the target block has not yet been found, and there is a block in the way
//                        //if this block has a perpendicular air block, we can pretend it is air as well
//                        //to determine perpendicular: check last block, and make sure the distance
//                        //from that block to this relative block > 1
//                        boolean hasAdjacentCloseAirBlock = false;
//                        double dist = nextInBetweenBlock.getLocation().distanceSquared(startLoc);
//                        for (int i = 0; i < 6; i++) {
//                            Block relativeBlock = nextInBetweenBlock.getLocation().add(deltaDirs[i * 3], deltaDirs[i * 3 + 1], deltaDirs[i * 3 + 2]).getBlock();
//
//                            if (transparentBlocks.contains(relativeBlock.getType())) {
//                                double relativeDist = relativeBlock.getLocation().distanceSquared(startLoc);
//                                double lastRelativeDist = relativeBlock.getLocation().distanceSquared(lastBlock.getLocation());
//
////                                if (dist - relativeDist >= 0 || (int) Math.abs(dist - relativeDist) <= 1) {
////                                if (dist - relativeDist >= 0 && lastRelativeDist >= 1) {
//                                if (dist - relativeDist >= 0 && !lastBlock.equals(relativeBlock)) {
//                                    mats.set(matindex - 1, "" + ChatColor.GREEN + relativeBlock
//                                            .getType() + "(" + mats.get(matindex - 1) + ")" + ChatColor.WHITE);
//                                    hasAdjacentCloseAirBlock = true;
//                                    break;
//                                }
//                            }
//                        }
//
//                        if(!hasAdjacentCloseAirBlock) {
//                            mats.set(matindex - 1, ChatColor.DARK_RED + nextInBetweenBlock.getType().toString() + ChatColor.WHITE);
//                            hiddenBlocks.add(block.getLocation());
//                            hiddenBlocks.add(nextInBetweenBlock.getLocation());
//                            foundBlock = true;
//                        }
//                    }
                }
            }

            if(DEBUG || originalMat == Material.STONE) {
                if(!foundStone) {
                    foundStone = true;
                    Bukkit.broadcastMessage("START STONES:");
                }

                if(mats.contains(ChatColor.GOLD + "STONE" + ChatColor.WHITE)) {
                    Bukkit.broadcastMessage(mats.toString());
                }
            }
        }

        return finalBlocks;
    }
}
