package me.tazadejava.mission;

import com.google.gson.JsonObject;
import me.tazadejava.blockranges.BlockRange2D;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;

//represents the graphical implementation of missions
public class MissionGraph {

    public enum MissionVertexType {
        ROOM, DECISION
    }

    public class MissionVertex {

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

        //TODO: LOAD EDGE DATA
    }

    public JsonObject save() {
        JsonObject data = new JsonObject();

        //TODO: SAVE THE EDGE DATA

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

    //this method will traverse A* style to find a path from the begin to end, and return the distance
    private LocationPath calculatePathBetweenNodes(MissionVertex begin, MissionVertex end) {
        //TODO: implement a A* inspired algorithm to find path to end or a room bounds
        //general logic: distance-biased four-way BFS-like algorithm that tries to find the closest path from begin to end, where if any are rooms, then we will end when the algorithm finds the bounds of the room

        //contains foot level locations that have an air above it
        PriorityQueue<Location> openList = new PriorityQueue<>(new Comparator<Location>() {
            @Override
            public int compare(Location o1, Location o2) {
                return Double.compare(o1.distanceSquared(end.location), o2.distanceSquared(end.location));
            }
        });

        openList.add(begin.location);

        HashMap<Location, Location> parents = new HashMap<>();
        parents.put(begin.location, null);

        Location endLocation = null;
        double distance = 0;

        BlockRange2D endBounds = mission.getRoom(end.name).getBounds();

        //this algorithm will look for possible locations at same level, one up, and one down.
        //it will not account for possible stepping locations more than one block up or down.
        int timeout = 500;
        while(!openList.isEmpty()) {
            timeout--;
            if(timeout <= 0) {
                return null;
            }

            Location currentLoc = openList.poll();

            //check for end goal
            if(end.type == MissionVertexType.ROOM) {
                if(endBounds.isInRange(currentLoc)) {
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

        return new LocationPath(path, distance);
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

        edgeWeights.get(begin).put(end, path.pathLength);
        edgeWeights.get(end).put(begin, path.pathLength);

        return path;
    }

    //this method will use the already defined edges to get a path. it will not traverse A* style to find a path
    public VertexPath getShortestPathUsingEdges(MissionVertexType vertexType1, String name1, MissionVertexType vertexType2, String name2) {
        MissionVertex begin = getVertex(vertexType1, name1);
        MissionVertex end = getVertex(vertexType2, name2);
        //TODO: IMPLEMENT DIJKSTRA'S ALGORITHM
        return null;
    }
}
