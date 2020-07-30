package me.tazadejava.map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;

import javax.imageio.ImageIO;
import javax.swing.*;
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

        double playerScaleX = (double) (playerX - xRange[0]) / xLength;
        double playerScaleZ = (double) (playerZ - zRange[0]) / zLength;

        try {
            InputStream stream = getClass().getClassLoader().getResourceAsStream("sparky_map.png");
            BufferedImage image = ImageIO.read(stream);
            stream.close();

            int angle = (int) -((player.getLocation().getYaw() % 360) - 180d);

            if(angle < 0) {
                angle += 360;
            }

            //round angle to nearest 15
            angle = (int) (Math.round(angle / 15.0) * 15.0);

            int pointerZTranslation = 64;
            MapCursorCollection cursors = new MapCursorCollection();
            canvas.drawImage(0, 0, rotateImage(image, angle, playerScaleX, playerScaleZ, pointerZTranslation));

            cursors.addCursor(new MapCursor((byte) 0, (byte) pointerZTranslation, (byte) 8, MapCursor.Type.RED_POINTER, true));

            canvas.setCursors(cursors);
        } catch (IOException e) {
            e.printStackTrace();
        } catch(IllegalArgumentException ex) {}
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

        graphics.dispose();

        //next, rotate

        BufferedImage rotated = new BufferedImage(width, height, image.getType());

        graphics = rotated.createGraphics();

        graphics.rotate(Math.toRadians(angle), rotated.getWidth() / 2, rotated.getHeight() / 2 + ((int) (((pointerZTranslation) / 255d) * rotated.getHeight())));
        graphics.drawImage(translated, null, -width, -height);

        graphics.dispose();

        return rotated;
    }
}
