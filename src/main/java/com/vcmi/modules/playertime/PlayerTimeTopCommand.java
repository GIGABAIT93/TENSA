package com.vcmi.modules.playertime;

import com.vcmi.Message;
import com.vcmi.VCMI;
import com.vcmi.config.Lang;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;

import java.sql.SQLException;

public class PlayerTimeTopCommand implements SimpleCommand {

    private final PlayerTimeTracker timeTracker;
    public PlayerTimeTopCommand(PlayerTimeTracker timeTracker) {
        this.timeTracker = timeTracker;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();

        if (!sender.hasPermission("vcmi.playertime.top") && !sender.hasPermission("vcmi.playertime.admin")) {
            sender.sendMessage(Lang.no_perms.get());
            return;
        }
        int limit = 10;

        this.timeTracker.getTopPlayers(limit).thenAccept(result -> {
            try {
                sender.sendMessage(Lang.player_time_top.get());
                int position = 1;
                while (result.next()) {
                    String playerInfo = result.getString(1);
                    String playTime = PlayerTimeModule.formatTime(Long.parseLong(result.getString(2)));
                    sender.sendMessage(Lang.player_time_top_entry.replace("{position}", String.valueOf(position), "{player}", playerInfo, "{time}", playTime));
                    position += 1;
                }
            } catch (SQLException e) {
                Message.error(e.getMessage());
            } finally {
                try {
                    result.close();
                } catch (SQLException e) {
                    Message.error(e.getMessage());
                }
            }
        });


    }

    public static void unregister() {
        CommandManager manager = VCMI.server.getCommandManager();
        manager.unregister("vplayertop");
        manager.unregister("vptop");
    }
}
