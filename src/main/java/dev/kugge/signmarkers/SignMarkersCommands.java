package dev.kugge.signmarkers;


import net.kyori.adventure.text.Component;
import org.bukkit.ServerLinks;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.stream.Stream;

public class SignMarkersCommands implements TabExecutor {
    private final SignMarkers plugin;

    private final String PERMISSION_ADMIN = "signmarkers.command.admin";

    SignMarkersCommands(SignMarkers plugin) {
        plugin.getCommand("signmarkers").setExecutor(this);
        this.plugin = plugin;
    }

    List<String> commands_user = List.of("help", "icons");
    List<String> commands_admin = Stream.concat(commands_user.stream(), Stream.of("reload")).toList();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        String cmd = (args.length > 0) ? args[0].toLowerCase() : "";

        switch (cmd) {
            case "reload":
                if (sender.hasPermission(PERMISSION_ADMIN)) {
                    plugin.loadAvailableIcons();
                    sender.sendMessage("§eSignMarkers configuration has been reloaded!");
                } else {
                    return false;
                }
                break;
            case "icons":
                sender.sendMessage("§eAvailable Marker Icons:");
                var sortedKeys = SignMarkers.availableIcons.keySet().stream().sorted().toList();
                var coloredNames = new StringBuilder();

                // Group by first letter; insert separator line '---' between groups
                var groups = new java.util.LinkedHashMap<Character, java.util.List<String>>();
                for (String key : sortedKeys) {
                    char first = Character.toUpperCase(key.charAt(0));
                    groups.computeIfAbsent(first, k -> new java.util.ArrayList<>()).add(key);
                }
                int groupIndex = 0;
                for (var entry : groups.entrySet()) {
                    if (groupIndex++ > 0) coloredNames.append("\n§8---\n");
                    var keys = entry.getValue();
                    for (int i = 0; i < keys.size(); i++) {
                        if (i == 0) coloredNames.append(" ");
                        if (i > 0) coloredNames.append(", ");
                        coloredNames.append(i % 2 == 0 ? "§f" : "§7").append(keys.get(i));
                    }
                }
                var names = coloredNames.toString();
                sender.sendMessage(" " + names);
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
        sender.sendMessage("§7/signmarkers §bicons §r- List available icons");
        if (sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage("§7/signmarkers §breload §r- Reload the plugin configuration");
        }
        sender.sendMessage("§eCommand alias: §f/sm §b<command>");
        sender.sendMessage("§eSign text lines:");
        sender.sendMessage("1: [map]");
        sender.sendMessage("2: marker text line 1");
        sender.sendMessage("3: marker text line 2 §7(optional)");
        sender.sendMessage("4: icon_name §7(use §6/signmarkers icons §7to list available icons)");
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        if (strings.length == 1) {
            if (commandSender.hasPermission(PERMISSION_ADMIN)) {
                return commands_admin;
            }
            return commands_user;
        }

        return List.of();
    }
}