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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

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
            extractMarkerResources(webRoot);
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
        var iconsDir = new File(SignMarkers.webRoot + "/markers");
        if (iconsDir.exists() && iconsDir.isDirectory()) {
            try {
                Files.walkFileTree(iconsDir.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        super.visitFile(file, attrs);

                        var f = file.toFile();
                        if (f.isFile() && f.getName().endsWith(".png")) {
                            String iconName = f.getName().substring(0, f.getName().length() - 4);
                            var relpath = SignMarkers.webRoot.toUri().relativize(f.toURI());

                            BufferedImage image = ImageIO.read(f);
                            int width = image.getWidth();
                            int height = image.getHeight();
                            var anchor = new Vector2i(height / 2, width / 2);
                            var newIcon = new IconInfo();
                            newIcon.anchor = anchor;
                            newIcon.iconAddress = relpath.toString();

                            var icon = availableIcons.get(iconName);
                            if (icon != null) {
                                if (newIcon.iconAddress.length() < icon.iconAddress.length()) {
                                    getLogger().warning("Duplicate icon '" + iconName + "' from '" + newIcon.iconAddress + "' overrides '" + icon.iconAddress + "'");
                                    availableIcons.put(iconName, newIcon);
                                }
                            } else {
                                availableIcons.put(iconName, newIcon);
                            }

                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        getLogger().info("Loaded " + availableIcons.size() + " marker icons.");
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

    /**
     * Extracts marker PNG files from plugin resources to BlueMap webroot
     * Always extracts to clean folder to ensure markers match plugin version
     */
    private void extractMarkerResources(Path webRoot) {
        try {
            Path markersDir = webRoot.resolve("markers").resolve("dynamp");

            // Clean existing markers/std directory if it exists
            if (Files.exists(markersDir)) {
                Files.walkFileTree(markersDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        super.visitFile(file, attrs);
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        super.postVisitDirectory(dir, exc);
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            // Create markers/std directory if it doesn't exist
            Files.createDirectories(markersDir);

            // Extract all marker PNG files from resources to clean directory
            extractResourcesFromJar("/markers/dynmap", markersDir, ".png");
            // also extract readme and license files
            extractResourcesFromJar("/markers/dynmap", markersDir, ".txt");
            extractResourcesFromJar("/markers/dynmap", markersDir, ".md");
            getLogger().info("Standard markers extracted to directory: " + markersDir.toString());

        } catch (Exception e) {
            getLogger().warning("Failed to extract marker resources: " + e.getMessage());
        }
    }

    /**
     * Extracts files from the plugin JAR to a target directory
     * Always extracts all files since directory is cleaned beforehand
     */
    private void extractResourcesFromJar(String resourcePath, Path targetDir, String fileExtension) throws IOException, URISyntaxException {

        URI uri = getClass().getResource(resourcePath).toURI();
        Path myPath;
        FileSystem fileSystem = null;
        try {
            if (uri.getScheme().equals("jar")) {
                fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
                myPath = fileSystem.getPath(resourcePath);
            } else {
                myPath = Paths.get(uri);
            }

            Stream<Path> walk = Files.walk(myPath, 1).filter(p -> {
                return p.getFileName().toString().endsWith(fileExtension);
            });

            for (Iterator<Path> it = walk.iterator(); it.hasNext(); ) {
                var path = it.next();
                try (InputStream ins = Files.newInputStream(path)) {
                    String fileName = path.getFileName().toString();
                    var targetFile = targetDir.resolve(fileName);
                    Files.copy(ins, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } finally {
            if (fileSystem != null) {
                fileSystem.close();
            }
        }

    }

    public static class IconInfo {
        public Vector2i anchor;
        public String iconAddress;
    }
}