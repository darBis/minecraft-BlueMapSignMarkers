package dev.kugge.signmarkers.watcher;

import de.bluecolored.bluemap.api.markers.POIMarker;
import dev.kugge.signmarkers.SignMarkers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector2i;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class SignWatcher implements Listener {
    @EventHandler
    public void onSignWrite(SignChangeEvent event) {
        Component header = event.line(0);
        if (header == Component.empty() || header == null) return;
        if (!header.toString().contains("[map]")) {
            // not a map sign
            return;
        }

        Component cicon = event.line(3);
        if (cicon == Component.empty() || cicon == null) {
            event.line(3, Component.text("<put icon here!>"));
            event.getPlayer().sendMessage(Component.text("SignMarkers: Missing icon name on line 4!").color(NamedTextColor.YELLOW));
            event.getPlayer().sendMessage(Component.text("Use command §6/signmarkers icons§r to list available icons."));
            return;
        }

        String iconName = LegacyComponentSerializer.legacySection().serialize(cicon);
        SignMarkers.IconInfo ii = SignMarkers.availableIcons.get(iconName);
        if (ii == null) {
            event.line(3, Component.text("<bad icon name!>"));
            event.getPlayer().sendMessage(Component.text("SignMarkers: Bad icon name on line 4!").color(NamedTextColor.RED));
            event.getPlayer().sendMessage(Component.text("Use command §6/signmarkers icons§r to list available icons."));
            return;
        }

        // TODO: improve text handling
        Component clabel1 = event.line(1);
        if (clabel1 == Component.empty() || clabel1 == null) return;

        Component clabel2 = event.line(2);
        String label = LegacyComponentSerializer.legacySection().serialize(clabel1) + "\n"
                + LegacyComponentSerializer.legacySection().serialize(clabel2);

        Block block = event.getBlock();

        Vector3d markerPos = getMarkerPosition(block);

        // make marker id from original block position without offset, to correctly match marker when sign gets destroyed
        String id = SignMarkers.MARKER_PREFIX + block.getX() + "-" + block.getY() + "-" + block.getZ();
        POIMarker marker = POIMarker.builder().label(label).position(markerPos).icon(ii.iconAddress, ii.anchor).maxDistance(100000).build();
        SignMarkers.markerSet.get(block.getWorld()).put(id, marker);

        // Delete [map] and icon lines
        event.line(0, Component.empty());
        event.line(3, Component.empty());

        event.getPlayer().sendMessage(Component.text("Created '" + iconName + "' marker"));
    }

    private static @NotNull Vector3d getMarkerPosition(Block block) {
        double xPos = block.getX();
        double zPos = block.getZ();
        double yPos = block.getY();

        // Check sign type and set yOffset accordingly
        boolean isWallSign = block.getBlockData().getMaterial().name().contains("WALL_SIGN");
        boolean isHangingSign = block.getBlockData().getMaterial().name().contains("HANGING_SIGN");
        if (isWallSign) {
            yPos = yPos + 1;
        } else if (isHangingSign) {
            yPos = yPos + 0.75;
        } else {
            yPos = yPos + 0.8; // base offset for standalone sign
        }

        // For wall signs, adjust X and Z to place marker at the block face
        if (isWallSign) {
            org.bukkit.block.data.Directional directional = (org.bukkit.block.data.Directional) block.getBlockData();
            org.bukkit.block.BlockFace facing = directional.getFacing();

            // Adjust position to be at the block face the sign is attached to
            switch (facing) {
                case NORTH: // Sign facing north, attached to south face
                    xPos = xPos + 0.5;
                    zPos = zPos + 1.0;
                    break;
                case SOUTH: // Sign facing south, attached to north face
                    xPos = xPos + 0.5;
                    break;
                case EAST:  // Sign facing east, attached to west face
                    zPos = zPos + 0.5;
                    break;
                case WEST:  // Sign facing west, attached to east face
                    xPos = xPos + 1.0;
                    zPos = zPos + 0.5;
                    break;
                default:
                    break;
            }
        } else {
            // place standalone sign marker at center of the block
            xPos = xPos + 0.5;
            zPos = zPos + 0.5;
        }

        // marker position on map
        Vector3d pos = new Vector3d(xPos, yPos, zPos);
        return pos;
    }
}
