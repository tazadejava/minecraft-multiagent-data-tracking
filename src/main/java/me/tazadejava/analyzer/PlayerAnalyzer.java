package me.tazadejava.analyzer;

import com.google.gson.JsonObject;
import com.sun.org.apache.xpath.internal.axes.OneStepIterator;
import me.tazadejava.blockranges.BlockRange2D;
import me.tazadejava.mission.MissionGraph;
import me.tazadejava.mission.MissionRoom;
import me.tazadejava.statstracker.EnhancedStatsTracker;
import me.tazadejava.mission.Mission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

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

    private MissionGraph.MissionVertex lastVertex;
    private Set<MissionGraph.MissionVertex> visitedVertices;
    private Set<MissionGraph.MissionVertex> unvisitedRooms;

    //roomSpeed: average MS per block in the room
    //decisionSpeed: average blocks per MS
    private double averagePlayerDecisionTraversalSpeed, averagePlayerRoomTriageSpeed;
    private List<DecisionTraversal> playerDecisionSpeeds = new ArrayList<>();
    private HashMap<MissionGraph.MissionVertex, Long> playerRoomTriageSpeeds = new HashMap<>();
    private long lastRoomEnterTime = -1, lastDecisionEnterTime = -1;

    public PlayerAnalyzer(Player player, Mission mission) {
        this.player = player;
        this.mission = mission;

        victimBlocks = new ArrayList<>();
        firstLevelActions = new ArrayList<>();

        visitedVertices = new HashSet<>();
        unvisitedRooms = new HashSet<>();

        unvisitedRooms.addAll(mission.getMissionGraph().getRoomVertices());

        currentRoom = null;
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

    private HashMap<MissionGraph.MissionVertex, Double> calculateRoomPotentials(MissionGraph.MissionVertex playerVertex) {
        HashMap<MissionGraph.MissionVertex, Double> roomPotentials = new HashMap<>();

        //TODO: IMPLEMENT ALGORITHM

        double maxDistanceToPlayer = 0;
        for(MissionGraph.MissionVertex room : mission.getMissionGraph().getRoomVertices()) {
            MissionGraph.VertexPath path = mission.getMissionGraph().getShortestPathUsingEdges(room.type, room.name, playerVertex.type, playerVertex.name);
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

    private List<MissionGraph.MissionVertex> calculateBestPath(MissionGraph.MissionVertex playerVertex, HashMap<MissionGraph.MissionVertex, Double> roomPotentials) {
        MissionGraph graph = mission.getMissionGraph().cloneGraphOnly();

        double maxDistanceToPlayer = 0;
        for(MissionGraph.MissionVertex room : mission.getMissionGraph().getRoomVertices()) {
            MissionGraph.VertexPath path = mission.getMissionGraph().getShortestPathUsingEdges(room.type, room.name, playerVertex.type, playerVertex.name);
            maxDistanceToPlayer = Math.max(maxDistanceToPlayer, path.getPathLength());
        }
        double roomPotentialWeight = Math.sqrt(maxDistanceToPlayer);

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

        //now, recursively find the next best room from any current room (starting at player's node)

        Set<MissionGraph.MissionVertex> visitedVertices = new HashSet<>();
        List<MissionGraph.MissionVertex> roomPath = new ArrayList<>();

        visitedVertices.add(playerVertex);
        roomPath.add(playerVertex);

        MissionGraph.MissionVertex currentVertex = playerVertex;
        //reconstruct the best path for next x rooms via room to decision node mapping
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

        for(int i = 0; i < 16; i++) {
            Bukkit.broadcastMessage("");
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "TOTAL PATH LENGTH: " + totalPathLength + " BLOCKS");
        double timeToFinish = calculatePlayerTimeToFinish(reconstructedPath, totalPathLength);
        if(timeToFinish != -1) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "EST. TIME TO FINISH: " + (calculatePlayerTimeToFinish(reconstructedPath, totalPathLength) / 1000d) + " seconds");
        } else {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "EST. TIME TO FINISH: calulating...");
        }
        Bukkit.broadcastMessage("");

        return reconstructedPath;
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
        if(playerVertex != null) {
            if(!playerVertex.equals(lastVertex)) {
                //best path

                HashMap<MissionGraph.MissionVertex, Double> roomPotentials = calculateRoomPotentials(playerVertex);
                List<MissionGraph.MissionVertex> bestPath = calculateBestPath(playerVertex, roomPotentials);

                List<String> bestPathFormat = new ArrayList<>();

                int decisionShow = 5;
                boolean first = true;
                for(MissionGraph.MissionVertex vertex : bestPath) {
                    if(first) {
                        first = false;
                        bestPathFormat.add(ChatColor.DARK_GRAY + vertex.toString());
                        continue;
                    }

                    ChatColor color = null;
                    if(vertex.type == MissionGraph.MissionVertexType.ROOM) {
                        if(visitedVertices.contains(vertex)) {
                            color = ChatColor.AQUA;
                        } else {
                            color = ChatColor.GOLD;
                        }
                        decisionShow--;
                    } else {
                        if(decisionShow <= 0) {
                            continue;
                        }
                        color = ChatColor.GRAY;
                    }

                    bestPathFormat.add(color + vertex.toString());
                }

                Bukkit.broadcastMessage("BEST PATH: " + bestPathFormat.toString());

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

    private void calculatePlayerSpeed(MissionGraph graph, MissionGraph.MissionVertex lastVertex, MissionGraph.MissionVertex currentVertex) {
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
}
