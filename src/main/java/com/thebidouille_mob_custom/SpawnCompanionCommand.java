package com.thebidouille_mob_custom;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SpawnCompanionCommand implements CommandExecutor, TabCompleter {
    private final CustomMobLoader plugin;

    public SpawnCompanionCommand(CustomMobLoader plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Vérifier si la commande est exécutée par un joueur
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur !");
            return true;
        }

        // Vérifier si l'ID du compagnon est spécifié
        if (args.length < 1) {
            sender.sendMessage("§cUtilisation: /spawncompanion <mobId>");
            return true;
        }

        Player player = (Player) sender;
        String mobId = args[0];

        // Vérifier les permissions
        if (!player.hasPermission("custommob.spawn." + mobId) && 
            !player.hasPermission("custommob.spawn.*")) {
            player.sendMessage("§cVous n'avez pas la permission de faire apparaître ce compagnon !");
            return true;
        }

        // Faire apparaître le compagnon
        Entity companion = plugin.spawnCompanion(mobId, player);
        
        if (companion == null) {
            player.sendMessage("§cLe compagnon '" + mobId + "' n'existe pas ou n'est pas configuré comme un compagnon !");
            return true;
        }

        player.sendMessage("§aVotre compagnon " + mobId + " est apparu !");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Filtrer les IDs des mobs qui sont des compagnons
            return plugin.getCustomMobs().values().stream()
                .filter(CustomMob::isCompanion)
                .map(CustomMob::getId)
                .filter(id -> id.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}