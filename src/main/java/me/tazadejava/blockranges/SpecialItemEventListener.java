package me.tazadejava.blockranges;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * Event Listener class that will call events for any SpecialItem implementations. Currently, only the PlayerInteractEvent is registered, but this template can easily be extended to any other event.
 */
public class SpecialItemEventListener implements Listener {

    private HashMap<SpecialItem.ItemEventHooks, List<SpecialItem>> hookedItems;

    public SpecialItemEventListener(Collection<SpecialItem> items) {
        hookedItems = new HashMap<>();

        for(SpecialItem item : items) {
            for(SpecialItem.ItemEventHooks hook : item.getHooks()) {
                if (!hookedItems.containsKey(hook)) {
                    hookedItems.put(hook, new ArrayList<>());
                }

                hookedItems.get(hook).add(item);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if(hookedItems.containsKey(SpecialItem.ItemEventHooks.PLAYER_INTERACT)) {
            for(SpecialItem item : hookedItems.get(SpecialItem.ItemEventHooks.PLAYER_INTERACT)) {
                item.onPlayerInteract(event);
            }
        }
    }
}
