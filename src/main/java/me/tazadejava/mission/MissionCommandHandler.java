package me.tazadejava.mission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.tazadejava.actiontracker.ActionTrackerPlugin;
import me.tazadejava.actiontracker.Utils;
import me.tazadejava.blockranges.BlockRange2D;
import me.tazadejava.blockranges.SelectionWand;
import me.tazadejava.blockranges.SpecialItem;
import me.tazadejava.map.DynamicMapRenderer;
import me.tazadejava.map.MapOverlayRenderer;
import me.tazadejava.statstracker.PreciseVisibleBlocksRaycaster;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.StringUtil;
import org.bukkit.util.Vector;

import java.io.*;
import java.util.*;

//handles the player's commands to pass to the MissionHandler class
public class MissionCommandHandler implements CommandExecutor, TabCompleter {

    private static final String GENERIC_IMPROPER_COMMAND_MESSAGE = "Improper command. Usage: /mission <create/start/abort/list/set/add/getitem> [command-specific arguments]";

    private final ActionTrackerPlugin plugin;
    private final MissionManager missionManager;
    private final HashMap<String, SpecialItem> specialItems;

    private HashMap<Location, BlockState> lastBlockState = new HashMap<>();
    private HashMap<Location, Player> blockPlayer = new HashMap<>();
    private Set<Material> excludedTransformations = new HashSet<>();

    private HashMap<Player, MissionRoom> roomBuilderRooms = new HashMap<>();
    private HashMap<Player, Mission> roomBuilderMissions = new HashMap<>();
    private HashMap<Player, int[]> roomBuilderRaycastYBounds = new HashMap<>();

    public MissionCommandHandler(ActionTrackerPlugin plugin, MissionManager missionManager, HashMap<String, SpecialItem> specialItems) {
        this.plugin = plugin;
        this.missionManager = missionManager;
        this.specialItems = specialItems;

        defineExcludedTransformations();
    }

    private void defineExcludedTransformations() {
//        excludedTransformations.add(Material.ACACIA_DOOR);
//        excludedTransformations.add(Material.OAK_DOOR);
//        excludedTransformations.add(Material.REDSTONE_TORCH);
//        excludedTransformations.add(Material.REDSTONE);
//        excludedTransformations.add(Material.REPEATER);
//        excludedTransformations.add(Material.COMPARATOR);
//        excludedTransformations.add(Material.CHEST);
//        excludedTransformations.add(Material.ENDER_CHEST);
//        excludedTransformations.add(Material.LEVER);
//        excludedTransformations.add(Material.STONE_BUTTON);
//        excludedTransformations.add(Material.TRIPWIRE_HOOK);
//        excludedTransformations.add(Material.OAK_SIGN);
//        excludedTransformations.add(Material.OAK_WALL_SIGN);
    }

    public void restoreBlocks(CommandSender sender) {
        if(!lastBlockState.isEmpty()) {
            for(Location loc : lastBlockState.keySet()) {
                Block block = loc.getBlock();
                BlockState state = lastBlockState.get(loc);

                blockPlayer.get(block.getLocation()).sendBlockChange(block.getLocation(), state.getBlockData());
            }
            if(sender != null) {
                sender.sendMessage("Restored " + lastBlockState.size() + " blocks.");
            }

            lastBlockState.clear();
            blockPlayer.clear();
        }
    }

    public void saveBlocks(CommandSender sender) {
        if(!lastBlockState.isEmpty()) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            JsonObject mainObjectLocs = new JsonObject();
            JsonObject mainObjectIndices = new JsonObject();

            JsonObject blockMapping = new JsonObject();

            mainObjectLocs.addProperty("missionName", "Test");
            mainObjectIndices.addProperty("missionName", "Test");

            JsonArray visibleBlocks = new JsonArray();
            JsonArray visibleBlocksIndices = new JsonArray();
            int index = 0;
            for(Location loc : lastBlockState.keySet()) {
                Block block = loc.getBlock();
                BlockState state = lastBlockState.get(loc);

                blockPlayer.get(block.getLocation()).sendBlockChange(block.getLocation(), state.getBlockData());

                String locString = block.getLocation().getBlockX() + " " + block.getLocation().getBlockY() + " " + block.getLocation().getBlockZ();
                visibleBlocks.add(locString);

                if(index == 0) {
                    blockMapping.addProperty("world", loc.getWorld().getName());
                }

                blockMapping.addProperty(locString, index);
                visibleBlocksIndices.add(index);
                index++;
            }

            mainObjectLocs.add("visibleBlocks", visibleBlocks);
            mainObjectIndices.add("visibleBlocks", visibleBlocksIndices);

            try {
                File saveFolder = new File(plugin.getDataFolder() + "/raycasts/");
                if(!saveFolder.exists()){
                    saveFolder.mkdir();
                }

                File saveFile = new File(plugin.getDataFolder() + "/raycasts/savetestLocs.json");
                if(!saveFile.exists()) {
                    saveFile.createNewFile();
                }

                FileWriter fileWriter = new FileWriter(saveFile);
                gson.toJson(mainObjectLocs, fileWriter);
                fileWriter.close();

                //save again for indices this time

                saveFile = new File(plugin.getDataFolder() + "/raycasts/savetestIndices.json");
                if(!saveFile.exists()) {
                    saveFile.createNewFile();
                }

                fileWriter = new FileWriter(saveFile);
                gson.toJson(mainObjectIndices, fileWriter);
                fileWriter.close();

                //save again for mapping this time

                saveFile = new File(plugin.getDataFolder() + "/raycasts/indexMapping.json");
                if(!saveFile.exists()) {
                    saveFile.createNewFile();
                }

                fileWriter = new FileWriter(saveFile);
                gson.toJson(blockMapping, fileWriter);
                fileWriter.close();

                sender.sendMessage("Saved the files!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void raycastTest(CommandSender commandSender, boolean restoreAfterEach, boolean loopRaycast) {
        raycastTest(commandSender, restoreAfterEach, loopRaycast, 0, 255);
    }

    private void raycastTest(CommandSender commandSender, boolean restoreAfterEach, boolean loopRaycast, int lowYBound, int upperYBound) {
        Player player = (Player) commandSender;

        restoreBlocks(player);

        lastBlockState.clear();
        blockPlayer.clear();

        PreciseVisibleBlocksRaycaster raycaster = new PreciseVisibleBlocksRaycaster(true, true, true, lowYBound, upperYBound);

        BlockData defaultMaterial = Bukkit.getServer().createBlockData(Material.GLASS);

        HashMap<Material, BlockData> customMaterials = new HashMap<>();

        customMaterials.put(Material.PRISMARINE, Bukkit.getServer().createBlockData(Material.MAGMA_BLOCK));
        customMaterials.put(Material.GOLD_BLOCK, Bukkit.getServer().createBlockData(Material.GLOWSTONE));

        new BukkitRunnable() {

            int count = 0;
            int sneakCount = 0;

            @Override
            public void run() {
                if (restoreAfterEach) {
                    restoreBlocks(commandSender);
                }

                long begin = System.currentTimeMillis();
                Set<Block> blocks = raycaster.getVisibleBlocks((Player) commandSender, lastBlockState.keySet());
                Bukkit.broadcastMessage((System.currentTimeMillis() - begin) + " MS to raycast");

                for (Block block : blocks) {
                    if (excludedTransformations.contains(block.getType())) {
                        continue;
                    }

                    lastBlockState.put(block.getLocation(), block.getState());
                    blockPlayer.put(block.getLocation(), player);

                    if(customMaterials.containsKey(block.getType())) {
                        player.sendBlockChange(block.getLocation(), customMaterials.get(block.getType()));
                    } else {
                        player.sendBlockChange(block.getLocation(), defaultMaterial);
                    }
                }

                count++;

                if (player.isSneaking()) {
                    sneakCount++;
                } else {
                    if (sneakCount != 0) {
                        sneakCount = 0;
                    }
                }

                if (sneakCount >= 6) {
                    if (restoreAfterEach) {
                        restoreBlocks(commandSender);
                        player.sendMessage("Abort raycaster.");
                    } else {
                        player.sendMessage("Abort raycaster. Type in /mission raycastreset to restore blocks, or perform the test again.");
                    }
                    cancel();
                }

                if(!loopRaycast) {
                    player.sendMessage("Abort raycaster. Type in /mission raycastreset to restore blocks, or perform the test again.");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 4L);
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        String[] argsOriginal = args.clone();
        //make all arguments lowercase to allow for switch parameter
        for(int i = 0; i < args.length; i++) {
            args[i] = args[i].toLowerCase();
        }

        if(args.length == 0) {
            commandSender.sendMessage(GENERIC_IMPROPER_COMMAND_MESSAGE);
        } else {
            switch(args[0].toLowerCase()) {
                case "raycast": //this will draw a continuous raycast to all visible blocks.
                    raycastTest(commandSender, false, true);
                    break;
                case "raycastybounds": //this will draw a continuous raycast to all visible blocks.
                    raycastTest(commandSender, false, true, 52, 53);
                    break;
                case "raycastdiscrete": //this will raycast, but will reset the raycasted before raycasting again. may flicker, watch out.
                    raycastTest(commandSender, true, true);
                    break;
                case "raycastonce": //raycast once only
                    raycastTest(commandSender, true, false);
                    break;
                case "raycastsave": //saves the current viewed blocks to JSON file
                    saveBlocks(commandSender);
                    break;
                case "raycastreset": //resets the blocks that are converted to glass
                    restoreBlocks(commandSender);
                    break;

                case "test":
                    missionManager.getMission(args[1]).getOriginalMissionGraph().getShortestPathToAllVertices(MissionGraph.MissionVertexType.ROOM, "0");
                    break;

                case "graphtest": //testing command to find distances between any two nodes; unsafe to crashing if supplied incorrect arguments
                    if(args.length < 6) {
                        commandSender.sendMessage("/mission graphtest <mission name> <room/decision> <name> <room/decision> <name>");
                        break;
                    } else {
                        Mission mission = missionManager.getMission(args[1]);

                        MissionGraph.MissionVertexType type1 = MissionGraph.MissionVertexType.valueOf(args[2].toUpperCase());
                        MissionGraph.MissionVertexType type2 = MissionGraph.MissionVertexType.valueOf(args[4].toUpperCase());

                        String name1 = args[3];
                        String name2 = args[5];

                        commandSender.sendMessage("Path between " + type1 + " " + name1 + " and " + type2 + " " + name2 + ":");

                        MissionGraph.VertexPath path = mission.getOriginalMissionGraph().getShortestPathUsingEdges(type1, name1, type2, name2);

                        if(path == null) {
                            commandSender.sendMessage("No path found.");
                            break;
                        }

                        commandSender.sendMessage(ChatColor.LIGHT_PURPLE + path.getPath().toString());
                        commandSender.sendMessage(ChatColor.GREEN + "Length: " + path.getPathLength() + " blocks");

                        if(path.getPath().size() > 0) {
                            LinkedList<MissionGraph.MissionVertex> pathTrace = (LinkedList<MissionGraph.MissionVertex>) path.getPath().clone();

                            Location firstLoc = pathTrace.poll().location;
                            Entity ent = firstLoc.getWorld().spawnEntity(firstLoc, EntityType.ARMOR_STAND);

                            commandSender.sendMessage("Showing path...");
                            new BukkitRunnable() {

                                @Override
                                public void run() {
                                    if (pathTrace.isEmpty()) {
                                        cancel();
                                        ent.remove();
                                        commandSender.sendMessage("Path show ended.");
                                        return;
                                    }

                                    ent.teleport(pathTrace.poll().location);
                                }
                            }.runTaskTimer(plugin, 20L, 20L);
                        }
                    }
                    break;
                case "create":
                    if(args.length < 2) {
                        commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission create <mission name>");
                        break;
                    }

                    if(missionManager.createMission(argsOriginal[1])) {
                        Mission mission = missionManager.getMission(args[1]);
                        commandSender.sendMessage(ChatColor.GREEN + "Created the mission!");
                        sendMissionCompletionProgress(commandSender, mission);
                    } else {
                        commandSender.sendMessage(ChatColor.RED + "A command with that name already exists!");
                    }
                    break;
                case "list":
                    Collection<Mission> missions = missionManager.getAllMissions();
                    if(missions == null) {
                        commandSender.sendMessage(ChatColor.RED + "There are no missions.");
                        break;
                    }

                    commandSender.sendMessage(ChatColor.BLUE + "Missions: (" + missions.size() + ") exist:");
                    for(Mission mission : missions) {
                        commandSender.sendMessage((mission.canRunMission() ? ChatColor.GREEN : ChatColor.RED) + "- " + mission.getMissionName());
                    }
                    break;
                case "info":
                    if(args.length < 2) {
                        commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission info <mission name>");
                        break;
                    }
                    if(!missionManager.doesMissionExist(args[1])) {
                        commandSender.sendMessage(ChatColor.RED + "A command with that name does not exist!");
                        break;
                    }

                    Mission mission = missionManager.getMission(args[1]);

                    commandSender.sendMessage(ChatColor.DARK_PURPLE + "Mission stats for " + mission.getMissionName() + ":");

                    commandSender.sendMessage(ChatColor.LIGHT_PURPLE + "  Number of rooms: " + mission.getRooms().size());

                    commandSender.sendMessage(ChatColor.LIGHT_PURPLE + "  Number of decision points (armor stands, shown for 5 seconds): " + mission.getDecisionPoints().size());

                    List<Entity> entities = new ArrayList<>();
                    for(Location loc : mission.getDecisionPoints().values()) {
                        entities.add(loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND));
                    }

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            for(Entity ent : entities) {
                                ent.remove();
                            }
                        }
                    }.runTaskLater(plugin, 100L);

                    break;
                case "set":
                    if(args.length < 3) {
                        commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission set <mission name> <duration/location/edges> [additional arguments]");
                        break;
                    }
                    if(!missionManager.doesMissionExist(args[1])) {
                        commandSender.sendMessage(ChatColor.RED + "A command with that name does not exist!");
                        break;
                    }

                    mission = missionManager.getMission(args[1]);

                    if(missionManager.isMissionInProgress(mission)) {
                        commandSender.sendMessage(ChatColor.RED + "That mission is currently in progress! You cannot modify an ongoing mission.");
                        break;
                    }

                    args[2] = args[2].toLowerCase();
                    switch(args[2]) {
                        case "duration":
                            if(args.length < 4) {
                                commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission set <mission name> duration <duration in seconds>");
                                break;
                            }
                            if(!Utils.isInteger(args[3])) {
                                commandSender.sendMessage(ChatColor.RED + "Duration must be an integer! Usage: /mission set <mission name> duration <duration in seconds>");
                                break;
                            }

                            mission.setDuration(Integer.parseInt(args[3]));
                            commandSender.sendMessage(ChatColor.GREEN + "Set mission duration to " + args[3] + " seconds!");
                            sendMissionCompletionProgress(commandSender, mission);
                            missionManager.saveData();
                            break;
                        case "location":
                            if(!(commandSender instanceof Player)) {
                                commandSender.sendMessage(ChatColor.RED + "You must be a player to execute this command!");
                                break;
                            }

                            Player p = (Player) commandSender;

                            mission.setPlayerSpawnLocation(p.getLocation());
                            commandSender.sendMessage(ChatColor.GREEN + "Set the current location as the mission spawn point!");
                            sendMissionCompletionProgress(commandSender, mission);
                            missionManager.saveData();
                            break;
                        case "edges":
                            if(args.length < 4) {
                                commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission set <mission name> edges <edges filename (in UUID folder)>");
                                break;
                            }

                            File file = new File(plugin.getDataFolder() + "/" + mission.getMissionID() + "/" + args[3]);

                            if(!file.exists()) {
                                commandSender.sendMessage(ChatColor.RED + "That file does not exist.");
                                break;
                            }

                            mission.getOriginalMissionGraph().clearAllEdges();

                            int lines = 0;
                            try {
                                BufferedReader reader = new BufferedReader(new FileReader(file));

                                String read;
                                while((read = reader.readLine()) != null) {
                                    if(read.isEmpty()) {
                                        continue;
                                    }
                                    if(read.startsWith("#")) {
                                        continue;
                                    }

                                    lines++;
                                    String[] split = read.split(" ");

                                    MissionGraph.MissionVertexType type1 = split[0].charAt(0) == 'R' ? MissionGraph.MissionVertexType.ROOM : MissionGraph.MissionVertexType.DECISION;
                                    MissionGraph.MissionVertexType type2 = split[1].charAt(0) == 'R' ? MissionGraph.MissionVertexType.ROOM : MissionGraph.MissionVertexType.DECISION;

                                    String name1 = split[0].substring(1);
                                    String name2 = split[1].substring(1);

                                    mission.getOriginalMissionGraph().defineEdge(type1, name1, type2, name2);

                                    commandSender.sendMessage(ChatColor.GRAY + "Defined edge - " + type1 + ": " + name1 + " and " + type2 + ": " + name2 + ". " + ChatColor.DARK_GRAY + "Length: " + mission.getOriginalMissionGraph().getShortestPathUsingEdges(type1, name1, type2, name2).getPathLength());
                                }

                                reader.close();

                                missionManager.saveData();

                                commandSender.sendMessage(ChatColor.GREEN + "Done! Defined " + lines + " edges under the file: " + file.getName() + ".");
                            } catch(Exception e) {
                                commandSender.sendMessage(ChatColor.RED + "Something went wrong. The edges were not created.");
                                commandSender.sendMessage(ChatColor.RED + "ERROR: " + e.getMessage());
                                e.printStackTrace();
                            }

                            break;
                        default:
                            commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission set <mission name> <duration/location/edges> [additional arguments]");
                            break;
                    }
                    break;
                case "start":
                    if(args.length < 2) {
                        commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission start <mission name>");
                        break;
                    }
                    if(!missionManager.doesMissionExist(args[1])) {
                        commandSender.sendMessage(ChatColor.RED + "That mission does not exist!");
                        break;
                    }

                    mission = missionManager.getMission(args[1]);

                    if(!mission.canRunMission()) {
                        commandSender.sendMessage(ChatColor.RED + "You cannot yet run this mission!");
                        sendMissionCompletionProgress(commandSender, mission);
                        break;
                    }

                    if(missionManager.startMission(commandSender, mission)) {
                        commandSender.sendMessage(ChatColor.GREEN + "Mission started with " + mission.getDuration() + " seconds. Data is now being tracked.");
                    } else {
                        commandSender.sendMessage(ChatColor.RED + "A mission is already in progress!");
                    }
                    break;
                case "abort":
                    if(missionManager.abortMission()) {
                        commandSender.sendMessage(ChatColor.RED + "Mission aborted. A log was not saved.");
                    } else {
                        commandSender.sendMessage(ChatColor.RED + "No mission is currently in progress!");
                    }
                    break;
                case "add":
                    if(!(commandSender instanceof Player)) {
                        commandSender.sendMessage(ChatColor.RED + "You must be a player to execute this command!");
                        break;
                    }

                    Player p = (Player) commandSender;

                    if(args.length < 3) {
                        commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission add <mission name> <decisionpoint/edge> [command-specific arguments]");
                        break;
                    }

                    switch(args[2].toLowerCase()) {
                        case "decisionpoint":
                            if(args.length < 4) {
                                commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission add <mission name> decisionpoint <decision point name>");
                                break;
                            }
                            if(!missionManager.doesMissionExist(args[1])) {
                                commandSender.sendMessage(ChatColor.RED + "That mission does not exist!");
                                break;
                            }

                            mission = missionManager.getMission(args[1]);
                            String pointName = args[3];

                            if(mission.hasDecisionPoint(pointName)) {
                                commandSender.sendMessage(ChatColor.RED + "A decision point with that name already exists!");
                                break;
                            }

                            Location loc = p.getLocation().clone();
                            loc.setX(Math.round(loc.getX() * 2) / 2.0);
                            loc.setY(Math.round(loc.getY() * 2) / 2.0);
                            loc.setZ(Math.round(loc.getZ() * 2) / 2.0);
                            mission.addDecisionPoint(pointName, loc);
                            commandSender.sendMessage("You've added a decision point named " + pointName + " at your current location (" + loc.getX() + " " + loc.getY() + " " + loc.getZ() + ")!");

                            Entity armorStand = loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    armorStand.remove();
                                }
                            }.runTaskLater(plugin, 60L);

                            missionManager.saveData();
                            break;
                        case "edge":
                            if(args.length < 7) {
                                commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission add <mission name> edge <room/decision> <room/decision name 1> <room/decision> <room/decision name 2> [time between path trace in ticks, FOR animation]");
                                break;
                            }
                            if(!missionManager.doesMissionExist(args[1])) {
                                commandSender.sendMessage(ChatColor.RED + "That mission does not exist!");
                                break;
                            }

                            mission = missionManager.getMission(args[1]);

                            MissionGraph.MissionVertexType type1 = null, type2 = null;
                            try {
                                type1 = MissionGraph.MissionVertexType.valueOf(args[3].toUpperCase());
                                type2 = MissionGraph.MissionVertexType.valueOf(args[5].toUpperCase());
                            } catch(IllegalArgumentException ex) {
                                commandSender.sendMessage(ChatColor.RED + "That's not a valid node type. It must be either room or decision for each node.");
                                break;
                            }

                            String name1 = args[4];
                            String name2 = args[6];

                            if(type1 == MissionGraph.MissionVertexType.ROOM && !mission.hasRoom(name1)) {
                                commandSender.sendMessage(ChatColor.RED + "There is no room node named " + name1 + ".");
                                break;
                            }
                            if(type1 == MissionGraph.MissionVertexType.DECISION && !mission.hasRoom(name1)) {
                                commandSender.sendMessage(ChatColor.RED + "There is no decision node named " + name1 + ".");
                                break;
                            }

                            if(type2 == MissionGraph.MissionVertexType.ROOM && !mission.hasRoom(name2)) {
                                commandSender.sendMessage(ChatColor.RED + "There is no room node named " + name2 + ".");
                                break;
                            }
                            if(type2 == MissionGraph.MissionVertexType.DECISION && !mission.hasRoom(name2)) {
                                commandSender.sendMessage(ChatColor.RED + "There is no decision node named " + name2 + ".");
                                break;
                            }

                            if(mission.getOriginalMissionGraph().doesEdgeExist(type1, name1, type2, name2)) {
                                commandSender.sendMessage(ChatColor.RED + "An edge between " + name1 + " and " + name2 + " already exists!");
                                break;
                            }

                            MissionGraph.LocationPath path = mission.getOriginalMissionGraph().defineEdge(type1, name1, type2, name2);

                            if(path == null) {
                                commandSender.sendMessage(ChatColor.RED + "ERROR: A path could not be found!");
                                break;
                            }

                            commandSender.sendMessage("You defined an edge between " + type1 + ": " + name1 +  " and " + type2 + ": " + name2 + "! The distance is " + path.getPathLength() + " blocks.");

                            if(path.getPathLength() > 0 && args.length > 7) {
                                int delayTicks;
                                if(Utils.isInteger(args[7])) {
                                    delayTicks = Integer.parseInt(args[7]);
                                } else {
                                    commandSender.sendMessage(ChatColor.RED + "Not a number.");
                                    break;
                                }

                                LinkedList<Location> pathTrace = (LinkedList<Location>) path.getPath().clone();
                                Location firstLoc = pathTrace.poll();
                                ArmorStand ent = (ArmorStand) firstLoc.getWorld().spawnEntity(firstLoc, EntityType.ARMOR_STAND);

                                ent.getEquipment().setHelmet(new ItemStack(Material.DRAGON_HEAD));

                                commandSender.sendMessage("Showing path...");
                                new BukkitRunnable() {

                                    int waitCount = 0;

                                    @Override
                                    public void run() {
                                        if (pathTrace.isEmpty()) {
                                            if(waitCount < 3) {
                                                waitCount++;
                                            } else {
                                                cancel();
                                                ent.remove();
                                                commandSender.sendMessage("Path show ended.");
                                            }
                                            return;
                                        }

                                        commandSender.sendMessage("Path in progress: " + (path.getPath().size() - pathTrace.size() + 1) + "/" + path.getPath().size());
                                        ent.teleport(pathTrace.poll());

                                        if(!pathTrace.isEmpty()) {
                                            EulerAngle nextPathPose = vectorToEulerAngle(ent.getLocation().clone().toVector().subtract(pathTrace.peek().toVector()));
                                            ent.setHeadPose(nextPathPose);
                                        }
                                    }
                                }.runTaskTimer(plugin, 20L, delayTicks);
                            }

                            missionManager.saveData();
                            break;
                        default:
                            commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission add <mission name> <decisionpoint/edge> [command-specific arguments]");
                            break;
                    }

                    break;
                case "room":
                    if(!(commandSender instanceof Player)) {
                        commandSender.sendMessage(ChatColor.RED + "You must be a player to execute this command!");
                        break;
                    }

                    p = (Player) commandSender;

                    //room builder mode specific arguments
                    if(roomBuilderRooms.containsKey(p)) {
                        boolean captured = true;
                        switch(args[1].toLowerCase()) {
                            case "raycast":
                                p.sendMessage("Began scanning visible blocks within the room boundaries. To end raycasting, type in /mission room save or /mission room cancel");
                                roomBuilderRooms.get(p).beginScanningRoomBlocks(plugin, p, roomBuilderRaycastYBounds.get(p)[0], roomBuilderRaycastYBounds.get(p)[1]);
                                break;
                            case "cancel":
                                p.sendMessage(ChatColor.RED + "You cancelled the room builder.");

                                roomBuilderRooms.get(p).endScanningRoomBlocks(new Runnable() {
                                    @Override
                                    public void run() {
                                        roomBuilderRooms.remove(p);
                                        roomBuilderMissions.remove(p);
                                    }
                                });
                                break;
                            case "save":
                                roomBuilderRooms.get(p).endScanningRoomBlocks(new Runnable() {
                                    @Override
                                    public void run() {
                                        roomBuilderMissions.get(p).addRoom(roomBuilderRooms.get(p));
                                        p.sendMessage(ChatColor.GREEN + "You created a new room named " + roomBuilderRooms.get(p).getRoomName() + " for mission " + roomBuilderMissions.get(p).getMissionName() + "!");
                                        p.sendMessage(ChatColor.GRAY + "Exited room builder mode.");

                                        roomBuilderRooms.remove(p);
                                        roomBuilderMissions.remove(p);

                                        missionManager.saveData();
                                    }
                                });
                                break;
                            case "setybounds":
                                if(args.length < 4) {
                                    commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission room setybounds <lower y bound> <upper y bound>");
                                    break;
                                }
                                if(!Utils.isInteger(args[2]) || !Utils.isInteger(args[3])) {
                                    commandSender.sendMessage(ChatColor.RED + "Improper command. The bounds must be integers. Usage: /mission room setybounds <lower y bound> <upper y bound>");
                                    break;
                                }

                                int lowerBound = Integer.parseInt(args[2]);
                                int upperBound = Integer.parseInt(args[3]);

                                if(lowerBound > upperBound) {
                                    commandSender.sendMessage(ChatColor.RED + "The lower bound must be less than or equal to the upper bound!");
                                    break;
                                }

                                roomBuilderRaycastYBounds.put(p, new int[] {lowerBound, upperBound});
                                commandSender.sendMessage("Set the current y bounds from " + lowerBound + " to " + upperBound + ".");
                                break;
                            default:
                                captured = false;
                        }

                        if(captured) {
                            break;
                        }
                    }

                    if(args.length < 3) {
                        commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission room <mission name> <create> [command-specific arguments]");
                        break;
                    }

                    switch(args[2].toLowerCase()) {
                        case "create":
                            if(args.length < 4) {
                                commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission room <mission name> create <room name>");
                                break;
                            }
                            if(!missionManager.doesMissionExist(args[1])) {
                                commandSender.sendMessage(ChatColor.RED + "That mission does not exist!");
                                break;
                            }

                            mission = missionManager.getMission(args[1]);

                            String roomName = args[3].toLowerCase();
                            if(mission.hasRoom(roomName)) {
                                commandSender.sendMessage(ChatColor.RED + "A room with that name already exists!");
                                break;
                            }

                            SelectionWand wand = (SelectionWand) specialItems.get("wand");
                            BlockRange2D range = wand.getPlayerSelection(p);

                            if(range == null || !range.isRangeCalculated()) {
                                commandSender.sendMessage(ChatColor.RED + "You must first specify room bounds (not including walls) before defining a room! See: " + ChatColor.GRAY + "/mission getitem wand" + ChatColor.RED + ".");
                                break;
                            }

                            //expand bounds by one to account for walls
                            range.expand(1);

                            if(roomBuilderRooms.containsKey(p)) {
                                p.sendMessage(ChatColor.RED + "You have discarded your previous room build.");
                            }

                            roomBuilderRooms.put(p, new MissionRoom(roomName, range));
                            roomBuilderMissions.put(p, mission);

                            if(!roomBuilderRaycastYBounds.containsKey(p)) {
                                roomBuilderRaycastYBounds.put(p, new int[]{0, 255});
                            }

                            wand.clearPlayerSelection(p);

                            commandSender.sendMessage(ChatColor.GREEN + "You have entered room builder mode for room " + roomName + ". First, enter the room, then call "+ ChatColor.GRAY + "/mission room raycast" + ChatColor.GREEN + " to begin scanning the blocks within the room.");
                            commandSender.sendMessage(ChatColor.GRAY + "You may want to define room y bounds via /mission room setybounds <lower bound> <upper bound>! The current bounds are from " + roomBuilderRaycastYBounds.get(p)[0] + " to " + roomBuilderRaycastYBounds.get(p)[1] + ".");

                            missionManager.saveData();
                            break;
                        default:
                            commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission room <mission name> <create> [command-specific arguments]");
                            break;
                    }
                    break;
                case "getitem":
                    if(!(commandSender instanceof Player)) {
                        commandSender.sendMessage(ChatColor.RED + "You must be a player to execute this command!");
                        break;
                    }
                    if(args.length < 2) {
                        commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission getitem <item name>");
                        break;
                    }

                    p = (Player) commandSender;

                    if(specialItems.containsKey(args[1].toLowerCase())) {
                        HashMap<Integer, ItemStack> unaddedItems = p.getInventory().addItem(specialItems.get(args[1].toLowerCase()).getItem());
                        if(!unaddedItems.isEmpty()) {
                            for(ItemStack item : unaddedItems.values()) {
                                p.getWorld().dropItem(p.getLocation(), item);
                            }
                        }
                    } else {
                        p.sendMessage(ChatColor.RED + "Unknown item!");
                    }
                    break;
                case "map":
                    if(commandSender instanceof Player) {
                        p = (Player) commandSender;
                        p.getInventory().setItemInMainHand(MapOverlayRenderer.getMap());
                    }
                    break;
                case "gps":
                    if(commandSender instanceof Player) {
                        p = (Player) commandSender;
                        p.getInventory().setItemInMainHand(DynamicMapRenderer.getMap());
                    }
                    break;
                default:
                    commandSender.sendMessage(GENERIC_IMPROPER_COMMAND_MESSAGE);
                    break;
            }
        }
        return true;
    }

    //untested, for experimental and demonstration purposes only; todo: currently does not work as intended
    private EulerAngle vectorToEulerAngle(Vector v) {
        double x = v.getX();
        double y = v.getY();
        double z = v.getZ();

        double xz = Math.sqrt(x*x + z*z);

        double eulX;
        if(x < 0) {
            if(y == 0) {
                eulX = Math.PI*0.5;
            } else {
                eulX = Math.atan(xz/y)+Math.PI;
            }
        } else {
            eulX = Math.atan(y/xz)+Math.PI*0.5;
        }

        double eulY;
        if(x == 0) {
            if(z > 0) {
                eulY = Math.PI;
            } else {
                eulY = 0;
            }
        } else {
            eulY = Math.atan(z/x)+Math.PI*0.5;
        }

        return new EulerAngle(eulX, eulY, 0);
    }

    private void sendMissionCompletionProgress(CommandSender sender, Mission mission) {
        sender.sendMessage("" + ChatColor.BLUE + ChatColor.UNDERLINE + "Progress for mission " + ChatColor.GOLD + ChatColor.UNDERLINE + mission.getMissionName() + ChatColor.BLUE + ChatColor.UNDERLINE + ":");

        if(mission.hasDuration()) {
            sender.sendMessage(ChatColor.GREEN + "- Duration: " + mission.getDuration() + " seconds");
        } else {
            sender.sendMessage(ChatColor.RED + "- Duration: unknown. Perform " + ChatColor.LIGHT_PURPLE + "/mission set " + mission.getMissionName() + " duration <duration>" + ChatColor.RED + ".");
        }

        if(mission.hasPlayerSpawnLocation()) {
            sender.sendMessage(ChatColor.GREEN + "- Spawn location: " + Utils.getFormattedLocation(mission.getPlayerSpawnLocation()));
        } else {
            sender.sendMessage(ChatColor.RED + "- Spawn location: unknown. Perform " + ChatColor.LIGHT_PURPLE + "/mission set " + mission.getMissionName() + " location " + ChatColor.RED + "to set the current location as the spawn location.");
        }

        if(mission.canRunMission()) {
            sender.sendMessage(ChatColor.GREEN + "- Can run mission: yes! (" + ChatColor.LIGHT_PURPLE + "/mission start " + mission.getMissionName() + ChatColor.GREEN + ")");
        } else {
            sender.sendMessage(ChatColor.RED + "- Can run mission: no!");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        switch(args.length) {
            case 1:
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[0], Arrays.asList("create", "start", "abort", "list", "set", "getitem", "info", "room", "add"), completions);
                Collections.sort(completions);

                return completions;
            case 2:
                switch(args[0].toLowerCase()) {
                    case "create":
                    case "abort":
                    case "list":
                        return new ArrayList<>();
                    case "start":
                    case "set":
                    case "info":
                    case "add":
                        completions = new ArrayList<>();

                        for(Mission mission : missionManager.getAllMissions()) {
                            completions.add(mission.getMissionName());
                        }

                        return completions;
                    case "getitem":
                        completions = new ArrayList<>(specialItems.keySet());
                        return completions;
                    case "room":
                        if(roomBuilderRooms.containsKey(sender)) {
                            completions = new ArrayList<>();
                            StringUtil.copyPartialMatches(args[1], Arrays.asList("raycast", "cancel", "setybounds", "save"), completions);
                            Collections.sort(completions);

                            return completions;
                        } else {
                            completions = new ArrayList<>();
                            for(Mission mission : missionManager.getAllMissions()) {
                                completions.add(mission.getMissionName());
                            }
                            return completions;
                        }
                    default:
                        return null;
                }
            case 3:
                switch(args[0].toLowerCase()) {
                    case "set":
                        completions = new ArrayList<>();
                        StringUtil.copyPartialMatches(args[2], Arrays.asList("duration", "location"), completions);

                        return completions;
                    case "add":
                        return Arrays.asList("decisionpoint", "edge");
                    case "room":
                        if(!roomBuilderRooms.containsKey(sender)) {
                            return new ArrayList<>(Arrays.asList("create"));
                        } else {
                            return null;
                        }
                    default:
                        return null;
                }
//            case 4:
//                switch(args[0].toLowerCase()) {
//                    case "room":
//                        if(!roomBuilderRooms.containsKey(sender)) {
//                            return new ArrayList<>(Arrays.asList("create"));
//                        } else {
//                            return null;
//                        }
//                    default:
//                        return null;
//                }
            default:
                return null;
        }
    }
}
