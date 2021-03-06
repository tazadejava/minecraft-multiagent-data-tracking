package me.tazadejava.blockranges;

import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Represents rectangular boundary. Primarily used to define the boundaries of a room in a mission map.
 */
public class BlockRange2D {

    public int startX, startZ, endX, endZ;

    private Block startBlock, endBlock;

    /**
     * Start X/Z has to be lower than end X/Z!
     * @param startX
     * @param endX
     * @param startZ
     * @param endZ
     */
    public BlockRange2D(int startX, int endX, int startZ, int endZ) {
        this.startX = startX;
        this.endX = endX;
        this.startZ = startZ;
        this.endZ = endZ;
    }

    /**
     * Used for defining block ranges dynamically via the SelectionWand tool.
     */
    public BlockRange2D() {

    }

    public BlockRange2D(JsonObject object) {
        startX = object.get("startX").getAsInt();
        startZ = object.get("startZ").getAsInt();
        endX = object.get("endX").getAsInt();
        endZ = object.get("endZ").getAsInt();
    }

    public JsonObject save() {
        JsonObject object = new JsonObject();

        object.addProperty("startX", startX);
        object.addProperty("startZ", startZ);
        object.addProperty("endX", endX);
        object.addProperty("endZ", endZ);

        return object;
    }

    public BlockRange2D clone() {
        BlockRange2D clone = new BlockRange2D();
        clone.startX = startX;
        clone.startZ = startZ;
        clone.endX = endX;
        clone.endZ = endZ;
        clone.startBlock = startBlock;
        clone.endBlock = endBlock;

        return clone;
    }

    public void setStartBlock(Block block) {
        startBlock = block;

        if(endBlock != null) {
            calculateRanges();
        }
    }

    public void setEndBlock(Block block) {
        endBlock = block;

        if(startBlock != null) {
            calculateRanges();
        }
    }

    public boolean isRangeCalculated() {
        return startBlock != null && endBlock != null;
    }

    /**
     * Expands in all four directions by amount of blocks
     * @param amount The number of blocks to expand the range in all directions
     * @return The same block range, with the expansion completed
     */
    public BlockRange2D expand(int amount) {
        startX -= amount;
        startZ -= amount;

        endX += amount;
        endZ += amount;

        return this;
    }

    /**
     * Rectangular collision check.
     * @param otherBounds
     * @return
     */
    public boolean collidesWith(BlockRange2D otherBounds) {
        return (startX <= otherBounds.endX) && (otherBounds.startX <= endX) && (startZ <= otherBounds.endZ) && (otherBounds.startZ <= endZ);
    }

    public int[] getRangeX() {
        return new int[] {startX, endX};
    }

    public int[] getRangeZ() {
        return new int[] {startZ, endZ};
    }

    /**
     * Used only in conjunction with the start/end block methods. Will ensure that start X/Z will be less than end X/Z.
     */
    private void calculateRanges() {
        if(startBlock.getX() < endBlock.getX()) {
            startX = startBlock.getX();
            endX = endBlock.getX();
        } else {
            startX = endBlock.getX();
            endX = startBlock.getX();
        }

        if(startBlock.getZ() < endBlock.getZ()) {
            startZ = startBlock.getZ();
            endZ = endBlock.getZ();
        } else {
            startZ = endBlock.getZ();
            endZ = startBlock.getZ();
        }
    }

    public int getArea() {
        return (endX - startX) * (endZ - startZ);
    }

    /**
     * Only checks X and Z, does not check Y
     * @param location
     * @return
     */
    public boolean isInRange(Location location) {
        return (location.getBlockX() >= startX && location.getBlockX() <= endX)
                && (location.getBlockZ() >= startZ && location.getBlockZ() <= endZ);
    }

    /**
     * Only checks X and Z, does not check Y
     * @param x
     * @param z
     * @return
     */
    public boolean isInRange(int x, int z) {
        return x >= startX && x <= endX && z >= startZ && z <= endZ;
    }
}
