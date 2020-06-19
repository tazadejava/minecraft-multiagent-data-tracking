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
//inspired by algorithm that Essie wrote in python
public class VisibleBlocksRaycaster {

    class RaycastBounds {

        private final float FOV_HORIZONTAL_ANGLE_HALF = (float) Math.toRadians(45);//35 //increased angle for better accuracy
        private final float FOV_VERTICAL_ANGLE_HALF = (float) Math.toRadians(53);//43 //increased angle for better accuracy

        private Vector playerLocation, playerDirection;

        private double leftSlope, rightSlope;
        private float horizontalAxisDeltaAngle;;

        private double downSlope, upSlope;
        private float verticalAxisDeltaAngle;

        public RaycastBounds(Player p) {
            playerLocation = p.getEyeLocation().toVector();
            playerDirection = p.getEyeLocation().getDirection();
//            Vector dir = p.getEyeLocation().getDirection();

//            calculateHorizontalSlopesAndAngles(dir, p.getEyeLocation().getYaw());
//            calculateVerticalSlopesAndAngles(dir, p.getEyeLocation().getPitch());
        }

        private void calculateHorizontalSlopesAndAngles(Vector dir, float yaw) {
            //axis: z leftright, x updown
            //logic: the block is within the bounds if the player's direction is aligned to an axis and the block is above the line of both the left and right vectors, adjusted to match the aligned axis.

            //find angle between x axis and player direction
            horizontalAxisDeltaAngle = dir.angle(new Vector(1, 0, 0));

            //need to wrap around axis angle more, depending on quadrant; 0 degrees is up in this case

            //case 1: is in 4th quadrant; need to invert and add PI
            //case 2: is in 1st quadrant; need to invert and add 270 degrees
            if(yaw > 0 && yaw < 90) {
                float inversion = (float) Math.PI - horizontalAxisDeltaAngle;
                horizontalAxisDeltaAngle = inversion + (float) Math.PI;
            } else if(yaw > 270 && yaw < 360) {
                float inversion = (float) (Math.PI / 2) - horizontalAxisDeltaAngle;
                horizontalAxisDeltaAngle = inversion + ((float) Math.PI * (3f / 2));
            }

            //the first angle is the horizontal FOV of the player
            //the second angle adjusts the left and right vectors to match this delta angle
            Vector leftVectorAdjusted = dir.clone().rotateAroundY(-FOV_HORIZONTAL_ANGLE_HALF - horizontalAxisDeltaAngle);
            Vector rightVectorAdjusted = dir.clone().rotateAroundY(FOV_HORIZONTAL_ANGLE_HALF - horizontalAxisDeltaAngle);
            leftSlope = leftVectorAdjusted.getX() / leftVectorAdjusted.getZ();
            rightSlope = rightVectorAdjusted.getX() / rightVectorAdjusted.getZ();
        }

        private void calculateVerticalSlopesAndAngles(Vector dir, float pitch) {
            //this algorithm is nearly identical to the horizontal one above; it simply adjusts axes to account for vertical rather than horizontal; also, since angle can only reach 180 degrees, it does not need to adjust depending on angle

            //find angle between y axis and player dir
//            verticalAxisDeltaAngle = dir.angle(new Vector(0, 1, 0));

//            Bukkit.broadcastMessage("ANGLE " + verticalAxisDeltaAngle + " PITCH " + pitch);

//            if(yaw > 0 && yaw < 90) {
//                float inversion = (float) Math.PI - verticalAxisDeltaAngle;
//                verticalAxisDeltaAngle = inversion + (float) Math.PI;
//            } else if(yaw > 270 && yaw < 360) {
//                float inversion = (float) (Math.PI / 2) - verticalAxisDeltaAngle;
//                verticalAxisDeltaAngle = inversion + ((float) Math.PI * (3f / 2));
//            }

            //the first angle is the horizontal FOV of the player
            //the second angle adjusts the left and right vectors to match this delta angle
//            Vector downVectorAdjusted = dir.clone().rotate
//            Vector downVectorAdjusted = dir.clone().rotateAroundX(Math.toRadians(-FOV_VERTICAL_ANGLE_HALF) - verticalAxisDeltaAngle);
//            Vector upVectorAdjusted = dir.clone().rotateAroundX(Math.toRadians(FOV_VERTICAL_ANGLE_HALF) - verticalAxisDeltaAngle);
//            downSlope = downVectorAdjusted.getY() / downVectorAdjusted.getZ();
//            upSlope = upVectorAdjusted.getY() / upVectorAdjusted.getZ();
        }

//        public boolean isInBounds(Location loc) {
//            Vector vecToLoc = loc.toVector().subtract(playerLocation);
//
////            Location adjustedLocHorizontal = vecToLoc.clone().rotateAroundY(-horizontalAxisDeltaAngle).toLocation(loc.getWorld());
////            boolean isInHorizontalBounds = (adjustedLocHorizontal.getX() >= leftSlope * adjustedLocHorizontal.getZ())
////                    && (adjustedLocHorizontal.getX() >= rightSlope * adjustedLocHorizontal.getZ());
//            boolean isInHorizontalBounds = true;
//
//            float verticalAngleDelta = vecToLoc.angle(playerDirection);
//            boolean isInVerticalBounds = verticalAngleDelta <= 50;
//
//            return isInHorizontalBounds && isInVerticalBounds;
//        }

        public boolean isInBounds(Location loc) {
            Vector vecToLoc = loc.toVector().subtract(playerLocation);
            return vecToLoc.angle(playerDirection) <= Math.toRadians(50);
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
            for(int dy = -5; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    Location loc = playerBlockLoc.clone().add(dx, dy, dz);

                    if (loc.getBlock().getType() != Material.AIR) {
                        if (bounds.isInBounds(loc)) {
                            loc.getBlock().setType(Material.GREEN_STAINED_GLASS);
                        } else {
                            loc.getBlock().setType(Material.RED_WOOL);
                        }
                    }
                }
            }
        }

        return blocks;
    }
}
