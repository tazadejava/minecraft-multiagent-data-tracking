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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
        excludedTransformations.add(Material.ACACIA_DOOR);
        excludedTransformations.add(Material.OAK_DOOR);
        excludedTransformations.add(Material.REDSTONE_TORCH);
        excludedTransformations.add(Material.REDSTONE);
        excludedTransformations.add(Material.REPEATER);
        excludedTransformations.add(Material.COMPARATOR);
        excludedTransformations.add(Material.CHEST);
        excludedTransformations.add(Material.ENDER_CHEST);
        excludedTransformations.add(Material.LEVER);
        excludedTransformations.add(Material.STONE_BUTTON);
        excludedTransformations.add(Material.TRIPWIRE_HOOK);
        excludedTransformations.add(Material.OAK_SIGN);
        excludedTransformations.add(Material.OAK_WALL_SIGN);
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
        Player player = (Player) commandSender;

        restoreBlocks(player);

        lastBlockState.clear();
        blockPlayer.clear();

        PreciseVisibleBlocksRaycaster raycaster = new PreciseVisibleBlocksRaycaster(true, true, true, 52, 53);
//        PreciseVisibleBlocksRaycaster raycaster = new PreciseVisibleBlocksRaycaster(true, true, 0, 255);

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
                Block[] blocks = raycaster.getVisibleBlocks((Player) commandSender, lastBlockState.keySet());
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
                        player.sendMessage("Abort raycaster. Type in /mission testreset to restore blocks, or perform the test again.");
                    }
                    cancel();
                }

                if(!loopRaycast) {
                    player.sendMessage("Abort raycaster. Type in /mission testreset to restore blocks, or perform the test again.");
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
                    break;
                case "set":
                    if(args.length < 3) {
                        commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission set <mission name> <duration/location> [additional arguments]");
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
                        default:
                            commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission set <mission name> <duration/location> [additional arguments]");
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
                case "room":
                    if(!(commandSender instanceof Player)) {
                        commandSender.sendMessage(ChatColor.RED + "You must be a player to execute this command!");
                        break;
                    }

                    Player p = (Player) commandSender;

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
                default:
                    commandSender.sendMessage(GENERIC_IMPROPER_COMMAND_MESSAGE);
                    break;
            }
        }
        return true;
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
                StringUtil.copyPartialMatches(args[0], Arrays.asList("create", "start", "abort", "list", "set", "getitem", "info", "room"), completions);
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
