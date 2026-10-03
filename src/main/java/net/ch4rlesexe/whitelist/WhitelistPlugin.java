package net.ch4rlesexe.whitelist;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "rwhitelist",
        name = "rWhitelist",
        version = "1.0.0",
        authors = {"ch4rlesexe"}
)
public final class WhitelistPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private WhitelistConfig config;
    private WhitelistDatabase database;

    @Inject
    public WhitelistPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        this.config = WhitelistConfig.load(dataDirectory, logger);
        this.database = new WhitelistDatabase(config, logger);
        this.database.init();

        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("whitelist")
                        .aliases("wl")
                        .plugin(this)
                        .build(),
                new WhitelistCommand(this)
        );

        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("redeem")
                        .aliases("code")
                        .plugin(this)
                        .build(),
                new RedeemCommand(this)
        );

        proxy.getEventManager().register(this, new SkinCommandListener(proxy, logger));

        logger.info("rWhitelist enabled — hub / survival / recording groups (see config.yml).");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (database != null) {
            database.close();
        }
    }




    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!config.enforceHub || !database.isWhitelistEnabled("hub")) {
            return;
        }

        var uuid = event.getPlayer().getUniqueId();
        var name = event.getPlayer().getUsername();


        if (database.isWhitelisted("hub", uuid, name)
                || database.isWhitelisted("survival", uuid, name)
                || database.isWhitelisted("recording", uuid, name)
                || database.isWhitelisted("admin", uuid, name)) {

            database.updateUsername(uuid, name);
            return;
        }

        event.setResult(LoginEvent.ComponentResult.denied(
                Component.text()
                        .append(Component.text("WHITELIST", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.newline())
                        .append(Component.newline())
                        .append(Component.text("You are not whitelisted on this server.", NamedTextColor.GRAY))
                        .build()
        ));
    }




    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        RegisteredServer target = event.getResult().getServer().orElse(null);
        if (target == null) return;

        String serverName = target.getServerInfo().getName().toLowerCase();
        Player player = event.getPlayer();
        var uuid = player.getUniqueId();
        var name = player.getUsername();




        if (database.isServerLocked(serverName)) {
            if (!database.isWhitelisted("admin", uuid, name)) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                player.sendMessage(
                        Component.text()
                                .append(Component.text("\u2716 ", NamedTextColor.RED))
                                .append(Component.text(serverName.substring(0, 1).toUpperCase() + serverName.substring(1),
                                        NamedTextColor.RED, TextDecoration.BOLD))
                                .append(Component.text(" is currently ", NamedTextColor.GRAY))
                                .append(Component.text("admin-locked", NamedTextColor.RED, TextDecoration.BOLD))
                                .append(Component.text(".", NamedTextColor.GRAY))
                                .build()
                );
                return;
            }

            return;
        }


        if (config.hubServers.contains(serverName)) {
            return;
        }


        if (config.survivalServers.contains(serverName)) {

            if (!database.isWhitelistEnabled(serverName) || !database.isWhitelistEnabled("survival")) {
                return;
            }

            if (!database.isWhitelisted("survival", uuid, name)) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                player.sendMessage(
                        Component.text()
                                .append(Component.text("\u2716 ", NamedTextColor.RED))
                                .append(Component.text("You are not whitelisted for ", NamedTextColor.GRAY))
                                .append(Component.text("Survival", NamedTextColor.GOLD, TextDecoration.BOLD))
                                .append(Component.text(" servers.", NamedTextColor.GRAY))
                                .build()
                );
                return;
            }
        }


        if (config.recordingServers.contains(serverName)) {

            if (!database.isWhitelistEnabled(serverName) || !database.isWhitelistEnabled("recording")) {
                return;
            }

            if (!database.isWhitelisted("recording", uuid, name)) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                player.sendMessage(
                        Component.text()
                                .append(Component.text("\u2716 ", NamedTextColor.RED))
                                .append(Component.text("You are not whitelisted for ", NamedTextColor.GRAY))
                                .append(Component.text("Recording", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                                .append(Component.text(" servers.", NamedTextColor.GRAY))
                                .build()
                );
            }
        }
    }



    public WhitelistDatabase getDatabase() {
        return database;
    }

    public WhitelistConfig getConfig() {
        return config;
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public Logger getLogger() {
        return logger;
    }
}
