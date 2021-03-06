package me.tazadejava.blockranges;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;

/**
 * Implementation of SpecialItem class that determines the boundaries of a left/right click to define a room boundary. Hooks to the playerInteractEvent to create this range. Used only if the mission room bounds need to be manually defined: see MissionCommandHandler for more details on the implementation of this item.
 */
public class SelectionWand extends SpecialItem {

    private HashMap<Player, BlockRange2D> selections = new HashMap<>();

    public SelectionWand() {
        super(new SpecialItem.ItemEventHooks[] {ItemEventHooks.PLAYER_INTERACT});
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        if(event.getHand() == EquipmentSlot.HAND && isItem(event.getPlayer().getInventory().getItemInMainHand())) {
            Player p = event.getPlayer();

            if(!selections.containsKey(p)) {
                selections.put(p, new BlockRange2D());
            }

            BlockRange2D range = selections.get(p);

            if(event.getAction() == Action.LEFT_CLICK_BLOCK) {
                range.setStartBlock(event.getClickedBlock());
                event.getPlayer().sendMessage(ChatColor.GRAY + "Added left-block range at (" + event.getClickedBlock().getX() + ", " + event.getClickedBlock().getZ() + ").");
                event.setCancelled(true);
            } else if(event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                range.setEndBlock(event.getClickedBlock());
                event.getPlayer().sendMessage(ChatColor.GRAY + "Added right-block range at (" + event.getClickedBlock().getX() + ", " + event.getClickedBlock().getZ() + ").");
                event.setCancelled(true);
            }

            if(range.isRangeCalculated()) {
                int[] x = range.getRangeX();
                int[] z = range.getRangeZ();
                event.getPlayer().sendMessage(ChatColor.GRAY + "A block range has been calculated: from (" + x[0] + ", " + z[0] + ") to (" + x[1] + ", " + z[1] + ").");
            }
        }
    }

    public BlockRange2D getPlayerSelection(Player p) {
        return selections.getOrDefault(p, null);
    }

    public void clearPlayerSelection(Player p) {
        if (selections.containsKey(p)) {
            selections.remove(p);
        }
    }

    @Override
    public ItemStack getItem() {
        ItemStack item = new ItemStack(Material.WOODEN_SHOVEL, 1);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Selection Wand");
        meta.setLore(formatLore(ChatColor.GRAY + "Left and right click this tool to select 2D rectangular regions where specific rooms reside!", ChatColor.GRAY + "Command: /mission add <mission name> <room name>"));
        item.setItemMeta(meta);

        return item;
    }
}
