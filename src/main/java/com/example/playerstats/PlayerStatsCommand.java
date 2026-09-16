package com.example.playerstats;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;

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
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!sender.hasPermission("playerstats.use")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用这个命令。");
            return true;
        }

        if (args.length < 1) {
            help(sender, label);
            return true;
        }

        OfflinePlayer target = findPlayer(args[0]);

        if (target == null) {
            sender.sendMessage(
                    ChatColor.RED + "找不到这个玩家，或者该玩家没有保存过数据。"
            );
            return true;
        }

        /*
         * /playerstats <玩家>
         * /playerstats <玩家> all
         */
        if (args.length == 1 || args[1].equalsIgnoreCase("all")) {
            showAll(sender, target);
            return true;
        }

        String type = args[1].toLowerCase(Locale.ROOT);

        switch (type) {

            case "mobs":
            case "mob":
            case "kills":
                showMobs(sender, target);
                break;

            case "items":
            case "item":
            case "pickup":
                showItems(sender, target);
                break;

            case "blocks":
            case "block":
            case "mine":
            case "mined":
                showBlocks(sender, target);
                break;

            case "craft":
            case "crafted":
                showCrafted(sender, target);
                break;

            case "use":
            case "used":
                showUsed(sender, target);
                break;

            case "stat":
                showSpecific(sender, target, args);
                break;

            default:
                sender.sendMessage(
                        ChatColor.RED + "未知分类: " + type
                );

                help(sender, label);
                break;
        }

        return true;
    }

    /**
     * 显示玩家主要统计
     */
    private void showAll(
            CommandSender sender,
            OfflinePlayer player
    ) {

        header(
                sender,
                player,
                "全部主要统计"
        );

        int deaths = api.getUntyped(
                player,
                Statistic.DEATHS
        );

        int jumps = api.getUntyped(
                player,
                Statistic.JUMP
        );

        int playTicks = api.getUntyped(
                player,
                Statistic.PLAY_ONE_MINUTE
        );

        sender.sendMessage(
                ChatColor.YELLOW + "基础:"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "  死亡: "
                        + ChatColor.WHITE
                        + deaths
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "  跳跃: "
                        + ChatColor.WHITE
                        + jumps
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "  游戏时间: "
                        + ChatColor.WHITE
                        + formatTicks(playTicks)
        );

        showTop(
                sender,
                "击杀怪物",
                api.allMobKills(player),
                20
        );

        showTop(
                sender,
                "拾取物品",
                api.allPickups(player),
                20
        );

        showTop(
                sender,
                "挖掘方块",
                api.allMined(player),
                20
        );

        showTop(
                sender,
                "合成物品",
                api.allCrafted(player),
                10
        );
    }

    /**
     * 显示击杀怪物
     */
    private void showMobs(
            CommandSender sender,
            OfflinePlayer player
    ) {

        header(
                sender,
                player,
                "击杀怪物"
        );

        showTop(
                sender,
                "KILL_ENTITY",
                api.allMobKills(player),
                50
        );
    }

    /**
     * 显示拾取物品
     */
    private void showItems(
            CommandSender sender,
            OfflinePlayer player
    ) {

        header(
                sender,
                player,
                "拾取物品"
        );

        showTop(
                sender,
                "PICKUP",
                api.allPickups(player),
                50
        );
    }

    /**
     * 显示挖掘方块
     */
    private void showBlocks(
            CommandSender sender,
            OfflinePlayer player
    ) {

        header(
                sender,
                player,
                "挖掘方块"
        );

        showTop(
                sender,
                "MINE_BLOCK",
                api.allMined(player),
                50
        );
    }

    /**
     * 显示合成物品
     */
    private void showCrafted(
            CommandSender sender,
            OfflinePlayer player
    ) {

        header(
                sender,
                player,
                "合成物品"
        );

        showTop(
                sender,
                "CRAFT_ITEM",
                api.allCrafted(player),
                50
        );
    }

    /**
     * 显示使用物品
     */
    private void showUsed(
            CommandSender sender,
            OfflinePlayer player
    ) {

        header(
                sender,
                player,
                "使用物品"
        );

        showTop(
                sender,
                "USE_ITEM",
                api.allUsed(player),
                50
        );
    }

    /**
     * 精确查询某一个 Statistic
     *
     * 示例：
     *
     * /playerstats Steve stat KILL_ENTITY ZOMBIE
     *
     * /playerstats Steve stat PICKUP DIAMOND
     *
     * /playerstats Steve stat MINE_BLOCK STONE
     */
    private void showSpecific(
            CommandSender sender,
            OfflinePlayer player,
            String[] args
    ) {

        if (args.length < 4) {

            sender.sendMessage(
                    ChatColor.YELLOW
                            + "/playerstats <玩家> stat <统计> <目标>"
            );

            sender.sendMessage(
                    ChatColor.GRAY
                            + "例如:"
            );

            sender.sendMessage(
                    ChatColor.GRAY
                            + "/playerstats Steve stat KILL_ENTITY ZOMBIE"
            );

            sender.sendMessage(
                    ChatColor.GRAY
                            + "/playerstats Steve stat PICKUP DIAMOND"
            );

            sender.sendMessage(
                    ChatColor.GRAY
                            + "/playerstats Steve stat MINE_BLOCK STONE"
            );

            return;
        }

        Statistic statistic;

        try {

            statistic = Statistic.valueOf(
                    args[2].toUpperCase(Locale.ROOT)
            );

        } catch (IllegalArgumentException exception) {

            sender.sendMessage(
                    ChatColor.RED
                            + "不存在的 Statistic: "
                            + args[2]
            );

            return;
        }

        String targetName =
                args[3].toUpperCase(Locale.ROOT);

        try {

            int value;

            switch (statistic.getType()) {

                case ENTITY:

                    EntityType entityType =
                            EntityType.valueOf(targetName);

                    value = player.getStatistic(
                            statistic,
                            entityType
                    );

                    break;

                case ITEM:
                case BLOCK:

                    Material material =
                            Material.matchMaterial(targetName);

                    if (material == null) {

                        sender.sendMessage(
                                ChatColor.RED
                                        + "不存在的物品/方块: "
                                        + targetName
                        );

                        return;
                    }

                    value = player.getStatistic(
                            statistic,
                            material
                    );

                    break;

                case UNTYPED:

                    value = player.getStatistic(
                            statistic
                    );

                    break;

                default:

                    value = 0;
                    break;
            }

            sender.sendMessage(
                    ChatColor.GREEN
                            + PlayerStatsAPI.pretty(
                            statistic.name()
                    )
                            + " / "
                            + ChatColor.WHITE
                            + PlayerStatsAPI.pretty(
                            targetName
                    )
                            + ": "
                            + ChatColor.WHITE
                            + value
            );

        } catch (IllegalArgumentException exception) {

            sender.sendMessage(
                    ChatColor.RED
                            + "这个 Statistic 与目标类型不匹配，"
                            + "或者目标不存在。"
            );
        }
    }

    /**
     * 显示统计列表
     */
    private void showTop(
            CommandSender sender,
            String title,
            List<? extends PlayerStatsAPI.StatEntry<?>> list,
            int limit
    ) {

        sender.sendMessage(
                ChatColor.YELLOW
                        + title
                        + ChatColor.GRAY
                        + "（只显示 > 0）"
        );

        if (list.isEmpty()) {

            sender.sendMessage(
                    ChatColor.DARK_GRAY
                            + "  暂无记录"
            );

            return;
        }

        int count =
                Math.min(
                        limit,
                        list.size()
                );

        for (int i = 0; i < count; i++) {

            PlayerStatsAPI.StatEntry<?> entry =
                    list.get(i);

            sender.sendMessage(
                    ChatColor.GRAY
                            + "  "
                            + PlayerStatsAPI.pretty(
                            entry.key().toString()
                    )
                            + ": "
                            + ChatColor.WHITE
                            + entry.value()
            );
        }
    }

    /**
     * 标题
     */
    private void header(
            CommandSender sender,
            OfflinePlayer player,
            String title
    ) {

        sender.sendMessage(
                ChatColor.DARK_AQUA
                        + "===== "
                        + player.getName()
                        + " / "
                        + title
                        + " ====="
        );
    }

    /**
     * 帮助
     */
    private void help(
            CommandSender sender,
            String label
    ) {

        sender.sendMessage(
                ChatColor.AQUA
                        + "PlayerStats"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "/"
                        + label
                        + " <玩家> [all]"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "/"
                        + label
                        + " <玩家> mobs"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "/"
                        + label
                        + " <玩家> items"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "/"
                        + label
                        + " <玩家> blocks"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "/"
                        + label
                        + " <玩家> craft"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "/"
                        + label
                        + " <玩家> use"
        );

        sender.sendMessage(
                ChatColor.GRAY
                        + "/"
                        + label
                        + " <玩家> stat <Statistic> <目标>"
        );
    }

    /**
     * 查找玩家
     *
     * 注意：
     * 这里没有使用 Bukkit 1.20.1 不存在的
     * getOfflinePlayerIfCached()。
     */
    private OfflinePlayer findPlayer(String name) {

        /*
         * 先查在线玩家。
         */
        OfflinePlayer online =
                Bukkit.getPlayerExact(name);

        if (online != null) {
            return online;
        }

        /*
         * 再查服务器已经保存过的离线玩家。
         */
        for (OfflinePlayer player :
                Bukkit.getOfflinePlayers()) {

            if (player.getName() != null
                    && player.getName().equalsIgnoreCase(name)) {

                return player;
            }
        }

        return null;
    }

    /**
     * 游戏刻转换成小时/分钟
     */
    private String formatTicks(int ticks) {

        long seconds =
                ticks / 20L;

        long hours =
                seconds / 3600L;

        long minutes =
                (seconds % 3600L) / 60L;

        return hours
                + "h "
                + minutes
                + "m";
    }

    /**
     * Tab 补全
     */
    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {

        /*
         * 第一个参数：玩家名
         */
        if (args.length == 1) {

            String prefix =
                    args[0].toLowerCase(Locale.ROOT);

            return Bukkit.getOnlinePlayers()
                    .stream()
                    .map(
                            org.bukkit.entity.Player::getName
                    )
                    .filter(
                            name -> name
                                    .toLowerCase(Locale.ROOT)
                                    .startsWith(prefix)
                    )
                    .sorted()
                    .collect(
                            Collectors.toList()
                    );
        }

        /*
         * 第二个参数：分类
         */
        if (args.length == 2) {

            return filter(
                    Arrays.asList(
                            "all",
                            "mobs",
                            "items",
                            "blocks",
                            "craft",
                            "use",
                            "stat"
                    ),
                    args[1]
            );
        }

        /*
         * 第三个参数：
         * Statistic
         */
        if (args.length == 3
                && args[1].equalsIgnoreCase("stat")) {

            return filter(
                    Arrays.stream(
                            Statistic.values()
                    )
                            .map(Enum::name)
                            .toList(),
                    args[2]
            );
        }

        /*
         * 第四个参数：
         * EntityType 或 Material
         */
        if (args.length == 4
                && args[1].equalsIgnoreCase("stat")) {

            try {

                Statistic statistic =
                        Statistic.valueOf(
                                args[2].toUpperCase(
                                        Locale.ROOT
                                )
                        );

                if (statistic.getType()
                        == Statistic.Type.ENTITY) {

                    return filter(
                            Arrays.stream(
                                    EntityType.values()
                            )
                                    .map(Enum::name)
                                    .toList(),
                            args[3]
                    );
                }

                if (statistic.getType()
                        == Statistic.Type.ITEM
                        || statistic.getType()
                        == Statistic.Type.BLOCK) {

                    return filter(
                            Arrays.stream(
                                    Material.values()
                            )
                                    .map(Enum::name)
                                    .toList(),
                            args[3]
                    );
                }

            } catch (IllegalArgumentException ignored) {
                // Statistic 不存在时不提供补全
            }
        }

        return List.of();
    }

    /**
     * Tab 补全过滤
     */
    private List<String> filter(
            List<String> values,
            String prefix
    ) {

        String p =
                prefix.toLowerCase(Locale.ROOT);

        return values.stream()
                .filter(
                        value -> value
                                .toLowerCase(Locale.ROOT)
                                .startsWith(p)
                )
                .limit(100)
                .toList();
    }
}
