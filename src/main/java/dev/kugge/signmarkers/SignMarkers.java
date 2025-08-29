package dev.kugge.signmarkers;

import com.flowpowered.math.vector.Vector2i;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import dev.kugge.signmarkers.watcher.SignDestroyWatcher;
import dev.kugge.signmarkers.watcher.SignWatcher;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SignMarkers extends JavaPlugin {

    public static final String MARKER_PREFIX = "marker-";
    public static Path webRoot;
    public static SignMarkers instance;
    public static Logger logger;
    public static Map<World, MarkerSet> markerSet = new ConcurrentHashMap<>();
    public static Map<String, IconInfo> availableIcons = new Hashtable<>();
    private SignMarkersCommands signMarkersCommands;

    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();
        createFiles();
        for (World world : Bukkit.getWorlds()) {
            loadWorldMarkerSet(world);
            registerWorld(world);
        }
        BlueMapAPI.onEnable(api -> {
            webRoot = api.getWebApp().getWebRoot();
            loadAvailableIcons();
        });
        Bukkit.getPluginManager().registerEvents(new SignWatcher(), this);
        Bukkit.getPluginManager().registerEvents(new SignDestroyWatcher(), this);

        final LifecycleEventManager<Plugin> lifecycleManager = this.getLifecycleManager();
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);

        signMarkersCommands = new SignMarkersCommands(this);
        getLogger().info("SignMarkers has been enabled.");
    }

    @Override
    public void onDisable() {
        for (World world : Bukkit.getWorlds())
            saveWorldMarkerSet(world);
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        // handle server reload
        if (event.cause() == ReloadableRegistrarEvent.Cause.RELOAD) {
            getLogger().info("Server reload detected, reloading SignMarkers configuration...");
            loadAvailableIcons();
        }
    }

    public void loadAvailableIcons() {
        File iconsDir = new File(SignMarkers.webRoot + "/markers");
        var iconCount = 0;
        if (iconsDir.exists() && iconsDir.isDirectory()) {
            for (File file : iconsDir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".png")) {
                    String iconName = file.getName().substring(0, file.getName().length() - 4);

                    try {
                        BufferedImage image = ImageIO.read(file);
                        int width = image.getWidth();
                        int height = image.getHeight();
                        var anchor = new Vector2i(height / 2, width / 2);
                        var ii = new IconInfo();
                        ii.anchor = anchor;
                        ii.iconAddress = "./markers/" + file.getName();
                        availableIcons.put(iconName, ii);
                        iconCount++;
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }
        }
        getLogger().info("Loaded " + iconCount + " marker icons.");
    }

    private void createFiles() {
        for (World world : Bukkit.getWorlds()) {
            String name = MARKER_PREFIX + "set-" + world.getName() + ".json";
            File file = new File(this.getDataFolder(), name);
            try {
                File folder = this.getDataFolder();
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                if (!file.exists()) {
                    file.createNewFile();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void saveWorldMarkerSet(World world) {
        String name = MARKER_PREFIX + "set-" + world.getName() + ".json";
        File file = new File(this.getDataFolder(), name);
        try (FileWriter writer = new FileWriter(file)) {
            MarkerGson.INSTANCE.toJson(markerSet.get(world), writer);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadWorldMarkerSet(World world) {
        String name = MARKER_PREFIX + "set-" + world.getName() + ".json";
        File file = new File(this.getDataFolder(), name);
        try (FileReader reader = new FileReader(file)) {
            MarkerSet set = MarkerGson.INSTANCE.fromJson(reader, MarkerSet.class);
            if (set != null) {
                convertOldMarkers(set);
                markerSet.put(world, set);
            }
        } catch (FileNotFoundException ignored) {
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static final java.util.regex.Pattern coordPattern = java.util.regex.Pattern
            .compile("^" + MARKER_PREFIX + "(-?\\d*\\.?\\d+)-(-?\\d*\\.?\\d+)-(-?\\d*\\.?\\d+)$");

    private void convertOldMarkers(MarkerSet set) {
        set.getMarkers().forEach((id, marker) -> {
            if (id.startsWith(MARKER_PREFIX) && id.contains(".")) {

                java.util.regex.Matcher matcher = coordPattern.matcher(id);
                if (matcher.matches()) {
                    try {
                        // Extract coordinates using regex groups
                        double x = Double.parseDouble(matcher.group(1));
                        double y = Double.parseDouble(matcher.group(2));
                        double z = Double.parseDouble(matcher.group(3));

                        int newX = (int) Math.floor(x);
                        int newY = (int) Math.floor(y);
                        int newZ = (int) Math.floor(z);

                        String newId = String.format(MARKER_PREFIX + "%d-%d-%d", newX, newY, newZ);

                        if (!newId.equals(id)) {
                            set.getMarkers().remove(id);
                            set.getMarkers().put(newId, marker);
                        }
                    } catch (NumberFormatException ignored) {
                        // Skip if parsing fails
                    }
                }
            }
        });
    }

    private void registerWorld(World world) {
        BlueMapAPI.onEnable(api -> api.getWorld(world).ifPresent(blueWorld -> {
            for (BlueMapMap map : blueWorld.getMaps()) {
                String label = "sign-markers-" + world.getName();
                MarkerSet set = markerSet.get(world);
                if (set == null) {
                    set = MarkerSet.builder().label(label).build();
                }
                map.getMarkerSets().put(label, set);
                markerSet.put(world, set);
            }
        }));
    }

    public static class IconInfo {
        public Vector2i anchor;
        public String iconAddress;
    }
}