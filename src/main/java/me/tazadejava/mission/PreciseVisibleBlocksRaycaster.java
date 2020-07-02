package me.tazadejava.mission;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

//revised algorithm that does a BFS on AIR blocks
//much more efficient than original "VisibleBlocksRaycaster" algorithm
/*
Key features:
- has a checker to determine if block is in FOV bounds
- will BFS through AIR blocks to determine visibility
- will ray cast once to closest face to determine if face is visible to player
- will stop BFS if air is "abandoned" more than once, ie it has no solid neighbors twice
- allows for fuzzy raycast hits by checking adjacent blocks in a 3x3 2D range for inaccuracies
- algorithm can be run async, but modifying blocks must be sync
 */
public class PreciseVisibleBlocksRaycaster {

    class FOVBounds {

        private final float FOV_HORIZONTAL_ANGLE_HALF = (float) Math.toRadians(45);//35 //increased angle for better accuracy
        private final float FOV_VERTICAL_ANGLE_HALF = (float) Math.toRadians(53);//43 //increased angle for better accuracy

        private Vector playerLocation, playerDirection;
        private double angle;

        public FOVBounds(Player p) {
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

    private BlockFace[] adjacentFaces;

    //high performance, low distance range
//    private static final int MAX_DISTANCE = 16;
//    private static final int MAX_TIMEOUT = 1000;

    //medium performance, median distance range
//    private static final int MAX_DISTANCE = 24;
//    private static final int MAX_TIMEOUT = 1500;

    //worse performance, best distance range
    private static final int MAX_DISTANCE = 32;
    private static final int MAX_TIMEOUT = 3000;

    private boolean doHyperPrecision;

    //if hyperPrecision is enabled, up to 5 raycasts will be sent out instead of one to determine if a block is visible to the player; may be more computationally heavy, but will improve accuracy
    public PreciseVisibleBlocksRaycaster(boolean doHyperPrecision) {
        this(doHyperPrecision, true);
    }

    public PreciseVisibleBlocksRaycaster(boolean doHyperPrecision, boolean considerUpBlocks) {
        this.doHyperPrecision = doHyperPrecision;

        if(considerUpBlocks) {
            adjacentFaces = new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.UP, BlockFace.DOWN};
        } else {
            adjacentFaces = new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.DOWN};
        }
    }

    public Block[] getVisibleBlocks(Player p) {
        return getVisibleBlocks(p, null);
    }

    public Block[] getVisibleBlocks(Player p, Set<Location> ignoredBlocks) {
        Set<Block> visibleBlocks = new HashSet<>();

        FOVBounds bounds = new FOVBounds(p);

        List<Block> lineOfSight = p.getLineOfSight(null, MAX_DISTANCE);

        if(!lineOfSight.isEmpty()) {
            Block lastLineOfSight = lineOfSight.get(lineOfSight.size() - 1);

            if(lastLineOfSight.getType() == Material.AIR) {
                return visibleBlocks.toArray(new Block[0]);
            }

            HashMap<Block, Block> visitedSolidBlocks = new HashMap<>(); //format: solid block, air block that defines it
            LinkedList<Block> unvisitedAirBlocks = new LinkedList<>();
            Set<Block> abandonedAirBlocks = new HashSet<>(); //represents air blocks that don't have any adjacent solid blocks; they can try to make connections with solid ground but they cannot expand more air

            //TODO: this saves computational power but it also makes the raycaster a bit less accurate. maybe figure out a way to fix that
//            if(ignoredBlocks != null) {
//                for(Location visitedLoc : ignoredBlocks) {
//                    visitedSolidBlocks.put(visitedLoc.getBlock(), null);
//                }
//            }

            if(lineOfSight.size() > 1) {
                Block airLineOfSight = lineOfSight.get(lineOfSight.size() - 2);
                visitedSolidBlocks.put(lastLineOfSight, airLineOfSight);
                unvisitedAirBlocks.add(airLineOfSight);
            }

            visibleBlocks.add(lineOfSight.get(lineOfSight.size() - 1));

            int timeout = MAX_TIMEOUT;
            while (!unvisitedAirBlocks.isEmpty()) {
                timeout--;
//                if(timeout % 100 == 0) {
//                    Bukkit.broadcastMessage("AIR BLOCKS: " + unvisitedAirBlocks.size());
//                }
                if(timeout <= 0) {
                    Bukkit.broadcastMessage("TIMEOUT!");
                    break;
                }

                Block airBlock = unvisitedAirBlocks.poll();

                boolean isAbandonedAir = abandonedAirBlocks.contains(airBlock);

                for (BlockFace face : adjacentFaces) {
                    Block adjacentBlock = airBlock.getRelative(face);

                    if (!bounds.isInBounds(adjacentBlock.getLocation().add(0.5, 0.5, 0.5))) {
                        continue;
                    }

                    if (adjacentBlock.getType() == Material.AIR) {
                        //if air, then make sure there is an adjacent block that doesn't already exist in visitedSolidBlocks. then, add to unvisited.
                        boolean foundAdjacentSolidBlock = false;
                        for (BlockFace adjacentFace : adjacentFaces) {
                            Block adjacentAdjacentBlock = adjacentBlock.getRelative(adjacentFace);

                            if (adjacentAdjacentBlock.getType() != Material.AIR && !visitedSolidBlocks.containsKey(adjacentAdjacentBlock)) {
                                visitedSolidBlocks.put(adjacentAdjacentBlock, adjacentBlock);

                                boolean raycast = doHyperPrecision ? raycastHitsBlockFuzzyHyperPrecise(p, adjacentFace, adjacentAdjacentBlock) : raycastHitsBlockFuzzy(p, adjacentFace, adjacentAdjacentBlock);
                                if (raycast) {
                                    visibleBlocks.add(adjacentAdjacentBlock);
                                }

                                unvisitedAirBlocks.add(adjacentBlock);
                                foundAdjacentSolidBlock = true;
                                break;
                            }
                        }

                        if(!foundAdjacentSolidBlock && !isAbandonedAir) {
                            unvisitedAirBlocks.add(adjacentBlock);
                            abandonedAirBlocks.add(adjacentBlock);
                        }
                    } else {
                        //if not air, then raycast to check visibility. add to visitedSolidBlocks
                        //not raycasting twice sometimes doesn't let it get detected, which is probably because of the air block that is touching it
//                        if(visitedSolidBlocks.containsKey(adjacentBlock)) {
//                            continue;
//                        }

                        //TODO: raycast to check
                        visitedSolidBlocks.put(adjacentBlock, airBlock);

                        boolean raycast = doHyperPrecision ? raycastHitsBlockFuzzyHyperPrecise(p, face, adjacentBlock) : raycastHitsBlockFuzzy(p, face, adjacentBlock);
                        if (raycast) {
                            visibleBlocks.add(adjacentBlock);
                        }
                    }
                }
            }

//            Bukkit.broadcastMessage("Timeout: " + timeout + "/" + MAX_TIMEOUT);
        }

        return visibleBlocks.toArray(new Block[0]);
    }

    private boolean raycastHitsBlockFuzzyHyperPrecise(Player p, BlockFace direction, Block targetBlock) {
        boolean raycastResult = raycastHitsBlockFuzzy(p, direction, targetBlock);
        if(raycastResult) {
            return true;
        }

        //if the fuzzyHit did not reach a result, test instead up to 4 additional raycasts

        double[] additionalRaycastOffsets = null;
        switch(direction) {
            case NORTH:
                additionalRaycastOffsets = new double[] {
                    0.05, 0.95, 0.95, //top left corner
                    0.95, 0.95, 0.95, //top right corner
                    0.05, 0.05, 0.95, //bottom left corner
                    0.95, 0.05, 0.95 //bottom right corner
                };
                break;
            case SOUTH:
                additionalRaycastOffsets = new double[] {
                        0.05, 0.95, 0.05, //top left corner
                        0.95, 0.95, 0.05, //top right corner
                        0.05, 0.05, 0.05, //bottom left corner
                        0.95, 0.05, 0.05 //bottom right corner
                };
                break;
            case WEST:
                additionalRaycastOffsets = new double[] {
                        0.95, 0.95, 0.05, //top left corner
                        0.95, 0.95, 0.95, //top right corner
                        0.95, 0.05, 0.05, //bottom left corner
                        0.95, 0.05, 0.95 //bottom right corner
                };
                break;
            case EAST:
                additionalRaycastOffsets = new double[] {
                        0.05, 0.95, 0.05, //top left corner
                        0.05, 0.95, 0.95, //top right corner
                        0.05, 0.05, 0.05, //bottom left corner
                        0.05, 0.05, 0.95 //bottom right corner
                };
                break;
            case DOWN:
                additionalRaycastOffsets = new double[] {
                        0.05, 0.95, 0.05, //closest corner
                        0.05, 0.95, 0.95, //corner on z
                        0.95, 0.95, 0.95, //corner on z and x
                        0.95, 0.95, 0.05 //corner on x
                };
                break;
            case UP:
                additionalRaycastOffsets = new double[] {
                        0.05, 0.05, 0.05, //closest corner
                        0.05, 0.05, 0.95, //corner on z
                        0.95, 0.05, 0.95, //corner on z and x
                        0.95, 0.05, 0.05 //corner on x
                };
                break;
        }

        for(int i = 0; i < 4; i++) {
            Location targetBlockFace = targetBlock.getLocation().add(additionalRaycastOffsets[i * 3], additionalRaycastOffsets[i * 3 + 1], additionalRaycastOffsets[i * 3 + 2]);

            RayTraceResult rayTrace = p.getWorld().rayTraceBlocks(p.getEyeLocation(), targetBlockFace.toVector().subtract(p.getEyeLocation().toVector()), MAX_DISTANCE);

            if(rayTrace == null) {
                continue;
            }

            if(rayTrace.getHitBlock().equals(targetBlock)) {
                return true;
            }

            if(checkFuzzyForTargetBlock(rayTrace.getHitBlock(), targetBlock, direction)) {
                return true;
            }
        }

        return false;
    }

    private boolean raycastHitsBlockFuzzy(Player p, BlockFace direction, Block targetBlock) {
        Location targetBlockFace = targetBlock.getLocation();

        switch(direction) {
            case NORTH:
                targetBlockFace.add(0.5, 0.5, 1);
                break;
            case SOUTH:
                targetBlockFace.add(0.5, 0.5, 0);
                break;
            case WEST:
                targetBlockFace.add(1, 0.5, 0.5);
                break;
            case EAST:
                targetBlockFace.add(0, 0.5, 0.5);
                break;
            case DOWN:
                targetBlockFace.add(0.5, 1, 0.5);
                break;
            case UP:
                targetBlockFace.add(0.5, 0, 0.5);
                break;
        }


        RayTraceResult rayTrace = p.getWorld().rayTraceBlocks(p.getEyeLocation(), targetBlockFace.toVector().subtract(p.getEyeLocation().toVector()), MAX_DISTANCE);
        
        if(rayTrace == null) {
            return false;
        }
        if(rayTrace.getHitBlockFace().getOppositeFace() != direction) {
            return false;
        }
        
        if(rayTrace.getHitBlock().equals(targetBlock)) {
            return true;
        }

        return checkFuzzyForTargetBlock(rayTrace.getHitBlock(), targetBlock, direction);
    }

    private boolean checkFuzzyForTargetBlock(Block raytraceBlock, Block targetBlock, BlockFace direction) {
        switch(direction) {
            case NORTH:
            case SOUTH:
                for(int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if(dx == 0 && dy == 0) {
                            continue;
                        }

                        Block adjacentBlock = raytraceBlock.getLocation().add(dx, dy, 0).getBlock();

                        if(adjacentBlock.equals(targetBlock)) {
                            return true;
                        }
                    }
                }
                return false;
            case UP:
            case DOWN:
                for(int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if(dx == 0 && dz == 0) {
                            continue;
                        }

                        Block adjacentBlock = raytraceBlock.getLocation().add(dx, 0, dz).getBlock();

                        if(adjacentBlock.equals(targetBlock)) {
                            return true;
                        }
                    }
                }
                return false;
            case EAST:
            case WEST:
                for(int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if(dy == 0 && dz == 0) {
                            continue;
                        }

                        Block adjacentBlock = raytraceBlock.getLocation().add(0, dy, dz).getBlock();

                        if(adjacentBlock.equals(targetBlock)) {
                            return true;
                        }
                    }
                }
                return false;
        }

        return false;
    }
}
