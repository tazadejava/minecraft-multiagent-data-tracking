package me.tazadejava.analyzer;

import com.google.gson.JsonObject;
import com.sun.org.apache.xpath.internal.axes.OneStepIterator;
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

        //modify weights to accomodate for potential
//        for(MissionGraph.MissionVertex roomPotential : roomPotentials.keySet()) {
//            for(MissionGraph.MissionVertex neighbor : graph.getNeighbors(roomPotential)) {
//                graph.modifyEdgeWeight(roomPotential, neighbor, graph.getEdgeWeight(roomPotential, neighbor) + Math.pow((1 - roomPotentials.get(roomPotential)) * roomPotentialWeight, 2));
//            }
//        }

        //find shortest path using tree estimate

        //first, calculate the path from any room to another room using Johnson's AND player's node if it is not a room node
        //store room to decision nodes mapping

        List<MissionGraph.MissionVertex> nodeCandidates = new ArrayList<>();
        HashMap<MissionGraph.MissionVertex, HashMap<MissionGraph.MissionVertex, Double>> roomDistances = new HashMap<>();
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
            roomDistances.put(beginNode, new HashMap<>());
            roomPaths.put(beginNode, new HashMap<>());

            for(MissionGraph.MissionVertex endNode : nodeCandidates) {
                if(!beginNode.equals(endNode)) {
                    MissionGraph.VertexPath path = graph.getShortestPathUsingEdges(beginNode.type, beginNode.name, endNode.type, endNode.name);

                    roomDistances.get(beginNode).put(endNode, path.getPathLength());
                    roomPaths.get(beginNode).put(endNode, path);
                }
            }
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
                        double distance = roomDistances.get(currentVertex).get(endVertex);
                        if(distance < minExplored) {
                            minExplored = distance;
                            minVertexExplored = endVertex;
                        }
                    } else {
                        double distance = roomDistances.get(currentVertex).get(endVertex);
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
            totalPathLength += roomDistances.get(roomPath.get(i)).get(roomPath.get(i + 1));
        }

        if(path != null) {
            reconstructedPath.add(path.getPath().getLast());
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "TOTAL PATH LENGTH: " + totalPathLength + " BLOCKS");

        return reconstructedPath;
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

                long start = System.currentTimeMillis();
                HashMap<MissionGraph.MissionVertex, Double> roomPotentials = calculateRoomPotentials(playerVertex);
                List<MissionGraph.MissionVertex> bestPath = calculateBestPath(playerVertex, roomPotentials);
                Bukkit.broadcastMessage(ChatColor.GRAY + "TOOK " + (System.currentTimeMillis() - start));

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

    private List<Map.Entry<MissionGraph.MissionVertex, MissionGraph.VertexPath>> getPathsToUnvisitedRooms(MissionGraph.MissionVertex currentVertex) {
        LinkedHashMap<MissionGraph.MissionVertex, MissionGraph.VertexPath> paths = new LinkedHashMap<>();

        for(MissionGraph.MissionVertex room : unvisitedRooms) {
            paths.put(room, mission.getMissionGraph().getShortestPathUsingEdges(currentVertex, room));
        }

        //sort by distance

        List<Map.Entry<MissionGraph.MissionVertex, MissionGraph.VertexPath>> pathsSorted = new ArrayList<>(paths.entrySet());
        pathsSorted.sort(new Comparator<Map.Entry<MissionGraph.MissionVertex, MissionGraph.VertexPath>>() {
            @Override
            public int compare(Map.Entry<MissionGraph.MissionVertex, MissionGraph.VertexPath> o1, Map.Entry<MissionGraph.MissionVertex, MissionGraph.VertexPath> o2) {
                return Double.compare(o1.getValue().getPathLength(), o2.getValue().getPathLength());
            }
        });

        return pathsSorted;
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
