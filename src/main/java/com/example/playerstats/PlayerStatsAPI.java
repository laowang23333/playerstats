package com.example.playerstats;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only facade over Bukkit/Spigot player statistics.
 *
 * For mob kills, this class first tries the Bukkit EntityType API and then,
 * when running on Mohist/Forge, tries to inspect the underlying Minecraft
 * StatsCounter reflectively. This keeps the plugin compilable against the
 * Spigot API while allowing Mod entity statistics to be discovered at runtime.
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

    public int getUsed(OfflinePlayer player, Statistic statistic, Material material) {
        return getMaterial(player, statistic, material);
    }

    public int getUsed(OfflinePlayer player, Material material) {
        return getUsed(player, Statistic.USE_ITEM, material);
    }

    /**
     * Returns every mob kill statistic that can be discovered.
     *
     * Bukkit EntityType entries are included first. On Mohist, the underlying
     * Minecraft StatsCounter is then inspected reflectively for additional
     * entity statistics, including Forge/Mod entities.
     */
    public List<StatEntry<?>> allMobKills(OfflinePlayer player) {
        List<StatEntry<?>> result = new ArrayList<>();

        // 1. Standard Bukkit entities.
        for (EntityType type : EntityType.values()) {
            if (!type.isAlive()) continue;

            int value;
            try {
                value = getMobKills(player, type);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            if (value > 0) {
                result.add(new StatEntry<>(type, value));
            }
        }

        // 2. Mohist/Forge entities not exposed through Bukkit EntityType.
        result.addAll(readModMobKills(player));

        result.sort(
                Comparator.comparingInt(StatEntry::value)
                        .reversed()
                        .thenComparing(
                                entry -> displayKey(entry.key()),
                                String.CASE_INSENSITIVE_ORDER
                        )
        );

        return result;
    }

    /**
     * Reads additional KILL_ENTITY entries from the underlying Minecraft
     * ServerPlayer/StatsCounter using reflection.
     */
    private List<StatEntry<?>> readModMobKills(OfflinePlayer player) {
        List<StatEntry<?>> result = new ArrayList<>();

        try {
            if (!player.isOnline()) {
                return result;
            }

            Object bukkitPlayer = player.getPlayer();
            if (bukkitPlayer == null) {
                return result;
            }

            // CraftPlayer#getHandle()
            Method getHandle = bukkitPlayer.getClass().getMethod("getHandle");
            Object serverPlayer = getHandle.invoke(bukkitPlayer);
            if (serverPlayer == null) {
                return result;
            }

            /*
             * ServerPlayer#getStats() exists in the 1.20.x Mojang mappings.
             * Mohist may expose it directly or through a superclass.
             */
            Method getStats = findMethod(serverPlayer.getClass(), "getStats");
            if (getStats == null) {
                return result;
            }

            Object statsCounter = getStats.invoke(serverPlayer);
            if (statsCounter == null) {
                return result;
            }

            /*
             * StatsCounter#getStats() returns the map of Stat<?> -> Integer.
             */
            Method getStatsMap = findMethod(statsCounter.getClass(), "getStats");
            if (getStatsMap == null) {
                return result;
            }

            Object mapObject = getStatsMap.invoke(statsCounter);
            if (!(mapObject instanceof Map<?, ?> statsMap)) {
                return result;
            }

            for (Map.Entry<?, ?> mapEntry : statsMap.entrySet()) {
                Object statObject = mapEntry.getKey();
                Object valueObject = mapEntry.getValue();

                if (!(valueObject instanceof Number number)) {
                    continue;
                }

                int value = number.intValue();
                if (value <= 0 || statObject == null) {
                    continue;
                }

                if (!isKillEntityStat(statObject)) {
                    continue;
                }

                Object valueObjectForEntity = extractStatValue(statObject);
                if (valueObjectForEntity == null) {
                    continue;
                }

                String entityId = entityIdFromStatValue(valueObjectForEntity);
                if (entityId == null || entityId.isBlank()) {
                    continue;
                }

                // If Bukkit already returned this entity, avoid duplicates.
                if (containsEntityId(result, entityId)) {
                    continue;
                }

                result.add(new StatEntry<>(new ModEntityKey(entityId), value));
            }

        } catch (Throwable ignored) {
            /*
             * Reflection is deliberately best-effort. If the running Mohist
             * build uses different names, the normal Bukkit statistics still
             * work instead of breaking the whole command.
             */
        }

        return result;
    }

    /**
     * Checks whether a Minecraft Stat is the ENTITY_KILLED statistic.
     */
    private boolean isKillEntityStat(Object statObject) {
        try {
            // Stat#getType() -> ResourceLocation in Mojang mappings.
            Method getType = findMethod(statObject.getClass(), "getType");
            if (getType != null) {
                Object type = getType.invoke(statObject);
                if (type != null) {
                    String text = type.toString().toLowerCase(Locale.ROOT);
                    if (text.contains("kill_entity")) {
                        return true;
                    }
                }
            }

            /*
             * Fallback: inspect the Stat's statistic type field/name.
             * This is intentionally loose because Mohist mappings vary.
             */
            String text = statObject.toString().toLowerCase(Locale.ROOT);
            return text.contains("kill_entity");

        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Gets the value/criterion attached to a Minecraft Stat.
     */
    private Object extractStatValue(Object statObject) {
        try {
            /*
             * Stat#getValue() is the normal 1.20.x Mojang-mapped method.
             */
            Method getValue = findMethod(statObject.getClass(), "getValue");
            if (getValue != null) {
                return getValue.invoke(statObject);
            }

            /*
             * Fallback for mappings where the field is exposed differently.
             */
            for (String fieldName : new String[]{"value", "name", "location"}) {
                Field field = findField(statObject.getClass(), fieldName);
                if (field != null) {
                    field.setAccessible(true);
                    Object value = field.get(statObject);
                    if (value != null) return value;
                }
            }
        } catch (Throwable ignored) {
            // best effort
        }

        return null;
    }

    /**
     * Converts the statistic value to an entity registry ID where possible.
     */
    private String entityIdFromStatValue(Object value) {
        try {
            /*
             * If the value is already a ResourceLocation, toString() is
             * normally "modid:entity_name".
             */
            String text = value.toString();

            if (text.contains(":")) {
                return text;
            }

            /*
             * Some mappings expose an EntityType object. Try registry key.
             */
            Method getDescriptionId = findMethod(value.getClass(), "getDescriptionId");
            if (getDescriptionId != null) {
                Object description = getDescriptionId.invoke(value);
                if (description != null) {
                    String id = description.toString();
                    int entityPos = id.indexOf("entity.");
                    if (entityPos >= 0) {
                        return id.substring(entityPos);
                    }
                }
            }

        } catch (Throwable ignored) {
            // best effort
        }

        return null;
    }

    private boolean containsEntityId(List<StatEntry<?>> entries, String id) {
        String normalized = id.toLowerCase(Locale.ROOT);

        for (StatEntry<?> entry : entries) {
            if (entry.key() instanceof EntityType type) {
                String name = type.name().toLowerCase(Locale.ROOT);
                if (name.equals(normalized)
                        || ("minecraft:" + name).equals(normalized)) {
                    return true;
                }
            }

            if (entry.key() instanceof ModEntityKey mod) {
                if (mod.id().equalsIgnoreCase(id)) {
                    return true;
                }
            }
        }

        return false;
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
            OfflinePlayer player,
            Statistic statistic,
            boolean blocksOnly) {

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

            if (value > 0) {
                result.add(new StatEntry<>(material, value));
            }
        }

        result.sort(
                Comparator.comparingInt(StatEntry<Material>::value)
                        .reversed()
                        .thenComparing(
                                entry -> entry.key().name(),
                                String.CASE_INSENSITIVE_ORDER
                        )
        );

        return result;
    }

    public static String pretty(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }

        // Registry IDs such as "modid:banshee".
        String clean = name;

        int colon = clean.indexOf(':');
        if (colon >= 0) {
            clean = clean.substring(colon + 1);
        }

        String[] parts = clean.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;

            if (out.length() > 0) {
                out.append(' ');
            }

            out.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1));
        }

        return out.toString();
    }

    /**
     * Returns a display name for both Bukkit and Mod entity keys.
     *
     * This method intentionally stays API-only. The actual Minecraft client
     * localization is normally applied by the client itself; for a server
     * message we therefore use a readable registry name as the safe fallback.
     */
    public static String displayKey(Object key) {
        if (key instanceof EntityType type) {
            return pretty(type.name());
        }

        if (key instanceof ModEntityKey mod) {
            return pretty(mod.id());
        }

        return pretty(String.valueOf(key));
    }

    private Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        Class<?> current = type;

        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    private Field findField(Class<?> type, String name) {
        Class<?> current = type;

        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
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

    /**
     * Key used for Mod entities that are not represented by Bukkit EntityType.
     */
    public record ModEntityKey(String id) {
        @Override
        public String toString() {
            return id;
        }
    }

    public record StatEntry<T>(T key, int value) {}
}
