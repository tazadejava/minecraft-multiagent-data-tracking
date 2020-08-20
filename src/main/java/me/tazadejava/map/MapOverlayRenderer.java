package me.tazadejava.map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Simple map overlay renderer that will only show the player on the map without rotating or translating the map. Does not include any overlay features like in the DynamicMapRenderer. Currently only works with Sparky map.
 */
public class MapOverlayRenderer extends MapRenderer {

    public static ItemStack getMap() {
        MapView map = Bukkit.createMap(Bukkit.getWorlds().get(0));
        map.getRenderers().clear();
        map.setLocked(true);
        map.setUnlimitedTracking(false);
        map.setScale(MapView.Scale.NORMAL);
        map.addRenderer(new MapOverlayRenderer());

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

        if(playerX < xRange[0]) {
            pointerX = -128;
        } else if(playerX > xRange[1]) {
            pointerX = 127;
        } else {
            double playerScale = (double) (playerX - xRange[0]) / xLength;
            pointerX = (byte) Math.floor((playerScale * 256) - 128);
        }

        if(playerZ < zRange[0]) {
            pointerZ = -128;
        } else if(playerZ > zRange[1]) {
            pointerZ = 127;
        } else {
            double playerScale = (double) (playerZ - zRange[0]) / zLength;
            pointerZ = (byte) Math.floor((playerScale * 256) - 128);
        }

        byte direction = (byte) Math.floor(((player.getLocation().getYaw() % 360) / 360d) * 16);

        try {
            InputStream stream = getClass().getClassLoader().getResourceAsStream("sparky_map.png");
            BufferedImage image = ImageIO.read(stream);
            canvas.drawImage(0, 0, image);
            stream.close();

            MapCursorCollection cursors = new MapCursorCollection();

            cursors.addCursor(new MapCursor(pointerX, pointerZ, direction, MapCursor.Type.RED_POINTER, true));

            canvas.setCursors(cursors);
        } catch (IOException e) {
            e.printStackTrace();
        } catch(IllegalArgumentException ex) {}
    }
}
