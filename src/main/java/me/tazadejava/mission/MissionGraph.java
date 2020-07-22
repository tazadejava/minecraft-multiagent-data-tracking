package me.tazadejava.mission;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.tazadejava.blockranges.BlockRange2D;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;

//represents the graphical implementation of missions
public class MissionGraph {

    public enum MissionVertexType {
        ROOM, DECISION
    }

    public static class MissionVertex {

        public MissionVertexType type;
        public Location location;

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

    private Set<Material> transparentMaterials = new HashSet<>(Arrays.asList(Material.AIR, Material.OAK_DOOR, Material.OAK_WALL_SIGN, Material.OAK_SIGN));

    private Mission mission;

    private HashMap<String, MissionVertex> roomVertices = new HashMap<>();
    private HashMap<String, MissionVertex> decisionVertices = new HashMap<>();

    private HashMap<MissionVertex, Set<MissionVertex>> edges = new HashMap<>();
    private HashMap<MissionVertex, HashMap<MissionVertex, Double>> edgeWeights = new HashMap<>();

    public MissionGraph(Mission mission) {
        this.mission = mission;
    }

    public MissionGraph(Mission mission, JsonObject data) {
        this.mission = mission;

        for(Map.Entry<String, JsonElement> entry : data.entrySet()) {
            String[] keySplit = entry.getKey().split(" ");
            MissionVertex vertex = getVertex(MissionVertexType.valueOf(keySplit[0]), keySplit[1]);

            edges.put(vertex, new HashSet<>());
            edgeWeights.put(vertex, new HashMap<>());

            for(Map.Entry<String, JsonElement> neighborEntry : entry.getValue().getAsJsonObject().entrySet()) {
                String[] neighborKeySplit = neighborEntry.getKey().split(" ");
                MissionVertex neighborVertex = getVertex(MissionVertexType.valueOf(neighborKeySplit[0]), neighborKeySplit[1]);

                edges.get(vertex).add(neighborVertex);
                edgeWeights.get(vertex).put(neighborVertex, neighborEntry.getValue().getAsDouble());
            }
        }
    }

    private MissionGraph() {

    }

    public MissionGraph cloneGraphOnly() {
        MissionGraph graph = new MissionGraph();

        graph.roomVertices = roomVertices;
        graph.decisionVertices = decisionVertices;

        for(MissionVertex vertex : edges.keySet()) {
            graph.edges.put(vertex, new HashSet<>(edges.get(vertex)));
            graph.edgeWeights.put(vertex, new HashMap<>(edgeWeights.get(vertex)));
        }

        return graph;
    }

    public JsonObject save() {
        JsonObject data = new JsonObject();

        for(MissionVertex edge : edges.keySet()) {
            JsonObject neighbors = new JsonObject();

            for(MissionVertex adjacency : edges.get(edge)) {
                neighbors.addProperty(adjacency.type + " " + adjacency.name, edgeWeights.get(edge).get(adjacency));
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

                    if(!transparentMaterials.contains(adjacentLoc.clone().add(0, -1, 0).getBlock().getType())) { //under foot level
                        if (transparentMaterials.contains(adjacentLoc.getBlock().getType())) { //foot level
                            if (transparentMaterials.contains(adjacentLoc.clone().add(0, 1, 0).getBlock().getType())) { //eye level
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

    //unknown of complexity; ensure it is not worse than dijkstra's before continuing
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

    public double getEdgeWeight(MissionVertex begin, MissionVertex end) {
        return edgeWeights.get(begin).get(end);
    }

    public Collection<MissionVertex> getRoomVertices() {
        return roomVertices.values();
    }

    public void clearAllEdges() {
        edges.clear();
        edgeWeights.clear();
    }
}
