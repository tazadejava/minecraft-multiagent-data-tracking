package me.tazadejava.analyzer;

import com.google.gson.JsonObject;
import me.tazadejava.blockranges.BlockRange2D;
import me.tazadejava.map.DynamicMapRenderer;
import me.tazadejava.mission.Mission;
import me.tazadejava.mission.MissionGraph;
import me.tazadejava.mission.MissionManager;
import me.tazadejava.mission.MissionRoom;
import me.tazadejava.statstracker.EnhancedStatsTracker;
import me.tazadejava.statstracker.PreciseVisibleBlocksRaycaster;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

/*
    DEVELOPER'S NOTE:

    for the most part, this class does not have to exist within the plugin, aside from a couple areas where the player is accessed instead of the stats.
    particularly: if for whatever reason someone wants this processing to exist OUTSIDE of the plugin itself, it can be done with a few changes, namely whenever the Player class or any other proprietary classes are used that are NOT
        a part of the LastStatsSnapshot class.
    as a result, the classes are coded in a strange way; instead of hooking directly into Minecraft events, this class relies a lot on the LastStatsSnapshot to obtain a snapshot of previous actions done, and uses this thereafter to
        create an understanding of the mission world and give best path recommendations.

    if this is actually something that wants to be done, I would start by funneling needed material into the LastStatsSnapshot class and redirecting all calls to this class as a start. then, this class will be able to separate from
        the project and run as a separate process, which in turn can be interacted with through sockets and networks, for example.
 */

/**
 * Handles player-specific actions and recommendations: particularly, the best path recommendations, speed analysis, and edge creation/deletion algorithms are run here.
 */
public class PlayerAnalyzer {

    /**
     * Class that assists with determining player speed between decision points (aka in the hallway).
     */
    public class DecisionTraversal {

        public MissionGraph.MissionVertex beginVertex, endVertex;
        public double pathLength;
        public long pathTime;

        public DecisionTraversal(MissionGraph graph, MissionGraph.MissionVertex beginVertex, MissionGraph.MissionVertex endVertex, long pathTime) {
            this.beginVertex = beginVertex;
            this.endVertex = endVertex;
            this.pathTime = pathTime;

            pathLength = graph.getShortestPathUsingEdges(beginVertex.type, beginVertex.name, endVertex.type, endVertex.name).getPathLength();
        }
    }

    /**
     * Used to assist with telling the player where to go relative to their current facing direction (ex: go left).
     */
    public enum Direction {
        NORTH, EAST, SOUTH, WEST
    }

    //list of blocks that are classified as victims
    public static final Set<Material> VICTIM_BLOCKS = new HashSet<>(Arrays.asList(Material.PRISMARINE, Material.GOLD_BLOCK));

    private static final BlockFace[] ADJACENT_FACES = new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.UP, BlockFace.DOWN};


    private Player player;
    private Mission mission;
    private MissionManager missionManager;

    //TODO: these are questions that will be asked to the player before any experiment starts, obtained via data beforehand
    //TODO: this can be used with Genesis, for example, to determine if specific players need recommendations at any given time
    //todo: recommendation: in true java spirit, the player's background data can be compartialized in a different class to hold their data, and accessible here via object
    private boolean isSpatiallyAware = true;

    //the indices will represent the victim number (arbitrary counter for victims seen, currently holds no significance)
    private List<Location> victimBlocks;
    private Block currentVictimTarget;

    private String currentRoom;

    private EnhancedStatsTracker.LastStatsSnapshot lastLastStats;

    private List<String> firstLevelActions;

    private MissionGraph.MissionVertex lastVertex;

    private Set<MissionGraph.MissionVertex> visitedVertices;
    private Set<MissionGraph.MissionVertex> unvisitedRooms;

    private boolean shouldUpdatePlayerGraph = false;

    //roomSpeed: average MS per block in the room
    //decisionSpeed: average blocks per MS
    private double averagePlayerDecisionTraversalSpeed, averagePlayerRoomTriageSpeed;
    private List<DecisionTraversal> playerDecisionSpeeds = new ArrayList<>();
    private HashMap<MissionGraph.MissionVertex, Long> playerRoomTriageSpeeds = new HashMap<>();
    private long lastRoomEnterTime = -1, lastDecisionEnterTime = -1;

    private HashMap<Direction, HashMap<Direction, String>> relativeHumanDirections = new HashMap<>();

    private PreciseVisibleBlocksRaycaster visibleBlocksRaycaster;
    private long lastRaycastTime;

    private static final boolean PRINT_RECOMMENDATIONS = true;
    private TextComponent actionBarMessage = null;
    private String lastRecommendationMessage;
    private List<String> bestPathFormat;
    private List<MissionGraph.MissionVertex> lastBestPath;

    //if set to true, then prints more verbose recommendation information when the player starts the mission
    private static final boolean DEBUG_VERBOSE_RECOMMENDATIONS = false;

    public PlayerAnalyzer(JavaPlugin plugin, Player player, Mission mission, MissionManager missionManager) {
        this.player = player;
        this.mission = mission;
        this.missionManager = missionManager;

        victimBlocks = new ArrayList<>();
        firstLevelActions = new ArrayList<>();

        visitedVertices = new HashSet<>();
        unvisitedRooms = new HashSet<>();

        unvisitedRooms.addAll(mission.getMissionGraph().getRoomVertices());

        currentRoom = null;

        calculateRelativeHumanDirections();

        visibleBlocksRaycaster = new PreciseVisibleBlocksRaycaster(true, true, false, mission.getPlayerSpawnLocation().getBlockY(), mission.getPlayerSpawnLocation().getBlockY() + 2);

        //give the player a map

        if(PRINT_RECOMMENDATIONS) {
            if (mission.getPlayerSpawnLocation().getWorld().getName().equals("falcon")) {
                player.getInventory().setItemInOffHand(DynamicMapRenderer.getMap(plugin, missionManager, player, false, true, DynamicMapRenderer.CustomMap.FALCON));

                //give the player a map that prints decision and room points
                if (DEBUG_VERBOSE_RECOMMENDATIONS) {
                    player.getInventory().setItemInMainHand(DynamicMapRenderer.getMap(plugin, missionManager, player, true, false, DynamicMapRenderer.CustomMap.FALCON));
                }
            } else {
                player.getInventory().setItemInOffHand(DynamicMapRenderer.getMap(plugin, missionManager, player, false, true, DynamicMapRenderer.CustomMap.SPARKY));

                //give the player a map that prints decision and room points
                if (DEBUG_VERBOSE_RECOMMENDATIONS) {
                    player.getInventory().setItemInMainHand(DynamicMapRenderer.getMap(plugin, missionManager, player, true, false, DynamicMapRenderer.CustomMap.SPARKY));
                }
            }
        }
    }

    /**
     * Maps relative directions into human-readable English.
     */
    private void calculateRelativeHumanDirections() {
        relativeHumanDirections.put(Direction.NORTH, new HashMap<>());
        relativeHumanDirections.put(Direction.EAST, new HashMap<>());
        relativeHumanDirections.put(Direction.WEST, new HashMap<>());
        relativeHumanDirections.put(Direction.SOUTH, new HashMap<>());

        String front = "in front of you";
        String left = "on your left";
        String right = "on your right";
        String behind = "behind you";

        relativeHumanDirections.get(Direction.NORTH).put(Direction.NORTH, front);
        relativeHumanDirections.get(Direction.NORTH).put(Direction.EAST, right);
        relativeHumanDirections.get(Direction.NORTH).put(Direction.WEST, left);
        relativeHumanDirections.get(Direction.NORTH).put(Direction.SOUTH, behind);

        relativeHumanDirections.get(Direction.SOUTH).put(Direction.NORTH, behind);
        relativeHumanDirections.get(Direction.SOUTH).put(Direction.EAST, left);
        relativeHumanDirections.get(Direction.SOUTH).put(Direction.WEST, right);
        relativeHumanDirections.get(Direction.SOUTH).put(Direction.SOUTH, front);

        relativeHumanDirections.get(Direction.EAST).put(Direction.NORTH, left);
        relativeHumanDirections.get(Direction.EAST).put(Direction.EAST, front);
        relativeHumanDirections.get(Direction.EAST).put(Direction.WEST, behind);
        relativeHumanDirections.get(Direction.EAST).put(Direction.SOUTH, right);

        relativeHumanDirections.get(Direction.WEST).put(Direction.NORTH, right);
        relativeHumanDirections.get(Direction.WEST).put(Direction.EAST, behind);
        relativeHumanDirections.get(Direction.WEST).put(Direction.WEST, front);
        relativeHumanDirections.get(Direction.WEST).put(Direction.SOUTH, left);
    }

    private void log(String action) {
        Bukkit.getLogger().info(action);
        firstLevelActions.add(action);

        //TODO: pass data into genesis, so that genesis can understand data
    }

    /**
     * Perform an update that involves methods necessary to run on the main thread.
     */
    public void updateSync() {
        //possibly recalculate edges
        //for methods that need raycasting; only run every 200 milliseconds
        if(System.currentTimeMillis() - lastRaycastTime >= 200) {
            lastRaycastTime = System.currentTimeMillis();

            Set<Block> visibleBlocks = visibleBlocksRaycaster.getVisibleBlocks(player);

            analyzeMissionGraphEdges(visibleBlocks);
            analyzeVisibleVictims(visibleBlocks);
        }

        if(PRINT_RECOMMENDATIONS) {
            if (actionBarMessage != null) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, actionBarMessage);
            }

            if (DEBUG_VERBOSE_RECOMMENDATIONS) {
                if (bestPathFormat != null) {
                    Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

                    Objective objective = scoreboard.registerNewObjective("path", "dummy", "" + ChatColor.GREEN + ChatColor.BOLD + "Best Path:");

                    objective.setDisplaySlot(DisplaySlot.SIDEBAR);

                    for (int i = 0; i < (bestPathFormat.size() < 16 ? bestPathFormat.size() : 16); i++) {
                        objective.getScore(bestPathFormat.get(i)).setScore(-(i + 1));
                    }

                    player.setScoreboard(scoreboard);
                }
            } else {
                if (lastRecommendationMessage != null) {
                    //update direction if player looked a different way
                    analyzeNextBestMove(lastBestPath, null);

                    Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

                    Objective objective = scoreboard.registerNewObjective("path", "dummy", "" + ChatColor.GREEN + ChatColor.BOLD + "Next Move:");

                    objective.setDisplaySlot(DisplaySlot.SIDEBAR);

                    if (lastRecommendationMessage.length() > 32) {
                        List<String> messages = new ArrayList<>();

                        String message = lastRecommendationMessage;
                        while (message.length() > 32) {
                            int bestIndex = 0;
                            int index;
                            String currentString = message;
                            while ((index = currentString.indexOf(" ")) != -1) {
                                if (bestIndex + index > 32) {
                                    break;
                                }

                                bestIndex += index + 1;
                                currentString = currentString.substring(index + 1);
                            }

                            messages.add(message.substring(0, bestIndex));
                            message = message.substring(bestIndex);
                        }

                        messages.add(message);

                        for (int i = 0; i < messages.size(); i++) {
                            objective.getScore(ChatColor.LIGHT_PURPLE + messages.get(i)).setScore(messages.size() - i - 1);
                        }
                    } else {
                        objective.getScore(lastRecommendationMessage).setScore(0);
                    }

                    player.setScoreboard(scoreboard);
                }
            }
        }
    }

    /**
     * Run methods that do not need to be on the main thread to run. Prefer method calls here if possible, since it will not lag the main thread as much.
     * @param lastStats
     */
    public void update(EnhancedStatsTracker.LastStatsSnapshot lastStats) {
        if(lastLastStats == null || lastLastStats.lastPlayerValues == null) {
            lastLastStats = lastStats;
            return;
        }

        EnhancedStatsTracker.LastStatsSnapshot deltaStats = lastStats.calculateDeltaSnapshot(lastLastStats);

        analyzeMissionGraph(lastStats);

        analyzeVictimTarget(lastStats);
        analyzeBrokenBlocks(lastStats, deltaStats);
        analyzeRoom(lastStats);
        analyzeClickedBlocks(deltaStats);

        lastLastStats = lastStats;
    }

    /**
     * If a victim is seen and they are not yet saved, then we will add it to a list of seen victims to keep track of where victims are. This list is shared by all players (stored in the MissionGraph representation), so any player can determine this.
     * @param visibleBlocks
     */
    private void analyzeVisibleVictims(Set<Block> visibleBlocks) {
        MissionGraph graph = mission.getMissionGraph();

        HashMap<MissionGraph.MissionVertex, Set<Block>> roomVerticesWithVictims = graph.getRoomVerticesWithVictims();
        HashMap<MissionGraph.MissionVertex, Set<Block>> roomVerticesSavedVictims = graph.getRoomVerticesSavedVictims();

        for(Block block : visibleBlocks) {
            if(VICTIM_BLOCKS.contains(block.getType())) {
                MissionRoom room = null;
                if(graph.getVictimRoom(block) != null) {
                    room = graph.getVictimRoom(block);
                } else {
                    for (MissionRoom possibleRoom : mission.getRooms()) {
                        if (possibleRoom.getBounds().isInRange(block.getLocation())) {
                            room = possibleRoom;
                            break;
                        }
                    }

                    if(room == null) {
                        continue;
                    } else {
                        graph.defineVictimRoom(block, room);
                    }
                }

                MissionGraph.MissionVertex roomVertex = graph.getRoomVertex(room);

                if(!roomVerticesSavedVictims.containsKey(roomVertex) || !roomVerticesSavedVictims.get(roomVertex).contains(block)) {
                    roomVerticesWithVictims.putIfAbsent(roomVertex, new HashSet<>());

                    if(!roomVerticesWithVictims.get(roomVertex).contains(block)) {
                        roomVerticesWithVictims.get(roomVertex).add(block);
                    }
                }
            }
        }
    }

    /**
     * This will run an algorithm that checks whether or not, from the blocks that the player can see, there exists a hole that connects two distinct rooms. If this is true, then we will create an edge in the graphical representation. Additionally, it will check whether or not edges should be removed if there is a blockage.
     * @param visibleBlocks
     */
    private void analyzeMissionGraphEdges(Set<Block> visibleBlocks) {
        HashMap<MissionRoom, Set<Block>> visibleBlocksByRoom = new HashMap<>();

        //take out the walls
        HashMap<BlockRange2D, MissionRoom> adjustedRoomBounds = new HashMap<>();

        //create an adjustment where one more than the walls are included; this allows for fuzzy bound collision between walls
        HashMap<MissionRoom, BlockRange2D> expandedRoomBounds = new HashMap<>();

        for(MissionRoom room : mission.getRooms()) {
            adjustedRoomBounds.put(room.getBounds().clone().expand(-1), room);

            expandedRoomBounds.put(room, room.getBounds().clone().expand(1));
        }

        for(Block visibleBlock : visibleBlocks) {
            for(BlockRange2D bounds : adjustedRoomBounds.keySet()) {
                if(bounds.isInRange(visibleBlock.getLocation())) {
                    MissionRoom room = adjustedRoomBounds.get(bounds);
                    visibleBlocksByRoom.putIfAbsent(room, new HashSet<>());
                    visibleBlocksByRoom.get(room).add(visibleBlock);
                }
            }
        }

        //check for new edges between rooms
        if(visibleBlocksByRoom.size() > 1) {
            HashMap<MissionRoom, Set<MissionRoom>> checkedRooms = new HashMap<>();

            for(MissionRoom originalRoom : visibleBlocksByRoom.keySet()) {
                checkedRooms.put(originalRoom, new HashSet<>());

                for(MissionRoom compareRoom : visibleBlocksByRoom.keySet()) {
                    //avoid checking edges both ways
                    if(checkedRooms.containsKey(compareRoom) && checkedRooms.get(compareRoom).contains(originalRoom)) {
                        continue;
                    }

                    checkedRooms.get(originalRoom).add(compareRoom);

                    if(originalRoom != compareRoom) {
                        if(mission.getMissionGraph().doesEdgeExist(MissionGraph.MissionVertexType.ROOM, originalRoom.getRoomName(), MissionGraph.MissionVertexType.ROOM, compareRoom.getRoomName())) {
                            continue;
                        }

                        BlockRange2D originalBounds = expandedRoomBounds.get(originalRoom);
                        BlockRange2D compareBounds = expandedRoomBounds.get(compareRoom);

                        if(originalBounds.collidesWith(compareBounds)) {
                            //then they possibly may have an edge

                            int minX = Integer.MAX_VALUE;
                            int minZ = Integer.MAX_VALUE;
                            int minY = Integer.MAX_VALUE;
                            int maxY = Integer.MIN_VALUE;
                            int maxX = Integer.MIN_VALUE;
                            int maxZ = Integer.MIN_VALUE;

                            Set<Block> boundaryBlock = new HashSet<>();
                            for(Block visibleBlock : visibleBlocks) {
                                if(originalBounds.isInRange(visibleBlock.getLocation())) {
                                    if (compareBounds.isInRange(visibleBlock.getLocation())) {
                                        boundaryBlock.add(visibleBlock);

                                        minX = Math.min(minX, visibleBlock.getX());
                                        maxX = Math.max(maxX, visibleBlock.getX());
                                        minY = Math.min(minY, visibleBlock.getY());
                                        maxY = Math.max(maxY, visibleBlock.getY());
                                        minZ = Math.min(minZ, visibleBlock.getZ());
                                        maxZ = Math.max(maxZ, visibleBlock.getZ());
                                    }
                                }
                            }

                            //todo: this algorithm works well within plugins itself, but outside of a plugin format it may not know blocks that have not been raytraced. possibly revise and include those blocks i.e. check if it is not in the raycasted and confirm that way instead?
                            boolean doesEdgeExist = false;

                            Set<Block> airBlocks = new HashSet<>();
                            Location holeLocation = null;

                            main:
                            for(int x = minX; x <= maxX; x++) {
                                for(int y = minY; y <= maxY; y++) {
                                    for (int z = minZ; z <= maxZ; z++) {
                                        Block block = new Location(player.getWorld(), x, y, z).getBlock();

                                        boolean hasAdjacentVisibleBlock = false;

                                        for(BlockFace dir : ADJACENT_FACES) {
                                            if(boundaryBlock.contains(block.getRelative(dir))) {
                                                hasAdjacentVisibleBlock = true;
                                                break;
                                            }
                                        }

                                        if(block.getType() == Material.AIR && hasAdjacentVisibleBlock) {
                                            airBlocks.add(block);

                                            //check if exists air blocks at least 2 high

                                            if(airBlocks.contains(block.getRelative(0, 1, 0)) || airBlocks.contains(block.getRelative(0, -1, 0))) {
                                                doesEdgeExist = true;
                                                holeLocation = block.getLocation();
                                                break main;
                                            }
                                        }
                                    }
                                }
                            }

                            if(doesEdgeExist) {
                                if(mission.getMissionGraph().isEdgeProtected(MissionGraph.MissionVertexType.ROOM, originalRoom.getRoomName(), MissionGraph.MissionVertexType.ROOM, compareRoom.getRoomName())) {
                                    //skip if already checked
                                    continue;
                                }

                                MissionGraph.LocationPath newPath = mission.getMissionGraph().defineEdge(MissionGraph.MissionVertexType.ROOM, originalRoom.getRoomName(), MissionGraph.MissionVertexType.ROOM, compareRoom.getRoomName());
                                mission.getMissionGraph().protectEdge(MissionGraph.MissionVertexType.ROOM, originalRoom.getRoomName(), MissionGraph.MissionVertexType.ROOM, compareRoom.getRoomName());

                                double manhattanDistance;
                                MissionGraph.MissionVertex originalVertex = null, compareVertex = null;
                                for(MissionGraph.MissionVertex vertex : mission.getMissionGraph().getRoomVertices()) {
                                    if(vertex.name.equals(originalRoom.getRoomName())) {
                                        originalVertex = vertex;
                                    }
                                    if(vertex.name.equals(compareRoom.getRoomName())) {
                                        compareVertex = vertex;
                                    }
                                }

                                manhattanDistance = originalVertex.location.distance(compareVertex.location);

                                //quality check; make sure the path is not simply the currently shortest path and we are actually finding a new shorter path
                                if(newPath.getPathLength() < manhattanDistance * 2d) {
                                    System.out.println("" + ChatColor.GOLD + ChatColor.BOLD + "FOUND EDGE BETWEEN ROOMS " + originalRoom.getRoomName() + " AND " + compareRoom.getRoomName() + " WITH LENGTH " + mission.getMissionGraph().getShortestPathUsingEdges(MissionGraph.MissionVertexType.ROOM, originalRoom.getRoomName(), MissionGraph.MissionVertexType.ROOM, compareRoom.getRoomName()).getPathLength());

                                    mission.getMissionGraph().getAddedEdgeLocationMarkers().add(holeLocation);

                                    //recalculate best path
                                    shouldUpdatePlayerGraph = true;
                                } else {
                                    System.out.println("" + ChatColor.GRAY + "Almost found an edge between " + originalRoom.getRoomName() + " AND " + compareRoom.getRoomName() + " WITH LENGTH " + mission.getMissionGraph().getShortestPathUsingEdges(MissionGraph.MissionVertexType.ROOM, originalRoom.getRoomName(), MissionGraph.MissionVertexType.ROOM, compareRoom.getRoomName()).getPathLength() + " " + (manhattanDistance * 2d));
                                    mission.getMissionGraph().deleteEdge(MissionGraph.MissionVertexType.ROOM, originalRoom.getRoomName(), MissionGraph.MissionVertexType.ROOM, compareRoom.getRoomName());
                                }
                            }
                        }
                    }
                }
            }
        }

        //check for disrupted edges due to blockages
        if(lastVertex != null) {
            //checks for the neighbors of last vertex, then also checks for the neighbors of the neighbors of the lastvertex (2 layers of checking)

            Set<MissionGraph.MissionVertex> toCheckNeighbors = new HashSet<>();

            toCheckNeighbors.add(lastVertex);

            toCheckNeighbors.addAll(mission.getMissionGraph().getNeighbors(lastVertex));

            for(MissionGraph.MissionVertex vertex : toCheckNeighbors) {
                for (MissionGraph.MissionVertex neighbor : mission.getMissionGraph().getNeighbors(vertex)) {
                    Location loc;
                    if ((loc = mission.getMissionGraph().verifyEdgeTraversable(vertex.type, vertex.name, neighbor.type, neighbor.name, visibleBlocks)) != null) {
                        System.out.println("" + ChatColor.RED + ChatColor.BOLD + "THE EDGE BETWEEN " + vertex + " AND " + neighbor + " IS NOT TRAVERSABLE!");
                        mission.getMissionGraph().deleteEdge(vertex.type, vertex.name, neighbor.type, neighbor.name);
                        mission.getMissionGraph().getRemovedEdgeLocationMarkers().add(loc);

                        shouldUpdatePlayerGraph = true;
                        break;
                    }
                }
            }
        }
    }

    @Deprecated
    /**
     * This method was originally created to weight rooms differently, but since it was difficult to find correlations in rooms, I discontinued creating it.
     */
    private HashMap<MissionGraph.MissionVertex, Double> calculateRoomPotentials(MissionGraph.MissionVertex playerVertex) {
        HashMap<MissionGraph.MissionVertex, Double> roomPotentials = new HashMap<>();

        double maxDistanceToPlayer = 0;
        for(MissionGraph.MissionVertex room : mission.getMissionGraph().getRoomVertices()) {
            MissionGraph.VertexPath path = mission.getMissionGraph().getShortestPathUsingEdges(room.type, room.name, playerVertex.type, playerVertex.name);

            //there is no currently known path to get to this room
            if(path == null) {
                continue;
            }

            maxDistanceToPlayer = Math.max(maxDistanceToPlayer, path.getPathLength());
        }

        //as a test: for now, rooms will be randomly assigned values
        for(MissionGraph.MissionVertex room : mission.getMissionGraph().getRoomVertices()) {
            if(visitedVertices.contains(room) || room.equals(playerVertex)) {
                roomPotentials.put(room, 0d); //later, make a weighted potential based on how much of the room is not seen, but it is much less than if it were not visited
            } else {
                double potential = 0;
                double remainingPotential = 1;

                int unvisitedNeighborCount = 0;
                for(MissionGraph.MissionVertex neighbor : mission.getMissionGraph().getNeighbors(room)) {
                    if(neighbor.type == MissionGraph.MissionVertexType.ROOM && !visitedVertices.contains(neighbor)) {
                        unvisitedNeighborCount++;
                    }
                }

                //based on if room has neighbors
                if(unvisitedNeighborCount > 0) {
                    if(unvisitedNeighborCount > 3) {
                        unvisitedNeighborCount = 3;
                    }
                    potential += .2 * unvisitedNeighborCount;
                    remainingPotential -= .2 * unvisitedNeighborCount;
                }

                //based on distance to player
                double distanceToPlayer = mission.getMissionGraph().getShortestPathUsingEdges(room.type, room.name, playerVertex.type, playerVertex.name).getPathLength();
                potential += (1d - (distanceToPlayer / maxDistanceToPlayer)) * remainingPotential;

                roomPotentials.put(room, potential);
            }
        }

        //display to player in pretty colors

        boolean DISPLAY = false;

        if(DISPLAY) {
            List<MissionGraph.MissionVertex> rooms = new ArrayList<>(mission.getMissionGraph().getRoomVertices());
            rooms.sort(new Comparator<MissionGraph.MissionVertex>() {
                @Override
                public int compare(MissionGraph.MissionVertex o1, MissionGraph.MissionVertex o2) {
                    return Double.compare(roomPotentials.get(o2), roomPotentials.get(o1));
                }
            });

            for (MissionGraph.MissionVertex room : rooms) {
                ChatColor color = ChatColor.WHITE;
                double potential = roomPotentials.get(room);

                if (potential > .8) {
                    color = ChatColor.GOLD;
                } else if (potential > .7) {
                    color = ChatColor.YELLOW;
                } else if (potential > .6) {
                    color = ChatColor.AQUA;
                } else if (potential > .4) {
                    color = ChatColor.WHITE;
                } else if (potential > .2) {
                    color = ChatColor.GRAY;
                } else {
                    color = ChatColor.DARK_GRAY;
                }

                Bukkit.broadcastMessage(color + "POTENTIAL " + room.name + ": " + potential);
            }
        }

        return roomPotentials;
    }

    private List<MissionGraph.MissionVertex> calculateBestPath(MissionGraph.MissionVertex startingVertex) {
        return calculateBestPath(startingVertex, true);
    }

    /**
     * Main method that calculates the player's best path from where they currently are using a series of calculations. A more verbose description of this implementation can be found on the Github project.
     * @param startingVertex Player's vertex, or the vertex that the best path begins.
     * @param onlyNonVisitedRooms Whether or not to consider rooms that have been visited. If true, only considers unvisited rooms. If false, only considers rooms that have been visited but still have victims inside. If false, will also not update the actionBarMessage.
     * @return
     */
    private List<MissionGraph.MissionVertex> calculateBestPath(MissionGraph.MissionVertex startingVertex, boolean onlyNonVisitedRooms) {
        MissionGraph graph = mission.getMissionGraph().cloneGraphOnly();

        //DEPRECATED: modify weights to accomodate for potential; this will allow for graph path to be weighted based on specific factors
//        for(MissionGraph.MissionVertex roomPotential : roomPotentials.keySet()) {
//            for(MissionGraph.MissionVertex neighbor : graph.getNeighbors(roomPotential)) {
//                graph.modifyEdgeWeight(roomPotential, neighbor, graph.getEdgeWeight(roomPotential, neighbor) + Math.pow((1 - roomPotentials.get(roomPotential)) * roomPotentialWeight, 2));
//            }
//        }

        //find shortest path using tree estimate

        //first, calculate the path from any room to another room using APSP weight algorithm AND player's node if it is not a room node
        //store room to decision nodes mapping

        //TODO: an optimization that can be made is to only run the APSP algorithm everytime an edge is removed/added, and otherwise reference a static copy of the APSP.

        //represents the rooms that will be considered in this best path
        List<MissionGraph.MissionVertex> nodeCandidates = new ArrayList<>();

        //first, we check if any rooms have not been visited
        if(onlyNonVisitedRooms) {
            for (MissionGraph.MissionVertex roomVertex : graph.getRoomVertices()) {
                if (!visitedVertices.contains(roomVertex)) {
                    nodeCandidates.add(roomVertex);
                }
            }
        } else {
            for(MissionGraph.MissionVertex roomVertex : graph.getRoomVertices()) {
                if(visitedVertices.contains(roomVertex) && graph.getRoomVerticesWithVictims().containsKey(roomVertex) && !graph.getRoomVerticesWithVictims().get(roomVertex).isEmpty()) {
                    nodeCandidates.add(roomVertex);
                }
            }
        }

        //if the player's current node is a decision node, then we will add it to be the first node.
        if(!nodeCandidates.contains(startingVertex)) {
            nodeCandidates.add(startingVertex);
        }

        //calculate path from one node to all other nodes (APSP)
        HashMap<MissionGraph.MissionVertex, HashMap<MissionGraph.MissionVertex, MissionGraph.VertexPath>> roomPaths = new HashMap<>();
        for(MissionGraph.MissionVertex beginNode : nodeCandidates) {
            roomPaths.put(beginNode, graph.getShortestPathToAllVertices(beginNode.type, beginNode.name));
        }

        //to account for LONG-TERM SUCCESS, we will "leaf" out the top x shortest paths, and analyze all the paths y times to look at the shortest long-term paths
        //then, we will iteratively find the next best room starting at the node that ends this path

        Set<MissionGraph.MissionVertex> visitedVertices = new HashSet<>();
        List<MissionGraph.MissionVertex> roomPath = new ArrayList<>();

        List<MissionGraph.MissionVertex> recursivePath = calculateBestLongTermPath(nodeCandidates, roomPaths, startingVertex);
        if(recursivePath == null) {
            visitedVertices.add(startingVertex);
            roomPath.add(startingVertex);
        } else {
            visitedVertices.addAll(recursivePath);
            roomPath.addAll(recursivePath);
        }

        //now, iteratively find the next best room from any current room (starting at player's node)
        //this will run a greedy algorithm that finds the next shortest distance from a node, then repeat
        MissionGraph.MissionVertex currentVertex = roomPath.get(roomPath.size() - 1);

        //cap at 30 rooms; performance doesn't seem to be an issue for this algorithm, however.
        for(int i = 0; i < 30; i++) {
            MissionGraph.MissionVertex minVertexExplored = null;
            MissionGraph.MissionVertex minVertexUnexplored = null;
            double minExplored = Double.MAX_VALUE;
            double minUnexplored = Double.MAX_VALUE;
            for(MissionGraph.MissionVertex endVertex : nodeCandidates) {
                if(!endVertex.equals(currentVertex) && !visitedVertices.contains(endVertex)) {
                    if(visitedVertices.contains(endVertex)) {
                        double distance = roomPaths.get(currentVertex).get(endVertex).getPathLength();
                        if(distance < minExplored) {
                            minExplored = distance;
                            minVertexExplored = endVertex;
                        }
                    } else {
                        double distance = roomPaths.get(currentVertex).get(endVertex).getPathLength();
                        if(distance < minUnexplored) {
                            minUnexplored = distance;
                            minVertexUnexplored = endVertex;
                        }
                    }
                }
            }

            MissionGraph.MissionVertex chosenVertex = null;
            if(minVertexUnexplored != null) {
                chosenVertex = minVertexUnexplored;
            } else {
                if(minVertexExplored == null) {
                    //no more nodes left in the path
                    break;
                } else {
                    chosenVertex = minVertexExplored;
                }
            }

            visitedVertices.add(chosenVertex);
            roomPath.add(chosenVertex);
            currentVertex = chosenVertex;
        }

        //reconstruct the best path for next x rooms via room to decision node mapping
        List<MissionGraph.MissionVertex> reconstructedPath = new ArrayList<>();
        double totalPathLength = 0;

        MissionGraph.VertexPath path = null;
        for(int i = 0; i < roomPath.size() - 1; i++) {
            path = roomPaths.get(roomPath.get(i)).get(roomPath.get(i + 1));
            reconstructedPath.addAll(path.getPath().subList(0, path.getPath().size() - 1));
            totalPathLength += roomPaths.get(roomPath.get(i)).get(roomPath.get(i + 1)).getPathLength();
        }

        if(path != null) {
            reconstructedPath.add(path.getPath().getLast());
        }

        //add additional reference locations for the room in the path, if applicable
        if(reconstructedPath.size() >= 2 && reconstructedPath.get(1).type == MissionGraph.MissionVertexType.ROOM) {
            MissionGraph.MissionVertex vertex = reconstructedPath.get(1);
            Location closestLocation = vertex.location;
            double closestDistance = closestLocation.distanceSquared(player.getLocation());

            for(Location referenceLoc : graph.getRoomEntranceExitLocations(mission.getRoom(vertex.name))) {
                double distance = referenceLoc.distanceSquared(player.getLocation());

                if(distance < closestDistance) {
                    closestDistance = distance;
                    closestLocation = referenceLoc;
                }
            }

            vertex.playerReferenceLocation = closestLocation;
        }

        //print estimated time left for player based on best path; is RED if the system doesn't think their current trajectory can get to all rooms on time
        if(onlyNonVisitedRooms && PRINT_RECOMMENDATIONS) {
            double timeToFinish = calculatePlayerTimeToFinish(reconstructedPath, totalPathLength);
            String timeToFinishMsg;

            double secondsLeft = ((Math.round((timeToFinish / 1000d) * 100d) / 100d));

            ChatColor timeToFinishColor;
            //if the player does not have enough time to complete the path, the color is red
            if(secondsLeft > missionManager.getMissionSecondsLeft()) {
                timeToFinishColor = ChatColor.RED;
            } else {
                timeToFinishColor = ChatColor.YELLOW;
            }

            if (timeToFinish != -1) {
                timeToFinishMsg = timeToFinishColor + "EST. TIME LEFT: " + secondsLeft + " seconds";
            } else {
                timeToFinishMsg = timeToFinishColor + "EST. TIME LEFT: ...";
            }

            if(DEBUG_VERBOSE_RECOMMENDATIONS) {
                actionBarMessage = new TextComponent(ChatColor.GREEN + "PATH LENGTH: " + ((Math.round(totalPathLength * 100d) / 100d)) + " BLOCKS" + ChatColor.WHITE + ChatColor.BOLD + " | " + timeToFinishMsg);
            } else {
                actionBarMessage = new TextComponent(timeToFinishMsg);
            }
        }

        return reconstructedPath;
    }

    /**
     * Limited-horizon shortest path algorithm that creates multiple "leaves" of possible paths and then after a certain amount of leaves, it will pick the overall shortest path. Complexity is 2^x for the number of layers done, which is why this is only done limited.
     * @param nodeCandidates
     * @param roomPaths
     * @param firstVertex
     * @return
     */
    private List<MissionGraph.MissionVertex> calculateBestLongTermPath(List<MissionGraph.MissionVertex> nodeCandidates, HashMap<MissionGraph.MissionVertex, HashMap<MissionGraph.MissionVertex, MissionGraph.VertexPath>> roomPaths, MissionGraph.MissionVertex firstVertex) {
        HashMap<List<MissionGraph.MissionVertex>, Double> currentPaths = new HashMap<>();
        HashMap<List<MissionGraph.MissionVertex>, Set<MissionGraph.MissionVertex>> visitedVertices = new HashMap<>();

        List<MissionGraph.MissionVertex> firstPath = new ArrayList<>(Collections.singletonList(firstVertex));
        currentPaths.put(firstPath, 0d);
        visitedVertices.put(firstPath, new HashSet<>(firstPath));

        //wrapper used so that the priority queue can access the currently looked at path and create comparisons based on this
        List<MissionGraph.MissionVertex>[] currentPath = new List[] {firstPath};

        //if any vertex has been visited, prioritize it last. Otherwise, priority is the vertex with the shorter length from the current vertex
        PriorityQueue<MissionGraph.MissionVertex> minVertices = new PriorityQueue<>((o1, o2) -> {
            if(visitedVertices.get(currentPath[0]).contains(o1)) {
                return 1;
            } else {
                if(visitedVertices.get(currentPath[0]).contains(o2)) {
                    return -1;
                } else {
                    //get the most recent vertex in the current path
                    MissionGraph.MissionVertex currentVertex = currentPath[0].get(currentPath[0].size() - 1);

                    if(currentVertex == o1) {
                        return 1;
                    } else if(currentVertex == o2) {
                        return -1;
                    }

                    return Double.compare(roomPaths.get(currentVertex).get(o1).getPathLength(), roomPaths.get(currentVertex).get(o2).getPathLength());
                }
            }
        });

        //traverse through 5 layers of multiple decisions (2^5)
        for(int i = 0; i < 5; i++) {
            Set<List<MissionGraph.MissionVertex>> clonedKeySet = new HashSet<>(currentPaths.keySet());
            for(List<MissionGraph.MissionVertex> path : clonedKeySet) {
                minVertices.clear();
                currentPath[0] = path;
                minVertices.addAll(nodeCandidates);

                //no more path remaining
                if(visitedVertices.get(path).contains(minVertices.peek())) {
                    continue;
                }

                //create branch for top 2 shortest paths
                for(int j = 0; j < 2; j++) {
                    List<MissionGraph.MissionVertex> newPath = new ArrayList<>(path);
                    Set<MissionGraph.MissionVertex> newVisited = new HashSet<>(visitedVertices.get(path));
                    newPath.add(minVertices.poll());
                    double pathLength = currentPaths.get(path) + roomPaths.get(newPath.get(newPath.size() - 2)).get(newPath.get(newPath.size() - 1)).getPathLength();

                    newVisited.add(newPath.get(newPath.size() - 1));

                    currentPaths.put(newPath, pathLength);
                    visitedVertices.put(newPath, newVisited);

                    //no more path remaining
                    if(minVertices.isEmpty() || visitedVertices.get(path).contains(minVertices.peek())) {
                        break;
                    }
                }

                //don't reuse this path, so remove it
                currentPaths.remove(path);
            }
        }

        //get the shortest path out of all traversed
        List<MissionGraph.MissionVertex> shortestPath = null;
        double pathLength = Double.MAX_VALUE;
        for(List<MissionGraph.MissionVertex> path : currentPaths.keySet()) {
            if(currentPaths.get(path) < pathLength) {
                pathLength = currentPaths.get(path);
                shortestPath = path;
            }
        }

        return shortestPath;
    }

    /**
     * Calculates time to get to all rooms based on current player speeds and total distance to remaining rooms on best path
     * @param bestPath
     * @param totalPathLength
     * @return In milliseconds, estimated time to traverse all rooms
     */
    private double calculatePlayerTimeToFinish(List<MissionGraph.MissionVertex> bestPath, double totalPathLength) {
        if(averagePlayerRoomTriageSpeed == 0 || averagePlayerDecisionTraversalSpeed == 0) {
            return -1;
        }

        double time = totalPathLength / averagePlayerDecisionTraversalSpeed;

        for(MissionGraph.MissionVertex vertex : bestPath) {
            if(vertex.type == MissionGraph.MissionVertexType.ROOM && !visitedVertices.contains(vertex)) {
                time += averagePlayerRoomTriageSpeed * mission.getRoom(vertex.name).getBounds().getArea();
            }
        }

        return time;
    }

    /**
     * Runs the best path algorithm, updates the player's current vertex, and formats recommendations to show the player
     * @param lastStats
     */
    private void analyzeMissionGraph(EnhancedStatsTracker.LastStatsSnapshot lastStats) {
        //calculate the current player node, checking rooms first
        MissionGraph.MissionVertex playerVertex = null;
        for(MissionRoom room : mission.getRooms()) {
            if(room.getBounds().isInRange(player.getLocation())) {
                playerVertex = new MissionGraph.MissionVertex(MissionGraph.MissionVertexType.ROOM, null, room.getRoomName());
            }
        }

        //if not in a room, check for decision nodes nearby up to 3 blocks away
        if(playerVertex == null) {
            double minDistance = Double.MAX_VALUE;
            for(Map.Entry<String, Location> entry : mission.getDecisionPoints().entrySet()) {
                double distance;
                if((distance = player.getLocation().distanceSquared(entry.getValue())) <= 9) {
                    if(distance < minDistance) {
                        minDistance = distance;
                        playerVertex = new MissionGraph.MissionVertex(MissionGraph.MissionVertexType.DECISION, null, entry.getKey());
                    }
                }
            }
        }

        //based on this result, output where the player can go next
        if((shouldUpdatePlayerGraph && lastVertex != null) || playerVertex != null) {
            if(shouldUpdatePlayerGraph || !playerVertex.equals(lastVertex)) {
                if(shouldUpdatePlayerGraph) {
                    shouldUpdatePlayerGraph = false;

                    if(playerVertex == null) {
                        playerVertex = lastVertex;
                    }
                }

//                HashMap<MissionGraph.MissionVertex, Double> roomPotentials = calculateRoomPotentials(playerVertex);
                List<MissionGraph.MissionVertex> bestPath = calculateBestPath(playerVertex);

                //append that with the best path of visited rooms
                if(!bestPath.isEmpty()) {
                    bestPath.addAll(calculateBestPath(bestPath.get(bestPath.size() - 1), false));
                }

                lastBestPath = bestPath;

                bestPathFormat = new ArrayList<>();
                HashMap<String, Integer> visitedVerticesCount = new HashMap<>();

                int decisionShow = 5;
                for(MissionGraph.MissionVertex vertex : bestPath) {
                    String color;
                    if(vertex.type == MissionGraph.MissionVertexType.ROOM) {
                        if(visitedVertices.contains(vertex)) {
                            color = "" + ChatColor.AQUA;
                        } else {
                            color = "" + ChatColor.GOLD;
                        }
                        decisionShow--;
                    } else {
                        if(decisionShow <= 0) {
                            continue;
                        }
                        color = "" + ChatColor.YELLOW;
                    }

                    if(!visitedVerticesCount.containsKey(vertex.toString())) {
                        visitedVerticesCount.put(vertex.toString(), 1);
                        bestPathFormat.add(color + vertex.toString());
                    } else {
                        visitedVerticesCount.put(vertex.toString(), visitedVerticesCount.get(vertex.toString()) + 1);
                        bestPathFormat.add(color + vertex.toString() + " (" + visitedVerticesCount.get(vertex.toString()) + ")");
                    }
                }

                if(PRINT_RECOMMENDATIONS) {
                    analyzeNextBestMove(bestPath, lastStats);
                }

                //analyze player speed
                calculatePlayerSpeed(mission.getMissionGraph(), lastVertex, playerVertex);

//                Bukkit.broadcastMessage(ChatColor.YELLOW + "AVERAGE HALLWAY SPEED: " + (averagePlayerDecisionTraversalSpeed * 1000) + " blocks/sec");
//                Bukkit.broadcastMessage(ChatColor.DARK_GREEN + "AVERAGE ROOM TIME: " + (averagePlayerRoomTriageSpeed / 1000) + " sec in a room");

                //analyze current player location
                visitedVertices.add(playerVertex);
                unvisitedRooms.remove(playerVertex);
                lastVertex = playerVertex;
            }
        }
    }

    /**
     * Based on the best path calculated and the player's location, format a human readable relative direction recommendation on where to move next
     * @param bestPath
     * @param lastStats
     */
    private void analyzeNextBestMove(List<MissionGraph.MissionVertex> bestPath, EnhancedStatsTracker.LastStatsSnapshot lastStats) {
        //simplified first-time analysis: at decision nodes, output if the player should enter a room connected, and if so, output where it is relative to the player
        Direction playerDir = getDirection(player.getLocation());

        if(bestPath.size() > 1) {
            if(bestPath.get(1).type == MissionGraph.MissionVertexType.ROOM) {
                Location playerToRoomLoc = player.getLocation().clone();
                playerToRoomLoc.setDirection(bestPath.get(1).getPlayerReferenceLocation().toVector().subtract(player.getLocation().toVector()));
                Direction roomDir = getDirection(playerToRoomLoc);

                if(bestPath.get(0).type == MissionGraph.MissionVertexType.DECISION) {
                    lastRecommendationMessage = ChatColor.LIGHT_PURPLE + "You should enter the room " + relativeHumanDirections.get(playerDir).get(roomDir) + ".";
                } else {
                    if(visitedVertices.contains(bestPath.get(0))) {
                        lastRecommendationMessage = ChatColor.LIGHT_PURPLE + "You should enter the room " + relativeHumanDirections.get(playerDir).get(roomDir) + ".";
                    } else {
                        lastRecommendationMessage = ChatColor.LIGHT_PURPLE + "After you explore this room, you should enter the room " + relativeHumanDirections.get(playerDir).get(roomDir) + ".";
                    }
                }
            } else {
                Location playerToRoomLoc = player.getLocation().clone();
                playerToRoomLoc.setDirection(bestPath.get(1).getPlayerReferenceLocation().toVector().subtract(player.getLocation().toVector()));
                Direction roomDir = getDirection(playerToRoomLoc);

                if(bestPath.get(0).type == MissionGraph.MissionVertexType.DECISION) {
                    lastRecommendationMessage = ChatColor.LIGHT_PURPLE + "Continue the hallway " + relativeHumanDirections.get(playerDir).get(roomDir) + ".";
                } else {
                    if(visitedVertices.contains(bestPath.get(0))) {
                        if(lastVertex != null && lastVertex.equals(bestPath.get(1))) {
                            lastRecommendationMessage = ChatColor.LIGHT_PURPLE + "You should exit the same way you came in.";
                        } else {
                            lastRecommendationMessage = ChatColor.LIGHT_PURPLE + "You should exit the room via the opening " + relativeHumanDirections.get(playerDir).get(roomDir) + ".";
                        }
                    } else {
                        if(lastVertex != null && lastVertex.equals(bestPath.get(1))) {
                            lastRecommendationMessage = ChatColor.LIGHT_PURPLE + "After you explore this room, you should exit the same way you came in.";
                        } else {
                            lastRecommendationMessage = ChatColor.LIGHT_PURPLE + "After you explore this room, you should exit the room via the opening " + relativeHumanDirections.get(playerDir).get(roomDir) + ".";
                        }
                    }
                }
            }

            if(DEBUG_VERBOSE_RECOMMENDATIONS) {
                Bukkit.broadcastMessage(lastRecommendationMessage);
            }
        }
    }

    /**
     * Convert a location's YAW to a direction (N E S W)
     * @param loc
     * @return
     */
    private Direction getDirection(Location loc) {
        Direction dir;
        double yaw = loc.getYaw();

        if(yaw < 0) {
            yaw += 360;
        }

        if(yaw >= 45 && yaw < 135) {
            dir = Direction.WEST;
        } else if(yaw >= 135 && yaw < 225) {
            dir = Direction.NORTH;
        } else if(yaw >= 225 && yaw < 315) {
            dir = Direction.EAST;
        } else {
            dir = Direction.SOUTH;
        }

        return dir;
    }

    /**
     * Calculate the player's speed based on the time they take to triage in a room and the time they take to move in the hallways
     * @param graph
     * @param lastVertex
     * @param currentVertex
     */
    private void calculatePlayerSpeed(MissionGraph graph, MissionGraph.MissionVertex lastVertex, MissionGraph.MissionVertex currentVertex) {
        if(lastVertex == null) {
            return;
        }

        if(currentVertex.type == MissionGraph.MissionVertexType.ROOM) {
            lastDecisionEnterTime = -1;
            if(lastRoomEnterTime == -1) {
                //has not entered room yet
                lastRoomEnterTime = System.currentTimeMillis();
            } else {
                if(!playerRoomTriageSpeeds.containsKey(lastVertex)) {
                    //entered a room before, going to another room
                    playerRoomTriageSpeeds.put(lastVertex, System.currentTimeMillis() - lastRoomEnterTime);
                    recalculateRoomTriageAverage();
                }

                lastRoomEnterTime = System.currentTimeMillis();
            }
        } else {
            if(lastDecisionEnterTime == -1) {
                lastDecisionEnterTime = System.currentTimeMillis();
            } else {
                //decision to decision node
                playerDecisionSpeeds.add(new DecisionTraversal(graph, lastVertex, currentVertex, System.currentTimeMillis() - lastDecisionEnterTime));
                lastDecisionEnterTime = System.currentTimeMillis();
                recalculateDecisionTraversalAverage();
            }

            if(lastVertex.type == MissionGraph.MissionVertexType.ROOM) {
                if(!playerRoomTriageSpeeds.containsKey(lastVertex)) {
                    //just exited a room
                    playerRoomTriageSpeeds.put(lastVertex, System.currentTimeMillis() - lastRoomEnterTime);
                    recalculateRoomTriageAverage();
                }

                lastRoomEnterTime = -1;
            }
        }
    }

    /**
     * Recalculates time it takes to triage rooms on average. Uses room area as a rough estimate for time it takes to triage rooms (bigger rooms => more time to triage)
     */
    private void recalculateRoomTriageAverage() {
        averagePlayerRoomTriageSpeed = 0d;
        int totalArea = 0;
        for(MissionGraph.MissionVertex room : playerRoomTriageSpeeds.keySet()) {
            averagePlayerRoomTriageSpeed += playerRoomTriageSpeeds.get(room);
            totalArea += mission.getRoom(room.name).getBounds().getArea();
        }

        averagePlayerRoomTriageSpeed = averagePlayerRoomTriageSpeed / totalArea;
    }

    /**
     * Recalculates time it takes to move between decision points in the hallway.
     */
    private void recalculateDecisionTraversalAverage() {
        averagePlayerDecisionTraversalSpeed = 0d;
        long totalTime = 0;
        for(DecisionTraversal traversal : playerDecisionSpeeds) {
            averagePlayerDecisionTraversalSpeed += traversal.pathLength;
            totalTime += traversal.pathTime;
        }

        averagePlayerDecisionTraversalSpeed /= totalTime;
    }

    /**
     * Determine, based on LineOfSight stats, whether or not player is looking at a victim. Will log when the player looks at them, looks away from them. Victim number represents the victim count that they have directly looked at so far. Does not use the blocks raycasting algorithm to see victims; only will log if the player DIRECTLY looks at the victim.
     * @param lastStats
     */
    private void analyzeVictimTarget(EnhancedStatsTracker.LastStatsSnapshot lastStats) {
        if(lastStats.lastPlayerValues.get("LineOfSight") instanceof String && ((String) lastStats.lastPlayerValues.get("LineOfSight")).isEmpty()) {
            currentVictimTarget = null;
            return;
        }

        JsonObject lineOfSight = (JsonObject) lastStats.lastPlayerValues.get("LineOfSight");

        if (lineOfSight.size() > 0 && lineOfSight.get("hitType").getAsString().equals("block")) {
            Location loc = new Location(player.getWorld(), lineOfSight.get("x").getAsDouble(), lineOfSight.get("y").getAsDouble(), lineOfSight.get("z").getAsDouble());
            Block block = loc.getBlock();

            MissionRoom blockRoom = null;
            for(MissionRoom room : mission.getRooms()) {
                if(room.getBounds().isInRange(loc)) {
                    blockRoom = room;
                    break;
                }
            }

            if(currentVictimTarget != null && !currentVictimTarget.equals(block)) {
                if(blockRoom == null) {
                    log(player.getName() + " looked away from victim " + (victimBlocks.indexOf(currentVictimTarget.getLocation())) + ".");
                } else {
                    log(player.getName() + " looked away from victim " + (victimBlocks.indexOf(currentVictimTarget.getLocation())) + " in room " + blockRoom.getRoomName() + ".");
                }
                currentVictimTarget = null;
            }

            if(VICTIM_BLOCKS.contains(block.getType()) && !block.equals(currentVictimTarget)) {
                if(!victimBlocks.contains(block.getLocation())) {
                    victimBlocks.add(block.getLocation());
                }

                if(blockRoom == null) {
                    log(player.getName() + " looked at victim " + (victimBlocks.indexOf(block.getLocation())) + ".");
                } else {
                    log(player.getName() + " looked at victim " + (victimBlocks.indexOf(block.getLocation())) + " in room " + blockRoom.getRoomName() + ".");
                }
                currentVictimTarget = block;
            }
        }
    }

    /**
     * Uses the stats to determine when a player breaks a block that is a victim. If so, we will log this as saving the victim and remove it from the MissionGraph stored list of yet to be saved victims.
     * @param lastStats
     * @param deltaStats
     */
    private void analyzeBrokenBlocks(EnhancedStatsTracker.LastStatsSnapshot lastStats, EnhancedStatsTracker.LastStatsSnapshot deltaStats) {
        boolean savedVictim = false;
        for(Material victimMat : VICTIM_BLOCKS) {
            if(deltaStats.lastPlayerBlocksBroken.containsKey(victimMat.toString().toLowerCase())) {
                //ASSUMPTION: PLAYER CAN ONLY BREAK ONE BLOCK IN ONE TICK'S TIME (don't think this can be violated in any way)
                Location loc = deltaStats.lastPlayerBlocksBrokenLocations.get(0);
                Block block = loc.getBlock();
                int victimNumber = victimBlocks.indexOf(loc);
                log(player.getName() + " saved victim " + victimNumber + ".");

                //if the player digs a victim, then make sure we log it as being saved!
                MissionGraph graph = mission.getMissionGraph();
                HashMap<MissionGraph.MissionVertex, Set<Block>> roomVerticesWithVictims = graph.getRoomVerticesWithVictims();
                HashMap<MissionGraph.MissionVertex, Set<Block>> roomVerticesSavedVictims = graph.getRoomVerticesSavedVictims();
                MissionRoom room = graph.getVictimRoom(block);
                if(room != null) {
                    MissionGraph.MissionVertex roomVertex = graph.getRoomVertex(room);

                    if (roomVerticesWithVictims.containsKey(roomVertex)) {
                        roomVerticesWithVictims.get(roomVertex).remove(block);

                        roomVerticesSavedVictims.putIfAbsent(roomVertex, new HashSet<>());
                        roomVerticesSavedVictims.get(roomVertex).add(block);
                    }
                }

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

    /**
     * Determine when player enters and exits rooms based on room boundaries.
     * @param lastStats
     */
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

    /**
     * Determine when a player opens a door or extinguishes a fire.
     * @param deltaStats
     */
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

    /**
     *
     * @return The last vertex that the player has been on. Can be null (they have not yet entered a vertex)!
     */
    public MissionGraph.MissionVertex getLastVertex() {
        return lastVertex;
    }

    /**
     *
     * @return Last calculated best path for player to go through all rooms. Can be null if they have not yet entered a vertex.
     */
    public List<MissionGraph.MissionVertex> getLastBestPath() {
        return lastBestPath;
    }
}
