package dev.kugge.signmarkers;


import net.kyori.adventure.text.Component;
import org.bukkit.ServerLinks;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;

public class SignMarkersCommands implements TabExecutor {
    private final SignMarkers plugin;

    SignMarkersCommands(SignMarkers plugin) {
        plugin.getCommand("signmarkers").setExecutor(this);
        this.plugin = plugin;
    }

    List<String> commands = List.of("help", "reload", "icons");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        String cmd = (args.length > 0) ? args[0].toLowerCase() : "";

        switch (cmd) {
            case "reload":
                plugin.loadAvailableIcons();
                sender.sendMessage("§eSignMarkers configuration has been reloaded!");
                break;
            case "icons":
                sender.sendMessage("§eAvailable icons:");
                var sortedKeys = SignMarkers.availableIcons.keySet().stream().sorted().toList();
                var coloredNames = new StringBuilder();
                for (int i = 0; i < sortedKeys.size(); i++) {
                    if (i > 0) coloredNames.append(", ");
                    coloredNames.append(i % 2 == 0 ? "§f" : "§7").append(sortedKeys.get(i));
                }
                var names = coloredNames.toString();
                sender.sendMessage(Component.text(" " + names));
                break;
            default:
                cmdHelp(sender);
                break;
        }

        return true;
    }

    private void cmdHelp(CommandSender sender) {
        // formating help: https://minecraft.fandom.com/wiki/Formatting_codes
        sender.sendMessage("§eSignMarkers commands:");
        sender.sendMessage("§7/signmarkers §bhelp §r- Show this help message");
        sender.sendMessage("§7/signmarkers §breload §r- Reload the plugin configuration");
        sender.sendMessage("§7/signmarkers §bicons §r- List available icons");
        sender.sendMessage("§eSign text format:");
        sender.sendMessage("1: [map]");
        sender.sendMessage("2: marker text line 1");
        sender.sendMessage("3: marker text line 2 §7(optional)");
        sender.sendMessage("4: icon_name §7(use §6/signmarkers icons §7to list available icons)");
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        if (strings.length == 1) {
            return commands;
        }

        return List.of();
    }
}