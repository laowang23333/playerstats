package com.example.playerstats;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Read-only facade over Bukkit/Spigot's vanilla per-player statistics.
 *
 * This class does NOT create its own database and does NOT track events.
 */
public final class PlayerStatsAPI {
    private final PlayerStatsPlugin plugin;

    public PlayerStatsAPI(PlayerStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public int getUntyped(OfflinePlayer player, Statistic statistic) {
        return safe(() -> player.getStatistic(statistic));
    }

    public int getEntity(OfflinePlayer player, Statistic statistic, EntityType entityType) {
        if (statistic.getType() != Statistic.Type.ENTITY) {
            throw new IllegalArgumentException(statistic + " is not an ENTITY statistic");
        }
        return safe(() -> player.getStatistic(statistic, entityType));
    }

    public int getMaterial(OfflinePlayer player, Statistic statistic, Material material) {
        if (statistic.getType() != Statistic.Type.ITEM
                && statistic.getType() != Statistic.Type.BLOCK) {
            throw new IllegalArgumentException(statistic + " is not an ITEM/BLOCK statistic");
        }
        return safe(() -> player.getStatistic(statistic, material));
    }

    public int getMobKills(OfflinePlayer player, EntityType entityType) {
        return getEntity(player, Statistic.KILL_ENTITY, entityType);
    }

    public int getDeathsBy(OfflinePlayer player, EntityType entityType) {
        return getEntity(player, Statistic.ENTITY_KILLED_BY, entityType);
    }

    public int getPickup(OfflinePlayer player, Material material) {
        return getMaterial(player, Statistic.PICKUP, material);
    }

    public int getDropped(OfflinePlayer player, Material material) {
        return getMaterial(player, Statistic.DROP, material);
    }

    public int getMined(OfflinePlayer player, Material material) {
        return getMaterial(player, Statistic.MINE_BLOCK, material);
    }

    public int getCrafted(OfflinePlayer player, Material material) {
        return getMaterial(player, Statistic.CRAFT_ITEM, material);
    }

    public int getUsed(OfflinePlayer player, Material material) {
        return getMaterial(player, Statistic.USE_ITEM, material);
    }

    public List<StatEntry<EntityType>> allMobKills(OfflinePlayer player) {
        List<StatEntry<EntityType>> result = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            if (!type.isAlive()) continue;
            int value;
            try {
                value = getMobKills(player, type);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (value > 0) result.add(new StatEntry<>(type, value));
        }
        result.sort(Comparator.comparingInt(StatEntry<EntityType>::value).reversed());
        return result;
    }

    public List<StatEntry<Material>> allPickups(OfflinePlayer player) {
        return allMaterials(player, Statistic.PICKUP, false);
    }

    public List<StatEntry<Material>> allMined(OfflinePlayer player) {
        return allMaterials(player, Statistic.MINE_BLOCK, true);
    }

    public List<StatEntry<Material>> allCrafted(OfflinePlayer player) {
        return allMaterials(player, Statistic.CRAFT_ITEM, false);
    }

    public List<StatEntry<Material>> allUsed(OfflinePlayer player) {
        return allMaterials(player, Statistic.USE_ITEM, false);
    }

    private List<StatEntry<Material>> allMaterials(
            OfflinePlayer player, Statistic statistic, boolean blocksOnly) {

        List<StatEntry<Material>> result = new ArrayList<>();
        for (Material material : Material.values()) {
            if (blocksOnly && !material.isBlock()) continue;
            if (!blocksOnly && material.isAir()) continue;

            int value;
            try {
                value = getMaterial(player, statistic, material);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (value > 0) result.add(new StatEntry<>(material, value));
        }

        result.sort(Comparator.comparingInt(StatEntry<Material>::value).reversed());
        return result;
    }

    public static String pretty(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private int safe(IntSupplier supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException ex) {
            return 0;
        }
    }

    @FunctionalInterface
    private interface IntSupplier {
        int get();
    }

    public record StatEntry<T>(T key, int value) {}
}
