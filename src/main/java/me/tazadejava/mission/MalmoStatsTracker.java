package me.tazadejava.mission;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

//use this stats tracker to output data similar to Malmo
public class MalmoStatsTracker implements StatsTracker {

    public class LastStatsSnapshot {

        public Statistic[] trackedStats;
        public int[] lastPlayerStats;
        public HashMap<String, Object> lastPlayerValues;
        public int lastPlayerDoorsOpened, lastPlayerFiresExtinguished;
        public HashMap<String, Integer> lastPlayerBlocksBroken;
        public List<Location> lastPlayerBlocksBrokenLocations;

        public LastStatsSnapshot(MalmoStatsTracker tracker, Player player) {
            this.trackedStats = tracker.trackedStats;

            if(tracker.lastPlayerStats.containsKey(player)) {
                this.lastPlayerStats = tracker.lastPlayerStats.get(player);
            }
            if(tracker.lastPlayerValues.containsKey(player)) {
                this.lastPlayerValues = tracker.lastPlayerValues.get(player);
            }
            if(tracker.lastPlayerDoorsOpened.containsKey(player)) {
                this.lastPlayerDoorsOpened = tracker.lastPlayerDoorsOpened.get(player);
            }
            if(tracker.lastPlayerFiresExtinguished.containsKey(player)) {
                this.lastPlayerFiresExtinguished = tracker.lastPlayerFiresExtinguished.get(player);
            }
            if(tracker.lastPlayerBlocksBroken.containsKey(player)) {
                this.lastPlayerBlocksBroken = tracker.lastPlayerBlocksBroken.get(player);
            }
            if(tracker.lastPlayerBlocksBrokenLocations.containsKey(player)) {
                this.lastPlayerBlocksBrokenLocations = tracker.lastPlayerBlocksBrokenLocations.get(player);
            }
        }

        private LastStatsSnapshot() {}

        public LastStatsSnapshot calculateDeltaSnapshot(LastStatsSnapshot lastStats) {
            LastStatsSnapshot deltaStats = new LastStatsSnapshot();

            deltaStats.trackedStats = trackedStats;

            deltaStats.lastPlayerStats = new int[lastPlayerStats.length];
            for(int i = 0; i < lastPlayerStats.length; i++) {
                deltaStats.lastPlayerStats[i] = lastPlayerStats[i] - lastStats.lastPlayerStats[i];
            }

            deltaStats.lastPlayerValues = new HashMap<>();
            for(Map.Entry<String, Object> entry : lastPlayerValues.entrySet()) {
                if(!lastStats.lastPlayerValues.containsKey(entry.getKey())) {
                    deltaStats.lastPlayerValues.put(entry.getKey(), entry.getValue());
                } else {
                    if(!lastStats.lastPlayerValues.get(entry.getKey()).equals(entry.getValue())) {
                        deltaStats.lastPlayerValues.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            deltaStats.lastPlayerDoorsOpened = lastPlayerDoorsOpened - lastStats.lastPlayerDoorsOpened;
            deltaStats.lastPlayerFiresExtinguished = lastPlayerFiresExtinguished - lastStats.lastPlayerFiresExtinguished;

            deltaStats.lastPlayerBlocksBroken = new HashMap<>();
            for(Map.Entry<String, Integer> entry : lastPlayerBlocksBroken.entrySet()) {
                if(!lastStats.lastPlayerBlocksBroken.containsKey(entry.getKey())) {
                    deltaStats.lastPlayerBlocksBroken.put(entry.getKey(), entry.getValue());
                } else {
                    int delta = entry.getValue() - lastStats.lastPlayerBlocksBroken.get(entry.getKey());
                    if(delta > 0) {
                        deltaStats.lastPlayerBlocksBroken.put(entry.getKey(), delta);
                    }
                }
            }

            deltaStats.lastPlayerBlocksBrokenLocations = new ArrayList<>();
            if(lastPlayerBlocksBrokenLocations.size() > lastStats.lastPlayerBlocksBrokenLocations.size()) {
                for(int i = lastStats.lastPlayerBlocksBrokenLocations.size(); i < lastPlayerBlocksBrokenLocations.size(); i++) {
                    deltaStats.lastPlayerBlocksBrokenLocations.add(lastPlayerBlocksBrokenLocations.get(i));
                }
            }

            return deltaStats;
        }
    }

    private LinkedHashMap<String, Statistic[]> trackedStatNames;
    private HashMap<Statistic, Integer> trackedStatsIndices;
    private Statistic[] trackedStats;
    private Set<Statistic> ignoredStatChanges;

    private HashMap<Player, int[]> initPlayerStats;
    private HashMap<Player, int[]> lastPlayerStats;

    // values stores data like health, score, foodlevel, etc; stats store data taken by server
    private HashMap<Player, HashMap<String, Object>> lastPlayerValues;

    // store dynamic event listening events
    private HashMap<Player, Integer> lastPlayerDoorsOpened, lastPlayerFiresExtinguished;
    private HashMap<Player, HashMap<String, Integer>> lastPlayerBlocksBroken;
    private HashMap<Player, List<Location>> lastPlayerBlocksBrokenLocations;

    private JavaPlugin plugin;
    private MissionEventListener eventListener;
    private JsonObject jsonLog;

    private List<Player> playerList;

    public MalmoStatsTracker(JavaPlugin plugin, MissionEventListener eventListener, JsonObject jsonLog) {
        this.plugin = plugin;
        this.eventListener = eventListener;
        this.jsonLog = jsonLog;

        generateTrackedStats();
    }

    private void generateTrackedStats() {
        trackedStatNames = new LinkedHashMap<>();
        trackedStatsIndices = new HashMap<>();
        lastPlayerValues = new HashMap<>();
        lastPlayerDoorsOpened = new HashMap<>();
        lastPlayerFiresExtinguished = new HashMap<>();
        lastPlayerBlocksBroken = new HashMap<>();
        lastPlayerBlocksBrokenLocations = new HashMap<>();

        trackedStatNames.put("Distance Travelled", new Statistic[] {Statistic.FALL_ONE_CM, Statistic.SPRINT_ONE_CM, Statistic.SWIM_ONE_CM, Statistic.WALK_UNDER_WATER_ONE_CM, Statistic.WALK_ON_WATER_ONE_CM, Statistic.AVIATE_ONE_CM, Statistic.BOAT_ONE_CM, Statistic.CLIMB_ONE_CM, Statistic.CROUCH_ONE_CM, Statistic.PIG_ONE_CM, Statistic.FLY_ONE_CM, Statistic.HORSE_ONE_CM, Statistic.MINECART_ONE_CM, Statistic.WALK_ONE_CM});
        trackedStatNames.put("TimeAlive", new Statistic[] {Statistic.TIME_SINCE_DEATH});
        trackedStatNames.put("MobsKilled", new Statistic[] {Statistic.MOB_KILLS});
        trackedStatNames.put("PlayersKilled", new Statistic[] {Statistic.PLAYER_KILLS});
        trackedStatNames.put("DamageTaken", new Statistic[] {Statistic.DAMAGE_TAKEN});
        trackedStatNames.put("DamageDealt", new Statistic[] {Statistic.DAMAGE_DEALT});

        ignoredStatChanges = new HashSet<>();
        ignoredStatChanges.add(Statistic.TIME_SINCE_DEATH);

        List<Statistic> statsList = new ArrayList<>();
        int index = 0;
        for(String key : trackedStatNames.keySet()) {
            Statistic[] stats = trackedStatNames.get(key);
            for(int i = 0; i < stats.length; i++) {
                trackedStatsIndices.put(stats[i], index);
                statsList.add(stats[i]);
                index++;
            }
        }

        trackedStats = statsList.toArray(new Statistic[0]);
    }

    public void startTracking() {
        //the players currently on the server will be automatically included in the mission
        playerList = new ArrayList<>();
        initPlayerStats = new HashMap<>();
        lastPlayerStats = new HashMap<>();

        lastPlayerDoorsOpened.clear();
        lastPlayerFiresExtinguished.clear();
        lastPlayerBlocksBroken.clear();
        lastPlayerBlocksBrokenLocations.clear();

        for(Player p : plugin.getServer().getOnlinePlayers()) {
            playerList.add(p);

            int[] initStats = new int[trackedStats.length];

            for(int i = 0; i < trackedStats.length; i++) {
                initStats[i] = p.getStatistic(trackedStats[i]);
            }

            initPlayerStats.put(p, initStats);
            lastPlayerStats.put(p, initStats);
        }

        eventListener.initMission(playerList);
    }

    public boolean appendCurrentStatsToLog() {
        JsonObject logsByPlayer = new JsonObject();
        for(Player p : playerList) {
            boolean statChanged = false;

            //stats
            int[] initStats = initPlayerStats.get(p);
            int[] lastStats = lastPlayerStats.get(p);
            int[] newStats = new int[trackedStats.length];
            int[] deltaStats = new int[trackedStats.length];

            for(int i = 0; i < trackedStats.length; i++) {
                newStats[i] = p.getStatistic(trackedStats[i]);

                deltaStats[i] = newStats[i] - initStats[i];

                if(newStats[i] - lastStats[i] != 0 && !ignoredStatChanges.contains(trackedStats[i])) {
                    statChanged = true;
                }
            }

            //values
            boolean updateValues = false;
            if(!lastPlayerValues.containsKey(p)) {
                updateValues = true;
            }

            HashMap<String, Object> newValues = generatePlayerValues(p);

            if(!updateValues) {
                HashMap<String, Object> lastValues = lastPlayerValues.get(p);

                for(String key : lastValues.keySet()) {
                    if(!lastValues.get(key).equals(newValues.get(key))) {
                        updateValues = true;
                        statChanged = true;
                    }
                }
            }

            if(updateValues) {
                lastPlayerValues.put(p, newValues);
            }

            //event listener: doors
            int doorsOpened = eventListener.getDoorsOpened(p);
            if(!lastPlayerDoorsOpened.containsKey(p) || doorsOpened != lastPlayerDoorsOpened.get(p)) {
                lastPlayerDoorsOpened.put(p, doorsOpened);
                statChanged = true;
            }

            int firesExtinguished = eventListener.getFiresExtinguished(p);
            if(!lastPlayerFiresExtinguished.containsKey(p) || firesExtinguished != lastPlayerFiresExtinguished.get(p)) {
                lastPlayerFiresExtinguished.put(p, firesExtinguished);
                statChanged = true;
            }

            //event listener: blocks broken and locations of these broken blocks
            HashMap<String, Integer> blocksBroken = eventListener.getBlocksBroken(p);
            List<Location> blocksBrokenLocations = eventListener.getBlocksBrokenLocations(p);

            if(!lastPlayerBlocksBroken.containsKey(p) || blocksBroken.size() != lastPlayerBlocksBroken.get(p).size()) {
                //create a new hashmap to ensure snapshot data is new and not overwritten
                lastPlayerBlocksBroken.put(p, new HashMap<>(blocksBroken));
                lastPlayerBlocksBrokenLocations.put(p, new ArrayList<>(blocksBrokenLocations));
                statChanged = true;
            } else {
                HashMap<String, Integer> lastBlocksBroken = lastPlayerBlocksBroken.get(p);
                //if the sizes are the same, then they must have the same blocks, since we can only add blocks to hashmap, not remove them
                for(String key : blocksBroken.keySet()) {
                    if(lastBlocksBroken.get(key) != blocksBroken.get(key)) {
                        //create a new hashmap to ensure snapshot data is new and not overwritten
                        lastPlayerBlocksBroken.put(p, new HashMap<>(blocksBroken));
                        lastPlayerBlocksBrokenLocations.put(p, new ArrayList<>(blocksBrokenLocations));
                        statChanged = true;
                        break;
                    }
                }
            }

            if(statChanged) {
                lastPlayerStats.put(p, newStats);

                JsonObject playerLog = new JsonObject();

                for(String key : trackedStatNames.keySet()) {
                    int sum = 0;

                    for(Statistic stat : trackedStatNames.get(key)) {
                        sum += deltaStats[trackedStatsIndices.get(stat)];
                    }

                    playerLog.addProperty(key, sum);
                }

                for(String key : newValues.keySet()) {
                    if(newValues.get(key) instanceof JsonElement) {
                        playerLog.add(key, (JsonElement) newValues.get(key));
                    } else {
                        playerLog.addProperty(key, String.valueOf(newValues.get(key)));
                    }
                }

                playerLog.addProperty("DoorsOpened", String.valueOf(doorsOpened));
                playerLog.addProperty("FiresExtinguished", String.valueOf(firesExtinguished));

                JsonArray blocksDigged = new JsonArray();

                for(Map.Entry<String, Integer> entry : blocksBroken.entrySet()) {
                    JsonObject blockCount = new JsonObject();
                    blockCount.addProperty(entry.getKey(), entry.getValue());
                    blocksDigged.add(blockCount);
                }

                playerLog.add("BlocksBroken", blocksDigged);

                logsByPlayer.add(p.getDisplayName(), playerLog);
            }
        }

        if(logsByPlayer.size() > 0) {
            jsonLog.add(LocalDateTime.now().toString(), logsByPlayer);
            return true;
        }

        return false;
    }

    private HashMap<String, Object> generatePlayerValues(Player p) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();

        values.put("Life", p.getHealth());
        values.put("Food", p.getFoodLevel());
        values.put("XP", p.getTotalExperience());
        values.put("IsAlive", !p.isDead());
        values.put("Air", p.getRemainingAir());
        values.put("Name", p.getName());
        values.put("IsSprinting", p.isSprinting());

        Location loc = p.getLocation();
        values.put("XPos", loc.getX());
        values.put("YPos", loc.getY());
        values.put("ZPos", loc.getZ());
        values.put("Pitch", loc.getPitch());
        values.put("Yaw", loc.getYaw());


        World world = p.getWorld();
        values.put("WorldTime", world.getTime());
        values.put("TotalTime", world.getFullTime());

        values.put("cell", "(" + loc.getBlockX() + "," + loc.getBlockZ() + ")");

        addLineOfSight(values, p);

        addHotbarItems(values, p);

        addNearbyBlocks(values, loc);

        return values;
    }

    private void addLineOfSight(HashMap<String, Object> values, Player p) {
        Predicate<Entity> noPlayerPredicate = new Predicate<Entity>() {
            @Override
            public boolean test(Entity entity) {
                return !entity.equals(p);
            }
        };

        RayTraceResult rayTrace = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), 50, FluidCollisionMode.SOURCE_ONLY, true, 1, noPlayerPredicate);

        if(rayTrace == null) {
            values.put("LineOfSight", "");
        } else {
            JsonObject rayHitDetails = new JsonObject();

            double distance = rayTrace.getHitPosition().distance(p.getLocation().toVector());

            if(rayTrace.getHitEntity() != null) {
                Entity ent = rayTrace.getHitEntity();
                Location entLoc = ent.getLocation();
                rayHitDetails.addProperty("x", entLoc.getX());
                rayHitDetails.addProperty("y", entLoc.getY());
                rayHitDetails.addProperty("z", entLoc.getZ());
                rayHitDetails.addProperty("yaw", entLoc.getYaw());
                rayHitDetails.addProperty("pitch", entLoc.getPitch());

                if(ent.getType() == EntityType.DROPPED_ITEM) {
                    Item item = (Item) ent;
                    ItemStack itemStack = item.getItemStack();

                    rayHitDetails.addProperty("stackSize", itemStack.getAmount());
                    rayHitDetails.addProperty("hitType", "item");
                } else {
                    rayHitDetails.addProperty("hitType", "entity");
                }

                rayHitDetails.addProperty("type", ent.getType().toString().toLowerCase());
                rayHitDetails.addProperty("inRange", distance <= 2.5);
                rayHitDetails.addProperty("distance", distance);
            } else {
                rayHitDetails.addProperty("hitType", "block");
//                rayHitDetails.addProperty("x", rayTrace.getHitPosition().getX());
//                rayHitDetails.addProperty("y", rayTrace.getHitPosition().getY());
//                rayHitDetails.addProperty("z", rayTrace.getHitPosition().getZ());
                rayHitDetails.addProperty("x", rayTrace.getHitBlock().getX());
                rayHitDetails.addProperty("y", rayTrace.getHitBlock().getY());
                rayHitDetails.addProperty("z", rayTrace.getHitBlock().getZ());
                rayHitDetails.addProperty("type", rayTrace.getHitBlock().getType().toString().toLowerCase());
                rayHitDetails.addProperty("inRange", distance <= 4.5); //this range is only valid for multiplayer
                rayHitDetails.addProperty("distance", distance);
            }

            values.put("LineOfSight", rayHitDetails);
        }
    }

//    private void addLineOfSight(HashMap<String, Object> values, Player p) {
//        RayTraceResult blockRayTrace = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 50, FluidCollisionMode.SOURCE_ONLY, true);
//        RayTraceResult entityRayTrace = rayTraceEntity(p);
//
//        if(blockRayTrace == null && entityRayTrace == null) {
//            values.put("LineOfSight", "");
//        } else {
//            JsonObject rayHitDetails = new JsonObject();
//
//            if(entityRayTrace != null) {
//                double distance = entityRayTrace.getHitPosition().distance(p.getEyeLocation().toVector());
//
//                Entity ent = entityRayTrace.getHitEntity();
//                Location entLoc = ent.getLocation();
//                rayHitDetails.addProperty("x", entLoc.getX());
//                rayHitDetails.addProperty("y", entLoc.getY());
//                rayHitDetails.addProperty("z", entLoc.getZ());
//                rayHitDetails.addProperty("yaw", entLoc.getYaw());
//                rayHitDetails.addProperty("pitch", entLoc.getPitch());
//
//                if(ent.getType() == EntityType.DROPPED_ITEM) {
//                    Item item = (Item) ent;
//                    ItemStack itemStack = item.getItemStack();
//
//                    rayHitDetails.addProperty("stackSize", itemStack.getAmount());
//                    rayHitDetails.addProperty("hitType", "item");
//                } else {
//                    rayHitDetails.addProperty("hitType", "entity");
//                }
//
//                rayHitDetails.addProperty("type", ent.getType().toString().toLowerCase());
//                rayHitDetails.addProperty("inRange", distance <= 3);
//                rayHitDetails.addProperty("distance", distance);
//
//                Bukkit.broadcastMessage(entityRayTrace.getHitEntity().getType().toString() + " " + distance);
//            } else {
//                double distance = blockRayTrace.getHitPosition().distance(p.getEyeLocation().toVector());
//
//                rayHitDetails.addProperty("hitType", "block");
//                rayHitDetails.addProperty("x", blockRayTrace.getHitPosition().getX());
//                rayHitDetails.addProperty("y", blockRayTrace.getHitPosition().getY());
//                rayHitDetails.addProperty("z", blockRayTrace.getHitPosition().getZ());
//                rayHitDetails.addProperty("type", blockRayTrace.getHitBlock().getType().toString().toLowerCase());
//                rayHitDetails.addProperty("inRange", distance <= 4.5); //this range is only valid for multiplayer
//                rayHitDetails.addProperty("distance", distance);
//
//                Bukkit.broadcastMessage(blockRayTrace.getHitBlock().getType().toString() + " " + distance);
//            }
//
//            values.put("LineOfSight", rayHitDetails);
//        }
//    }

    //allows async retrieval of entities around the player, 32-40 blocks away
//    private RayTraceResult rayTraceEntity(Player p) {
////        RayTraceResult rayTrace = new RayTraceResult();
//        Location loc = p.getEyeLocation();
//        List<Entity> entities = new ArrayList<>();
//        for(int dx = -2; dx <= 2; dx++) {
//            for(int dz = -2; dz <= 2; dz++) {
//                Chunk chunk = p.getWorld().getChunkAt(loc.getBlockX() + (dx * 16), loc.getBlockZ() + (dz * 16));
//                if(!chunk.isLoaded()) {
//                    continue;
//                }
//
//                for(Entity ent : chunk.getEntities()) {
//                    if(ent != p) {
//                        entities.add(ent);
//                    }
//                }
//            }
//        }
//
//
//
//        return null;
//    }

    private void addHotbarItems(HashMap<String, Object> values, Player p) {
        PlayerInventory inv = p.getInventory();
        for(int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);

            if(item == null) {
                values.put("Hotbar_" + i + "_size", 0);
                values.put("Hotbar_" + i + "_item", "air");
            } else {
                values.put("Hotbar_" + i + "_size", item.getAmount());
                values.put("Hotbar_" + i + "_item", item.getType().toString().toLowerCase());
            }
        }
    }

    private void addNearbyBlocks(HashMap<String, Object> values, Location loc) {
        JsonArray nearbyBlocksJson = new JsonArray();
        for(int dy = -1; dy <= 2; dy++) {
            for(int dz = -1; dz <= 1; dz++) {
                for(int dx = -1; dx <= 1; dx++) {
                    Block block = loc.add(dx, dy, dz).getBlock();
                    nearbyBlocksJson.add(block.getType().toString().toLowerCase());
                }
            }
        }
        values.put("nearby", nearbyBlocksJson);
    }

    public LastStatsSnapshot getLastStatsSnapshot(Player player) {
        return new LastStatsSnapshot(this, player);
    }

    public List<Player> getPlayerList() {
        return playerList;
    }
}
