package me.tazadejava.analyzer;

import com.google.gson.JsonObject;
import me.tazadejava.blockranges.BlockRange2D;
import me.tazadejava.map.DynamicMapRenderer;
import me.tazadejava.map.MapOverlayRenderer;
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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

//for the most part, this class does not have to exist within the plugin:
//to export to a separate process:
// - save the json file continuously and have an external process check for changes, then perform similar actions to what is being done here, but search the JSON file instead of casting
public class PlayerAnalyzer {

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

    public enum Direction {
        NORTH, EAST, SOUTH, WEST
    }

    public static final Set<Material> VICTIM_BLOCKS = new HashSet<>(Arrays.asList(Material.PRISMARINE, Material.GOLD_BLOCK));
    private static final BlockFace[] ADJACENT_FACES = new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.UP, BlockFace.DOWN};


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

    //TODO: THIS ONLY WORKS FOR THE SPARKY MAP; IN THE FUTURE, MAKE IT CONFIGURABLE BY THE MISSION
    private PreciseVisibleBlocksRaycaster visibleBlocksRaycaster = new PreciseVisibleBlocksRaycaster(true, true, false, 52, 54);
    private long lastRaycastTime;

    private static final boolean PRINT = true;
    private TextComponent actionBarMessage = null;
    private String lastRecommendationMessage;
    private List<String> bestPathFormat;
    private List<MissionGraph.MissionVertex> lastBestPath;

    private static final boolean DEBUG_PRINT = false;

    public PlayerAnalyzer(Player player, Mission mission, MissionManager missionManager) {
        this.player = player;
        this.mission = mission;

        victimBlocks = new ArrayList<>();
        firstLevelActions = new ArrayList<>();

        visitedVertices = new HashSet<>();
        unvisitedRooms = new HashSet<>();

        unvisitedRooms.addAll(mission.getMissionGraph().getRoomVertices());

        currentRoom = null;

        calculateRelativeHumanDirections();

        //give the player a map
//        if(DEBUG_PRINT) {
            player.getInventory().setItemInMainHand(DynamicMapRenderer.getMap(missionManager, player, false));
            player.getInventory().setItemInOffHand(DynamicMapRenderer.getMap(missionManager, player, true));
//        } else {
//            player.getInventory().setItemInMainHand(DynamicMapRenderer.getMap(missionManager, player));
//        }

    }

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

        //TODO temp: print to all players
//        for(Player p : Bukkit.getOnlinePlayers()) {
//            p.sendMessage(action);
//        }

        //TODO: pass data into genesis, so that genesis can understand data
    }

    //sync update
    public void updateSync() {
        //possibly recalculate edges
        analyzeMissionGraphEdges();

        if (actionBarMessage != null) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, actionBarMessage);
        }

        if(DEBUG_PRINT) {
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

                if(lastRecommendationMessage.length() > 32) {
                    List<String> messages = new ArrayList<>();

                    String message = lastRecommendationMessage;
                    while(message.length() > 32) {
                        int bestIndex = 0;
                        int index;
                        String currentString = message;
                        while((index = currentString.indexOf(" ")) != -1) {
                            if(bestIndex + index > 32) {
                                break;
                            }

                            bestIndex += index + 1;
                            currentString = currentString.substring(index + 1);
                        }

                        messages.add(message.substring(0, bestIndex));
                        message = message.substring(bestIndex);
                    }

                    messages.add(message);

                    for(int i = 0; i < messages.size(); i++) {
                        objective.getScore(ChatColor.LIGHT_PURPLE + messages.get(i)).setScore(messages.size() - i - 1);
                    }
                } else {
                    objective.getScore(lastRecommendationMessage).setScore(0);
                }

                player.setScoreboard(scoreboard);
            }
        }
    }

    //async update
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

    //goal: use visibility algorithm to determine whether or not edges should be changed
    private void analyzeMissionGraphEdges() {
        if(System.currentTimeMillis() - lastRaycastTime < 200) {
            return;
        }
        lastRaycastTime = System.currentTimeMillis();

        Set<Block> visibleBlocks = visibleBlocksRaycaster.getVisibleBlocks(player);

        Material[] mats = new Material[] {Material.BLACK_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.BROWN_STAINED_GLASS, Material.CYAN_STAINED_GLASS, Material.WHITE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.GREEN_STAINED_GLASS};
        BlockData defaultMaterial = Bukkit.getServer().createBlockData(mats[(int) (Math.random() * mats.length)]);
        BlockData specialMaterial = Bukkit.getServer().createBlockData(Material.MAGMA_BLOCK);
//        for (Block block : visibleBlocks) {
//            player.sendBlockChange(block.getLocation(), defaultMaterial);
//        }

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
//                            Bukkit.broadcastMessage("POSSIBLE EDGE BETWEEN ROOMS " + originalRoom.getRoomName() + " AND " + compareRoom.getRoomName());

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
                                        player.sendBlockChange(visibleBlock.getLocation(), specialMaterial);

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

                                //quality check; make sure the path is not simply the currently shortest path
                                MissionGraph.LocationPath newPath = mission.getMissionGraph().defineEdge(MissionGraph.MissionVertexType.ROOM, originalRoom.getRoomName(), MissionGraph.MissionVertexType.ROOM, compareRoom.getRoomName());
                                mission.getMissionGraph().protectEdge(MissionGraph.MissionVertexType.ROOM, originalRoom.getRoomName(), MissionGraph.MissionVertexType.ROOM, compareRoom.getRoomName());

                                double manhattanDistance = 0;
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

                                if(newPath.getPathLength() < manhattanDistance * 2d) {
                                    Bukkit.broadcastMessage("" + ChatColor.GOLD + ChatColor.BOLD + "FOUND EDGE BETWEEN ROOMS " + originalRoom.getRoomName() + " AND " + compareRoom.getRoomName() + " WITH LENGTH " + mission.getMissionGraph().getShortestPathUsingEdges(MissionGraph.MissionVertexType.ROOM, originalRoom.getRoomName(), MissionGraph.MissionVertexType.ROOM, compareRoom.getRoomName()).getPathLength());

                                    //recalculate best path
                                    shouldUpdatePlayerGraph = true;
                                } else {
                                    Bukkit.broadcastMessage("" + ChatColor.GRAY + "Almost found an edge between " + originalRoom.getRoomName() + " AND " + compareRoom.getRoomName() + " WITH LENGTH " + mission.getMissionGraph().getShortestPathUsingEdges(MissionGraph.MissionVertexType.ROOM, originalRoom.getRoomName(), MissionGraph.MissionVertexType.ROOM, compareRoom.getRoomName()).getPathLength() + " " + (manhattanDistance * 2d));
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
            for(MissionGraph.MissionVertex neighbor : mission.getMissionGraph().getNeighbors(lastVertex)) {
                if(!mission.getMissionGraph().verifyEdgeTraversable(lastVertex.type, lastVertex.name, neighbor.type, neighbor.name, visibleBlocks)) {
                    Bukkit.broadcastMessage("" + ChatColor.RED + ChatColor.BOLD + "THE EDGE BETWEEN " + lastVertex + " AND " + neighbor + " IS NOT TRAVERSABLE!");
                    mission.getMissionGraph().deleteEdge(lastVertex.type, lastVertex.name, neighbor.type, neighbor.name);

                    shouldUpdatePlayerGraph = true;
                    break;
                }
            }
        }
    }

    private HashMap<MissionGraph.MissionVertex, Double> calculateRoomPotentials(MissionGraph.MissionVertex playerVertex) {
        HashMap<MissionGraph.MissionVertex, Double> roomPotentials = new HashMap<>();

        //TODO: IMPLEMENT ALGORITHM

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

//    private List<MissionGraph.MissionVertex> calculateBestPath(MissionGraph.MissionVertex playerVertex, HashMap<MissionGraph.MissionVertex, Double> roomPotentials) {
    private List<MissionGraph.MissionVertex> calculateBestPath(MissionGraph.MissionVertex playerVertex) {
        MissionGraph graph = mission.getMissionGraph().cloneGraphOnly();

        //modify weights to accomodate for potential; this will allow for graph path to be weighted based on specific factors
//        for(MissionGraph.MissionVertex roomPotential : roomPotentials.keySet()) {
//            for(MissionGraph.MissionVertex neighbor : graph.getNeighbors(roomPotential)) {
//                graph.modifyEdgeWeight(roomPotential, neighbor, graph.getEdgeWeight(roomPotential, neighbor) + Math.pow((1 - roomPotentials.get(roomPotential)) * roomPotentialWeight, 2));
//            }
//        }

        //find shortest path using tree estimate

        //first, calculate the path from any room to another room using APSP weight algorithm AND player's node if it is not a room node
        //store room to decision nodes mapping

        List<MissionGraph.MissionVertex> nodeCandidates = new ArrayList<>();
        HashMap<MissionGraph.MissionVertex, HashMap<MissionGraph.MissionVertex, MissionGraph.VertexPath>> roomPaths = new HashMap<>();

        for(MissionGraph.MissionVertex roomVertex : graph.getRoomVertices()) {
            if(!visitedVertices.contains(roomVertex)) {
                nodeCandidates.add(roomVertex);
            }
        }

        if(!nodeCandidates.contains(playerVertex)) {
            nodeCandidates.add(playerVertex);
        }

        for(MissionGraph.MissionVertex beginNode : nodeCandidates) {
            roomPaths.put(beginNode, graph.getShortestPathToAllVertices(beginNode.type, beginNode.name));
        }

        //to account for LONG-TERM SUCCESS, we will "leaf" out the top x shortest paths, and analyze all the paths y times to look at the shortest long-term paths
        //then, we will iteratively find the next best room starting at the node that ends this path

        Set<MissionGraph.MissionVertex> visitedVertices = new HashSet<>();
        List<MissionGraph.MissionVertex> roomPath = new ArrayList<>();

        List<MissionGraph.MissionVertex> recursivePath = calculateBestLongTermPath(nodeCandidates, roomPaths, playerVertex);
        if(recursivePath == null) {
            visitedVertices.add(playerVertex);
            roomPath.add(playerVertex);
        } else {
            visitedVertices.addAll(recursivePath);
            roomPath.addAll(recursivePath);
        }

        //now, iteratively find the next best room from any current room (starting at player's node)
        MissionGraph.MissionVertex currentVertex = roomPath.get(roomPath.size() - 1);

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

        if(PRINT) {
            double timeToFinish = calculatePlayerTimeToFinish(reconstructedPath, totalPathLength);
            String timeToFinishMsg;
            if (timeToFinish != -1) {
                timeToFinishMsg = ChatColor.YELLOW + "EST. TIME LEFT: " + ((Math.round((calculatePlayerTimeToFinish(reconstructedPath, totalPathLength) / 1000d) * 100d) / 100d)) + " seconds";
            } else {
                timeToFinishMsg = ChatColor.YELLOW + "EST. TIME LEFT: ...";
            }

            if(DEBUG_PRINT) {
                actionBarMessage = new TextComponent(ChatColor.GREEN + "PATH LENGTH: " + ((Math.round(totalPathLength * 100d) / 100d)) + " BLOCKS" + ChatColor.WHITE + ChatColor.BOLD + " | " + timeToFinishMsg);
            } else {
                actionBarMessage = new TextComponent(timeToFinishMsg);
            }
        }

        return reconstructedPath;
    }

    //consider leaf branching situation, multiple shortest paths
    private List<MissionGraph.MissionVertex> calculateBestLongTermPath(List<MissionGraph.MissionVertex> nodeCandidates, HashMap<MissionGraph.MissionVertex, HashMap<MissionGraph.MissionVertex, MissionGraph.VertexPath>> roomPaths, MissionGraph.MissionVertex firstVertex) {
        HashMap<List<MissionGraph.MissionVertex>, Double> currentPaths = new HashMap<>();
        HashMap<List<MissionGraph.MissionVertex>, Set<MissionGraph.MissionVertex>> visitedVertices = new HashMap<>();

        List<MissionGraph.MissionVertex> firstPath = new ArrayList<>(Collections.singletonList(firstVertex));
        currentPaths.put(firstPath, 0d);
        visitedVertices.put(firstPath, new HashSet<>(firstPath));

        List<MissionGraph.MissionVertex>[] currentPath = new List[] {firstPath};

        PriorityQueue<MissionGraph.MissionVertex> minVertices = new PriorityQueue<>(new Comparator<MissionGraph.MissionVertex>() {
            @Override
            public int compare(MissionGraph.MissionVertex o1, MissionGraph.MissionVertex o2) {
                if(visitedVertices.get(currentPath[0]).contains(o1)) {
                    return 1;
                } else {
                    if(visitedVertices.get(currentPath[0]).contains(o2)) {
                        return -1;
                    } else {
                        MissionGraph.MissionVertex currentVertex = currentPath[0].get(currentPath[0].size() - 1);

                        if(currentVertex == o1) {
                            return 1;
                        } else if(currentVertex == o2) {
                            return -1;
                        }

                        return Double.compare(roomPaths.get(currentVertex).get(o1).getPathLength(), roomPaths.get(currentVertex).get(o2).getPathLength());
                    }
                }
            }
        });

        //traverse through 5 layers of multiple decisions
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

                //don't reuse this path
                currentPaths.remove(path);
            }

            //print out possible paths

//            Bukkit.broadcastMessage(ChatColor.BOLD + "END OF ITERATION " + i);
//            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "PATHS: " + currentPaths.size());
//
//            List<List<MissionGraph.MissionVertex>> pathsSorted = new ArrayList<>(currentPaths.keySet());
//
//            pathsSorted.sort(new Comparator<List<MissionGraph.MissionVertex>>() {
//                @Override
//                public int compare(List<MissionGraph.MissionVertex> o1, List<MissionGraph.MissionVertex> o2) {
//                    return Double.compare(currentPaths.get(o1), currentPaths.get(o2));
//                }
//            });
//
//            for(List<MissionGraph.MissionVertex> pathLoop : pathsSorted) {
//                Bukkit.broadcastMessage(pathLoop.toString() + " " + ChatColor.YELLOW + currentPaths.get(pathLoop));
//            }
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

//        Bukkit.broadcastMessage(ChatColor.GOLD + "SHORTEST PATH: " + currentPaths.get(shortestPath) + " " + shortestPath);

        return shortestPath;
    }

    //returns in milliseconds
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

    private void analyzeMissionGraph(EnhancedStatsTracker.LastStatsSnapshot lastStats) {
        MissionGraph graph = mission.getMissionGraph();

        //calculate the current player node, checking rooms first
        MissionGraph.MissionVertex playerVertex = null;
        for(MissionRoom room : mission.getRooms()) {
            if(room.getBounds().isInRange(player.getLocation())) {
                playerVertex = new MissionGraph.MissionVertex(MissionGraph.MissionVertexType.ROOM, null, room.getRoomName());
            }
        }

        //if not in a room, check for decision nodes nearby
        if(playerVertex == null) {
            for(Map.Entry<String, Location> entry : mission.getDecisionPoints().entrySet()) {
                if(player.getLocation().distanceSquared(entry.getValue()) <= 4) {
                    playerVertex = new MissionGraph.MissionVertex(MissionGraph.MissionVertexType.DECISION, null, entry.getKey());
                    break;
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

                //best path

//                HashMap<MissionGraph.MissionVertex, Double> roomPotentials = calculateRoomPotentials(playerVertex);
                List<MissionGraph.MissionVertex> bestPath = calculateBestPath(playerVertex);
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

                if(PRINT) {
                    analyzeNextBestMove(bestPath, lastStats);
                }

                //analyze player speed
//                Bukkit.broadcastMessage("CURRENT NODE: " + playerVertex);
                calculatePlayerSpeed(mission.getMissionGraph(), lastVertex, playerVertex);

//                Bukkit.broadcastMessage(ChatColor.YELLOW + "AVERAGE HALLWAY SPEED: " + (averagePlayerDecisionTraversalSpeed * 1000) + " blocks/sec");
//                Bukkit.broadcastMessage(ChatColor.DARK_GREEN + "AVERAGE ROOM TIME: " + (averagePlayerRoomTriageSpeed / 1000) + " sec in a room");

                //analyze current player location
                visitedVertices.add(playerVertex);
                unvisitedRooms.remove(playerVertex);
                lastVertex = playerVertex;

//                player.sendMessage("Your current node is " + ChatColor.DARK_PURPLE + playerVertex.type + ": " + playerVertex.name);

                Set<MissionGraph.MissionVertex> neighbors = graph.getNeighbors(playerVertex);
                if(neighbors.size() == 1) {
//                    player.sendMessage("  You will go here next: " + ChatColor.GOLD + neighbors);
                } else {
                    List<MissionGraph.MissionVertex> likelyNeighbors = new ArrayList<>();
                    List<MissionGraph.MissionVertex> unlikelyNeighbors = new ArrayList<>();

                    for(MissionGraph.MissionVertex vertex : neighbors) {
                        if(visitedVertices.contains(vertex)) {
                            unlikelyNeighbors.add(vertex);
                        } else {
                            if(vertex.type == MissionGraph.MissionVertexType.ROOM) {
                                likelyNeighbors.add(0, vertex);
                            } else {
                                likelyNeighbors.add(vertex);
                            }
                        }
                    }

//                    player.sendMessage("  You will probably go here next: " + ChatColor.YELLOW + likelyNeighbors);
//                    player.sendMessage("  You might go here next: " + ChatColor.GRAY + unlikelyNeighbors);
                }
            }
        }
    }

    //goal: using the best path and player location, determine in EASILY HUMAN READABLE FORMAT the next best move
    private void analyzeNextBestMove(List<MissionGraph.MissionVertex> bestPath, EnhancedStatsTracker.LastStatsSnapshot lastStats) {
        //simplified first-time analysis: at decision nodes, output if the player should enter a room connected, and if so, output where it is relative to the player
        Direction playerDir = getDirection(player.getLocation());

        if(bestPath.size() > 1) {
            if(bestPath.get(1).type == MissionGraph.MissionVertexType.ROOM) {
                Location playerToRoomLoc = player.getLocation().clone();
                playerToRoomLoc.setDirection(bestPath.get(1).location.toVector().subtract(player.getLocation().toVector()));
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
                playerToRoomLoc.setDirection(bestPath.get(1).location.toVector().subtract(player.getLocation().toVector()));
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

            if(DEBUG_PRINT) {
                Bukkit.broadcastMessage(lastRecommendationMessage);
            }
        }
    }

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
            } else if(lastVertex != null) {
                //decision to decision node
                playerDecisionSpeeds.add(new DecisionTraversal(graph, lastVertex, currentVertex, System.currentTimeMillis() - lastDecisionEnterTime));
                lastDecisionEnterTime = System.currentTimeMillis();
                recalculateDecisionTraversalAverage();
            }

            if(lastVertex != null && lastVertex.type == MissionGraph.MissionVertexType.ROOM) {
                if(!playerRoomTriageSpeeds.containsKey(lastVertex)) {
                    //just exited a room
                    playerRoomTriageSpeeds.put(lastVertex, System.currentTimeMillis() - lastRoomEnterTime);
                    recalculateRoomTriageAverage();
                }

                lastRoomEnterTime = -1;
            }
        }
    }

    private void recalculateRoomTriageAverage() {
//        Bukkit.broadcastMessage("ROOM TRIAGE " + playerRoomTriageSpeeds.get(playerRoomTriageSpeeds.size() - 1));
        averagePlayerRoomTriageSpeed = 0;
        int totalArea = 0;
        for(MissionGraph.MissionVertex room : playerRoomTriageSpeeds.keySet()) {
            averagePlayerRoomTriageSpeed += playerRoomTriageSpeeds.get(room);
            totalArea += mission.getRoom(room.name).getBounds().getArea();
        }

        averagePlayerRoomTriageSpeed = averagePlayerRoomTriageSpeed / totalArea;
    }

    private void recalculateDecisionTraversalAverage() {
//        Bukkit.broadcastMessage("DECISION TRAVERSAL " + playerDecisionSpeeds.get(playerDecisionSpeeds.size() - 1).pathTime);
        averagePlayerDecisionTraversalSpeed = 0;
        long totalTime = 0;
        for(DecisionTraversal traversal : playerDecisionSpeeds) {
            averagePlayerDecisionTraversalSpeed += traversal.pathLength;
            totalTime += traversal.pathTime;
        }

        averagePlayerDecisionTraversalSpeed /= totalTime;
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

    public List<MissionGraph.MissionVertex> getLastBestPath() {
        return lastBestPath;
    }
}
