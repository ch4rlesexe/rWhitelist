package net.ch4rlesexe.whitelist;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

public final class SkinCommandListener {
    private final ProxyServer proxy;
    private final Logger logger;


    private static final Set<String> KNOWN_SUBCOMMANDS = Set.of(
            "set", "select", "clear", "reset", "update", "refresh", "menu", "gui",
            "help", "status", "undo", "fav", "favourite", "url", "drop", "search", "random"
    );

    public SkinCommandListener(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    private boolean isAdmin(com.velocitypowered.api.command.CommandSource source) {
        if (!(source instanceof Player player)) {
            return true;
        }
        return player.hasPermission("skinsrestorer.admin")
                || player.hasPermission("rwhitelist.admin")
                || player.hasPermission("blockrandomizer.admin")
                || player.hasPermission("group.owner")
                || player.hasPermission("group.staff");
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onCommandExecute(CommandExecuteEvent event) {
        String raw = event.getCommand().trim();
        if (raw.isEmpty()) return;

        String[] parts = raw.split("\\s+");
        String base = parts[0].toLowerCase();


        if (!base.equals("skin") && !base.equals("sr")) {
            return;
        }


        if (!(event.getCommandSource() instanceof Player caller)) {
            return;
        }


        if (!isAdmin(caller)) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            caller.sendMessage(
                    net.kyori.adventure.text.Component.text("You do not have permission to use skin commands.", net.kyori.adventure.text.format.NamedTextColor.RED)
            );
            return;
        }




        if (parts.length == 2) {
            String arg = parts[1];
            if (!KNOWN_SUBCOMMANDS.contains(arg.toLowerCase())) {
                String rewritten = "skin set " + arg + " " + caller.getUsername();
                event.setResult(CommandExecuteEvent.CommandResult.command(rewritten));
                return;
            }
        }



        if (parts.length == 3 && (parts[1].equalsIgnoreCase("set") || parts[1].equalsIgnoreCase("select"))) {
            String skinArg = parts[2];
            String rewritten = "skin set " + skinArg + " " + caller.getUsername();
            event.setResult(CommandExecuteEvent.CommandResult.command(rewritten));
            return;
        }





        if (parts.length >= 4 && (parts[1].equalsIgnoreCase("set") || parts[1].equalsIgnoreCase("select"))) {
            String arg1 = parts[2];
            String arg2 = parts[3];

            if (proxy.getPlayer(arg1).isPresent() && proxy.getPlayer(arg2).isEmpty()) {
                StringBuilder sb = new StringBuilder("skin set ");
                sb.append(arg2).append(" ").append(arg1);
                for (int i = 4; i < parts.length; i++) {
                    sb.append(" ").append(parts[i]);
                }
                event.setResult(CommandExecuteEvent.CommandResult.command(sb.toString()));
                return;
            }
        }


        if (parts.length == 2 && parts[1].equalsIgnoreCase("resetall")) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            caller.sendMessage(net.kyori.adventure.text.Component.text("Resetting skins for all online players...", net.kyori.adventure.text.format.NamedTextColor.GREEN));
            for (Player p : proxy.getAllPlayers()) {
                proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), "sr clear " + p.getUsername());
            }
            return;
        }
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onTabComplete(TabCompleteEvent event) {
        String partial = event.getPartialMessage().trim();
        String lower = partial.toLowerCase();

        if (lower.startsWith("/skin ") || lower.startsWith("/sr ")) {
            if (!isAdmin(event.getPlayer())) {
                event.getSuggestions().clear();
                return;
            }

            String[] tokens = partial.split("\\s+");
            List<String> suggestions = event.getSuggestions();
            boolean endsWithSpace = partial.endsWith(" ");

            if (tokens.length == 2 && tokens[1].equalsIgnoreCase("set") && endsWithSpace) {

                suggestions.clear();
                for (Player p : proxy.getAllPlayers()) {
                    suggestions.add(p.getUsername());
                }
            } else if (tokens.length == 3 && tokens[1].equalsIgnoreCase("set")) {
                if (endsWithSpace) {

                    suggestions.clear();
                    for (Player p : proxy.getAllPlayers()) {
                        suggestions.add(p.getUsername());
                    }
                } else {

                    String prefix = tokens[2].toLowerCase();
                    suggestions.clear();
                    for (Player p : proxy.getAllPlayers()) {
                        if (p.getUsername().toLowerCase().startsWith(prefix)) {
                            suggestions.add(p.getUsername());
                        }
                    }
                }
            } else if (tokens.length == 1 && endsWithSpace) {

                if (!suggestions.contains("set")) suggestions.add(0, "set");
                if (!suggestions.contains("clear")) suggestions.add(1, "clear");
                for (Player p : proxy.getAllPlayers()) {
                    if (!suggestions.contains(p.getUsername())) {
                        suggestions.add(p.getUsername());
                    }
                }
            }
        }
    }
}
