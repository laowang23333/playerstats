package com.example.playerstats;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.Material;
import org.bukkit.Statistic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class PlayerStatsCommand implements CommandExecutor, TabCompleter {
    private final PlayerStatsAPI api;

    public PlayerStatsCommand(PlayerStatsAPI api) {
        this.api = api;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("playerstats.use")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用这个命令。");
            return true;
        }

        if (args.length < 1) {
            help(sender, label);
            return true;
        }

        OfflinePlayer target = findPlayer(args[0]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(ChatColor.RED + "找不到这个玩家，或者该玩家没有保存过数据。");
            return true;
        }

        if (args.length == 1 || args[1].equalsIgnoreCase("all")) {
            showAll(sender, target);
            return true;
        }

        String type = args[1].toLowerCase(Locale.ROOT);
        switch (type) {
            case "mobs", "mob", "kills" -> showMobs(sender, target);
            case "items", "item", "pickup" -> showItems(sender, target);
            case "blocks", "block", "mine", "mined" -> showBlocks(sender, target);
            case "craft", "crafted" -> showCrafted(sender, target);
            case "use", "used" -> showUsed(sender, target);
            case "stat" -> showSpecific(sender, target, args);
            default -> {
                sender.sendMessage(ChatColor.RED + "未知分类: " + type);
                help(sender, label);
            }
        }
        return true;
    }

    private void showAll(CommandSender sender, OfflinePlayer p) {
        header(sender, p, "全部主要统计");

        int deaths = api.getUntyped(p, Statistic.DEATHS);
        int jumps = api.getUntyped(p, Statistic.JUMP);
        int playTicks = api.getUntyped(p, Statistic.PLAY_ONE_MINUTE);

        sender.sendMessage(ChatColor.YELLOW + "基础:");
        sender.sendMessage(ChatColor.GRAY + "  死亡: " + ChatColor.WHITE + deaths);
        sender.sendMessage(ChatColor.GRAY + "  跳跃: " + ChatColor.WHITE + jumps);
        sender.sendMessage(ChatColor.GRAY + "  游戏时间: " + ChatColor.WHITE + formatTicks(playTicks));

        showTop(sender, "击杀怪物", api.allMobKills(p), 20);
        showTop(sender, "拾取物品", api.allPickups(p), 20);
        showTop(sender, "挖掘方块", api.allMined(p), 20);
        showTop(sender, "合成物品", api.allCrafted(p), 10);
    }

    private void showMobs(CommandSender sender, OfflinePlayer p) {
        header(sender, p, "击杀怪物");
        showTop(sender, "KILL_ENTITY", api.allMobKills(p), 50);
    }

    private void showItems(CommandSender sender, OfflinePlayer p) {
        header(sender, p, "拾取物品");
        showTop(sender, "PICKUP", api.allPickups(p), 50);
    }

    private void showBlocks(CommandSender sender, OfflinePlayer p) {
        header(sender, p, "挖掘方块");
        showTop(sender, "MINE_BLOCK", api.allMined(p), 50);
    }

    private void showCrafted(CommandSender sender, OfflinePlayer p) {
        header(sender, p, "合成物品");
        showTop(sender, "CRAFT_ITEM", api.allCrafted(p), 50);
    }

    private void showUsed(CommandSender sender, OfflinePlayer p) {
        header(sender, p, "使用物品");
        showTop(sender, "USE_ITEM", api.allUsed(p), 50);
    }

    private void showSpecific(CommandSender sender, OfflinePlayer p, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "/playerstats <玩家> stat <统计> <目标>");
            sender.sendMessage(ChatColor.GRAY + "例如:");
            sender.sendMessage(ChatColor.GRAY + "/playerstats Steve stat KILL_ENTITY ZOMBIE");
            sender.sendMessage(ChatColor.GRAY + "/playerstats Steve stat PICKUP DIAMOND");
            sender.sendMessage(ChatColor.GRAY + "/playerstats Steve stat MINE_BLOCK STONE");
            return;
        }

        Statistic statistic;
        try {
            statistic = Statistic.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + "不存在的 Statistic: " + args[2]);
            return;
        }

        String targetName = args[3].toUpperCase(Locale.ROOT);
        try {
            int value;
            switch (statistic.getType()) {
                case ENTITY -> value = p.getStatistic(statistic,
                        EntityType.valueOf(targetName));
                case ITEM, BLOCK -> value = p.getStatistic(statistic,
                        Material.matchMaterial(targetName));
                case UNTYPED -> value = p.getStatistic(statistic);
                default -> value = 0;
            }

            sender.sendMessage(ChatColor.GREEN + PlayerStatsAPI.pretty(statistic.name())
                    + " / " + PlayerStatsAPI.pretty(targetName)
                    + ": " + ChatColor.WHITE + value);
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "这个 Statistic 与目标类型不匹配，或者目标不存在。");
        }
    }

    private void showTop(CommandSender sender, String title,
                         List<? extends PlayerStatsAPI.StatEntry<?>> list, int limit) {
        sender.sendMessage(ChatColor.YELLOW + title + ChatColor.GRAY + "（只显示 > 0）");
        if (list.isEmpty()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "  暂无记录");
            return;
        }

        int count = Math.min(limit, list.size());
        for (int i = 0; i < count; i++) {
            var entry = list.get(i);
            sender.sendMessage(ChatColor.GRAY + "  "
                    + PlayerStatsAPI.pretty(entry.key().toString())
                    + ": " + ChatColor.WHITE + entry.value());
        }
    }

    private void header(CommandSender sender, OfflinePlayer p, String title) {
        sender.sendMessage(ChatColor.DARK_AQUA + "===== " + p.getName() + " / " + title + " =====");
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.AQUA + "PlayerStats");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " <玩家> [all]");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " <玩家> mobs");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " <玩家> items");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " <玩家> blocks");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " <玩家> craft");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " <玩家> use");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " <玩家> stat <Statistic> <目标>");
    }

    private OfflinePlayer findPlayer(String name) {
        OfflinePlayer exact = Bukkit.getOfflinePlayerIfCached(name);
        if (exact != null) return exact;

        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (p.getName() != null && p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }

        return Bukkit.getPlayerExact(name);
    }

    private String formatTicks(int ticks) {
        long seconds = ticks / 20L;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return filter(Arrays.asList("all", "mobs", "items", "blocks", "craft", "use", "stat"), args[1]);
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("stat")) {
            return filter(Arrays.stream(Statistic.values()).map(Enum::name).toList(), args[2]);
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("stat")) {
            try {
                Statistic s = Statistic.valueOf(args[2].toUpperCase(Locale.ROOT));
                if (s.getType() == Statistic.Type.ENTITY) {
                    return filter(Arrays.stream(EntityType.values()).map(Enum::name).toList(), args[3]);
                }
                if (s.getType() == Statistic.Type.ITEM || s.getType() == Statistic.Type.BLOCK) {
                    return filter(Arrays.stream(Material.values()).map(Enum::name).toList(), args[3]);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(v -> v.toLowerCase(Locale.ROOT).startsWith(p))
                .limit(100)
                .toList();
    }
}
