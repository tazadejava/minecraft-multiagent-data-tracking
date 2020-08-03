package me.tazadejava.map;

import me.tazadejava.analyzer.PlayerAnalyzer;
import me.tazadejava.mission.Mission;
import me.tazadejava.mission.MissionGraph;
import me.tazadejava.mission.MissionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

public class DynamicMapRenderer extends MapRenderer {

    private MissionManager manager;
    private Player player;

    private PlayerAnalyzer analyzer;
    private Mission mission;

    private BufferedImage mapImage;

    private int[] xRange, zRange;

    private DynamicMapRenderer(MissionManager missionManager, Player player) {
        this.manager = missionManager;
        this.player = player;

        try {
            InputStream stream = getClass().getClassLoader().getResourceAsStream("sparky_map.png");
            mapImage = ImageIO.read(stream);
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        xRange = new int[] {-2154, -2105};
        zRange = new int[] {152, 199};
    }

    public static ItemStack getMap(MissionManager missionManager, Player player) {
        MapView map = Bukkit.createMap(Bukkit.getWorlds().get(0));
        map.getRenderers().clear();
        map.setLocked(true);
        map.setUnlimitedTracking(false);
        map.setScale(MapView.Scale.NORMAL);
        map.addRenderer(new DynamicMapRenderer(missionManager, player));

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        meta.setMapView(map);
        mapItem.setItemMeta(meta);

        return mapItem;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        //block range:
        //X: -2154 to -2105
        //Z: 152 to 199

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
        canvas.drawImage(0, 0, rotateImage(mapImage, angle, playerScaleX, playerScaleZ, pointerZTranslation));

        cursors.addCursor(new MapCursor((byte) 0, (byte) pointerZTranslation, (byte) 8, MapCursor.Type.RED_POINTER, true));

        canvas.setCursors(cursors);
    }

    private BufferedImage rotateImage(BufferedImage image, int angle, double xScale, double zScale, int pointerZTranslation) {
        int width = image.getWidth();
        int height = image.getHeight();

        //translate, then rotate

        BufferedImage translated = new BufferedImage(width * 3, height * 3, image.getType());

        Graphics2D graphics = translated.createGraphics();

        graphics.translate(width, height);

        int xDelta = (int) (-xScale * width) +  (width / 2);
        int zDelta = (int) (-zScale * height) + (height / 2) + (int) (((pointerZTranslation) / 255d) * height);
        graphics.drawImage(image, null, xDelta, zDelta);

        drawOnMap(graphics, xDelta, zDelta, width, height);

        graphics.dispose();

        //next, rotate

        BufferedImage rotated = new BufferedImage(width, height, image.getType());

        graphics = rotated.createGraphics();

        graphics.rotate(Math.toRadians(angle), rotated.getWidth() / 2, rotated.getHeight() / 2 + ((int) (((pointerZTranslation) / 255d) * rotated.getHeight())));
        graphics.drawImage(translated, null, -width, -height);

        graphics.dispose();

        return rotated;
    }

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

        List<MissionGraph.MissionVertex> bestPath = analyzer.getLastBestPath();

        if(bestPath == null) {
            return;
        }

        int iconSize, colorIntensity;

        int size = Math.min(bestPath.size(), 7);

        MissionGraph graph = mission.getMissionGraph();

//        for(int i = size - 1; i > 0; i--) {
//            MissionGraph.MissionVertex nextVertex = bestPath.get(i);
//
//            colorIntensity = (int) (255 * Math.pow(((double) (size - i) / (size - 1)), 2));
//            iconSize = (int) (5 * ((double) (size - i) / (size - 1))) + 3;
//
//            int x = mapOffsetX + (int) (128 * getScale(xRange, nextVertex.location.getBlockX()));
//            int z = mapOffsetZ + (int) (128 * getScale(zRange, nextVertex.location.getBlockZ()));
//
//            //draw edge path
//            graphics.setColor(new Color(colorIntensity, 0, colorIntensity));
//            LinkedList<Location> edgePath = graph.getExactPathBetweenEdges(bestPath.get(i).type, bestPath.get(i).name, bestPath.get(i + 1).type, bestPath.get(i + 1).name);
//            for(Location loc : edgePath) {
//                int locX = mapOffsetX + (int) (128 * getScale(xRange, loc.getBlockX()));
//                int locZ = mapOffsetZ + (int) (128 * getScale(zRange, loc.getBlockZ()));
//                graphics.drawRect(locX, locZ, 1, 1);
//            }
//
//            //draw destination node
//            graphics.setColor(new Color(0, colorIntensity, 0));
//            graphics.fillOval(x - (iconSize / 2), z - (iconSize / 2), iconSize, iconSize);
//        }

        for(int i = 1; i < size; i++) {
            MissionGraph.MissionVertex nextVertex = bestPath.get(i);

            colorIntensity = (int) (255 * Math.pow(((double) (size - i) / (size)), 2));
            iconSize = (int) (5 * ((double) (size - i) / (size))) + 3;

            int x = mapOffsetX + (int) (128 * getScale(xRange, nextVertex.location.getBlockX()));
            int z = mapOffsetZ + (int) (128 * getScale(zRange, nextVertex.location.getBlockZ()));

            //draw edge path
            if(i < size - 1) {
                graphics.setColor(new Color(colorIntensity, 0, colorIntensity));
                LinkedList<Location> edgePath = graph.getExactPathBetweenEdges(bestPath.get(i).type, bestPath.get(i).name, bestPath.get(i + 1).type, bestPath.get(i + 1).name);
                for (Location loc : edgePath) {
                    int locX = mapOffsetX + (int) (128 * getScale(xRange, loc.getBlockX()));
                    int locZ = mapOffsetZ + (int) (128 * getScale(zRange, loc.getBlockZ()));
                    graphics.drawRect(locX, locZ, 1, 1);
                }
            }

            //draw destination node
            graphics.setColor(new Color(0, colorIntensity, 0));
            graphics.fillOval(x - (iconSize / 2), z - (iconSize / 2), iconSize, iconSize);
        }
    }

    /**
     * Scale from 0 to 1 based on where on the map it is
     * @param range
     * @param value
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
