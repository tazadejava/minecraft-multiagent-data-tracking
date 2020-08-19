package me.tazadejava.mission;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tazadejava.actiontracker.Utils;
import me.tazadejava.blockranges.BlockRange2D;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

//represents the graphical implementation of missions
public class MissionGraph {

    public enum MissionVertexType {
        ROOM, DECISION
    }

    public static class MissionVertex {

        public MissionVertexType type;
        public Location location;

        //used to determine door locations for rooms
        public Location playerReferenceLocation;

        public String name;

        public MissionVertex(MissionVertexType type, Location location, String name) {
            this.type = type;
            this.location = location;
            this.name = name;
        }

        @Override
        public String toString() {
            return type + " " + name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MissionVertex that = (MissionVertex) o;
            return type == that.type &&
                    Objects.equals(name, that.name);
        }

        public Location getPlayerReferenceLocation() {
            return playerReferenceLocation == null ? location : playerReferenceLocation;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name);
        }
    }

    public class VertexPath {

        private LinkedList<MissionVertex> path;
        private double pathLength;

        public VertexPath(LinkedList<MissionVertex> path, double pathLength) {
            this.path = path;
            this.pathLength = pathLength;
        }

        public LinkedList<MissionVertex> getPath() {
            return path;
        }

        public double getPathLength() {
            return pathLength;
        }
    }

    public class LocationPath {

        private LinkedList<Location> path;
        private double pathLength;

        public LocationPath(LinkedList<Location> path, double pathLength) {
            this.path = path;
            this.pathLength = pathLength;
        }

        public LinkedList<Location> getPath() {
            return path;
        }

        public double getPathLength() {
            return pathLength;
        }
    }

    private static final int[] ADJACENT_DIRECTIONS = {
            1, 0,
            0, 1,
            -1, 0,
            0, -1
    };

    //TODO: this is my no means a comprehensive list... in the future, to prevent having to mark EVERY material, maybe use BlockStates instead?
    private Set<Material> passableMaterials = new HashSet<>(Arrays.asList(Material.AIR, Material.OAK_DOOR, Material.OAK_WALL_SIGN, Material.OAK_SIGN, Material.IRON_DOOR, Material.DARK_OAK_DOOR, Material.LEVER, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, Material.HEAVY_WEIGHTED_PRESSURE_PLATE, Material.STONE_PRESSURE_PLATE, Material.REDSTONE_WALL_TORCH));

    private Mission mission;

    private HashMap<String, MissionVertex> roomVertices = new HashMap<>();
    private HashMap<String, MissionVertex> decisionVertices = new HashMap<>();

    private HashMap<String, Set<Location>> roomEntranceExitLocations = new HashMap<>();

    private HashMap<MissionVertex, Set<MissionVertex>> edges = new HashMap<>();
    private HashMap<MissionVertex, HashMap<MissionVertex, Double>> edgeWeights = new HashMap<>();
    private HashMap<MissionVertex, HashMap<MissionVertex, LinkedList<Location>>> edgePaths = new HashMap<>();

    //represents edges that have been verified to be traversable
    private HashMap<MissionVertex, Set<MissionVertex>> verifiedEdges = new HashMap<>();

    //added and removed edges; tracker to keep track of where edges are created and destroyed
    private Set<Location> addedEdgeLocationMarkers = new HashSet<>();
    private Set<Location> removedEdgeLocationMarkers = new HashSet<>();

    private HashMap<Block, MissionRoom> victimRooms = new HashMap<>();
    private HashMap<MissionGraph.MissionVertex, Set<Block>> roomVerticesWithVictims = new HashMap<>(), roomVerticesSavedVictims = new HashMap<>();

    public MissionGraph(Mission mission) {
        this.mission = mission;
    }

    public MissionGraph(Mission mission, JsonObject data, World world) {
        this.mission = mission;

        if(data == null) {
            return;
        }

        for(MissionRoom room : mission.getRooms()) {
            roomEntranceExitLocations.put(room.getRoomName(), new HashSet<>(room.getEntranceExitLocations()));
        }

        for(Map.Entry<String, JsonElement> entry : data.entrySet()) {
            String[] keySplit = entry.getKey().split(" ");
            MissionVertex vertex = getVertex(MissionVertexType.valueOf(keySplit[0]), keySplit[1]);

            edges.put(vertex, new HashSet<>());
            edgeWeights.put(vertex, new HashMap<>());
            edgePaths.put(vertex, new HashMap<>());

            for(Map.Entry<String, JsonElement> neighborEntry : entry.getValue().getAsJsonObject().entrySet()) {
                String[] neighborKeySplit = neighborEntry.getKey().split(" ");
                MissionVertex neighborVertex = getVertex(MissionVertexType.valueOf(neighborKeySplit[0]), neighborKeySplit[1]);

                edges.get(vertex).add(neighborVertex);

                JsonObject adjacencyData = neighborEntry.getValue().getAsJsonObject();

                edgeWeights.get(vertex).put(neighborVertex, adjacencyData.get("length").getAsDouble());

                LinkedList<Location> locs = new LinkedList<>();

                for(JsonElement loc : adjacencyData.get("path").getAsJsonArray()) {
                    String[] split = loc.getAsString().split(" ");
                    locs.add(new Location(world, Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2])));
                }

                edgePaths.get(vertex).put(neighborVertex, locs);
            }
        }
    }

    private MissionGraph() {

    }

    public MissionGraph clone() {
        MissionGraph graph = new MissionGraph();

        graph.mission = mission;

        graph.roomVertices = roomVertices;
        graph.decisionVertices = decisionVertices;

        for(String roomName : roomEntranceExitLocations.keySet()) {
            graph.roomEntranceExitLocations.put(roomName, new HashSet<>(roomEntranceExitLocations.get(roomName)));
        }

        for(Block block : victimRooms.keySet()) {
            graph.victimRooms.put(block, victimRooms.get(block));
        }

        for(MissionVertex roomVertex : roomVerticesWithVictims.keySet()) {
            roomVerticesWithVictims.put(roomVertex, new HashSet<>(roomVerticesWithVictims.get(roomVertex)));
        }

        for(MissionVertex roomVertex : roomVerticesSavedVictims.keySet()) {
            roomVerticesSavedVictims.put(roomVertex, new HashSet<>(roomVerticesSavedVictims.get(roomVertex)));
        }

        for(MissionVertex vertex : edges.keySet()) {
            graph.edges.put(vertex, new HashSet<>(edges.get(vertex)));
            graph.edgeWeights.put(vertex, new HashMap<>(edgeWeights.get(vertex)));
            graph.edgePaths.put(vertex, new HashMap<>(edgePaths.get(vertex)));
        }

        return graph;
    }

    public MissionGraph cloneGraphOnly() {
        MissionGraph graph = clone();

        graph.mission = null;

        return graph;
    }

    public JsonObject save() {
        JsonObject data = new JsonObject();

        for(MissionVertex edge : edges.keySet()) {
            JsonObject neighbors = new JsonObject();

            for(MissionVertex adjacency : edges.get(edge)) {
                JsonObject adjacencyData = new JsonObject();
                adjacencyData.addProperty("length", edgeWeights.get(edge).get(adjacency));
                JsonArray pointsArray = new JsonArray();
                for(Location loc : edgePaths.get(edge).get(adjacency)) {
                    pointsArray.add(loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
                }
                adjacencyData.add("path", pointsArray);

                neighbors.add(adjacency.type + " " + adjacency.name, adjacencyData);
            }

            data.add(edge.type + " " + edge.name, neighbors);
        }

        return data;
    }

    //get some location in this room
    private Location getRoomLocation(MissionRoom room) {
        //temporarily, let's just find the middle of the room; this implementation does not work if the map is multi-floored
        double averageX = (room.getBounds().startX + room.getBounds().endX) / 2d;
        double averageZ = (room.getBounds().startZ + room.getBounds().endZ) / 2d;
        Location middle = new Location(mission.getPlayerSpawnLocation().getWorld(), averageX, mission.getPlayerSpawnLocation().getY(), averageZ);
        return middle;
    }

    public Set<Location> getRoomEntranceExitLocations(MissionRoom room) {
        return roomEntranceExitLocations.get(room.getRoomName());
    }

    //if not found in database, then define it first
    private MissionVertex getVertex(MissionVertexType type, String name) {
        MissionVertex vertex = null;
        if(type == MissionVertexType.ROOM) {
            if(!roomVertices.containsKey(name)) {
                roomVertices.put(name, vertex = new MissionVertex(type, getRoomLocation(mission.getRoom(name)), name));
            } else {
                vertex = roomVertices.get(name);
            }
        } else if(type == MissionVertexType.DECISION) {
            if(!decisionVertices.containsKey(name)) {
                decisionVertices.put(name, vertex = new MissionVertex(type, mission.getDecisionPoints().get(name), name));
            } else {
                vertex = decisionVertices.get(name);
            }
        }

        return vertex;
    }

    private double getManhattanDistance(Location begin, Location end) {
        return Math.abs(begin.getBlockX() - end.getBlockX()) + Math.abs(begin.getBlockZ() - end.getBlockZ());
    }

    //this method will traverse A* style to find a path from the begin to end, and return the distance
    private LocationPath calculatePathBetweenNodes(MissionVertex begin, MissionVertex end) {
        //general logic: distance-biased four-way BFS-like algorithm that tries to find the closest path from begin to end, where if any are rooms, then we will end when the algorithm finds the bounds of the room

        //heuristics cost calculation
        HashMap<Location, Double> totalLocationCosts = new HashMap<>();
        HashMap<Location, Integer> numberOfBlocksFromBegin = new HashMap<>();

        //contains foot level locations that have an air above it
        PriorityQueue<Location> openList = new PriorityQueue<>(new Comparator<Location>() {
            @Override
            public int compare(Location o1, Location o2) {
                return Double.compare(totalLocationCosts.get(o1), totalLocationCosts.get(o2));
            }
        });

        Location endLocation = null;
        double distance = 0;

        //check for if begin is in between multiple blocks, then place it on a block
        Location beginLoc = begin.location.clone();
        if(beginLoc.getX() == (int) beginLoc.getX()) {
            if(beginLoc.getZ() == (int) beginLoc.getZ()) {
                beginLoc.add(0.5, 0, 0.5);
                distance += Math.sqrt(2 * Math.pow(0.5, 2));
            } else {
                beginLoc.add(0.5, 0, 0);
                distance += 0.5;
            }
        } else if(beginLoc.getZ() == (int) beginLoc.getZ()) {
            beginLoc.add(0, 0, 0.5);
            distance += 0.5;
        }

        HashMap<Location, Location> parents = new HashMap<>();

        openList.add(beginLoc);
        parents.put(beginLoc, null);
        totalLocationCosts.put(beginLoc, getManhattanDistance(beginLoc, end.location));
        numberOfBlocksFromBegin.put(beginLoc, 0);

        BlockRange2D endBounds = mission.getRoom(end.name).getBounds().clone();
        //don't count walls
        endBounds.expand(-1);

        //this algorithm will look for possible locations at same level, one up, and one down.
        //it will not account for possible stepping locations more than one block up or down.
        int timeout = 5000; //5000 is more than enough for the Sparky map, and most other general cases. For reference, Sparky doesn't traverse to less than 4000 between any two nodes, and typically only takes about 400-600 for far distances
        while(!openList.isEmpty()) {
            timeout--;
            if(timeout <= 0) {
                return null;
            }

            Location currentLoc = openList.poll();

            //check for end goal
            if(end.type == MissionVertexType.ROOM) {
                if(endBounds.isInRange(currentLoc)) {
                    distance += end.location.distance(currentLoc);
                    endLocation = currentLoc;
                    break;
                }
            } else {
                if(end.location.distanceSquared(currentLoc) <= 1) {
                    distance += end.location.distance(currentLoc);
                    endLocation = currentLoc;
                    break;
                }
            }

            for(int i = 0; i < 4; i++) {
                for(int dy = -1; dy <= 1; dy++) {
                    Location adjacentLoc = currentLoc.clone().add(ADJACENT_DIRECTIONS[i * 2], dy, ADJACENT_DIRECTIONS[i * 2 + 1]);

                    if(parents.containsKey(adjacentLoc)) {
                        continue;
                    }

                    if(!passableMaterials.contains(adjacentLoc.clone().add(0, -1, 0).getBlock().getType())) { //under foot level
                        if (passableMaterials.contains(adjacentLoc.getBlock().getType())) { //foot level
                            if (passableMaterials.contains(adjacentLoc.clone().add(0, 1, 0).getBlock().getType())) { //eye level
                                int adjacentBlocksFromStart = numberOfBlocksFromBegin.get(currentLoc) + 1;
                                numberOfBlocksFromBegin.put(adjacentLoc, adjacentBlocksFromStart);
                                totalLocationCosts.put(adjacentLoc, adjacentBlocksFromStart + getManhattanDistance(adjacentLoc, end.location));
                                openList.add(adjacentLoc);
                                parents.put(adjacentLoc, currentLoc);
                                break;
                            }
                        }
                    }
                }
            }
        }

        LinkedList<Location> path = new LinkedList<>();

        path.add(endLocation);

        Location nextLoc = endLocation;
        while((nextLoc = parents.get(nextLoc)) != null) {
            path.addFirst(nextLoc);
            distance++;
        }

        distance = Math.round(distance * 100) / 100d;

        return new LocationPath(path, distance);
    }

    public boolean doesEdgeExist(MissionVertexType vertexType1, String name1, MissionVertexType vertexType2, String name2) {
        MissionVertex vertex1 = getVertex(vertexType1, name1);
        MissionVertex vertex2 = getVertex(vertexType2, name2);
        return edges.containsKey(vertex1) && edges.get(vertex1).contains(vertex2);
    }

    //returns distance between two points
    public LocationPath defineEdge(MissionVertexType vertexType1, String name1, MissionVertexType vertexType2, String name2) {
        return defineEdge(getVertex(vertexType1, name1), getVertex(vertexType2, name2));
    }

    //uses A* world traversal algorithm
    public LocationPath defineEdge(MissionVertex begin, MissionVertex end) {
        if(!edges.containsKey(begin)) {
            edges.put(begin, new HashSet<>());
        }
        if(!edges.containsKey(end)) {
            edges.put(end, new HashSet<>());
        }

        edges.get(begin).add(end);
        edges.get(end).add(begin);

        //calculate weights
        if(!edgeWeights.containsKey(begin)) {
            edgeWeights.put(begin, new HashMap<>());
        }
        if(!edgeWeights.containsKey(end)) {
            edgeWeights.put(end, new HashMap<>());
        }

        LocationPath path = calculatePathBetweenNodes(begin, end);

        if(path == null) {
            return null;
        }

        edgeWeights.get(begin).put(end, path.pathLength);
        edgeWeights.get(end).put(begin, path.pathLength);

        if(!edgePaths.containsKey(begin)) {
            edgePaths.put(begin, new HashMap<>());
        }
        if(!edgePaths.containsKey(end)) {
            edgePaths.put(end, new HashMap<>());
        }

        edgePaths.get(begin).put(end, path.path);
        edgePaths.get(end).put(begin, path.path);

        //add an entrance where it was made
        if(begin.type == MissionVertexType.ROOM && end.type == MissionVertexType.ROOM) {
            Location openedLocation = null;

            BlockRange2D beginBounds = mission.getRoom(begin.name).getBounds();
            BlockRange2D endBounds = mission.getRoom(end.name).getBounds();

            for(Location loc : path.getPath()) {
                if(beginBounds.isInRange(loc)) {
                    if(endBounds.isInRange(loc)) {
                        openedLocation = loc;
                        break;
                    }
                } else {
                    openedLocation = loc;
                    break;
                }
            }

            if(openedLocation != null) {
                roomEntranceExitLocations.get(begin.name).add(openedLocation);
                roomEntranceExitLocations.get(end.name).add(openedLocation);
            }
        }

        return path;
    }

    //proper dijkstra's algorithm
    public HashMap<MissionVertex, VertexPath> getShortestPathToAllVertices(MissionVertexType beginVertexType, String beginVertexName) {
        MissionVertex begin = getVertex(beginVertexType, beginVertexName);

        HashMap<MissionVertex, Double> distances = new HashMap<>(); //distance from beginning to this vertex
        HashMap<MissionVertex, MissionVertex> parents = new HashMap<>();

        distances.put(begin, 0d);
        parents.put(begin, null);

        //TODO: for better performance, replace the PriorityQueue with a fibonacci heap
        PriorityQueue<MissionVertex> openList = new PriorityQueue<>(new Comparator<MissionVertex>() {
            @Override
            public int compare(MissionVertex o1, MissionVertex o2) {
                return Double.compare(distances.get(o1), distances.get(o2));
            }
        });

        for(MissionVertex vertex : edges.keySet()) {
            if(vertex.equals(begin)) {
                openList.add(vertex);
                continue;
            }

            distances.put(vertex, Double.MAX_VALUE);
            openList.add(vertex);
        }

        while(!openList.isEmpty()) {
            MissionVertex min = openList.poll();

            for(MissionVertex neighbor : getNeighbors(min)) {
                if(distances.get(neighbor) > distances.get(min) + edgeWeights.get(min).get(neighbor)) {
                    distances.put(neighbor, distances.get(min) + edgeWeights.get(min).get(neighbor));
                    parents.put(neighbor, min);

                    if(openList.contains(neighbor)) {
                        openList.remove(neighbor);
                        openList.add(neighbor);
                    }
                }
            }
        }

        HashMap<MissionVertex, VertexPath> paths = new HashMap<>();

        openList.addAll(edges.keySet());

        //sort approximately via distance to begin to save path calculations if it already exists
        while(!openList.isEmpty()) {
            MissionVertex min = openList.poll();

            if(min.equals(begin)) {
                continue;
            }

            //get parents
            LinkedList<MissionVertex> path = new LinkedList<>();

            MissionVertex current = min;
            while(current != null) {
                if(paths.containsKey(current)) {
                    path.addAll(0, paths.get(current).getPath());
                    break;
                }

                path.addFirst(current);
                current = parents.get(current);
            }

            paths.put(min, new VertexPath(path, distances.get(min)));
        }

        //print dijkstra's sizes
//        openList.addAll(edges.keySet());
//
//        while(!openList.isEmpty()) {
//            MissionVertex vertex = openList.poll();
//            if(vertex.equals(begin)) {
//                continue;
//            }
//
//            VertexPath path = getShortestPathUsingEdges(begin, vertex);
//
//            Bukkit.broadcastMessage("" + ChatColor.BOLD + begin + " TO " + vertex);
//            Bukkit.broadcastMessage(ChatColor.YELLOW + "COMPARE " + path.pathLength + " WITH DIJKSTRA'S " + paths.get(vertex).pathLength);
//            Bukkit.broadcastMessage(ChatColor.BLUE + paths.get(vertex).path.toString());
//        }

        return paths;
    }

    //this method will use the already defined edges to get a path. it will not traverse A* style to find a path
    public VertexPath getShortestPathUsingEdges(MissionVertexType vertexType1, String name1, MissionVertexType vertexType2, String name2) {
        MissionVertex begin = getVertex(vertexType1, name1);
        MissionVertex end = getVertex(vertexType2, name2);

        return getShortestPathUsingEdges(begin, end);
    }

    //dijkstra's like algorithm, but stops early when it finds the end
    private VertexPath getShortestPathUsingEdges(MissionVertex begin, MissionVertex end) {
        HashMap<MissionVertex, Double> distanceToStart = new HashMap<>();

        PriorityQueue<MissionVertex> openList = new PriorityQueue<>(new Comparator<MissionVertex>() {
            @Override
            public int compare(MissionVertex o1, MissionVertex o2) {
                return Double.compare(distanceToStart.get(o1), distanceToStart.get(o2));
            }
        });

        HashMap<MissionVertex, MissionVertex> parents = new HashMap<>();

        distanceToStart.put(begin, 0d);
        openList.add(begin);
        parents.put(begin, null);

        boolean foundEnd = false;

        while(!openList.isEmpty()) {
            MissionVertex currentVertex = openList.poll();

            if(currentVertex == end) {
                foundEnd = true;
                break;
            }

            if (edges.containsKey(currentVertex)) {
                for (MissionVertex adjacentVertex : edges.get(currentVertex)) {
                    double distance = distanceToStart.get(currentVertex) + edgeWeights.get(currentVertex).get(adjacentVertex);

                    if(!distanceToStart.containsKey(adjacentVertex)) {
                        distanceToStart.put(adjacentVertex, distance);
                        openList.add(adjacentVertex);

                        parents.put(adjacentVertex, currentVertex);
                    } else if(distance < distanceToStart.get(adjacentVertex)) {
                        distanceToStart.put(adjacentVertex, distance);

                        openList.remove(adjacentVertex);
                        openList.add(adjacentVertex);

                        parents.put(adjacentVertex, currentVertex);
                    }
                }
            }
        }

        if(!foundEnd) {
            return null;
        }

        LinkedList<MissionVertex> path = new LinkedList<>();
        double distance = 0;

        MissionVertex nextLoc = end;
        do {
            path.addFirst(nextLoc);

            if(parents.get(nextLoc) != null) {
                distance += edgeWeights.get(nextLoc).get(parents.get(nextLoc));
            }
        } while((nextLoc = parents.get(nextLoc)) != null);

        distance = Math.round(distance * 100) / 100d;

        return new VertexPath(path, distance);
    }

    /**
     * verify that the edge exists and is traversable. This checks a path from node a to b that was defined in the graphical representation
     * @param type1
     * @param name1
     * @param type2
     * @param name2
     * @param visibleBlocks
     * @return null if edge is traversable, the blocking location if there is something blocking the edge from letting the player go through
     */
    public Location verifyEdgeTraversable(MissionVertexType type1, String name1, MissionVertexType type2, String name2, Set<Block> visibleBlocks) {
        MissionVertex begin = getVertex(type1, name1);
        MissionVertex end = getVertex(type2, name2);

        if(verifiedEdges.containsKey(begin) && verifiedEdges.get(begin).contains(end)) {
            return null;
        }

        LinkedList<Location> edgePath = edgePaths.get(begin).get(end);

        for(Location loc : edgePath) {
            //this algorithm will check for air blocks above the location. this requires access to the world and is not a good way to do it separate from a plugin implementation
            //in the future: possible fix could be to track which blocks were seen that were AIR as well, then track those too

            //ground level and up blockage
            if(visibleBlocks.contains(loc.getBlock())) {
                Block relative = loc.getBlock().getRelative(0, 1, 0);
                if(visibleBlocks.contains(relative) && !passableMaterials.contains(relative.getType())) {
                    MissionRoom currentRoom = null;
                    for(MissionRoom room : mission.getRooms()) {
                        if(room.getBounds().isInRange(loc)) {
                            currentRoom = room;
                            break;
                        }
                    }

                    if(currentRoom == null) {
                        System.out.println("FAIL HALLWAY GROUND " + Utils.getFormattedLocation(loc) + " " + loc.getBlock().getType());
                        return loc;
                    } else {
                        //if within a room, be a little more lenient on the block checking, since there are multiple ways around a specific area
                        relative = loc.getBlock().getRelative(0, 2, 0);
                        if(visibleBlocks.contains(relative) && !passableMaterials.contains(relative.getType())) {
                            System.out.println("FAIL ROOM 2 above " + Utils.getFormattedLocation(loc.getBlock().getRelative(0, 2, 0).getLocation()) + " " + loc.getBlock().getRelative(0, 2, 0).getType());
                            return loc;
                        }
                    }
                }
            } else {
                //eye level blockage]
                Block relative = loc.getBlock().getRelative(0, 1, 0);
                if (visibleBlocks.contains(relative) && !passableMaterials.contains(relative.getType())) {
                    System.out.println("FAIL EYE LEVEL " + Utils.getFormattedLocation(loc) + " " + loc.getBlock().getType());
                    return loc;
                }
            }
        }

        return null;
    }

    /**
     * Prevents edge from being removed from a failed verification
     * @param type1
     * @param name1
     * @param type2
     * @param name2
     */
    public void protectEdge(MissionVertexType type1, String name1, MissionVertexType type2, String name2) {
        MissionVertex begin = getVertex(type1, name1);
        MissionVertex end = getVertex(type2, name2);

        verifiedEdges.putIfAbsent(begin, new HashSet<>());
        verifiedEdges.putIfAbsent(end, new HashSet<>());

        verifiedEdges.get(begin).add(end);
        verifiedEdges.get(end).add(begin);
    }

    public boolean isEdgeProtected(MissionVertexType type1, String name1, MissionVertexType type2, String name2) {
        MissionVertex begin = getVertex(type1, name1);
        MissionVertex end = getVertex(type2, name2);

        return verifiedEdges.containsKey(begin) && verifiedEdges.get(begin).contains(end);
    }

    /**
     * Deletes the edge both ways, if it exists
     * @param type1
     * @param name1
     * @param type2
     * @param name2
     */
    public void deleteEdge(MissionVertexType type1, String name1, MissionVertexType type2, String name2) {
        MissionVertex begin = getVertex(type1, name1);
        MissionVertex end = getVertex(type2, name2);

        edges.get(begin).remove(end);
        edges.get(end).remove(begin);

        edgeWeights.get(begin).remove(end);
        edgeWeights.get(end).remove(begin);

        edgePaths.get(begin).remove(end);
        edgePaths.get(end).remove(begin);
    }

    public Set<MissionVertex> getNeighbors(MissionVertex vertex) {
        return edges.getOrDefault(getVertex(vertex.type, vertex.name), null);
    }

    public boolean modifyEdgeWeight(MissionVertex begin, MissionVertex end, double weight) {
        if(mission != null) { //must be a copy of the mission graph; cannot modify original
            return false;
        }
        if(!edgeWeights.containsKey(begin)) {
            return false;
        }
        if(!edgeWeights.get(begin).containsKey(end)) {
            return false;
        }

        edgeWeights.get(begin).put(end, weight);
        return true;
    }

    public LinkedList<Location> getExactPathBetweenEdges(MissionVertexType type1, String name1, MissionVertexType type2, String name2) {
        MissionVertex begin = getVertex(type1, name1);
        MissionVertex end = getVertex(type2, name2);

        return edgePaths.get(begin).get(end);
    }

    public double getEdgeWeight(MissionVertex begin, MissionVertex end) {
        return edgeWeights.get(begin).get(end);
    }

    public Collection<MissionVertex> getRoomVertices() {
        return roomVertices.values();
    }

    public MissionVertex getRoomVertex(MissionRoom room) {
        return roomVertices.get(room.getRoomName());
    }

    public void defineVictimRoom(Block block, MissionRoom room) {
        victimRooms.put(block, room);
    }

    /**
     *
     * @param block
     * @return MissionRoom if it exists, or null otherwise
     */
    public MissionRoom getVictimRoom(Block block) {
        return victimRooms.containsKey(block) ? victimRooms.get(block) : null;
    }

    public HashMap<MissionVertex, Set<Block>> getRoomVerticesWithVictims() {
        return roomVerticesWithVictims;
    }

    public HashMap<MissionVertex, Set<Block>> getRoomVerticesSavedVictims() {
        return roomVerticesSavedVictims;
    }

    public void clearAllEdges() {
        edges.clear();
        edgeWeights.clear();
    }

    public Set<Location> getAddedEdgeLocationMarkers() {
        return addedEdgeLocationMarkers;
    }

    public Set<Location> getRemovedEdgeLocationMarkers() {
        return removedEdgeLocationMarkers;
    }
}
