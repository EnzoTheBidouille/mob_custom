package com.thebidouille_mob_custom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

public class CompanionTeleportListener implements Listener {
    private final JavaPlugin plugin;

    public CompanionTeleportListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!event.isCancelled()) {
            Player player = event.getPlayer();
            Location from = event.getFrom();
            Location to = event.getTo();
            World fromWorld = from.getWorld();
            
            // On attend 1 tick pour être sûr que le joueur est téléporté
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Recherche des compagnons uniquement dans le monde de départ
                    for (Entity entity : fromWorld.getNearbyEntities(from, 30, 30, 30)) {
                        Component customName = entity.customName();
                        if (customName != null) {
                            String name = PlainTextComponentSerializer.plainText().serialize(customName);
                            if (name.contains(player.getName())) {
                                // Offset par rapport au joueur pour éviter les collisions
                                double offsetX = entity.getLocation().getX() - from.getX();
                                double offsetY = entity.getLocation().getY() - from.getY();
                                double offsetZ = entity.getLocation().getZ() - from.getZ();
                                
                                // Calcule la nouvelle position en gardant l'offset relatif
                                Location companionDest = to.clone().add(offsetX, offsetY, offsetZ);
                                
                                // Téléporte le compagnon
                                entity.teleport(companionDest);
                                
                                // Debug message
                                plugin.getLogger().info("Companion teleported with player " + player.getName());
                            }
                        }
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }
}