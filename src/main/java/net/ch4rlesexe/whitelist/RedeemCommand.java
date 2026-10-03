package net.ch4rlesexe.whitelist;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class RedeemCommand implements SimpleCommand {

    private final WhitelistPlugin plugin;

    public RedeemCommand(WhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(Component.text()
                    .append(Component.text("Usage: ", NamedTextColor.RED))
                    .append(Component.text("/redeem <code>", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" — Enter your whitelist access code.", NamedTextColor.GRAY))
                    .build());
            return;
        }

        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Only in-game players can redeem codes.", NamedTextColor.RED));
            return;
        }

        String code = args[0].trim();
        var uuid = player.getUniqueId();
        var name = player.getUsername();

        CompletableFuture.supplyAsync(() -> plugin.getDatabase().redeemCode(code, uuid, name))
                .thenAccept(result -> {
                    switch (result.status()) {
                        case SUCCESS -> {
                            String group = result.group();
                            String groupDisplay = group.substring(0, 1).toUpperCase() + group.substring(1);
                            player.sendMessage(Component.text()
                                    .append(Component.newline())
                                    .append(Component.text("  ✔ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                                    .append(Component.text("Access Granted! ", NamedTextColor.GREEN, TextDecoration.BOLD))
                                    .append(Component.text("You have redeemed code ", NamedTextColor.GRAY))
                                    .append(Component.text(code.toUpperCase(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                                    .append(Component.text("!", NamedTextColor.GRAY))
                                    .append(Component.newline())
                                    .append(Component.text("  You are now whitelisted for ", NamedTextColor.GRAY))
                                    .append(Component.text("[" + groupDisplay + "]", NamedTextColor.GOLD, TextDecoration.BOLD))
                                    .append(Component.text(" servers.", NamedTextColor.GRAY))
                                    .append(Component.newline())
                                    .append(Component.text("  Open your server selector to connect!", NamedTextColor.AQUA))
                                    .append(Component.newline())
                                    .build());
                        }
                        case NOT_FOUND -> player.sendMessage(Component.text()
                                .append(Component.text("✖ ", NamedTextColor.RED))
                                .append(Component.text("Invalid access code '", NamedTextColor.GRAY))
                                .append(Component.text(code, NamedTextColor.YELLOW))
                                .append(Component.text("'. Please check the spelling or ask staff for a code.", NamedTextColor.GRAY))
                                .build());
                        case EXHAUSTED -> player.sendMessage(Component.text()
                                .append(Component.text("✖ ", NamedTextColor.RED))
                                .append(Component.text("This access code has reached its maximum uses and is no longer valid.", NamedTextColor.GRAY))
                                .build());
                        case ALREADY_REDEEMED -> player.sendMessage(Component.text()
                                .append(Component.text("• ", NamedTextColor.YELLOW))
                                .append(Component.text("You have already redeemed code '", NamedTextColor.GRAY))
                                .append(Component.text(code.toUpperCase(), NamedTextColor.YELLOW))
                                .append(Component.text("'.", NamedTextColor.GRAY))
                                .build());
                        case ERROR -> player.sendMessage(Component.text()
                                .append(Component.text("✖ ", NamedTextColor.RED))
                                .append(Component.text("Database error while redeeming code. Please try again later.", NamedTextColor.GRAY))
                                .build());
                    }
                });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }
}
