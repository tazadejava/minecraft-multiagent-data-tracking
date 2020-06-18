package me.tazadejava.mission;

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

        private Player p;

        private double leftSlopeNeg, rightSlopeNeg, leftSlopePos, rightSlopePos;
        private float axisDeltaAngle;

        public RaycastBounds(Player p) {
            this.p = p;

            Vector dir = p.getEyeLocation().getDirection();

            Vector leftVector = dir.clone().rotateAroundY(Math.toRadians(-35));
            Vector rightVector = dir.clone().rotateAroundY(Math.toRadians(35));

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
                Bukkit.broadcastMessage("" + Math.toDegrees(axisDeltaAngle));
            } else if(yaw > 270 && yaw < 360) {
                float inversion = (float) (Math.PI / 2) - axisDeltaAngle;
                axisDeltaAngle = inversion + ((float) Math.PI * (3f / 2));
                Bukkit.broadcastMessage("" + Math.toDegrees(axisDeltaAngle) + " " + inversion + " " + Math.toDegrees(((float) Math.PI * (3f / 2))));
            } else {
                Bukkit.broadcastMessage("NO YAW " + yaw + " " + Math.toDegrees(axisDeltaAngle));
            }

            //adjust the left and right vectors to match this delta angle
            Vector leftVectorAdjustedNegative = leftVector.clone().rotateAroundY(-axisDeltaAngle);
            Vector rightVectorAdjustedNegative = rightVector.clone().rotateAroundY(-axisDeltaAngle);
            Vector leftVectorAdjustedPositive = leftVector.clone().rotateAroundY(axisDeltaAngle);
            Vector rightVectorAdjustedPositive = rightVector.clone().rotateAroundY(axisDeltaAngle);

            leftSlopeNeg = leftVectorAdjustedNegative.getX() / leftVectorAdjustedNegative.getZ();
            rightSlopeNeg = rightVectorAdjustedNegative.getX() / rightVectorAdjustedNegative.getZ();

            leftSlopePos = leftVectorAdjustedPositive.getX() / leftVectorAdjustedPositive.getZ();
            rightSlopePos = rightVectorAdjustedPositive.getX() / rightVectorAdjustedPositive.getZ();
        }

        public boolean isInBounds(Location loc) {
            Vector vecToLoc = loc.toVector().subtract(p.getEyeLocation().toVector());

            vecToLoc.rotateAroundY(-axisDeltaAngle);
            Location adjustedLoc = vecToLoc.toLocation(loc.getWorld());

            return (adjustedLoc.getX() >= leftSlopeNeg * adjustedLoc.getZ())
                    && (adjustedLoc.getX() >= rightSlopeNeg * adjustedLoc.getZ());
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
        Location playerBlockLoc = p.getEyeLocation().getBlock().getLocation();
        for(int dx = -5; dx <= 5; dx++) {
            for(int dz = -5; dz <= 5; dz++) {
                Location loc = playerBlockLoc.clone().add(dx, 0, dz);

                if(loc.getBlock().getType() != Material.AIR) {
                    if(bounds.isInBounds(loc)) {
                        loc.getBlock().setType(Material.GREEN_WOOL);
                    } else {
                        loc.getBlock().setType(Material.RED_WOOL);
                    }
                }
            }
        }

        return blocks;
    }
}
