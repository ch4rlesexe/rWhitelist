# rWhitelist

Velocity multi-group whitelist with redeem codes, server locks, and skin-command helpers. This one's solid.

## Groups

Default logical groups: `hub`, `survival`, `recording`, `admin`.

Map your Velocity registered server names under `servers:` in `config.yml`.

```yaml
mysql:
  host: 127.0.0.1
  port: 3306
  database: CHANGE_ME
  username: CHANGE_ME
  password: CHANGE_ME
  table-prefix: rwl_
settings:
  enforce-hub: true
servers:
  hub:
    - lobby
    - hub
  survival:
    - survival
  recording:
    - recording
toggle-targets:
  - hub
  - survival
  - recording
```

## Commands

- `/whitelist` (`/wl`) — manage lists, locks, codes
- `/redeem <code>` — redeem an invite code

## Permissions

| Node | Description |
|------|-------------|
| `rwhitelist.manage` | Use `/whitelist` (`/wl`) admin commands |
| `rwhitelist.admin` | Treated as staff for skin-command helpers (with other staff nodes below) |

`/redeem` has no permission node (everyone).

Skin-command bypass also accepts `skinsrestorer.admin`, `blockrandomizer.admin`, `group.owner`, or `group.staff` (external / LuckPerms-style nodes).
