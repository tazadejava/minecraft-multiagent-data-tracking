package me.tazadejava.mission;

import me.tazadejava.actiontracker.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

//get the blocks that the player can see
//based on algorithm that Essie wrote in python; ported to Java
public class VisibleBlocksRaycaster {

    class RaycastBounds {

//        private static final int FOV_ANGLE_HALF = 35;
        private static final int FOV_ANGLE_HALF = 45;

        private Vector playerLocation;

        private double leftSlope, rightSlope;
        private float axisDeltaAngle;

        public RaycastBounds(Player p) {
            playerLocation = p.getEyeLocation().toVector();

            Vector dir = p.getEyeLocation().getDirection();

            //axis: z leftright, x updown
            //logic: the block is within the bounds if the player's direction is aligned to an axis and the block is above the line of both the left and right vectors, adjusted to match the aligned axis.

            //find angle between x axis and player direction
            axisDeltaAngle = dir.angle(new Vector(1, 0, 0));

            //need to wrap around axis angle more, depending on quadrant; 0 degrees is up in this case

            //case 1: is in 4th quadrant; need to invert and add PI
            //case 2: is in 1st quadrant; need to invert and add 270 degrees
            float yaw = p.getEyeLocation().getYaw();
            if(yaw > 0 && yaw < 90) {
                float inversion = (float) Math.PI - axisDeltaAngle;
                axisDeltaAngle = inversion + (float) Math.PI;
            } else if(yaw > 270 && yaw < 360) {
                float inversion = (float) (Math.PI / 2) - axisDeltaAngle;
                axisDeltaAngle = inversion + ((float) Math.PI * (3f / 2));
            }
            Bukkit.broadcastMessage(Math.toDegrees(axisDeltaAngle) + " " + Utils.getFormattedLocation(p.getEyeLocation()));

            //the first angle is the horizontal FOV of the player
            //the second angle adjusts the left and right vectors to match this delta angle
            Vector leftVectorAdjusted = dir.clone().rotateAroundY(Math.toRadians(-FOV_ANGLE_HALF) - axisDeltaAngle);
            Vector rightVectorAdjusted = dir.clone().rotateAroundY(Math.toRadians(FOV_ANGLE_HALF) - axisDeltaAngle);
            leftSlope = leftVectorAdjusted.getX() / leftVectorAdjusted.getZ();
            rightSlope = rightVectorAdjusted.getX() / rightVectorAdjusted.getZ();
        }

        public boolean isInBounds(Location loc) {
            Vector vecToLoc = loc.toVector().subtract(playerLocation);

            vecToLoc.rotateAroundY(-axisDeltaAngle);
            Location adjustedLoc = vecToLoc.toLocation(loc.getWorld());

            return (adjustedLoc.getX() >= leftSlope * adjustedLoc.getZ())
                    && (adjustedLoc.getX() >= rightSlope * adjustedLoc.getZ());
        }
    }

    /*
    Idea:
    Two line segments going out horizontally and vertically
    They represent rectangular bounds of where the player can look
    If within the line segment bounds, then the player can see it. Otherwise, they cannot

    Origin: player location
    Direction: player location direction vector

    Get left and right horizontal lines, then get lower and upper vertical lines, then treat them as bounds
    If the player sees them, turn it into green wool; otherwise, red wool
     */

    public List<Block> getVisibleBlocks(Player p) {
        List<Block> blocks = new ArrayList<>();

        RaycastBounds bounds = new RaycastBounds(p);

        //for now, get nearby blocks
        Location playerBlockLoc = p.getEyeLocation();
        for(int dx = -5; dx <= 5; dx++) {
            for(int dz = -5; dz <= 5; dz++) {
                Location loc = playerBlockLoc.clone().add(dx, 0, dz);

                if(loc.getBlock().getType() != Material.AIR) {
                    if(bounds.isInBounds(loc)) {
                        if(loc.getBlock().getType() == Material.YELLOW_STAINED_GLASS) {
                            loc.getBlock().setType(Material.GREEN_STAINED_GLASS);
                        } else {
                            loc.getBlock().setType(Material.GREEN_CONCRETE);
                        }
                    } else {
                        loc.getBlock().setType(Material.RED_WOOL);
                    }
                }
            }
        }

        return blocks;
    }
}
