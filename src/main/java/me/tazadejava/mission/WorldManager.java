package me.tazadejava.mission;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class that will autoload worlds that were not part of the original worlds (typically world, world_nether, world_end).
 *
 * This allows for missions that are on different worlds to be used effectively.
 */
public class WorldManager {

    private JavaPlugin plugin;
    private Gson gson;

    private List<String> loadedWorlds = new ArrayList<>();

    public WorldManager(JavaPlugin plugin) {
        this.plugin = plugin;

        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void loadWorlds() {
        File worldFile = new File(plugin.getDataFolder().getAbsolutePath() + "/worlds.json");

        if(worldFile.exists()) {
            try {
                FileReader reader = new FileReader(worldFile);
                JsonObject data = gson.fromJson(reader, JsonObject.class);
                reader.close();

                JsonArray worldsList = data.getAsJsonArray("worlds");

                for(JsonElement world : worldsList) {
                    loadedWorlds.add(world.getAsString());
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            for(String world : loadedWorlds) {
                Bukkit.getLogger().info("Loaded the world " + world + " from folder.");
                new WorldCreator(world).createWorld();
            }
        }
    }

    public void saveData() {
        try {
            File worldFile = new File(plugin.getDataFolder().getAbsolutePath() + "/worlds.json");

            if(!worldFile.exists()) {
                worldFile.createNewFile();
            }

            JsonObject data = new JsonObject();

            JsonArray worldsList = new JsonArray();

            for(String worldName : loadedWorlds) {
                worldsList.add(worldName);
            }

            data.add("worlds", worldsList);

            FileWriter writer = new FileWriter(worldFile);
            gson.toJson(data, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean loadNewWorld(String name) {
       if(!doesWorldFolderExist(name)) {
           return false;
       }

       new WorldCreator(name).createWorld();
       loadedWorlds.add(name);
       saveData();
       return true;
    }

    public boolean doesWorldFolderExist(String name) {
        File mainFolder = Bukkit.getWorldContainer();

        for(File file : mainFolder.listFiles()) {
            if(file.isDirectory()) {
                if(file.getName().equals(name)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isWorldLoaded(String name) {
        return loadedWorlds.contains(name);
    }

    public boolean unloadWorld(String name) {
        if(!loadedWorlds.contains(name)) {
            return false;
        }

        Bukkit.unloadWorld(name, true);
        loadedWorlds.remove(name);
        saveData();
        return true;
    }

    public List<String> getWorlds() {
        return loadedWorlds;
    }
}
