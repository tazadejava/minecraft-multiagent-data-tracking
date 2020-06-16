package me.tazadejava.blockranges;

import org.bukkit.ChatColor;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

//requirements: item must be named and lored to become a special item
public abstract class SpecialItem {

    public enum ItemEventHooks {
        PLAYER_INTERACT
    }

    private ItemEventHooks[] hooks;

    public SpecialItem(ItemEventHooks[] hooks) {
        this.hooks = hooks;
    }

    public boolean isItem(ItemStack item) {
        if(item == null) {
            return false;
        }

        ItemStack reference = getItem();
        if(reference.getType() != item.getType()) {
            return false;
        }
        if(!item.hasItemMeta()) {
            return false;
        }
        if(!item.getItemMeta().hasDisplayName() || !item.getItemMeta().hasLore()) {
            return false;
        }

        return item.getItemMeta().getDisplayName().equals(reference.getItemMeta().getDisplayName()) && item.getItemMeta().getLore().equals(reference.getItemMeta().getLore());
    }

    public abstract ItemStack getItem();

    //helper method to make lore not too long; append to new line if necessary; max 48 char
    protected List<String> formatLore(String... lore) {
        List<String> formattedLore = new ArrayList<>();

        for(String line : lore) {
            if(line.length() <= 48) {
                formattedLore.add(line);
            } else {
                String[] split = line.split(" ");

                String lastColor = null;
                StringBuilder newLine = new StringBuilder();

                for(String word : split) {
                    String lastColors = ChatColor.getLastColors(word);
                    if(!lastColors.isEmpty()) {
                        lastColor = lastColors;
                    }

                    if(newLine.length() + word.length() > 48) {
                        formattedLore.add(newLine.toString());

                        if(lastColor != null) {
                            newLine = new StringBuilder(lastColor + " " + word);
                        } else {
                            newLine = new StringBuilder(" " + word);
                        }
                    } else {
                        if(newLine.length() != 0) {
                            newLine.append(" ");
                        }

                        newLine.append(word);
                    }
                }

                if(newLine.length() != 0) {
                    formattedLore.add(newLine.toString());
                }
            }
        }

        return formattedLore;
    }

    public void onPlayerInteract(PlayerInteractEvent event) {

    }

    public ItemEventHooks[] getHooks() {
        return hooks;
    }
}
