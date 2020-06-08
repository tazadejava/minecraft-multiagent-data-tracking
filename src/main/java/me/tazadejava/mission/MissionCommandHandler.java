package me.tazadejava.mission;

import me.tazadejava.actiontracker.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

//handles the player's commands to pass to the MissionHandler class
public class MissionCommandHandler implements CommandExecutor {

    private MissionHandler missionHandler;

    public MissionCommandHandler(MissionHandler missionHandler) {
        this.missionHandler = missionHandler;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        //make all arguments lowercase to allow for switch parameter
        for(int i = 0; i < args.length; i++) {
            args[i] = args[i].toLowerCase();
        }

        if(args.length == 0) {
            commandSender.sendMessage("Improper command. Usage: /mission <start> [command-specific arguments]");
        } else {
            switch(args[0]) {
                case "start":
                    if(args.length < 2) {
                        commandSender.sendMessage("Improper command. Usage: /mission start <duration in seconds>");
                        break;
                    }
                    if(!Utils.isInteger(args[1])) {
                        commandSender.sendMessage("Improper command. Usage: /mission start <duration in seconds>");
                        break;
                    }

                    int duration = Integer.parseInt(args[1]);

                    if(missionHandler.startMission(commandSender, duration)) {
                        commandSender.sendMessage("Mission started with " + duration + " seconds. Data is now being tracked.");
                    } else {
                        commandSender.sendMessage("A mission is already in progress!");
                    }
                    break;
                default:
                    commandSender.sendMessage("Improper command. Usage: /mission <start> [command-specific arguments]");
                    break;
            }
        }
        return true;
    }
}
