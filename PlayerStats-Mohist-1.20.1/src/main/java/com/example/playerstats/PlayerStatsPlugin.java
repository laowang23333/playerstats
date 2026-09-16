package com.example.playerstats;

import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerStatsPlugin extends JavaPlugin {
    private PlayerStatsAPI statsAPI;

    @Override
    public void onEnable() {
        statsAPI = new PlayerStatsAPI(this);
        PlayerStatsCommand command = new PlayerStatsCommand(statsAPI);

        var cmd = getCommand("playerstats");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        getLogger().info("PlayerStats enabled. Reading vanilla Minecraft statistics directly.");
    }

    public PlayerStatsAPI getStatsAPI() {
        return statsAPI;
    }
}
