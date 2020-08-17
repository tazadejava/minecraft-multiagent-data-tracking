package me.tazadejava.map;

import com.google.gson.*;
import me.tazadejava.blockranges.BlockRange2D;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

//goal: creates a graph representation of a file based on a CSV file

/**
 * Creates graphical representations used by the recommendation best path system from CSV files. Independent of any other class. Can test via the static main method in the class for effectiveness, viewing the logs for accuracy. Requires an accurate top-left coordinate XYZ of the mission in the real world to work. Assumes Y coordinate does not change.
 *
 * Usage within the mission environment is streamlined through the MissionCommandHandler, under the /mission import command.
 */
public class GraphGenerator {

    public static class PointPath {

        private LinkedList<Point> path;
        private double pathLength;

        public PointPath(LinkedList<Point> path, double pathLength) {
            this.path = path;
            this.pathLength = pathLength;
        }

        public LinkedList<Point> getPath() {
            return path;
        }

        public double getPathLength() {
            return pathLength;
        }
    }

    public static class DecisionPoint {

        private double row, col;

        public HashMap<BlockRange2D, PointPath> connectedRooms = new HashMap<>();
        public HashMap<DecisionPoint, PointPath> connectedDecisionPoints = new HashMap<>();

        public DecisionPoint(double x, double z) {
            this.row = x;
            this.col = z;
        }

        public boolean inBounds(String[][] mapping) {
            if(row < 0 || col < 0) {
                return false;
            }
            if(row >= mapping.length || col >= mapping[(int) row].length) {
                return false;
            }

            return true;
        }

        public double distance(DecisionPoint other) {
            return Math.sqrt(Math.pow(row - other.row, 2) + Math.pow(col - other.col, 2));
        }

        public Point toPoint() {
            return new Point(getRow(), getCol());
        }

        public int getRow() {
            return (int) row;
        }

        public int getCol() {
            return (int) col;
        }

        public String get(String[][] mapping) {
            return mapping[(int) row][(int) col];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DecisionPoint point = (DecisionPoint) o;
            return row == point.row &&
                    col == point.col;
        }

        @Override
        public int hashCode() {
            return Objects.hash(row, col);
        }

        @Override
        public String toString() {
            return "(" + row + ", " + col + ")";
        }
    }

    public static class Point {
        private int row, col;

        public Point(int row, int col) {
            this.row = row;
            this.col = col;
        }

        public int[] getDelta(Point point) {
            return new int[] {row > point.row ? -1 : (row < point.row ? 1 : 0), col > point.col ? -1 : (col < point.col ? 1 : 0)};
        }

        public Point inDirection(int[] delta) {
            if(delta.length < 2) {
                return null;
            }

            return new Point(row + delta[0], col + delta[1]);
        }

        public boolean inBounds(String[][] mapping) {
            if(row < 0 || col < 0) {
                return false;
            }
            if(row >= mapping.length || col >= mapping[row].length) {
                return false;
            }

            return true;
        }

        public String get(String[][] mapping) {
            return mapping[row][col];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Point point = (Point) o;
            return row == point.row &&
                    col == point.col;
        }

        @Override
        public int hashCode() {
            return Objects.hash(row, col);
        }

        @Override
        public String toString() {
            return "(" + row + ", " + col + ")";
        }
    }

    public static class EnclosedSpace {

        public Set<Point> enclosedSpace;
        public int adjacentDoorCount;

        public EnclosedSpace(Set<Point> enclosedSpace, int adjacentDoorCount) {
            this.enclosedSpace = enclosedSpace;
            this.adjacentDoorCount = adjacentDoorCount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EnclosedSpace that = (EnclosedSpace) o;
            return adjacentDoorCount == that.adjacentDoorCount &&
                    Objects.equals(enclosedSpace, that.enclosedSpace);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enclosedSpace, adjacentDoorCount);
        }
    }

    private static final int[] DIRECTION_DELTAS = {
            0, 1,
            0, -1,
            1, 0,
            -1, 0
    };

    public static void main(String[] args) {
//        File file = new File("/home/yoshi/Documents/GenesisUROP/OriginalMaps/sparky.csv");
//        generateGraphFromCSV(file, new File("/home/yoshi/Documents/GenesisUROP/test/"), -2153, 52, 153);

        File file = new File("/home/yoshi/Documents/GenesisUROP/OriginalMaps/falcon.csv");
        generateGraphFromCSV(file, new File("/home/yoshi/Documents/GenesisUROP/test/"), -2108, 60, 144);
    }

    //assumes CSV file
    //assumes:
    //  D - door
    //  S - spawn point; assuming it is in a hallway
    //  none - air, walking space
    //room definition:
    //  space with at least 2x2 space
    //  surrounded by either door or a 1x1 area to get out
    //needs the start X and Z from top right to correctly place in real world

    /**
     * Generates a graph from a CSV file, and pastes the UUID folder in the outputDirectory
     * @param inputFile
     * @param outputDirectory
     * @param startX
     * @param y
     * @param startZ
     * @return The ID that was generated for this data
     */
    public static String generateGraphFromCSV(File inputFile, File outputDirectory, int startX, int y, int startZ) {
        //adjust for manual border
        startX -= 1;
        startZ -= 1;

        List<String[]> lines = new ArrayList<>();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(inputFile));

            String read;
            while((read = reader.readLine()) != null) {
                List<String> line = new ArrayList<>();
                int startIndex = 0;
                int currentIndex = 0;
                for(char val : read.toCharArray()) {
                    if(val == ',') {
                        line.add(read.substring(startIndex, currentIndex));
                        startIndex = currentIndex + 1;
                    }
                    currentIndex++;
                }

                if(startIndex <= read.length()) {
                    line.add(read.substring(startIndex, currentIndex));
                }

                //put border around left and right
                line.add(0, "B");
                line.add("B");

                lines.add(line.toArray(new String[0]));
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //put border around the top and bottom
        lines.add(0, createRow(lines.get(0).length, "B"));
        lines.add(createRow(lines.get(0).length, "B"));

        String[][] mapping = lines.toArray(new String[0][0]);

        formatPrint(mapping);

        System.out.println("Merging adjacent doors to simplify calculations...");

        int mergedDoorsCount = 0;

        for(int x = 0; x < mapping.length; x++) {
            for(int z = 0; z < mapping[x].length; z++) {
                if(mapping[x][z].equals("D")) {
                    //check adjacent for D. if so, change to a wall
                    for(int i = 0; i < 4; i++) {
                        if(mapping[x + DIRECTION_DELTAS[i * 2]][z + DIRECTION_DELTAS[i * 2 + 1]].equals("D")) {
                            mergedDoorsCount++;
                            mapping[x + DIRECTION_DELTAS[i * 2]][z + DIRECTION_DELTAS[i * 2 + 1]] = "W";
                        }
                    }
                }
            }
        }

        if(mergedDoorsCount > 0) {
            System.out.println("MERGED " + mergedDoorsCount + " DOORS.");
            formatPrint(mapping);
        }

        //first, find all enclosed spaces

        Set<Point> traversedPoints = new HashSet<>(); //all empty areas
        List<Set<Point>> enclosedSpaces = new ArrayList<>();
        List<Integer> adjacentDoors = new ArrayList<>();

        for(int x = 0; x < mapping.length; x++) {
            for(int z = 0; z < mapping[x].length; z++) {
                Point point = new Point(x, z);

                if(!point.get(mapping).isEmpty()) {
                    continue;
                }

                if(!traversedPoints.contains(point)) {
                    EnclosedSpace space = getEnclosedSpace(point, mapping);

                    enclosedSpaces.add(space.enclosedSpace);
                    traversedPoints.addAll(space.enclosedSpace);
                    adjacentDoors.add(space.adjacentDoorCount);
                }
            }
        }

        //next, classify rooms and hallways

        /*
        room definition: not a hallway, bounded rectangularly

        hallway definition: number of doors > 4

         */

        Set<Point> hallwayPoints = null;
        int doors = 0;

        for(int i = 0; i < enclosedSpaces.size(); i++) {
            if(adjacentDoors.get(i) > doors) {
                doors = adjacentDoors.get(i);
                hallwayPoints = enclosedSpaces.get(i);
            }
        }

        //if no hallway found, then this algorithm needs to be revised
        if(hallwayPoints == null) {
            return null;
        }

        //based on this definition, then hallways with 1x1 pathways anywhere are separate and should be split into rooms

        System.out.println("Now checking for rooms without doors (narrow pathways)!");

        Set<Point> roomSeparationPoints = new HashSet<>();

        for(Point point : hallwayPoints) {
            int nonAirCounter = 0;

            for(int i = 0; i < 4; i++) {
                Point adjacent = new Point(point.row + DIRECTION_DELTAS[i * 2], point.col + DIRECTION_DELTAS[i * 2 + 1]);

                if(adjacent.inBounds(mapping)) {
                    if (!adjacent.get(mapping).isEmpty()) {
                        nonAirCounter++;
                    }
                }
            }

            if(nonAirCounter == 2) {
                roomSeparationPoints.add(point);
            }
        }

        System.out.println(roomSeparationPoints);

        //if exists a connection of 3 adjacent points or more in one direction, then it is a separate room

        for(Point separationPoint : roomSeparationPoints) {
            int adjacentSeparateRoomPointsNorthSouth = 0;
            int adjacentSeparateRoomPointsEastWest = 0;
            for(int i = 0; i < 4; i++) {
                Point adjacent = new Point(separationPoint.row + DIRECTION_DELTAS[i * 2], separationPoint.col + DIRECTION_DELTAS[i * 2 + 1]);

                if(adjacent.inBounds(mapping)) {
                    if(roomSeparationPoints.contains(adjacent)) {
                        if(i < 2) {
                            adjacentSeparateRoomPointsNorthSouth++;
                        } else {
                            adjacentSeparateRoomPointsEastWest++;
                        }
                    }
                }
            }

            //we found a separate room
            if(adjacentSeparateRoomPointsNorthSouth >= 2 || adjacentSeparateRoomPointsEastWest >= 2) {
                //find out which side is the room and which is the hallway
                Stack<Point> openList = new Stack<>();
                Set<Point> emptyTiles = new HashSet<>();
                HashMap<Point, Point> emptyTileParents = new HashMap<>();
                Set<Point> visited = new HashSet<>();

                openList.add(separationPoint);
                //visited is the separation points (points in the narrow hallway)
                visited.add(separationPoint);

                while(!openList.isEmpty()) {
                    Point currentPoint = openList.pop();
                    for(int i = 0; i < 4; i++) {
                        Point adjacent = new Point(currentPoint.row + DIRECTION_DELTAS[i * 2], currentPoint.col + DIRECTION_DELTAS[i * 2 + 1]);

                        if(!adjacent.inBounds(mapping)) {
                            continue;
                        }
                        if(visited.contains(adjacent)) {
                            continue;
                        }

                        if(adjacent.get(mapping).isEmpty() && !roomSeparationPoints.contains(adjacent)) {
                            emptyTiles.add(adjacent);
                            emptyTileParents.put(adjacent, currentPoint);
                        } else {
                            if (roomSeparationPoints.contains(adjacent)) {
                                openList.add(adjacent);
                                visited.add(adjacent);
                            }
                        }
                    }
                }

                for(Point emptyTile : emptyTiles) {
                    EnclosedSpace space = getEnclosedSpace(emptyTile, mapping, visited);

                    //is the room
                    if(space.adjacentDoorCount <= 4) {
                        space.enclosedSpace.addAll(visited); //add to hallway instead of room to make room bounds better
//                        hallwayPoints.addAll(visited);

                        hallwayPoints.removeAll(space.enclosedSpace);
                        enclosedSpaces.add(space.enclosedSpace);
                        adjacentDoors.add(space.adjacentDoorCount);
                    } else {
//                        Point parent = emptyTileParents.get(emptyTile);

                        //to simplify decision point creation, place a fake door at this point!
//                        mapping[parent.row][parent.col] = "D";
                        mapping[emptyTile.row][emptyTile.col] = "D";
                    }
                }
            }
        }

        //next, rooms with less than 4 tiles should be merged with the adjacent room
        //rooms that are significantly smaller than the adjacent room will be merged

        System.out.println("Now checking undersized rooms and merging small adjacent rooms!");

        int index = 0;
        Iterator<Set<Point>> enclosedSpaceIterator = enclosedSpaces.iterator();
        while(enclosedSpaceIterator.hasNext()) {
            Set<Point> enclosedSpace = enclosedSpaceIterator.next();
            if(enclosedSpace.size() < 4) { //if it is a small room, merge
                for(Point point : enclosedSpace) {
                    for(int i = 0; i < 4; i++) {
                        Point adjacent = new Point(point.row + DIRECTION_DELTAS[i * 2], point.col + DIRECTION_DELTAS[i * 2 + 1]);

                        if(adjacent.get(mapping).equals("D")) {
                            Point searchPoint = new Point(adjacent.row + DIRECTION_DELTAS[i * 2], adjacent.col + DIRECTION_DELTAS[i * 2 + 1]);
                            for(Set<Point> adjacentEnclosedSpace : enclosedSpaces) {
                                if(adjacentEnclosedSpace.contains(searchPoint)) {
                                    adjacentEnclosedSpace.addAll(enclosedSpace);
                                    adjacentEnclosedSpace.add(adjacent);
                                    enclosedSpaceIterator.remove();
                                    adjacentDoors.remove(index);
                                    break;
                                }
                            }
                        }
                    }
                }
            } else if (enclosedSpace.size() <= 16) { //if the room is smaller than the adjacent room by a lot, merge
                //this may or may not be the best heuristic to determining this!
                smallEnclosedSpace:
                for(Point point : enclosedSpace) {
                    for(int i = 0; i < 4; i++) {
                        Point adjacent = new Point(point.row + DIRECTION_DELTAS[i * 2], point.col + DIRECTION_DELTAS[i * 2 + 1]);

                        if(adjacent.get(mapping).equals("D")) {
                            Point adjacentAdjacent = adjacent.inDirection(new int[] {DIRECTION_DELTAS[i * 2], DIRECTION_DELTAS[i * 2 + 1]});
                            if(traversedPoints.contains(adjacentAdjacent)) {
                                if(!hallwayPoints.contains(adjacentAdjacent)) {
                                    for(Set<Point> adjacentSpace : enclosedSpaces) {
                                        if(adjacentSpace.contains(adjacentAdjacent)) {
                                            adjacentSpace.addAll(enclosedSpace);
                                            adjacentSpace.add(adjacent);

                                            enclosedSpaceIterator.remove();
                                            adjacentDoors.remove(index);
                                            break smallEnclosedSpace;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            index++;
        }

        System.out.println("Now determining room bounds!");

        //remove the hallway from the enclosed spaces to coordinate the indices of the enclosed spaces with the room ranges
        enclosedSpaces.remove(hallwayPoints);

        List<BlockRange2D> roomRanges = new ArrayList<>();

        for(Set<Point> points : enclosedSpaces) {
            roomRanges.add(getBoundary(points));
        }

        System.out.println("Now, checking for non-rectangular rooms and separating into separate rooms...");
        //TODO: this can be revised by storing multiple bounds instead of one in the future.

        //process:
        /*

        - if not all 4 corners are in the room, then something is wrong

        - ***this only works for rooms that are in an L shape of some form

        - if an L is detected, split into two rooms
         */

        HashMap<BlockRange2D, Set<BlockRange2D>> connectedRooms = new HashMap<>();

        HashMap<EnclosedSpace, EnclosedSpace> replacementRooms = new HashMap<>();

        index = 0;
        Iterator<BlockRange2D> rangeIterator = roomRanges.iterator();
        while(rangeIterator.hasNext()) {
            BlockRange2D range = rangeIterator.next();
            //points that may not be in the room
            List<Point> suspiciousPoints = new ArrayList<>();
            Set<Point> enclosedSpace = enclosedSpaces.get(index);

            Point[] cornerPoints = new Point[] {new Point(range.startX, range.startZ), new Point(range.startX, range.endZ),
                    new Point(range.endX, range.startZ), new Point(range.endX, range.endZ)};

            for(Point point : cornerPoints) {
                if(!enclosedSpace.contains(point)) {
                    suspiciousPoints.add(point);
                }
            }

            if(!suspiciousPoints.isEmpty()) {
                for(Point suspiciousPoint : suspiciousPoints) {
                    //not in the room, since we established it was not in the room's enclosed space
                    if(traversedPoints.contains(suspiciousPoint)) {
                        //we found that this room is an L, so we need to split up the room into two sections
                        /*
                        Process:
                        - start from the top row, go down (X is bounded by roomBounds) until encounter walls, continue until row after is less rows than the one before by biggest amount
                            - is an estimation of where the corner is
                        - split above is one room, below is another room
                         */

                        int maxWallRow = range.startX;
                        int maxWallDifference = 0;

                        int previousWallCount = -1;

                        for(int x = range.startX; x <= range.endX; x++) {//x is row
                            int nonAirCount = 0;
                            for(int z = range.startZ; z <= range.endZ; z++) {
                                if(!mapping[x][z].isEmpty()) {
                                    nonAirCount++;
                                }
                            }

                            if(previousWallCount != -1) {
                                int wallDifference = previousWallCount - nonAirCount;

                                if (wallDifference > maxWallDifference) {
                                    maxWallDifference = wallDifference;
                                    maxWallRow = x - 1;
                                }
                            }

                            previousWallCount = nonAirCount;
                        }

                        System.out.println("CUT AT " + maxWallRow);

                        //recalculate enclosed spaces for both sides

                        Set<Point> topRoomBottomBoundaries = new HashSet<>();

                        for(int z = range.startZ; z <= range.endZ; z++) {
                            topRoomBottomBoundaries.add(new Point(maxWallRow, z));
                        }

                        //get a point above this row
                        Point topPoint = null;
                        Point bottomPoint = null;
                        for(Point point : enclosedSpace) {
                            if(point.row < maxWallRow) {
                                topPoint = point;
                            }
                            if(point.row > maxWallRow) {
                                bottomPoint = point;
                            }
                        }

                        if(topPoint == null || bottomPoint == null) {
                            System.out.println("ERROR. SOMETHING WENT WRONG WITH FINDING THE TOP OF THE L ROOM.");
                            break;
                        }

                        EnclosedSpace topSpace = getEnclosedSpace(topPoint, mapping, topRoomBottomBoundaries);
                        EnclosedSpace bottomSpace = getEnclosedSpace(bottomPoint, mapping, topSpace.enclosedSpace);

                        rangeIterator.remove();

                        replacementRooms.put(topSpace, bottomSpace);

                        break;
                    }
                }
            }

            index++;
        }

        if(!replacementRooms.isEmpty()) {
            for(EnclosedSpace space : replacementRooms.keySet()) {
                EnclosedSpace complementarySpace = replacementRooms.get(space);

                BlockRange2D topRoom = getBoundary(space.enclosedSpace);
                BlockRange2D bottomRoom = getBoundary(complementarySpace.enclosedSpace);

                roomRanges.add(topRoom);
                roomRanges.add(bottomRoom);
                adjacentDoors.add(space.adjacentDoorCount);
                adjacentDoors.add(complementarySpace.adjacentDoorCount);

                connectedRooms.putIfAbsent(topRoom, new HashSet<>());
                connectedRooms.putIfAbsent(bottomRoom, new HashSet<>());

                connectedRooms.get(topRoom).add(bottomRoom);
                connectedRooms.get(bottomRoom).add(topRoom);
            }
        }

        formatPrint(mapping);
        specialFormatPrint(mapping, roomRanges, new ArrayList<>());

        System.out.println("Now determining initial decision points!");

        //decision point algorithm:
        //doors will always have a decision point if pointing to the hallway, in the middle of the hallway
        //decision points that are 1-2 blocks away from each other will be merged to the average location

        List<DecisionPoint> decisionPoints = new ArrayList<>();

        for(BlockRange2D roomRange : roomRanges) {
            //loop through perimeter of room, looking for doors to append decision points to
            for(int row = roomRange.startX; row <= roomRange.endX; row++) {
                //left
                Point roomPoint = new Point(row, roomRange.startZ);
                Point doorPoint = new Point(row, roomRange.startZ - 1);
                addPotentialDecisionPoint(decisionPoints, roomRange, roomPoint, doorPoint, hallwayPoints, mapping);
                addPotentialRoomToRoomEdge(roomRanges, roomRange, roomPoint, doorPoint, mapping, connectedRooms);

                //right
                roomPoint = new Point(row, roomRange.endZ);
                doorPoint = new Point(row, roomRange.endZ + 1);
                addPotentialDecisionPoint(decisionPoints, roomRange, roomPoint, doorPoint, hallwayPoints, mapping);
                addPotentialRoomToRoomEdge(roomRanges, roomRange, roomPoint, doorPoint, mapping, connectedRooms);
            }

            for(int col = roomRange.startZ; col <= roomRange.endZ; col++) {
                //up
                Point roomPoint = new Point(roomRange.startX, col);
                Point doorPoint = new Point(roomRange.startX - 1, col);
                addPotentialDecisionPoint(decisionPoints, roomRange, roomPoint, doorPoint, hallwayPoints, mapping);
                addPotentialRoomToRoomEdge(roomRanges, roomRange, roomPoint, doorPoint, mapping, connectedRooms);

                //down
                roomPoint = new Point(roomRange.endX, col);
                doorPoint = new Point(roomRange.endX + 1, col);
                addPotentialDecisionPoint(decisionPoints, roomRange, roomPoint, doorPoint, hallwayPoints, mapping);
                addPotentialRoomToRoomEdge(roomRanges, roomRange, roomPoint, doorPoint, mapping, connectedRooms);
            }
        }

        System.out.println("Connecting all decision points to each other with shortest paths and creating extra points when needed!");

        //first, expand line segments for all decision points to find collisions between the x and z axes

        Set<DecisionPoint> rowExpansionPoints = new HashSet<>();
        Set<DecisionPoint> colExpansionPoints = new HashSet<>();
        Set<DecisionPoint> collisionPoints = new HashSet<>();

        for(DecisionPoint decisionPoint : decisionPoints) {
            //expand row (left to right)
            expandDecisionPointLineInDirection(colExpansionPoints, rowExpansionPoints, collisionPoints, decisionPoint, 0, -1, mapping, decisionPoints, false);
            expandDecisionPointLineInDirection(colExpansionPoints, rowExpansionPoints, collisionPoints, decisionPoint, 0, 1, mapping, decisionPoints, false);
            //expand col
            expandDecisionPointLineInDirection(rowExpansionPoints, colExpansionPoints, collisionPoints, decisionPoint, -1, 0, mapping, decisionPoints, false);
            expandDecisionPointLineInDirection(rowExpansionPoints, colExpansionPoints, collisionPoints, decisionPoint, 1, 0, mapping, decisionPoints, false);
        }
        System.out.println("    Determining new decision points to be made...");

        if(!collisionPoints.isEmpty()) {
            System.out.println("    Adding " + collisionPoints.size() + " new decision points!");
            decisionPoints.addAll(collisionPoints);
        }

        System.out.println("    Merging close decision points!");

        //merge decision points up to 3 blocks away

        HashMap<DecisionPoint, DecisionPoint> mergePoints = new HashMap<>();

        for(DecisionPoint decisionPoint : decisionPoints) {
            for(DecisionPoint comparePoint : decisionPoints) {
                if(decisionPoint == comparePoint) {
                    continue;
                }
                double distance = decisionPoint.distance(comparePoint);

                if(distance < 3) {//limitation: multiple merges will only take the last merge
                    mergePoints.put(decisionPoint, comparePoint);
                }
            }
        }

        for(DecisionPoint mergePoint : mergePoints.keySet()) {
            DecisionPoint otherPoint = mergePoints.get(mergePoint);
            if(!decisionPoints.contains(otherPoint)) {
                continue;
            }

            System.out.println("Merging decision points " + decisionPoints.indexOf(mergePoint) + " and " + decisionPoints.indexOf(otherPoint));

            decisionPoints.remove(mergePoint);
            decisionPoints.remove(otherPoint);

            DecisionPoint newPoint = new DecisionPoint((mergePoint.row + otherPoint.row) / 2d, (mergePoint.col + otherPoint.col) / 2d);

            newPoint.connectedRooms.putAll(mergePoint.connectedRooms);
            newPoint.connectedRooms.putAll(otherPoint.connectedRooms);

            newPoint.connectedDecisionPoints.putAll(mergePoint.connectedDecisionPoints);
            newPoint.connectedDecisionPoints.putAll(otherPoint.connectedDecisionPoints);

            decisionPoints.add(newPoint);
        }

        //debug via input
        debugPrintConnections(decisionPoints, roomRanges, null);

        System.out.println("    Now, connecting all decision points!");

        HashMap<DecisionPoint, DecisionPoint[]> adjacentDecisionPoints = new HashMap<>();

        for(DecisionPoint point : decisionPoints) {
            adjacentDecisionPoints.put(point, new DecisionPoint[4]);
        }

        for(DecisionPoint decisionPoint : decisionPoints) {
            //expand row (left to right)
            expandDecisionPointLineInDirection(colExpansionPoints, rowExpansionPoints, collisionPoints, decisionPoint, 0, -1, mapping, decisionPoints, true, adjacentDecisionPoints);
            expandDecisionPointLineInDirection(colExpansionPoints, rowExpansionPoints, collisionPoints, decisionPoint, 0, 1, mapping, decisionPoints, true, adjacentDecisionPoints);
            //expand col
            expandDecisionPointLineInDirection(rowExpansionPoints, colExpansionPoints, collisionPoints, decisionPoint, -1, 0, mapping, decisionPoints, true, adjacentDecisionPoints);
            expandDecisionPointLineInDirection(rowExpansionPoints, colExpansionPoints, collisionPoints, decisionPoint, 1, 0, mapping, decisionPoints, true, adjacentDecisionPoints);
        }

        String missionID = UUID.randomUUID().toString() + "-" + LocalDateTime.now().toString();
        missionID = missionID.replaceAll("[^a-zA-Z0-9]", "");

        System.out.println("Creating a new graph folder: ID generated: " + missionID);

        File folder = new File(outputDirectory + "/" + missionID + "/");
        folder.mkdir();

        System.out.println("Creating rooms.json...");
        System.out.println("Creating decisionGraph.json...");

        try {
            File rooms = new File(folder.getAbsolutePath() + "/rooms.json");
            File decisionGraph = new File(folder.getAbsolutePath() + "/decisionGraph.json");

            rooms.createNewFile();
            decisionGraph.createNewFile();

            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            //create rooms json

            JsonObject roomsData = new JsonObject();

            HashMap<BlockRange2D, JsonObject> roomGraphData = new HashMap<>();

            //store boundaries of room

            int roomIndex = 0;
            for(BlockRange2D room : roomRanges) {
                roomGraphData.put(room, new JsonObject());

                JsonObject roomSpecificData = new JsonObject();

                JsonObject boundsData = new JsonObject();

                //expand to encapsulate borders
                room.expand(1);

                //convert from row/col to x/z
                boundsData.addProperty("startX", room.startZ + startX);
                boundsData.addProperty("startZ", room.startX + startZ);
                boundsData.addProperty("endX", room.endZ + startX);
                boundsData.addProperty("endZ", room.endX + startZ);

                roomSpecificData.add("bounds", boundsData);

                JsonArray entranceExitArray = new JsonArray();

                for(int x = room.startX; x <= room.endX; x++) {
                    for(int z = room.startZ; z <= room.endZ; z++) {
                        if(mapping[x][z].equals("D")) {
                            entranceExitArray.add((z + startX) + " " + y + " " + (x + startZ));
                        }
                    }
                }

                roomSpecificData.add("entranceExitLocations", entranceExitArray);

                roomsData.add(String.valueOf(roomIndex), roomSpecificData);

                roomIndex++;
            }

            FileWriter writer = new FileWriter(rooms);
            gson.toJson(roomsData, writer);
            writer.close();

            //create decision graphs json

            JsonObject decisionData = new JsonObject();

            JsonObject decisionPointsList = new JsonObject();
            JsonObject graphData = new JsonObject();

            int decisionIndex = 0;
            for(DecisionPoint point : decisionPoints) {
                decisionPointsList.addProperty(String.valueOf(decisionIndex), decisionPointToLocation(point, startX, y, startZ));

                JsonObject decisionNodeData = new JsonObject();

                //add the decision paths
                for(DecisionPoint adjacent : point.connectedDecisionPoints.keySet()) {
                    PointPath path = point.connectedDecisionPoints.get(adjacent);

                    JsonObject pathData = new JsonObject();

                    pathData.addProperty("length", path.getPathLength());

                    JsonArray pathCoordinates = new JsonArray();

                    for(Point pathPoint : path.path) {
                        pathCoordinates.add(pointToLocation(pathPoint, startX, y, startZ));
                    }

                    pathData.add("path", pathCoordinates);

                    decisionNodeData.add("DECISION " + decisionPoints.indexOf(adjacent), pathData);
                }

                //add rooms both ways, since rooms do not store adjacent data
                for(BlockRange2D adjacent : point.connectedRooms.keySet()) {
                    PointPath path = point.connectedRooms.get(adjacent);

                    JsonObject pathData = pathToJsonObject(path, startX, y, startZ);

                    decisionNodeData.add("ROOM " + roomRanges.indexOf(adjacent), pathData);
                    roomGraphData.get(adjacent).add("DECISION " + decisionPoints.indexOf(point), pathData);
                }

                graphData.add("DECISION " + decisionIndex, decisionNodeData);

                decisionIndex++;
            }

            //create the room edges between themselves
            for(BlockRange2D room : connectedRooms.keySet()) {
                for(BlockRange2D connectedRoom : connectedRooms.get(room)) {
                    JsonObject pathData = pathToJsonObject(calculatePathBetweenNodes(mapping,
                            new Point((room.getRangeX()[1] + room.getRangeX()[0]) / 2, (room.getRangeZ()[1] + room.getRangeZ()[0]) / 2),
                            new Point((connectedRoom.getRangeX()[1] + connectedRoom.getRangeX()[0]) / 2, (connectedRoom.getRangeZ()[1] + connectedRoom.getRangeZ()[0]) / 2)), startX, y, startZ);

                    roomGraphData.get(room).add("ROOM " + roomRanges.indexOf(connectedRoom), pathData);
                }
            }

            for(BlockRange2D roomData : roomGraphData.keySet()) {
                graphData.add("ROOM " + roomRanges.indexOf(roomData), roomGraphData.get(roomData));
            }

            decisionData.add("decisionPoints", decisionPointsList);
            decisionData.add("graphData", graphData);

            writer = new FileWriter(decisionGraph);
            gson.toJson(decisionData, writer);
            writer.close();

            System.out.println("Done!");

            specialFormatPrint(mapping, roomRanges, decisionPoints);

            //debug via input
            debugPrintConnections(decisionPoints, roomRanges, roomGraphData);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return missionID;
    }

    private static BlockRange2D getBoundary(Set<Point> points) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for(Point point : points) {
            minX = Math.min(point.row, minX);
            minZ = Math.min(point.col, minZ);
            maxX = Math.max(point.row, maxX);
            maxZ = Math.max(point.col, maxZ);
        }

        return new BlockRange2D(minX, maxX, minZ, maxZ);
    }

    private static JsonObject pathToJsonObject(PointPath path, int startX, int y, int startZ) {
        JsonObject pathData = new JsonObject();

        pathData.addProperty("length", path.getPathLength());

        JsonArray pathCoordinates = new JsonArray();

        for(Point pathPoint : path.path) {
            pathCoordinates.add(pointToLocation(pathPoint, startX, y, startZ));
        }

        pathData.add("path", pathCoordinates);

        return pathData;
    }

    private static void debugPrintConnections(List<DecisionPoint> decisionPoints, List<BlockRange2D> roomRanges, HashMap<BlockRange2D, JsonObject> roomGraphData) {
        for(DecisionPoint point : decisionPoints) {
            System.out.println("DECISION " + decisionPoints.indexOf(point) + " IS CONNECTED TO " + point.connectedDecisionPoints.size() + " OTHER DECISION POINT(S) AND " + point.connectedRooms.size() + " ROOM(S)");

            for(DecisionPoint adjacent : point.connectedDecisionPoints.keySet()) {
                PointPath path = point.connectedDecisionPoints.get(adjacent);
                System.out.println("\tDECISION " + decisionPoints.indexOf(adjacent) + " (LENGTH " + path.pathLength + ")");
            }

            for(BlockRange2D room : point.connectedRooms.keySet()) {
                PointPath path = point.connectedRooms.get(room);
                System.out.println("\tROOM " + roomRanges.indexOf(room) + " (LENGTH " + path.pathLength + ")");
            }

            System.out.println();
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if(roomGraphData != null) {
            for (BlockRange2D range : roomRanges) {
                System.out.println("ROOM " + roomRanges.indexOf(range));

                JsonArray connections = new JsonArray();

                for(Map.Entry<String, JsonElement>  entry : roomGraphData.get(range).entrySet()) {
                    connections.add(entry.getKey());
                }

                System.out.println("\t" + gson.toJson(connections));

                System.out.println();
            }
        }
    }

    private static String decisionPointToLocation(DecisionPoint point, int startX, int y, int startZ) {
        return (point.col + startX) + " " + y + " " + (point.row + startZ);
    }

    private static String pointToLocation(Point point, int startX, int y, int startZ) {
        return (point.col + startX) + " " + y + " " + (point.row + startZ);
    }

    private static void expandDecisionPointLineInDirection(Set<DecisionPoint> currentAxisVisitedPoints, Set<DecisionPoint> oppositeAxisVisitedPoints, Set<DecisionPoint> collisionPoints, DecisionPoint decisionPoint, int deltaRow, int deltaCol, String[][] mapping, List<DecisionPoint> decisionPoints, boolean considerDecisionPointDistances) {
        expandDecisionPointLineInDirection(currentAxisVisitedPoints, oppositeAxisVisitedPoints, collisionPoints, decisionPoint, deltaRow, deltaCol, mapping, decisionPoints, considerDecisionPointDistances, null);
    }

    private static int getAdjacentIndex(int deltaRow, int deltaCol) {
        if(deltaRow < 0) {
            return 0;
        } else if(deltaRow > 0) {
            return 1;
        } else if (deltaCol < 0) {
            return 2;
        } else {
            return 3;
        }
    }

    private static int getOppositeAdjacentIndex(int deltaRow, int deltaCol) {
        if(deltaRow < 0) {
            return 1;
        } else if(deltaRow > 0) {
            return 0;
        } else if (deltaCol < 0) {
            return 3;
        } else {
            return 2;
        }
    }

    /**
     * Helper method that generically expands in a direction to find collisions between axes
     * @param currentAxisVisitedPoints List of points that have been visited on the current axis
     * @param oppositeAxisVisitedPoints List of points visited on the opposite axis
     * @param collisionPoints List that will be added to to represent axis collisions
     * @param decisionPoint Starting point
     * @param deltaRow Direction to move in row
     * @param deltaCol Direction to move in col
     * @param mapping Mapping of original map
     * @param decisionPoints List of all decision points
     * @param considerDecisionPointDistances To determine whether decision points should be looked at and connected to, or should the algorithm go simply wall to wall
     */
    private static void expandDecisionPointLineInDirection(Set<DecisionPoint> currentAxisVisitedPoints, Set<DecisionPoint> oppositeAxisVisitedPoints, Set<DecisionPoint> collisionPoints, DecisionPoint decisionPoint, int deltaRow, int deltaCol, String[][] mapping, List<DecisionPoint> decisionPoints, boolean considerDecisionPointDistances, HashMap<DecisionPoint, DecisionPoint[]> adjacentDecisionPoints) {
        DecisionPoint currentPoint = new DecisionPoint(decisionPoint.getRow() + deltaRow, decisionPoint.getCol() + deltaCol);

        openListLoop:
        while(currentPoint.inBounds(mapping)) {
            currentAxisVisitedPoints.add(currentPoint);

            if(considerDecisionPointDistances && adjacentDecisionPoints.get(decisionPoint)[getAdjacentIndex(deltaRow, deltaCol)] == null) {
                Point adjacentNegative = new Point(currentPoint.getRow() + deltaCol, currentPoint.getCol() + deltaRow);
                Point adjacentPositive = new Point(currentPoint.getRow() - deltaCol, currentPoint.getCol() - deltaRow);
                //check for any decision currentPoints first
                Point currentPointRounded = currentPoint.toPoint();
                for (DecisionPoint loopDecisionPoint : decisionPoints) {
                    Point loopDecisionPointRounded = loopDecisionPoint.toPoint();
                    if (currentPointRounded.equals(loopDecisionPointRounded) || adjacentNegative.equals(loopDecisionPointRounded) || adjacentPositive.equals(loopDecisionPointRounded)) {
                        PointPath path = calculatePathBetweenNodes(mapping, new Point(decisionPoint.getRow(), decisionPoint.getCol()), new Point(loopDecisionPoint.getRow(), loopDecisionPoint.getCol()));
                        decisionPoint.connectedDecisionPoints.put(loopDecisionPoint, path);
                        loopDecisionPoint.connectedDecisionPoints.put(decisionPoint, path);

                        adjacentDecisionPoints.get(decisionPoint)[getAdjacentIndex(deltaRow, deltaCol)] = loopDecisionPoint;
                        adjacentDecisionPoints.get(loopDecisionPoint)[getOppositeAdjacentIndex(deltaRow, deltaCol)] = decisionPoint;

                        break openListLoop;
                    }
                }
            }

            //finally, check for a wall
            if(!currentPoint.get(mapping).isEmpty()) {
                break;
            }

            if(!considerDecisionPointDistances) {
                //check if collision with opposite axis
                if (oppositeAxisVisitedPoints.contains(currentPoint) && !decisionPoints.contains(currentPoint)) {
                    //make sure this new collision point is not too close to any other decision point

                    boolean shouldAdd = true;
                    for (DecisionPoint loopDecisionPoint : decisionPoints) {
                        if (Math.abs(loopDecisionPoint.col - currentPoint.col) <= 1) {
                            if (Math.abs(loopDecisionPoint.row - currentPoint.row) <= 1) {
                                shouldAdd = false;
                                break;
                            }
                        }
                    }

                    if (shouldAdd) {
                        for (DecisionPoint loopDecisionPoint : collisionPoints) {
                            if (Math.abs(loopDecisionPoint.col - currentPoint.col) <= 1) {
                                if (Math.abs(loopDecisionPoint.row - currentPoint.row) <= 1) {
                                    shouldAdd = false;
                                    break;
                                }
                            }
                        }
                    }

                    if (shouldAdd) {
                        collisionPoints.add(currentPoint);
                    }
                }
            }

            //now, add to openList
            currentPoint = new DecisionPoint(currentPoint.row + deltaRow, currentPoint.col + deltaCol);
        }
    }

    private static void addPotentialRoomToRoomEdge(List<BlockRange2D> rooms, BlockRange2D room, Point roomPoint, Point doorPoint, String[][] mapping, HashMap<BlockRange2D, Set<BlockRange2D>> connectedRooms) {
        if(doorPoint.inBounds(mapping) && doorPoint.get(mapping).equals("D")) {
            Point outerPoint = doorPoint.inDirection(roomPoint.getDelta(doorPoint));

            for(BlockRange2D roomBounds : rooms) {
                if(roomBounds != room) {
                    if(roomBounds.isInRange(outerPoint.row, outerPoint.col)) {
                        connectedRooms.putIfAbsent(room, new HashSet<>());
                        connectedRooms.putIfAbsent(roomBounds, new HashSet<>());

                        connectedRooms.get(room).add(roomBounds);
                        connectedRooms.get(roomBounds).add(room);
                    }
                }
            }
        }
    }

    private static void addPotentialDecisionPoint(List<DecisionPoint> decisionPoints, BlockRange2D room, Point roomPoint, Point doorPoint, Set<Point> hallwayPoints, String[][] mapping) {
        if(doorPoint.inBounds(mapping) && doorPoint.get(mapping).equals("D")) {
            DecisionPoint decisionPoint = generateNewDecisionPoint(hallwayPoints, mapping, roomPoint, doorPoint);

            if(decisionPoint != null) {
                if (decisionPoints.contains(decisionPoint)) {
                    decisionPoints.get(decisionPoints.indexOf(decisionPoint)).connectedRooms.put(room, getPathFromDecisionPointToRoom(mapping, decisionPoint, room));
                } else {
                    decisionPoint.connectedRooms.put(room, getPathFromDecisionPointToRoom(mapping, decisionPoint, room));
                    decisionPoints.add(decisionPoint);
                }
            }
        }
    }

    //NOTICE: there is a slight accuracy drop because we round to integer instead of double, but this should be almost correct and a good enough estimate for the map
    private static PointPath getPathFromDecisionPointToRoom(String[][] mapping, DecisionPoint decisionPoint, BlockRange2D room) {
        return calculatePathBetweenNodes(mapping, new Point(decisionPoint.getRow(), decisionPoint.getCol()), new Point((room.getRangeX()[1] + room.getRangeX()[0]) / 2, (room.getRangeZ()[1] + room.getRangeZ()[0]) / 2));
    }

    //this uses the decision point class, but this doesn't mean we are looking between decision points.
    private static PointPath calculatePathBetweenNodes(String[][] mapping, Point begin, Point end) {
        HashMap<Point, Double> totalLocationCosts = new HashMap<>();
        HashMap<Point, Integer> numberOfBlocksFromBegin = new HashMap<>();

        PriorityQueue<Point> openList = new PriorityQueue<>(new Comparator<Point>() {
            @Override
            public int compare(Point o1, Point o2) {
                return Double.compare(totalLocationCosts.get(o1), totalLocationCosts.get(o2));
            }
        });

        HashMap<Point, Point> parents = new HashMap<>();

        openList.add(begin);
        parents.put(begin, null);
        totalLocationCosts.put(begin, getManhattanDistance(begin, end));
        numberOfBlocksFromBegin.put(begin, 0);

        while(!openList.isEmpty()) {
            Point currentPoint = openList.poll();

            if(end.equals(currentPoint)) {
                break;
            }

            for(int i = 0; i < 4; i++) {
                for(int dy = -1; dy <= 1; dy++) {
                    Point adjacentLoc = currentPoint.inDirection(new int[] {DIRECTION_DELTAS[i * 2], DIRECTION_DELTAS[i * 2 + 1]});

                    if(parents.containsKey(adjacentLoc)) {
                        continue;
                    }

                    if(!adjacentLoc.inBounds(mapping)) {
                        continue;
                    }

                    if(!adjacentLoc.get(mapping).equals("D") && !adjacentLoc.get(mapping).isEmpty()) {
                        continue;
                    }

                    int adjacentBlocksFromStart = numberOfBlocksFromBegin.get(currentPoint) + 1;
                    numberOfBlocksFromBegin.put(adjacentLoc, adjacentBlocksFromStart);
                    totalLocationCosts.put(adjacentLoc, adjacentBlocksFromStart + getManhattanDistance(adjacentLoc, end));
                    openList.add(adjacentLoc);
                    parents.put(adjacentLoc, currentPoint);
                }
            }
        }

        LinkedList<Point> path = new LinkedList<>();

        path.add(end);

        double distance = 0;

        Point nextLoc = end;
        while((nextLoc = parents.get(nextLoc)) != null) {
            path.addFirst(nextLoc);
            distance++;
        }

        distance = Math.round(distance * 100) / 100d;

        return new PointPath(path, distance);
    }

    private static double getManhattanDistance(Point begin, Point end) {
        return Math.abs(begin.row - end.row) + Math.abs(begin.col - end.col);
    }

    /**
     * Attempts to generate a decision point if the adjacentPoint is facing the hallway, returning the middle of the hallway. Otherwise, returns null.
     * @param hallwayPoints
     * @param mapping
     * @param point
     * @param adjacentPoint
     * @return
     */
    private static DecisionPoint generateNewDecisionPoint(Set<Point> hallwayPoints, String[][] mapping, Point point, Point adjacentPoint) {
        int[] direction = point.getDelta(adjacentPoint);

        Point hallwayCandidate = adjacentPoint.inDirection(direction);
        if(hallwayPoints.contains(hallwayCandidate)) {
            int hallwayLength = 0;
            Point currentPoint = hallwayCandidate;
            while(currentPoint.inBounds(mapping) && currentPoint.get(mapping).isEmpty()) {
                hallwayLength++;
                currentPoint = currentPoint.inDirection(direction);
            }

            if(direction[0] == 0) {
                return new DecisionPoint(point.row, hallwayCandidate.col + ((hallwayLength - 1) / 2d * direction[1]));
            } else {
                return new DecisionPoint(((((hallwayCandidate.row + 0.5d) * 2d) + ((hallwayLength - 1) * direction[0])) / 2d), point.col);
            }
        }

        return null;
    }

    private static EnclosedSpace getEnclosedSpace(Point start, String[][] mapping) {
        return getEnclosedSpace(start, mapping, null);
    }

    private static EnclosedSpace getEnclosedSpace(Point start, String[][] mapping, Set<Point> additionalWalls) {
        Set<Point> enclosedSpace = new HashSet<>();
        LinkedList<Point> openList = new LinkedList<>();
        int adjacentDoorsCount = 0;

        openList.add(start);
        enclosedSpace.add(start);

        while(!openList.isEmpty()) {
            Point next = openList.poll();

            for(int i = 0; i < 4; i++) {
                Point delta = new Point(next.row + DIRECTION_DELTAS[i * 2], next.col + DIRECTION_DELTAS[i * 2 + 1]);

                if(enclosedSpace.contains(delta)) {
                    continue;
                }

                if(additionalWalls != null && additionalWalls.contains(delta)) {
                    continue;
                }

                if(delta.inBounds(mapping)) {
                    if(delta.get(mapping).isEmpty()) {
                        enclosedSpace.add(delta);
                        openList.add(delta);
                    } else {
                        switch(delta.get(mapping)) {
                            case "D":
                                adjacentDoorsCount++;
                                break;
                        }
                    }
                }
            }
        }

        return new EnclosedSpace(enclosedSpace, adjacentDoorsCount);
    }

    private static void formatPrint(String[][] mapping) {
        System.out.print("    ");

        for(int col = 0; col < mapping[0].length; col++) {
            System.out.print(String.format("%02d", col) + " ");
        }

        System.out.println();

        int rowIndex = 0;
        for(String[] row : mapping) {
            System.out.print(String.format("%02d", rowIndex) + " ");
            System.out.print("[");
            for(int i = 0; i < row.length; i++) {
                if(row[i].isEmpty()) {
                    System.out.print(" ");
                } else {
                    System.out.print(row[i]);
                }

                if(i < row.length - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println("]");
            rowIndex++;
        }
    }

    private static void specialFormatPrint(String[][] mapping, List<BlockRange2D> roomRanges, List<DecisionPoint> decisionPoints) {
        System.out.println();

        char[] symbols = new char[] {'~', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '_', '+', '=', '.', ',', '?', '/', '\\', '<', '>', '|'};

        System.out.print("    ");

        for(int col = 0; col < mapping[0].length; col++) {
            System.out.print(String.format("%02d", col) + "  ");
        }

        System.out.println();

        int rowIndex = 0;
        for(String[] row : mapping) {
            System.out.print(String.format("%02d", rowIndex) + " ");
            System.out.print("[");
            for(int i = 0; i < row.length; i++) {
                boolean found = false;
                int index = 0;
                for(BlockRange2D range : roomRanges) {
                    if(range.isInRange(rowIndex, i)) {
                        if(true) {
                            System.out.print(String.format("%02d", index));
                        } else {
                            System.out.print(symbols[index % symbols.length] + " ");
                        }
                        found = true;
                        break;
                    }
                    index++;
                }

                index = 0;
                for(DecisionPoint point : decisionPoints) {
                    if(point.getRow() == rowIndex && point.getCol() == i) {
//                        System.out.print("X");
                        System.out.print(String.format("%02d", index));
                        found = true;
                        break;
                    }
                    index++;
                }

                if(!found) {
                    if (row[i].isEmpty()) {
                        if (!found) {
                            System.out.print("  ");
                        }
                    } else {
                        System.out.print(row[i] + " ");
                    }
                }

                if(i < row.length - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println("]");

            rowIndex++;
        }
    }

    private static String[] createRow(int length, String fill) {
        String[] row = new String[length];
        for(int i = 0; i < length; i++) {
            row[i] = fill;
        }

        return row;
    }
}
