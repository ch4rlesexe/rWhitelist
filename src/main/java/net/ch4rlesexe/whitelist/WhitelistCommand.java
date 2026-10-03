package net.ch4rlesexe.whitelist;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class WhitelistCommand implements SimpleCommand {

    private static final Set<String> VALID_GROUPS = Set.of("hub", "survival", "recording", "admin");
    private static final String MOJANG_API = "https://api.mojang.com/users/profiles/minecraft/";

    private final WhitelistPlugin plugin;

    public WhitelistCommand(WhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rwhitelist.manage");
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        if (args.length == 0) {
            sendHelp(source);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "on" -> handleOn(invocation);
            case "off" -> handleOff(invocation);
            case "add" -> handleAdd(invocation);
            case "remove", "rem", "del" -> handleRemove(invocation);
            case "clear" -> handleClear(invocation);
            case "list", "ls" -> handleList(invocation);
            case "check", "info" -> handleCheck(invocation);
            case "lock" -> handleLock(invocation);
            case "unlock" -> handleUnlock(invocation);
            case "status" -> handleStatus(invocation);
            case "code" -> handleCode(invocation);
            case "reload" -> handleReload(invocation);
            default -> sendHelp(source);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0].toLowerCase() : "";
            return List.of("on", "off", "add", "remove", "clear", "list", "check", "lock", "unlock", "status", "code", "reload", "help").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            if (sub.equals("code")) {
                return List.of("create", "list", "delete").stream()
                        .filter(s -> s.startsWith(partial))
                        .collect(Collectors.toList());
            }
            if (sub.equals("on") || sub.equals("off")) {
                List<String> targets = new java.util.ArrayList<>();
                targets.add("all");
                targets.add("hub");
                targets.add("survival");
                for (var s : plugin.getProxy().getAllServers()) {
                    targets.add(s.getServerInfo().getName().toLowerCase());
                }
                return targets.stream().filter(t -> t.startsWith(partial)).distinct().collect(Collectors.toList());
            }
            if (sub.equals("add") || sub.equals("remove") || sub.equals("rem") || sub.equals("del")) {
                return groupSuggestions(partial);
            }
            if (sub.equals("list") || sub.equals("ls") || sub.equals("clear")) {
                return groupSuggestions(partial);
            }
            if (sub.equals("check") || sub.equals("info")) {

                return plugin.getProxy().getAllPlayers().stream()
                        .map(Player::getUsername)
                        .filter(n -> n.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
            if (sub.equals("lock") || sub.equals("unlock")) {

                return plugin.getProxy().getAllServers().stream()
                        .map(s -> s.getServerInfo().getName())
                        .filter(n -> n.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
        }
        if (args.length == 3 && (sub.equals("add") || sub.equals("remove") || sub.equals("rem") || sub.equals("del"))) {
            String partial = args[2].toLowerCase();
            return plugin.getProxy().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private List<String> groupSuggestions(String partial) {
        return List.of("hub", "survival", "recording", "admin", "all").stream()
                .filter(g -> g.startsWith(partial))
                .collect(Collectors.toList());
    }



    private void handleAdd(Invocation invocation) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        if (args.length < 3) {
            source.sendMessage(error("Usage: /whitelist add <group|all> <player>"));
            return;
        }

        String groupArg = args[1].toLowerCase();
        String playerName = args[2];
        String addedBy = source instanceof Player p ? p.getUsername() : "Console";

        if (!groupArg.equals("all") && !VALID_GROUPS.contains(groupArg)) {
            source.sendMessage(error("Invalid group. Valid groups: hub, survival, recording, admin, all"));
            return;
        }


        CompletableFuture.supplyAsync(() -> resolvePlayer(playerName)).thenAccept(resolved -> {
            if (resolved == null) {
                source.sendMessage(error("Could not find player '" + playerName + "'. Check the spelling."));
                return;
            }


            List<String> groups;
            if (groupArg.equals("all")) {
                groups = List.of("hub", "survival", "recording", "admin");
            } else if (!groupArg.equals("hub")) {
                groups = List.of("hub", groupArg);
            } else {
                groups = List.of("hub");
            }

            TextComponent.Builder msg = Component.text();
            for (String group : groups) {
                boolean added = plugin.getDatabase().addPlayer(group, resolved.uuid, resolved.name, addedBy);
                if (added) {
                    msg.append(Component.text("\u2714 ", NamedTextColor.GREEN))
                            .append(Component.text(resolved.name, NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.text(" added to ", NamedTextColor.GRAY))
                            .append(groupTag(group))
                            .append(Component.newline());
                } else {
                    msg.append(Component.text("\u2022 ", NamedTextColor.YELLOW))
                            .append(Component.text(resolved.name, NamedTextColor.AQUA))
                            .append(Component.text(" already in ", NamedTextColor.GRAY))
                            .append(groupTag(group))
                            .append(Component.newline());
                }
            }
            source.sendMessage(msg.build());
        });
    }



    private void handleRemove(Invocation invocation) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        if (args.length < 3) {
            source.sendMessage(error("Usage: /whitelist remove <group|all> <player>"));
            return;
        }

        String groupArg = args[1].toLowerCase();
        String playerName = args[2];

        if (!groupArg.equals("all") && !VALID_GROUPS.contains(groupArg)) {
            source.sendMessage(error("Invalid group. Valid groups: hub, survival, recording, admin, all"));
            return;
        }

        CompletableFuture.supplyAsync(() -> {

            Player online = plugin.getProxy().getPlayer(playerName).orElse(null);
            if (online != null) return new ResolvedPlayer(online.getUniqueId(), online.getUsername());

            UUID fromDb = plugin.getDatabase().lookupUuid(playerName);
            if (fromDb != null) return new ResolvedPlayer(fromDb, playerName);


            return null;
        }).thenAccept(resolved -> {
            List<String> groups = groupArg.equals("all")
                    ? List.of("hub", "survival", "recording", "admin")
                    : List.of(groupArg);

            TextComponent.Builder msg = Component.text();
            for (String group : groups) {
                boolean removed;
                if (resolved != null) {
                    removed = plugin.getDatabase().removePlayer(group, resolved.uuid);
                } else {
                    removed = plugin.getDatabase().removePlayerByName(group, playerName);
                }

                if (removed) {
                    msg.append(Component.text("✔ ", NamedTextColor.GREEN))
                            .append(Component.text(playerName, NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.text(" removed from ", NamedTextColor.GRAY))
                            .append(groupTag(group))
                            .append(Component.newline());
                } else {
                    msg.append(Component.text("• ", NamedTextColor.YELLOW))
                            .append(Component.text(playerName, NamedTextColor.AQUA))
                            .append(Component.text(" was not in ", NamedTextColor.GRAY))
                            .append(groupTag(group))
                            .append(Component.newline());
                }
            }
            source.sendMessage(msg.build());
        });
    }



    private void handleClear(Invocation invocation) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        if (args.length < 2) {
            source.sendMessage(error("Usage: /whitelist clear <group>"));
            return;
        }

        String group = args[1].toLowerCase();
        if (!VALID_GROUPS.contains(group)) {
            source.sendMessage(error("Invalid group. Valid groups: " + String.join(", ", VALID_GROUPS)));
            return;
        }

        CompletableFuture.supplyAsync(() -> plugin.getDatabase().clearGroup(group)).thenAccept(count -> {
            if (count < 0) {
                source.sendMessage(error("Database error occurred while clearing " + group + " whitelist."));
                return;
            }

            source.sendMessage(
                    Component.text()
                            .append(Component.text("✔ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                            .append(Component.text("Cleared ", NamedTextColor.GRAY))
                            .append(Component.text(count, NamedTextColor.YELLOW, TextDecoration.BOLD))
                            .append(Component.text(" player" + (count == 1 ? "" : "s") + " from ", NamedTextColor.GRAY))
                            .append(groupTag(group))
                            .append(Component.text(" whitelist.", NamedTextColor.GRAY))
                            .build()
            );
        });
    }



    private void handleList(Invocation invocation) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        if (args.length < 2) {
            source.sendMessage(error("Usage: /whitelist list <group>"));
            return;
        }

        String group = args[1].toLowerCase();
        if (!VALID_GROUPS.contains(group)) {
            source.sendMessage(error("Invalid group. Valid groups: hub, survival, recording, admin"));
            return;
        }

        CompletableFuture.supplyAsync(() -> plugin.getDatabase().listGroup(group)).thenAccept(entries -> {
            if (entries.isEmpty()) {
                source.sendMessage(
                        Component.text()
                                .append(groupTag(group))
                                .append(Component.text(" whitelist is empty.", NamedTextColor.GRAY))
                                .build()
                );
                return;
            }

            TextComponent.Builder msg = Component.text()
                    .append(Component.newline())
                    .append(Component.text("  ━━━ ", NamedTextColor.DARK_GRAY))
                    .append(groupTag(group))
                    .append(Component.text(" Whitelist ", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text("(" + entries.size() + ")", NamedTextColor.GRAY))
                    .append(Component.text(" ━━━", NamedTextColor.DARK_GRAY))
                    .append(Component.newline());

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy");
            for (var entry : entries) {
                boolean online = plugin.getProxy().getPlayer(entry.uuid()).isPresent();
                msg.append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(online ? "● " : "○ ", online ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
                        .append(Component.text(entry.username(), NamedTextColor.WHITE)
                                .hoverEvent(HoverEvent.showText(
                                        Component.text()
                                                .append(Component.text("UUID: ", NamedTextColor.GRAY))
                                                .append(Component.text(entry.uuid().toString(), NamedTextColor.AQUA))
                                                .append(Component.newline())
                                                .append(Component.text("Added by: ", NamedTextColor.GRAY))
                                                .append(Component.text(entry.addedBy(), NamedTextColor.YELLOW))
                                                .append(Component.newline())
                                                .append(Component.text("Date: ", NamedTextColor.GRAY))
                                                .append(Component.text(sdf.format(entry.addedAt()), NamedTextColor.WHITE))
                                                .append(Component.newline())
                                                .append(Component.newline())
                                                .append(Component.text("Click to remove", NamedTextColor.RED, TextDecoration.ITALIC))
                                                .build()
                                ))
                                .clickEvent(ClickEvent.suggestCommand("/whitelist remove " + group + " " + entry.username()))
                        )
                        .append(Component.newline());
            }

            source.sendMessage(msg.build());
        });
    }



    private void handleCheck(Invocation invocation) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        if (args.length < 2) {
            source.sendMessage(error("Usage: /whitelist check <player>"));
            return;
        }

        String playerName = args[1];

        CompletableFuture.supplyAsync(() -> {

            Player online = plugin.getProxy().getPlayer(playerName).orElse(null);
            UUID uuid = online != null ? online.getUniqueId() : plugin.getDatabase().lookupUuid(playerName);
            if (uuid == null) return null;
            return new Object[]{uuid, online != null ? online.getUsername() : playerName,
                    plugin.getDatabase().getPlayerGroups(uuid)};
        }).thenAccept(result -> {
            if (result == null) {
                source.sendMessage(error("Player '" + playerName + "' not found in any whitelist."));
                return;
            }

            UUID uuid = (UUID) result[0];
            String name = (String) result[1];
            @SuppressWarnings("unchecked")
            List<String> groups = (List<String>) result[2];

            TextComponent.Builder msg = Component.text()
                    .append(Component.newline())
                    .append(Component.text("  Player: ", NamedTextColor.GRAY))
                    .append(Component.text(name, NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("  UUID: ", NamedTextColor.GRAY))
                    .append(Component.text(uuid.toString(), NamedTextColor.DARK_AQUA))
                    .append(Component.newline())
                    .append(Component.text("  Groups: ", NamedTextColor.GRAY));

            if (groups.isEmpty()) {
                msg.append(Component.text("none", NamedTextColor.RED, TextDecoration.ITALIC));
            } else {
                for (int i = 0; i < groups.size(); i++) {
                    if (i > 0) msg.append(Component.text(", ", NamedTextColor.DARK_GRAY));
                    msg.append(groupTag(groups.get(i)));
                }
            }
            msg.append(Component.newline());

            source.sendMessage(msg.build());
        });
    }



    private void handleLock(Invocation invocation) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        if (args.length < 2) {
            source.sendMessage(error("Usage: /whitelist lock <server>"));
            return;
        }

        String serverName = args[1].toLowerCase();
        String lockedBy = source instanceof Player p ? p.getUsername() : "Console";


        if (plugin.getProxy().getServer(serverName).isEmpty()) {
            source.sendMessage(error("Server '" + serverName + "' does not exist."));
            return;
        }

        boolean locked = plugin.getDatabase().lockServer(serverName, lockedBy);
        if (locked) {
            String displayName = serverName.substring(0, 1).toUpperCase() + serverName.substring(1);


            plugin.getProxy().getServer(serverName).ifPresent(server -> {
                for (Player p : server.getPlayersConnected()) {
                    if (!plugin.getDatabase().isWhitelisted("admin", p.getUniqueId())) {

                        plugin.getProxy().getServer("lobby").ifPresent(lobby ->
                                p.createConnectionRequest(lobby).fireAndForget()
                        );
                        p.sendMessage(
                                Component.text()
                                        .append(Component.text("\u2716 ", NamedTextColor.RED))
                                        .append(Component.text(displayName, NamedTextColor.RED, TextDecoration.BOLD))
                                        .append(Component.text(" has been ", NamedTextColor.GRAY))
                                        .append(Component.text("admin-locked", NamedTextColor.RED, TextDecoration.BOLD))
                                        .append(Component.text(". You have been moved to the lobby.", NamedTextColor.GRAY))
                                        .build()
                        );
                    }
                }
            });

            source.sendMessage(
                    Component.text()
                            .append(Component.text("\uD83D\uDD12 ", NamedTextColor.RED))
                            .append(Component.text(displayName, NamedTextColor.RED, TextDecoration.BOLD))
                            .append(Component.text(" is now ", NamedTextColor.GRAY))
                            .append(Component.text("admin-locked", NamedTextColor.RED, TextDecoration.BOLD))
                            .append(Component.text(". Only ", NamedTextColor.GRAY))
                            .append(groupTag("admin"))
                            .append(Component.text(" players can join.", NamedTextColor.GRAY))
                            .build()
            );
        } else {
            source.sendMessage(error(serverName + " is already admin-locked."));
        }
    }



    private void handleUnlock(Invocation invocation) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        if (args.length < 2) {
            source.sendMessage(error("Usage: /whitelist unlock <server>"));
            return;
        }

        String serverName = args[1].toLowerCase();
        boolean unlocked = plugin.getDatabase().unlockServer(serverName);
        if (unlocked) {
            String displayName = serverName.substring(0, 1).toUpperCase() + serverName.substring(1);
            source.sendMessage(
                    Component.text()
                            .append(Component.text("\uD83D\uDD13 ", NamedTextColor.GREEN))
                            .append(Component.text(displayName, NamedTextColor.GREEN, TextDecoration.BOLD))
                            .append(Component.text(" has been ", NamedTextColor.GRAY))
                            .append(Component.text("unlocked", NamedTextColor.GREEN, TextDecoration.BOLD))
                            .append(Component.text(". Normal whitelist rules apply.", NamedTextColor.GRAY))
                            .build()
            );
        } else {
            source.sendMessage(error(serverName + " is not currently locked."));
        }
    }



    private void handleOn(Invocation invocation) {
        toggleWhitelist(invocation, true);
    }

    private void handleOff(Invocation invocation) {
        toggleWhitelist(invocation, false);
    }

    private void toggleWhitelist(Invocation invocation, boolean enable) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        String target;
        if (args.length >= 2) {
            target = args[1].toLowerCase();
        } else if (source instanceof Player player && player.getCurrentServer().isPresent()) {
            target = player.getCurrentServer().get().getServerInfo().getName().toLowerCase();
        } else {
            target = "all";
        }

        String changedBy = source instanceof Player p ? p.getUsername() : "Console";

        if (target.equals("all")) {
            for (String t : plugin.getConfig().allToggleTargets) {
                if (enable) {
                    plugin.getDatabase().enableWhitelist(t);
                } else {
                    plugin.getDatabase().disableWhitelist(t, changedBy);
                }
            }
            source.sendMessage(Component.text()
                    .append(Component.text(enable ? "✔ " : "✖ ", enable ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                    .append(Component.text("Whitelist has been ", NamedTextColor.GRAY))
                    .append(Component.text(enable ? "enabled" : "disabled", enable ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(" across ", NamedTextColor.GRAY))
                    .append(Component.text("all servers", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.GRAY))
                    .build());
            return;
        }

        String displayName = target.substring(0, 1).toUpperCase() + target.substring(1);
        if (enable) {
            plugin.getDatabase().enableWhitelist(target);
            source.sendMessage(Component.text()
                    .append(Component.text("✔ ", NamedTextColor.GREEN))
                    .append(Component.text("Whitelist has been ", NamedTextColor.GRAY))
                    .append(Component.text("enabled", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(" for ", NamedTextColor.GRAY))
                    .append(Component.text(displayName, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.GRAY))
                    .build());
        } else {
            plugin.getDatabase().disableWhitelist(target, changedBy);
            source.sendMessage(Component.text()
                    .append(Component.text("✖ ", NamedTextColor.YELLOW))
                    .append(Component.text("Whitelist has been ", NamedTextColor.GRAY))
                    .append(Component.text("disabled", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(" for ", NamedTextColor.GRAY))
                    .append(Component.text(displayName, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.GRAY))
                    .build());
        }
    }



    private void handleCode(Invocation invocation) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        if (args.length < 2) {
            source.sendMessage(Component.text()
                    .append(Component.newline())
                    .append(Component.text("  ━━━ Whitelist Access Codes ━━━", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(cmdLine("/wl code create <group> [uses] [code]", "Create an access code"))
                    .append(cmdLine("/wl code list", "List active access codes"))
                    .append(cmdLine("/wl code delete <code>", "Delete an access code"))
                    .append(Component.newline())
                    .build());
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "create", "new", "add" -> handleCodeCreate(invocation);
            case "list", "ls" -> handleCodeList(invocation);
            case "delete", "del", "remove", "rem" -> handleCodeDelete(invocation);
            default -> source.sendMessage(error("Unknown code action. Use: create, list, delete"));
        }
    }

    private void handleCodeCreate(Invocation invocation) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        if (args.length < 3) {
            source.sendMessage(error("Usage: /wl code create <group> [uses] [code]"));
            return;
        }

        String group = args[2].toLowerCase();
        if (!VALID_GROUPS.contains(group)) {
            source.sendMessage(error("Invalid group. Valid groups: survival, recording, hub, admin"));
            return;
        }

        int uses = 1;
        if (args.length >= 4) {
            try {
                uses = Integer.parseInt(args[3]);
                if (uses < 1) uses = 1;
            } catch (NumberFormatException e) {
                source.sendMessage(error("Uses must be a positive number."));
                return;
            }
        }

        String code;
        if (args.length >= 5) {
            code = args[4].toUpperCase();
        } else {
            String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
            StringBuilder sb = new StringBuilder();
            java.util.Random rnd = new java.util.Random();
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
            code = sb.toString();
        }

        String createdBy = source instanceof Player p ? p.getUsername() : "Console";
        boolean created = plugin.getDatabase().createCode(code, group, uses, createdBy);

        if (created) {
            source.sendMessage(Component.text()
                    .append(Component.newline())
                    .append(Component.text("  ✔ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text("Access Code Created!", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("  • Code: ", NamedTextColor.GRAY))
                    .append(Component.text(code, NamedTextColor.YELLOW, TextDecoration.BOLD)
                            .clickEvent(ClickEvent.copyToClipboard(code))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to copy code", NamedTextColor.YELLOW))))
                    .append(Component.newline())
                    .append(Component.text("  • Group: ", NamedTextColor.GRAY))
                    .append(groupTag(group))
                    .append(Component.newline())
                    .append(Component.text("  • Max Uses: ", NamedTextColor.GRAY))
                    .append(Component.text(uses, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("  Players redeem with: ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("/redeem " + code, NamedTextColor.AQUA))
                    .append(Component.newline())
                    .build());
        } else {
            source.sendMessage(error("Failed to create code. It may already exist."));
        }
    }

    private void handleCodeList(Invocation invocation) {
        var source = invocation.source();
        CompletableFuture.supplyAsync(() -> plugin.getDatabase().listCodes()).thenAccept(codes -> {
            if (codes.isEmpty()) {
                source.sendMessage(Component.text("No active access codes found. Create one with /wl code create <group> [uses] [code]", NamedTextColor.GRAY));
                return;
            }

            TextComponent.Builder msg = Component.text()
                    .append(Component.newline())
                    .append(Component.text("  ━━━ Active Access Codes (" + codes.size() + ") ━━━", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.newline());

            for (var c : codes) {
                int remaining = Math.max(0, c.maxUses() - c.usesCount());
                boolean exhausted = remaining == 0;

                msg.append(Component.text("  • ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(c.code(), exhausted ? NamedTextColor.DARK_GRAY : NamedTextColor.YELLOW, TextDecoration.BOLD)
                                .clickEvent(ClickEvent.copyToClipboard(c.code()))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to copy code", NamedTextColor.YELLOW))))
                        .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                        .append(groupTag(c.group()))
                        .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(c.usesCount() + "/" + c.maxUses() + " uses", exhausted ? NamedTextColor.RED : NamedTextColor.GRAY))
                        .append(Component.text(" [delete]", NamedTextColor.RED)
                                .clickEvent(ClickEvent.suggestCommand("/wl code delete " + c.code()))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to delete code", NamedTextColor.RED))))
                        .append(Component.newline());
            }

            source.sendMessage(msg.build());
        });
    }

    private void handleCodeDelete(Invocation invocation) {
        String[] args = invocation.arguments();
        var source = invocation.source();

        if (args.length < 3) {
            source.sendMessage(error("Usage: /wl code delete <code>"));
            return;
        }

        String code = args[2].toUpperCase();
        boolean deleted = plugin.getDatabase().deleteCode(code);
        if (deleted) {
            source.sendMessage(Component.text()
                    .append(Component.text("✔ ", NamedTextColor.GREEN))
                    .append(Component.text("Access code '", NamedTextColor.GRAY))
                    .append(Component.text(code, NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text("' deleted.", NamedTextColor.GRAY))
                    .build());
        } else {
            source.sendMessage(error("Code '" + code + "' not found."));
        }
    }



    private void handleReload(Invocation invocation) {
        var source = invocation.source();
        plugin.getDatabase().reloadCache();
        source.sendMessage(Component.text()
                .append(Component.text("✔ ", NamedTextColor.GREEN))
                .append(Component.text("Whitelist cache and settings reloaded.", NamedTextColor.GRAY))
                .build());
    }



    private void handleStatus(Invocation invocation) {
        var source = invocation.source();
        var lockedServers = plugin.getDatabase().getLockedServers();

        TextComponent.Builder msg = Component.text()
                .append(Component.newline())
                .append(Component.text("  \u2501\u2501\u2501 ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Server Whitelist Status", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" \u2501\u2501\u2501", NamedTextColor.DARK_GRAY))
                .append(Component.newline());

        for (var server : plugin.getProxy().getAllServers()) {
            String name = server.getServerInfo().getName();
            String lower = name.toLowerCase();
            boolean locked = lockedServers.contains(lower);
            boolean wlOn = plugin.getDatabase().isWhitelistEnabled(lower);
            int players = server.getPlayersConnected().size();
            String displayName = name.substring(0, 1).toUpperCase() + name.substring(1);

            msg.append(Component.text("  ", NamedTextColor.DARK_GRAY));
            msg.append(Component.text(locked ? "\uD83D\uDD12 " : (wlOn ? "\u25CF " : "\u25CB "),
                    locked ? NamedTextColor.RED : (wlOn ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
            msg.append(Component.text(displayName, NamedTextColor.WHITE, TextDecoration.BOLD));
            msg.append(Component.text(" (" + players + " online)", NamedTextColor.DARK_GRAY));
            msg.append(Component.text(" \u2014 Whitelist: ", NamedTextColor.GRAY));
            msg.append(Component.text(wlOn ? "ON" : "OFF", wlOn ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD));


            msg.append(Component.text(" "));
            if (wlOn) {
                msg.append(Component.text("[turn off]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.suggestCommand("/wl off " + lower))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to turn off whitelist for " + displayName, NamedTextColor.RED))));
            } else {
                msg.append(Component.text("[turn on]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.suggestCommand("/wl on " + lower))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to turn on whitelist for " + displayName, NamedTextColor.GREEN))));
            }


            msg.append(Component.text("  "));
            if (locked) {
                msg.append(Component.text("[unlock]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.suggestCommand("/wl unlock " + lower))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to unlock " + displayName, NamedTextColor.GREEN))));
            } else {
                msg.append(Component.text("[lock]", NamedTextColor.DARK_RED)
                        .clickEvent(ClickEvent.suggestCommand("/wl lock " + lower))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to admin-lock " + displayName, NamedTextColor.DARK_RED))));
            }

            msg.append(Component.newline());
        }

        source.sendMessage(msg.build());
    }



    private void sendHelp(com.velocitypowered.api.command.CommandSource source) {
        source.sendMessage(Component.text()
                .append(Component.newline())
                .append(Component.text("  ━━━ ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Network Whitelist", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" ━━━", NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.newline())
                .append(cmdLine("/wl on [server|all]", "Turn whitelist ON"))
                .append(cmdLine("/wl off [server|all]", "Turn whitelist OFF"))
                .append(cmdLine("/wl add <group|all> <player>", "Add a player (Java or Bedrock)"))
                .append(cmdLine("/wl remove <group|all> <player>", "Remove a player"))
                .append(cmdLine("/wl clear <group>", "Clear all players from a group"))
                .append(cmdLine("/wl list <group>", "List whitelisted players"))
                .append(cmdLine("/wl check <player>", "Check a player's groups"))
                .append(cmdLine("/wl code create <grp> [n] [cd]", "Create an access code"))
                .append(cmdLine("/wl code list", "List access codes"))
                .append(cmdLine("/wl code delete <code>", "Delete an access code"))
                .append(cmdLine("/redeem <code>", "Redeem an access code"))
                .append(cmdLine("/wl lock <server>", "Admin-lock a server"))
                .append(cmdLine("/wl unlock <server>", "Unlock a server"))
                .append(cmdLine("/wl status", "Show server whitelist & lock status"))
                .append(cmdLine("/wl reload", "Reload cache & configuration"))
                .append(Component.newline())
                .append(Component.text("  Groups: ", NamedTextColor.GRAY))
                .append(groupTag("hub"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(groupTag("survival"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(groupTag("recording"))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(groupTag("admin"))
                .append(Component.newline())
                .build()
        );
    }



    private static Component cmdLine(String cmd, String desc) {
        return Component.text()
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(cmd, NamedTextColor.GOLD)
                        .clickEvent(ClickEvent.suggestCommand(cmd.split(" <")[0] + " "))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to use", NamedTextColor.YELLOW))))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text(desc, NamedTextColor.GRAY))
                .append(Component.newline())
                .build();
    }

    private static Component groupTag(String group) {
        return switch (group) {
            case "hub" -> Component.text("[Hub]", NamedTextColor.GREEN, TextDecoration.BOLD);
            case "survival" -> Component.text("[Survival]", NamedTextColor.GOLD, TextDecoration.BOLD);
            case "recording" -> Component.text("[Recording]", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);
            case "admin" -> Component.text("[Admin]", NamedTextColor.RED, TextDecoration.BOLD);
            default -> Component.text("[" + group + "]", NamedTextColor.WHITE);
        };
    }

    private static Component error(String text) {
        return Component.text()
                .append(Component.text("✖ ", NamedTextColor.RED))
                .append(Component.text(text, NamedTextColor.GRAY))
                .build();
    }


    private ResolvedPlayer resolvePlayer(String name) {

        Player online = plugin.getProxy().getPlayer(name).orElse(null);
        if (online != null) {
            return new ResolvedPlayer(online.getUniqueId(), online.getUsername());
        }


        UUID fromDb = plugin.getDatabase().lookupUuid(name);
        if (fromDb != null) {
            return new ResolvedPlayer(fromDb, name);
        }


        if (name.startsWith(".")) {
            String gamertag = name.substring(1);
            UUID bedrockUuid = fetchBedrockUuid(gamertag);
            if (bedrockUuid != null) {
                return new ResolvedPlayer(bedrockUuid, name);
            }

            long hash = 0;
            for (char c : gamertag.toLowerCase().toCharArray()) {
                hash = hash * 31 + c;
            }
            String hashHex = String.format("%012x", Math.abs(hash) % 0xFFFFFFFFFFFFL);
            String uuidStr = "00000000-0000-0000-0009-" + hashHex;
            plugin.getLogger().info("Generated offline Bedrock UUID for {}: {}", name, uuidStr);
            return new ResolvedPlayer(UUID.fromString(uuidStr), name);
        }


        UUID javaUuid = fetchMojangUuid(name);
        if (javaUuid != null) {
            return new ResolvedPlayer(javaUuid, name);
        }


        UUID fallbackUuid = fetchFallbackMojangUuid(name);
        if (fallbackUuid != null) {
            return new ResolvedPlayer(fallbackUuid, name);
        }


        UUID bedrockUuid = fetchBedrockUuid(name);
        if (bedrockUuid != null) {
            return new ResolvedPlayer(bedrockUuid, "." + name);
        }




        UUID offlineJavaUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        plugin.getLogger().info("Generated offline pre-join UUID for {}: {}", name, offlineJavaUuid);
        return new ResolvedPlayer(offlineJavaUuid, name);
    }

    private UUID fetchMojangUuid(String name) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(MOJANG_API + name).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "rWhitelist/1.0 (Minecraft Server Proxy)");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) return null;

            String body;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                body = reader.lines().collect(Collectors.joining());
            }

            String idField = extractJsonField(body, "id");
            if (idField == null) return null;

            String uuidStr = idField.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                    "$1-$2-$3-$4-$5"
            );
            return UUID.fromString(uuidStr);
        } catch (Exception e) {
            plugin.getLogger().warn("Mojang API lookup failed for '{}': {}", name, e.getMessage());
            return null;
        }
    }

    private UUID fetchFallbackMojangUuid(String name) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create("https://playerdb.co/api/player/minecraft/" + name).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "rWhitelist/1.0 (Minecraft Server Proxy)");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) return null;

            String body;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                body = reader.lines().collect(Collectors.joining());
            }

            String idField = extractJsonField(body, "id");
            if (idField == null) return null;
            if (idField.contains("-")) {
                return UUID.fromString(idField);
            }
            String uuidStr = idField.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                    "$1-$2-$3-$4-$5"
            );
            return UUID.fromString(uuidStr);
        } catch (Exception ignored) {
            return null;
        }
    }

    private UUID fetchBedrockUuid(String gamertag) {
        try {
            String encoded = URLEncoder.encode(gamertag, StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) URI.create("https://api.geysermc.org/v2/xbox/xuid/" + encoded).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Geyser-Floodgate");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) return null;

            String body;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                body = reader.lines().collect(Collectors.joining());
            }

            int xuidIdx = body.indexOf("\"xuid\":");
            if (xuidIdx == -1) return null;
            int start = xuidIdx + 7;
            while (start < body.length() && (body.charAt(start) == ' ' || body.charAt(start) == ':')) start++;
            int end = start;
            while (end < body.length() && Character.isDigit(body.charAt(end))) end++;
            long xuid = Long.parseLong(body.substring(start, end));
            String hex = String.format("%016x", xuid);
            String formattedUuid = "00000000-0000-0000-" + hex.substring(0, 4) + "-" + hex.substring(4);
            return UUID.fromString(formattedUuid);
        } catch (Exception e) {
            plugin.getLogger().warn("Geyser API lookup failed for Bedrock player '{}': {}", gamertag, e.getMessage());
            return null;
        }
    }

    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private record ResolvedPlayer(UUID uuid, String name) {}
}
