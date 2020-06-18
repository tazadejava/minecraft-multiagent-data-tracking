package me.tazadejava.mission;

import me.tazadejava.actiontracker.ActionTrackerPlugin;
import me.tazadejava.actiontracker.Utils;
import me.tazadejava.blockranges.BlockRange2D;
import me.tazadejava.blockranges.SelectionWand;
import me.tazadejava.blockranges.SpecialItem;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

//handles the player's commands to pass to the MissionHandler class
public class MissionCommandHandler implements CommandExecutor, TabCompleter {

    private static final String GENERIC_IMPROPER_COMMAND_MESSAGE = "Improper command. Usage: /mission <create/start/abort/list/set/add/getitem> [command-specific arguments]";

    private final ActionTrackerPlugin plugin;
    private final MissionHandler missionHandler;
    private final HashMap<String, SpecialItem> specialItems;

    public MissionCommandHandler(ActionTrackerPlugin plugin, MissionHandler missionHandler, HashMap<String, SpecialItem> specialItems) {
        this.plugin = plugin;
        this.missionHandler = missionHandler;
        this.specialItems = specialItems;
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
            switch(args[0]) {
                case "test":
                    if(!(commandSender instanceof Player)) {
                        commandSender.sendMessage(ChatColor.RED + "You must be a player to execute this command!");
                        break;
                    }

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            new VisibleBlocksRaycaster().getVisibleBlocks((Player) commandSender);
                        }
                    }.runTaskTimer(plugin, 0, 10L);
                    break;
                case "create":
                    if(args.length < 2) {
                        commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission create <mission name>");
                        break;
                    }

                    if(missionHandler.createMission(argsOriginal[1])) {
                        Mission mission = missionHandler.getMission(args[1]);
                        commandSender.sendMessage(ChatColor.GREEN + "Created the mission!");
                        sendMissionCompletionProgress(commandSender, mission);
                    } else {
                        commandSender.sendMessage(ChatColor.RED + "A command with that name already exists!");
                    }
                    break;
                case "list":
                    Collection<Mission> missions = missionHandler.getAllMissions();
                    if(missions == null) {
                        commandSender.sendMessage(ChatColor.RED + "There are no missions.");
                        break;
                    }

                    commandSender.sendMessage(ChatColor.BLUE + "Missions: (" + missions.size() + ") exist:");
                    for(Mission mission : missions) {
                        commandSender.sendMessage((mission.canRunMission() ? ChatColor.GREEN : ChatColor.RED) + "- " + mission.getMissionName());
                    }
                    break;
                case "set":
                    if(args.length < 3) {
                        commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission set <mission name> <duration/location> [additional arguments]");
                        break;
                    }
                    if(!missionHandler.doesMissionExist(args[1])) {
                        commandSender.sendMessage(ChatColor.RED + "A command with that name does not exist!");
                        break;
                    }

                    Mission mission = missionHandler.getMission(args[1]);

                    if(missionHandler.isMissionInProgress(mission)) {
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
                            missionHandler.saveData();
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
                            missionHandler.saveData();
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
                    if(!missionHandler.doesMissionExist(args[1])) {
                        commandSender.sendMessage(ChatColor.RED + "That mission does not exist!");
                        break;
                    }

                    mission = missionHandler.getMission(args[1]);

                    if(!mission.canRunMission()) {
                        commandSender.sendMessage(ChatColor.RED + "You cannot yet run this mission!");
                        sendMissionCompletionProgress(commandSender, mission);
                        break;
                    }

                    if(missionHandler.startMission(commandSender, mission)) {
                        commandSender.sendMessage(ChatColor.GREEN + "Mission started with " + mission.getDuration() + " seconds. Data is now being tracked.");
                    } else {
                        commandSender.sendMessage(ChatColor.RED + "A mission is already in progress!");
                    }
                    break;
                case "abort":
                    if(missionHandler.abortMission()) {
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
                    if(args.length < 3) {
                        commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission add <mission name> <room> [command-specific arguments]");
                        break;
                    }

                    Player p = (Player) commandSender;

                    switch(args[2].toLowerCase()) {
                        case "room":
                            if(args.length < 4) {
                                commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission add <mission name> room <room name>");
                                break;
                            }
                            if(!missionHandler.doesMissionExist(args[1])) {
                                commandSender.sendMessage(ChatColor.RED + "That mission does not exist!");
                                break;
                            }

                            mission = missionHandler.getMission(args[1]);

                            String roomName = args[3].toLowerCase();
                            if(mission.hasRoom(roomName)) {
                                commandSender.sendMessage(ChatColor.RED + "A room with that name already exists!");
                                break;
                            }

                            SelectionWand wand = (SelectionWand) specialItems.get("wand");
                            BlockRange2D range = wand.getPlayerSelection(p);

                            if(range == null || !range.isRangeCalculated()) {
                                commandSender.sendMessage(ChatColor.RED + "You must first specify room bounds before defining a room! See: " + ChatColor.GRAY + "/mission getitem wand" + ChatColor.RED + ".");
                                break;
                            }

                            mission.addRoom(roomName, range);
                            commandSender.sendMessage("A room has been added to the mission!");
                            missionHandler.saveData();
                            break;
                        default:
                            commandSender.sendMessage(ChatColor.RED + "Improper command. Usage: /mission add <mission name> <room> [command-specific arguments]");
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
        sender.sendMessage(ChatColor.BLUE + "Progress for mission " + ChatColor.GOLD + mission.getMissionName() + ChatColor.BLUE + ":");

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
            sender.sendMessage(ChatColor.GREEN + "- Can run mission: yes!");
        } else {
            sender.sendMessage(ChatColor.RED + "- Can run mission: no!");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        switch(args.length) {
            case 1:
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[0], Arrays.asList("create", "start", "abort", "list", "set", "getitem", "add"), completions);
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
                    case "add":
                        completions = new ArrayList<>();

                        for(Mission mission : missionHandler.getAllMissions()) {
                            completions.add(mission.getMissionName());
                        }

                        return completions;
                    case "getitem":
                        completions = new ArrayList<>(specialItems.keySet());

                        return completions;
                    default:
                        return null;
                }
            case 3:
                switch(args[0].toLowerCase()) {
                    case "set":
                        completions = new ArrayList<>();
                        StringUtil.copyPartialMatches(args[0], Arrays.asList("duration", "location"), completions);
                        return completions;
                    case "add":
                        return new ArrayList<>(Arrays.asList("room"));
                    default:
                        return null;
                }
            default:
                return null;
        }
    }
}
