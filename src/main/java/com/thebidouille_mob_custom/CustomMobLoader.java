package com.thebidouille_mob_custom;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

public class CustomMobLoader extends JavaPlugin implements Listener {
    private Map<String, CustomMob> customMobs = new HashMap<>();
    private Map<UUID, UUID> companionOwners = new HashMap<>(); // Entité -> Joueur
    private Map<UUID, List<UUID>> playerCompanions = new HashMap<>(); // Joueur -> Liste d'entités
    
    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        loadCustomMobs();
        getCommand("spawncompanion").setExecutor(new SpawnCompanionCommand(this));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new CompanionTeleportListener(this), this);

        // Démarrer la tâche de suivi des compagnons
        startCompanionFollowTask();

        getLogger().info("CustomMobLoader a été activé avec succès!");
    }

    public Map<String, CustomMob> getCustomMobs() {
        return this.customMobs;
    }
    
    private void loadCustomMobs() {
        File mobsFolder = new File(getDataFolder(), "mobs");
        if (!mobsFolder.exists()) {
            mobsFolder.mkdirs();
            saveDefaultMob();
        }
        
        for (File file : mobsFolder.listFiles()) {
            if (file.getName().endsWith(".yml")) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                String mobId = file.getName().replace(".yml", "");
                
                CustomMob customMob = new CustomMob(
                    mobId,
                    EntityType.valueOf(config.getString("baseEntity")),
                    config.getString("name"),
                    config.getDouble("health"),
                    config.getDouble("damage"),
                    config.getDouble("speed"),
                    config.getBoolean("isCompanion", false),
                    config.getDouble("followDistance", 3.0),
                    config.getBoolean("protectOwner", true)
                );
                
                customMobs.put(mobId, customMob);
                getLogger().info("Loaded custom mob: " + mobId);
            }
        }
    }
    
    private void saveDefaultMob() {
        File exampleFile = new File(getDataFolder(), "mobs/example_companion.yml");
        FileConfiguration config = new YamlConfiguration();
        
        config.set("baseEntity", "WOLF");
        config.set("name", "§bFidèle Compagnon");
        config.set("health", 20.0);
        config.set("damage", 5.0);
        config.set("speed", 0.3);
        config.set("isCompanion", true);
        config.set("followDistance", 3.0);
        config.set("protectOwner", true);
        
        try {
            config.save(exampleFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public Entity spawnCompanion(String mobId, Player owner) {
        CustomMob customMob = customMobs.get(mobId);
        if (customMob == null || !customMob.isCompanion()) return null;
        
        Location spawnLoc = owner.getLocation().add(1, 0, 1);
        Entity entity = spawnLoc.getWorld().spawnEntity(spawnLoc, customMob.getBaseEntity());
        
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            
            // Configuration de base
            livingEntity.setCustomName(customMob.getName() + " §7(" + owner.getName() + ")");
            livingEntity.setCustomNameVisible(true);
            
            livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(customMob.getHealth());
            livingEntity.setHealth(customMob.getHealth());
            livingEntity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(customMob.getDamage());
            livingEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(customMob.getSpeed());
            
            // Configurations spécifiques aux compagnons
            if (entity instanceof Tameable) {
                ((Tameable) entity).setOwner(owner);
                ((Tameable) entity).setTamed(true);
            }
            
            // Enregistrer la relation propriétaire-compagnon
            companionOwners.put(entity.getUniqueId(), owner.getUniqueId());
            playerCompanions.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>())
                          .add(entity.getUniqueId());
        }
        
        return entity;
    }
    
    private void startCompanionFollowTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, UUID> entry : companionOwners.entrySet()) {
                    Entity companion = getServer().getEntity(entry.getKey());
                    Player owner = getServer().getPlayer(entry.getValue());
                    
                    if (companion == null || owner == null || !owner.isOnline()) continue;
                    
                    if (companion instanceof Mob) {
                        Mob mobCompanion = (Mob) companion;
                        CustomMob customMob = getCustomMobFromEntity(companion);
                        
                        if (customMob == null) continue;
                        
                        double followDistance = customMob.getFollowDistance();
                        double currentDistance = companion.getLocation().distance(owner.getLocation());
                        
                        if (currentDistance > followDistance) {
                            // Version 1.21.1 - Méthode simple de suivi
                            mobCompanion.setTarget(null);
                            // Utilise la navigation native du mob
                            Location targetLoc = owner.getLocation();
                            // mobCompanion.teleport(targetLoc);
                            
                            // Alternative : déplacement progressif
                            Vector direction = owner.getLocation()
                                .subtract(companion.getLocation())
                                .toVector()
                                .normalize()
                                .multiply(customMob.getSpeed());
                            Location newLoc = companion.getLocation().add(direction);
                            companion.teleport(newLoc);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 5L);
    }
    
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (companionOwners.containsKey(event.getEntity().getUniqueId())) {
            UUID ownerId = companionOwners.get(event.getEntity().getUniqueId());
            
            // Empêcher le compagnon d'attaquer son propriétaire
            if (event.getTarget() != null && event.getTarget().getUniqueId().equals(ownerId)) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Protéger le propriétaire
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            List<UUID> companions = playerCompanions.get(victim.getUniqueId());
            
            if (companions != null) {
                for (UUID companionId : companions) {
                    Entity companion = getServer().getEntity(companionId);
                    if (companion instanceof LivingEntity) {
                        CustomMob customMob = getCustomMobFromEntity(companion);
                        if (customMob != null && customMob.isProtectOwner()) {
                            // Le compagnon attaque l'agresseur
                            if (companion instanceof Creature && event.getDamager() instanceof LivingEntity) {
                                ((Creature) companion).setTarget((LivingEntity) event.getDamager());
                            }
                        }
                    }
                }
            }
        }
    }
    
    private CustomMob getCustomMobFromEntity(Entity entity) {
        if (entity.getCustomName() == null) return null;
        
        for (CustomMob mob : customMobs.values()) {
            if (entity.getCustomName().startsWith(mob.getName())) {
                return mob;
            }
        }
        return null;
    }
}

class CustomMob {
    private final String id;
    private final EntityType baseEntity;
    private final String name;
    private final double health;
    private final double damage;
    private final double speed;
    private final boolean isCompanion;
    private final double followDistance;
    private final boolean protectOwner;
    
    public CustomMob(String id, EntityType baseEntity, String name, double health, 
                    double damage, double speed, boolean isCompanion, 
                    double followDistance, boolean protectOwner) {
        this.id = id;
        this.baseEntity = baseEntity;
        this.name = name;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.isCompanion = isCompanion;
        this.followDistance = followDistance;
        this.protectOwner = protectOwner;
    }
    
    // Getters
    public String getId() { return id; }
    public EntityType getBaseEntity() { return baseEntity; }
    public String getName() { return name; }
    public double getHealth() { return health; }
    public double getDamage() { return damage; }
    public double getSpeed() { return speed; }
    public boolean isCompanion() { return isCompanion; }
    public double getFollowDistance() { return followDistance; }
    public boolean isProtectOwner() { return protectOwner; }
}