package me.tazadejava.actiontracker;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Holds miscellaneous utility functions used throughout the project.
 */
public class Utils {

    public static boolean isInteger(String val) {
        try {
            Integer.parseInt(val);
            return true;
        } catch(NumberFormatException ex) {
            return false;
        }
    }

    public static String getFormattedLocation(Location loc) {
        return "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    /**
     * Allows saving a location to a JSON file via the JsonObject
     * @param loc
     * @return
     */
    public static JsonObject saveLocation(Location loc) {
        JsonObject content = new JsonObject();
        content.addProperty("world", loc.getWorld().getUID().toString());
        content.addProperty("x", loc.getX());
        content.addProperty("y", loc.getY());
        content.addProperty("z", loc.getZ());
        content.addProperty("yaw", loc.getYaw());
        content.addProperty("pitch", loc.getPitch());

        return content;
    }

    /**
     * Retrieves a saved JsonObject location from the saveLocation method and converts it back into a Location
     * @param object
     * @return
     */
    public static Location getLocation(JsonObject object) {
        return new Location(Bukkit.getWorld(UUID.fromString(object.get("world").getAsString())), object.get("x").getAsDouble(), object.get("y").getAsDouble(), object.get("z").getAsDouble(), object.get("yaw").getAsFloat(), object.get("pitch").getAsFloat());
    }
}
