package me.tazadejava.blockranges;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;

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

                showPlayerRange(x, z);
            }
        }
    }

    private void showPlayerRange(int[] x, int[] z) {
        //TODO: show range in stained glass or some indication, client side block
    }

    public BlockRange2D getPlayerSelection(Player p) {
        return selections.getOrDefault(p, null);
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
