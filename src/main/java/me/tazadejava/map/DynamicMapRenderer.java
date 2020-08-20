package me.tazadejava.map;

import me.tazadejava.analyzer.PlayerAnalyzer;
import me.tazadejava.mission.Mission;
import me.tazadejava.mission.MissionGraph;
import me.tazadejava.mission.MissionManager;
import me.tazadejava.mission.MissionRoom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.*;

/**
 * The "Google Maps" implementation that assists players traversing through the missions. Will automatically rotate and translate a 128x128 image to determine where a player is currently located on the map.
 */
public class DynamicMapRenderer extends MapRenderer {

    public enum CustomMap {
        FALCON, SPARKY;
    }

    private JavaPlugin plugin;
    private MissionManager manager;
    private Player player;

    private boolean showRoomAndDecisionLabels;
    private boolean showBestPathAndVictims;

    private PlayerAnalyzer analyzer;
    private Mission mission;

    private BufferedImage mapImage;

    private int[] xRange, zRange;

    //choose to either use the map image that has all the answers or use a unpopulated map
    private final boolean USE_ANSWER_MAP = false;

    //variables used only if not using the answer map
    private String[][] mapping;

    private DynamicMapRenderer(JavaPlugin plugin, MissionManager missionManager, Player player, boolean showRoomAndDecisionLabels, boolean showBestPathAndVictims, CustomMap map) {
        this.plugin = plugin;
        this.manager = missionManager;
        this.player = player;
        this.showRoomAndDecisionLabels = showRoomAndDecisionLabels;
        this.showBestPathAndVictims = showBestPathAndVictims;

        switch(map) {
            case SPARKY:
                if(USE_ANSWER_MAP) {
                    try {
                        InputStream stream = getClass().getClassLoader().getResourceAsStream("sparky_map.png");
                        mapImage = ImageIO.read(stream);
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    xRange = new int[]{-2154, -2105};
                    zRange = new int[]{152, 199};
                } else {
                    setMapImageFromCSV("sparky.csv", -2153, 153);
                }

                break;
            case FALCON:
                if(USE_ANSWER_MAP) {
                    try {
                        InputStream stream = getClass().getClassLoader().getResourceAsStream("falcon_map.png");
                        mapImage = ImageIO.read(stream);
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    xRange = new int[]{-2109, -2020};
                    zRange = new int[]{144, 192};
                } else {
                    setMapImageFromCSV("falcon.csv", -2108, 144);
                }
                break;
        }
    }

    /**
     * Main method that will create a BufferedImage from a CSV file and set the mapImage variable to this image.
     * @param name CSV file within the resources folder. Ends with .csv
     * @param startX Top-left x coordinate of the first block on the CSV file.
     * @param startZ Top-left z coordinate of the first block on the CSV file.
     */
    private void setMapImageFromCSV(String name, int startX, int startZ) {
        //adjust for manual border
        startX -= 1;
        startZ -= 1;

        if(mapping == null) {
            defineMapping(name);
        }

        BufferedImage originalImage = new BufferedImage(mapping[0].length, mapping.length, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = originalImage.createGraphics();

        for(int r = 0; r < mapping.length; r++) {
            for(int c = 0; c < mapping[r].length; c++) {
                String val = mapping[r][c];

                int color;
                if(val.isEmpty()) {
                    color = Color.WHITE.getRGB();
                } else {
                    switch(val) {
                        case "D":
                            color = Color.GREEN.getRGB();
                            break;
                        default:
                            color = Color.BLACK.getRGB();
                            break;
                    }
                }
                originalImage.setRGB(c, r, color);
            }
        }

        graphics.dispose();

        mapImage = getScaledImage(128, 128, originalImage);

        xRange = new int[] {startX, startX + originalImage.getWidth()};
        zRange = new int[] {startZ, startZ + originalImage.getHeight()};
    }

    /**
     * Creates a 2D array that maps a CSV file in the resources folder to a String mapping
     * @param name Name of the CSV file in the resources file, including .csv
     */
    private void defineMapping(String name) {
        List<String[]> lines = new ArrayList<>();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(name)));

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

        String[] row = new String[lines.get(0).length];
        Arrays.fill(row, "B");

        lines.add(0, row);
        lines.add(row);

        mapping = lines.toArray(new String[0][0]);
    }

    /**
     * Scales an image to the specified width and height.
     * @param width Desired width.
     * @param height Desired height.
     * @param originalImage Original image passed in.
     * @return The scaled image.
     */
    private BufferedImage getScaledImage(int width, int height, BufferedImage originalImage) {
        BufferedImage scaledImage = new BufferedImage(width, height, originalImage.getType());
        Graphics2D graphics = scaledImage.createGraphics();

        //draw scaled version
        graphics.drawImage(originalImage, 0, 0, scaledImage.getWidth(), scaledImage.getHeight(), 0, 0, originalImage.getWidth(), originalImage.getHeight(), null);

        graphics.dispose();

        return scaledImage;
    }

    /**
     * Should run async to not lag the server. Will check for created and deleted edges, and update mapping accordingly. DISABLED until further notice. Can be used in the future to manually map onto the CSV file mapping instead of creating image overlays like the current implementation does.
     */
    private void updateMappingImage() {
        BufferedImage originalImage = new BufferedImage(mapping[0].length, mapping.length, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = originalImage.createGraphics();

        for(int r = 0; r < mapping.length; r++) {
            for(int c = 0; c < mapping[r].length; c++) {
                String val = mapping[r][c];

                int color;
                if(val.isEmpty()) {
                    color = Color.WHITE.getRGB();
                } else {
                    switch(val) {
                        case "D":
                            color = Color.GREEN.getRGB();
                            break;
                        default:
                            color = Color.BLACK.getRGB();
                            break;
                    }
                }
                originalImage.setRGB(c, r, color);
            }
        }

        graphics.dispose();

        mapImage = getScaledImage(128, 128, originalImage);
    }

    /**
     * Creates an ItemStack representation of this class so that it can be used.
     * @param plugin
     * @param missionManager
     * @param player Will show best path recommendations to this player
     * @param showRoomAndDecisionLabels Whether or not to show the debug labels that print numbers for the decision and room vertices on the map
     * @param showBestPathAndVictims Whether or not to show recommendations on the map; namely, this shows the best path, victims, and modified edges
     * @param mapType Map specification to use (csv or image).
     * @return
     */
    public static ItemStack getMap(JavaPlugin plugin, MissionManager missionManager, Player player, boolean showRoomAndDecisionLabels, boolean showBestPathAndVictims, CustomMap mapType) {
        MapView map = Bukkit.createMap(Bukkit.getWorlds().get(0));
        map.getRenderers().clear();
        map.setLocked(true);
        map.setUnlimitedTracking(false);
        map.setScale(MapView.Scale.NORMAL);
        map.addRenderer(new DynamicMapRenderer(plugin, missionManager, player, showRoomAndDecisionLabels, showBestPathAndVictims, mapType));

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        meta.setMapView(map);
        mapItem.setItemMeta(meta);

        return mapItem;
    }

    /**
     * Renders the map onto an image. Is a Spigot method overriden to map the image, rotate it, and translate it.
     * @param map
     * @param canvas
     * @param player
     */
    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        double playerScaleX = getScale(xRange, player.getLocation().getBlockX());
        double playerScaleZ = getScale(zRange, player.getLocation().getBlockZ());

        int angle = (int) -((player.getLocation().getYaw() % 360) - 180d);

        if(angle < 0) {
            angle += 360;
        }

        //round angle to nearest 15
        angle = (int) (Math.round(angle / 15.0) * 15.0);

        int pointerZTranslation = 64;
        MapCursorCollection cursors = new MapCursorCollection();
        //draw the image
        canvas.drawImage(0, 0, getTranslatedRotatedImage(mapImage, angle, playerScaleX, playerScaleZ, pointerZTranslation));

        //draw the cursor at the bottom half of the map
        cursors.addCursor(new MapCursor((byte) 0, (byte) pointerZTranslation, (byte) 8, MapCursor.Type.RED_POINTER, true));

        canvas.setCursors(cursors);
    }

    /**
     * Will translate, then rotate the image to fit on the map "Google Maps" style
     * @param image Image to translate and rotate
     * @param angle Angle to rotate
     * @param xScale Player's relative X position on the map (0 to 1)
     * @param zScale Player's relative Z position on the map (0 to 1)
     * @param pointerZTranslation How down is the cursor from the top of the map
     * @return
     */
    private BufferedImage getTranslatedRotatedImage(BufferedImage image, int angle, double xScale, double zScale, int pointerZTranslation) {
        int width = image.getWidth();
        int height = image.getHeight();

        //translate first

        BufferedImage translated = new BufferedImage(width * 3, height * 3, image.getType());

        Graphics2D graphics = translated.createGraphics();

        graphics.translate(width, height);

        int xDelta = (int) (-xScale * width) + (width / 2);
        int zDelta = (int) (-zScale * height) + (height / 2) + (int) (((pointerZTranslation) / 255d) * height);
        graphics.drawImage(image, null, xDelta, zDelta);

        drawOnMap(graphics, xDelta, zDelta, width, height);

        graphics.dispose();

        //next, rotate

        BufferedImage rotated = new BufferedImage(width, height, image.getType());

        graphics = rotated.createGraphics();

        graphics.rotate(Math.toRadians(angle), rotated.getWidth() / 2f, rotated.getHeight() / 2f + ((int) (((pointerZTranslation) / 255d) * rotated.getHeight())));
        graphics.drawImage(translated, null, -width, -height);

        graphics.dispose();

        return rotated;
    }

    /**
     * Annnotate the map with overlays. This is where things like decision/room vertices, best path circles, added/removed edges are rendered.
     * @param graphics Graphics instance for image to draw over.
     * @param mapOffsetX X offset to add to drawn images
     * @param mapOffsetZ Z offset to add to drawn images
     * @param width Map width
     * @param height Map height
     */
    private void drawOnMap(Graphics2D graphics, int mapOffsetX, int mapOffsetZ, int width, int height) {
        if(analyzer == null || mission == null) {
            if(manager.getCurrentMission() != null && manager.isMissionInProgress(manager.getCurrentMission())) {
                analyzer = manager.getPlayerAnalyzer(player);
                mission = manager.getCurrentMission();
            }
            return;
        }
        if(!manager.isMissionInProgress(mission)) {
            analyzer = null;
            mission = null;
            return;
        }

        if(showRoomAndDecisionLabels) {
            drawDecisionAndRoomLabels(graphics, mapOffsetX, mapOffsetZ, width, height);
            return;
        }

        //draw rooms with victims in them that the player missed!
        if(showBestPathAndVictims) {
            drawLeftVictimRooms(graphics, mapOffsetX, mapOffsetZ, width, height);

            //draw added and removed edges
            drawAddedRemovedEdges(graphics, mapOffsetX, mapOffsetZ, width, height);

            List<MissionGraph.MissionVertex> bestPath = analyzer.getLastBestPath();

            if (bestPath == null) {
                return;
            }

            int iconSize, colorIntensity;

            int pathSize = Math.min(bestPath.size(), 7);

            MissionGraph graph = mission.getMissionGraph();

            for (int i = 1; i < pathSize; i++) {
                MissionGraph.MissionVertex nextVertex = bestPath.get(i);

                colorIntensity = (int) (255 * Math.pow(((double) (pathSize - i) / (pathSize)), 2));
                iconSize = (int) (5 * ((double) (pathSize - i) / (pathSize))) + 3;

                int x = mapOffsetX + (int) (width * getScale(xRange, nextVertex.location.getBlockX()));
                int z = mapOffsetZ + (int) (height * getScale(zRange, nextVertex.location.getBlockZ()));

                //draw edge path
                if (i < pathSize - 1) {
                    graphics.setColor(new Color(colorIntensity, 0, colorIntensity));
                    LinkedList<Location> edgePath = graph.getExactPathBetweenEdges(bestPath.get(i).type, bestPath.get(i).name, bestPath.get(i + 1).type, bestPath.get(i + 1).name);
                    if (edgePath != null) {
                        for (Location loc : edgePath) {
                            int locX = mapOffsetX + (int) (width * getScale(xRange, loc.getBlockX()));
                            int locZ = mapOffsetZ + (int) (height * getScale(zRange, loc.getBlockZ()));
                            graphics.drawRect(locX, locZ, 1, 1);
                        }
                    }
                }

                //draw destination node
                graphics.setColor(new Color(0, colorIntensity, 0));
                graphics.fillOval(x - (iconSize / 2), z - (iconSize / 2), iconSize, iconSize);
            }
        }
    }

    /**
     * Draws red boxes around rooms where victims exist
     * @param graphics
     * @param mapOffsetX
     * @param mapOffsetZ
     */
    private void drawLeftVictimRooms(Graphics2D graphics, int mapOffsetX, int mapOffsetZ, int width, int height) {
        MissionGraph graph = mission.getMissionGraph();

        HashMap<MissionGraph.MissionVertex, Set<Block>> roomsWithVictims = graph.getRoomVerticesWithVictims();

        graphics.setColor(new Color(255, 0, 0, 128));

        for(MissionGraph.MissionVertex roomVertex : roomsWithVictims.keySet()) {
            if(analyzer.getLastVertex() != null && analyzer.getLastVertex().equals(roomVertex)) {
                //don't indicate that a victim is still in this room until we leave the room!
                continue;
            }

            if(!roomsWithVictims.get(roomVertex).isEmpty()) {
                MissionRoom room = mission.getRoom(roomVertex.name);

                int startX = mapOffsetX + (int) (width * getScale(xRange, room.getBounds().startX));
                int startZ = mapOffsetZ + (int) (height * getScale(zRange, room.getBounds().startZ));
                int endX = mapOffsetX + (int) (width * getScale(xRange, room.getBounds().endX));
                int endZ = mapOffsetZ + (int) (height * getScale(zRange, room.getBounds().endZ));

                graphics.fillRect(startX, startZ, endX - startX, endZ - startZ);
            }
        }
    }

    /**
     * Draw the added and removed edges in the form of big X's and O's
     * @param graphics
     * @param mapOffsetX
     * @param mapOffsetZ
     * @param width
     * @param height
     */
    private void drawAddedRemovedEdges(Graphics2D graphics, int mapOffsetX, int mapOffsetZ, int width, int height) {
        MissionGraph graph = mission.getMissionGraph();

        graphics.setFont(new Font("Arial", Font.BOLD, 14));

        graphics.setColor(Color.GREEN);
        for(Location loc : graph.getAddedEdgeLocationMarkers()) {
            int startX = mapOffsetX + (int) (width * getScale(xRange, loc.getBlockX()));
            int startZ = mapOffsetZ + (int) (height * getScale(zRange, loc.getBlockZ()));

            graphics.drawOval(startX, startZ, 8, 8);
        }

        graphics.setColor(Color.RED);
        for(Location loc : graph.getRemovedEdgeLocationMarkers()) {
            int startX = mapOffsetX + (int) (width * getScale(xRange, loc.getBlockX()));
            int startZ = mapOffsetZ + (int) (height * getScale(zRange, loc.getBlockZ()));

            graphics.drawString("X", startX, startZ);
        }
    }

    /**
     * Draw the decision and room vertices as numbers on the map. Should only be used for testing purposes.
     * @param graphics
     * @param mapOffsetX
     * @param mapOffsetZ
     * @param width
     * @param height
     */
    private void drawDecisionAndRoomLabels(Graphics2D graphics, int mapOffsetX, int mapOffsetZ, int width, int height) {
        //draw decision and room circles
        graphics.setColor(Color.ORANGE);
        int ovalSize = 6;
        for(MissionRoom room : mission.getRooms()) {
            int locX = mapOffsetX + (int) (width * getScale(xRange, (room.getBounds().getRangeX()[1] + room.getBounds().getRangeX()[0]) / 2));
            int locZ = mapOffsetZ + (int) (height * getScale(zRange, (room.getBounds().getRangeZ()[1] + room.getBounds().getRangeZ()[0]) / 2));

            graphics.fillOval(locX - (ovalSize / 2), locZ - (ovalSize / 2), ovalSize, ovalSize);
        }

        for(String decisionPoint : mission.getDecisionPoints().keySet()) {
            Location loc = mission.getDecisionPoints().get(decisionPoint);

            int locX = mapOffsetX + (int) (width * getScale(xRange, loc.getBlockX()));
            int locZ = mapOffsetZ + (int) (height * getScale(zRange, loc.getBlockZ()));

            graphics.fillOval(locX - (ovalSize / 2), locZ - (ovalSize / 2), ovalSize, ovalSize);
        }

        //draw decision and room numbers
        graphics.setFont(new Font("TimesRoman", Font.BOLD, 10));

        graphics.setColor(Color.RED);
        FontMetrics font = graphics.getFontMetrics();
        for(MissionRoom room : mission.getRooms()) {
            String text = room.getRoomName();

            Rectangle2D textBounds = font.getStringBounds(text, graphics);

            int locX = mapOffsetX + (int) (width * getScale(xRange, (room.getBounds().getRangeX()[1] + room.getBounds().getRangeX()[0]) / 2));
            int locZ = mapOffsetZ + (int) (height * getScale(zRange, (room.getBounds().getRangeZ()[1] + room.getBounds().getRangeZ()[0]) / 2));

            graphics.drawString(text, locX - (int) (textBounds.getWidth() / 2), locZ - (int) (textBounds.getHeight() / 2) + font.getAscent());
        }

        graphics.setColor(Color.GREEN);
        for(String decisionPoint : mission.getDecisionPoints().keySet()) {
            Rectangle2D textBounds = font.getStringBounds(decisionPoint, graphics);

            Location loc = mission.getDecisionPoints().get(decisionPoint);

            int locX = mapOffsetX + (int) (width * getScale(xRange, loc.getBlockX()));
            int locZ = mapOffsetZ + (int) (height * getScale(zRange, loc.getBlockZ()));

            graphics.drawString(decisionPoint, locX - (int) (textBounds.getWidth() / 2), locZ - (int) (textBounds.getHeight() / 2) + font.getAscent());
        }
    }
    /**
     * Scale from 0 to 1 based on where on the inputted range the value is.
     * @param range
     * @param value
     * @return Scale from 0 to 1. Will not go over 1 or under 0, instead encapsulating the numbers in the lower and upper bounds.
     */
    private double getScale(int[] range, int value) {
        int scaleLength = range[1] - range[0];
        double scale = (double) (value - range[0]) / scaleLength;

        if(scale > 1) {
            return 1;
        } else if(scale < 0) {
            return 0;
        } else {
            return scale;
        }
    }
}
