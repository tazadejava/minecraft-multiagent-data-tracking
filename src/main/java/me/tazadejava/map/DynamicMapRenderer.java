package me.tazadejava.map;

import org.bukkit.Bukkit;
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

public class DynamicMapRenderer extends MapRenderer {

    public static ItemStack getMap() {
        MapView map = Bukkit.createMap(Bukkit.getWorlds().get(0));
        map.getRenderers().clear();
        map.setLocked(true);
        map.setUnlimitedTracking(false);
        map.setScale(MapView.Scale.NORMAL);
        map.addRenderer(new DynamicMapRenderer());

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

        int[] xRange = new int[] {-2154, -2105};
        int[] zRange = new int[] {152, 199};

        int xLength = xRange[1] - xRange[0];
        int zLength = zRange[1] - zRange[0];

        int playerX = player.getLocation().getBlockX();
        int playerZ = player.getLocation().getBlockZ();

        //convert to -128 to 127

        byte pointerX, pointerZ;

        double playerScaleX = (double) (playerX - xRange[0]) / xLength;
        double playerScaleZ = (double) (playerZ - zRange[0]) / zLength;

        if(playerX < xRange[0]) {
            pointerX = -128;
        } else if(playerX > xRange[1]) {
            pointerX = 127;
        } else {
            pointerX = (byte) Math.floor((playerScaleX * 256) - 128);
        }

        if(playerZ < zRange[0]) {
            pointerZ = -128;
        } else if(playerZ > zRange[1]) {
            pointerZ = 127;
        } else {
            pointerZ = (byte) Math.floor((playerScaleZ * 256) - 128);
        }

        try {
            InputStream stream = getClass().getClassLoader().getResourceAsStream("sparky_map.png");
            BufferedImage image = ImageIO.read(stream);
            stream.close();

            int angle = (int) -((player.getLocation().getYaw() % 360) - 180d);

            if(angle < 0) {
                angle += 360;
            }

            //round to nearest 15
//            angle = (int) (Math.round(angle / 15.0) * 15.0);

            int pointerZTranslation = 64;
            MapCursorCollection cursors = new MapCursorCollection();
            canvas.drawImage(0, 0, rotateImage(image, angle, playerScaleX, playerScaleZ, pointerZTranslation, cursors));

            cursors.addCursor(new MapCursor((byte) 0, (byte) pointerZTranslation, (byte) 8, MapCursor.Type.RED_POINTER, true));

            canvas.setCursors(cursors);
        } catch (IOException e) {
            e.printStackTrace();
        } catch(IllegalArgumentException ex) {}
    }

    private byte scaleToMapCoordinates(double scale) {
        int result = (int) ((scale * 255d) - 128);

        if(result < -128) {
            return (byte) -128;
        } else if(result > 127) {
            return (byte) 127;
        } else {
            return (byte) result;
        }
    }

    private BufferedImage rotateImage(BufferedImage image, int angle, double xScale, double zScale, int pointerZTranslation, MapCursorCollection cursors) {
        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage rotated = new BufferedImage(width, height, image.getType());

        Graphics2D graphics = rotated.createGraphics();

//        graphics.rotate(Math.toRadians(angle), width / 2d, height / 2d);
//        graphics.drawImage(image, null, (int) (width * (1 - xScale)) - (width / 2), (int) (height * (1 - zScale)) - (height / 2));

        Bukkit.broadcastMessage("ANGLE " + angle);

        double xRotate = width * xScale;
//        double zRotate = (zScale * height) + ((pointerZTranslation / 255d) * height);
        double zRotate = zScale * height;

        graphics.translate(xRotate, zRotate);
        graphics.rotate(Math.toRadians(angle));
        cursors.addCursor(new MapCursor(scaleToMapCoordinates(xRotate / width), scaleToMapCoordinates(zRotate / height), (byte) 0, MapCursor.Type.RED_X, true));

        graphics.translate(-xRotate, -zRotate);
        graphics.drawImage(image, null, 0, 0);

//        graphics.drawImage(image, null, -width / 2, -height / 2);

//        graphics.drawImage(image, null, (int) (-xScale * width), (int) (-zScale * height));
//        graphics.drawImage(image, null, (int) (-xScale * width) +  (width / 2), (int) (-zScale * height) + (height / 2) + (int) (((pointerZTranslation) / 255d) * height));

        graphics.dispose();

//        BufferedImage translated = new BufferedImage(width, height, image.getType());
//
//        graphics = translated.createGraphics();
//
////        graphics.drawImage(rotated, null, width / 2, height / 2);
////        graphics.translate(-xRotate, -zRotate);
//        graphics.drawImage(rotated, null, 0, 0);
//
//        graphics.dispose();

        return rotated;
//        return translated;
    }
}
