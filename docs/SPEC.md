# Odyssey Library — Implementation Specification

Status: **Implemented in 1.0.0-alpha.4** (see section 25 for notes) · Source of truth for code. `DESIGN.md` = what/why, this file = how.
Any deviation during coding is recorded here in the same PR.

## 0. Build
| Key | Value |
|---|---|
| Minecraft / Forge | 1.20.1 / **47.4.28** (`forge_version_range=[47,)`) |
| Gradle / ForgeGradle | 8.8 / `[6.0.16,6.2)` |
| Mappings | `official` 1.20.1 (same as owner's existing mods) |
| Java | 17 |
| mod_id / group / package | `odysseylib` / `com.lime` / `com.lime.odyssey.lib` |
| Side | **Server-only.** `displayTest = "IGNORE_ALL_VERSION"` so clients without it connect |
| Hard deps | Forge, Minecraft. **No third-party libraries** (JSON via Gson bundled with MC; hashing via JDK) |
| Soft deps | Curios, Origins (OdysseyOrigins), Sophisticated Backpacks — reflection-free optional compat classes loaded only if `ModList.isLoaded` |
| Published | JitPack `com.github.twistylime:OdysseyLibrary:<tag>`; modules use `compileOnly`+`runtimeOnly` via `fg.deobf` |

## 1. Package layout
```
com.lime.odyssey.lib
├─ OdysseyLib.java                 @Mod entry; wires services in order (§2)
├─ api/                            STABLE public API — the only package modules import
│  ├─ OdysseyModule.java  ModuleInfo.java  ModuleCategory.java
│  ├─ OdysseyApi.java              static accessor: OdysseyApi.get().mailbox() …
│  ├─ command/  config/  data/  ui/  reward/  mailbox/  points/  event/  perm/  msg/  notify/  audit/  snapshot/  migrate/  sched/
├─ impl/                           internal implementations (not API; may change in MINOR versions)
├─ compat/  curios/ origins/ backpacks/
└─ util/                           ItemCodec, Json, Durations, Loc, Region …
```
API stability: anything in `api` follows SemVer; breaking change = MAJOR bump of the Library.

## 2. Lifecycle & threading
Startup order (on `ServerAboutToStartEvent` → `ServerStartedEvent`):
`Async executor → Data store → Config → Messages → Permissions → Audit → Event bus → Scheduler → Points → Mailbox → Rewards → Snapshot → UI → Commands → Modules.onEnable → Legacy migrations → Hub`.
Shutdown reverse order; `ServerStoppingEvent` flushes all dirty data **synchronously with timeout** (10 s/file, logged).

Threading rules (enforced in review):
- **Server thread**: world, entities, players, menus, Brigadier execution, event dispatch.
- **IO executor** (`odyssey-io`, 2 threads, daemon=false): file reads/writes, hashing, HTTP (Web Controls).
- Off-thread → server: `OdysseyApi.sched().sync(Runnable)` (queued, drained at start of server tick).
- Data handed to IO is an **immutable snapshot** (serialized JSON string / byte[] produced on the server thread or from immutable records).

## 3. Module API
```java
public interface OdysseyModule {
    ModuleInfo info();                          // id, displayName, category, version, icon (ItemStack supplier), description
    default void onEnable(ModuleContext ctx) {} // register commands/config/listeners via ctx (auto-unregistered on disable)
    default void onDisable() {}
    default void onConfigChanged(ConfigChangedEvent e) {}
    default List<HubEntry> hubEntries(ServerPlayer viewer) { return List.of(); }
}
```
- Registration: in the module's `@Mod` constructor → `OdysseyApi.registerModule(new XModule())` (queued until server start).
- `ModuleContext` gives scoped handles: `commands()`, `config(Class<T>)`, `data(String)`, `listen(...)`, `every(...)`, `perm(String)`, `log()`.
  Everything created through the context is tracked and torn down on disable → **live enable/disable** (`/odyssey module <id> enable|disable`).
- Module enabled-state persisted in `config/odyssey/modules.json`.
- Categories: `LIBRARY, MODIFICATION, PATCH, IMPROVEMENT`.

## 4. Commands
- Library owns the Brigadier root `odyssey`. `/odyssey` alone opens the Hub.
- `ctx.commands().register(LiteralArgumentBuilder<CommandSourceStack>)` attaches under `/odyssey <moduleId>` (module chooses its literal, e.g. `bosses`; collisions → startup error naming both modules).
- Root subcommands owned by Library: `modules`, `module <id> enable|disable`, `config [module]`, `mailbox [send|purge]`, `points [player] [add|remove|set]`, `afk`, `perf`, `audit`, `reload-messages` (messages only; never needed for configs).
- **Aliases** (`config/odyssey/aliases.json`): `{ "afk":"odyssey afk", "mailbox":"odyssey mailbox", "points":"odyssey points", "mev":"odyssey minievents", "bin":"odyssey bin" }` — each registered as `literal(alias).redirect(target)`; toggling takes effect live by resending the command tree (`server.getCommands().sendCommands(player)` for all online).
- Every node carries metadata (`CommandMeta`: description, permission, example) exported for Web Controls auto-complete.
- Dynamic command tree changes always followed by `sendCommands` to online players.

## 5. Permissions
```java
OdysseyPerm p = ctx.perm("start", PermDefault.ADMIN);   // node odyssey.<module>.start
p.check(ServerPlayer|CommandSourceStack) -> boolean
```
- Backed by Forge `PermissionAPI` (`PermissionNode<Boolean>` registered in `PermissionGatherEvent.Nodes`); default resolver = vanilla OP level.
- `PermDefault`: `ALL` (0), `ADMIN` (configurable `adminOpLevel`, default **2**), `OWNER` (4).
- Checked on command `requires(...)` **and** on every UI click action (UI framework does it automatically for buttons with a perm).
- Library nodes registered dynamically per module before the gather event (modules register during mod construction).

## 6. Configuration (live)
- One JSON file per module: `config/odyssey/<module>.json`. Schema declared as a Java class with annotated fields:
```java
public final class SweeperConfig {
  @Entry(comment="Minutes between sweeps") @Range(min=1, max=1440) public int intervalMinutes = 10;
  @Entry public List<Integer> countdown = List.of(60,30,15,10,5,4,3,2,1);
  @Entry @Range(min=1, max=54) public int binPages = 6;
}
```
- Supported types: boolean, int/long/double (+`@Range`), String (+`@Pattern`,`@MaxLen`), enum, `Duration`, `ResourceLocation`, `ItemStack`, `List<…>`, `Map<String,…>`, nested records.
- Change paths: UI editor (auto-generated from schema), `/odyssey config <module> <key> <value>`, Web Controls, **or editing the file on disk** (watched with `WatchService` on the IO thread, debounced 1 s).
- Pipeline: parse → validate → **swap immutable instance atomically** (`AtomicReference`) on server thread → fire `ConfigChangedEvent(module, changedKeys)` → write file (if not from disk) → audit.
- Invalid change: rejected, previous value kept, error shown to the editor (or logged + admins notified for disk edits).
- Unknown keys preserved on rewrite; missing keys filled with defaults; `"$schema": <int>` version with migrators.

## 7. Data store
```java
DataFile<T> f = ctx.data("bin", BinData.CODEC, BinData::empty); // <world>/odyssey/<module>/bin.json
T v = f.get(); f.update(old -> newValue); // marks dirty
```
- Format JSON (Gson), `{ "schema": n, "data": … }`. ItemStacks as SNBT strings via `ItemCodec`.
- Save: dirty files snapshotted on server thread every 30 s (configurable) and on stop → IO thread writes **tmp → fsync → rotate `.bak` (keep 3) → atomic move**.
- Load: main → `.bak.1..3` fallback; unreadable file moved to `quarantine/<name>-<timestamp>.json`, admins notified; never overwritten while quarantined.
- Per-player data: `ctx.playerData("mail")` → `<world>/odyssey/<module>/players/<uuid>.json`, loaded on login async (join waits ≤ 0 ticks: default empty view until loaded, actions queued), unloaded on logout after save.
- Logs (e.g. sweeper takes): `ctx.log("takes")` → `logs/odyssey/<module>/<name>-YYYY-MM-DD.log`, async append, 30-day retention.

## 8. UI framework (chest UIs)
Baseline: owner's MiniEvents/ChatGames `Gui`/`GuiMenu`/`GuiManager`/`TextInputMenu`, generalised.
```java
public abstract class OdysseyScreen {            // renderer-agnostic model
  protected abstract void build(ScreenBuilder b, ServerPlayer viewer);
}
b.title(Component).rows(1..6)
 .button(slot, icon, perm?, click -> …)        // Click: LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE, DROP
 .toggle(slot, label, Supplier<Boolean>, Consumer<Boolean>)
 .input(slot, label, AnvilPrompt)             // anvil text input
 .itemSlot(slot, filter, onChange)            // editable slot (reward editors, recipe grid)
 .paged(area, List<T>, renderer)              // automatic prev/next
 .back(previousScreen)
```
- **Chest renderer**: `GenericMenu`-based `OdysseyMenu extends AbstractContainerMenu` (rows 1–6, vanilla `MenuType.GENERIC_9xN` so no client mod). Anvil input via `OdysseyAnvilMenu extends AnvilMenu` (rename text = value, output click = submit).
- **Anti-dupe** (all mandatory): cancel every click not on an editable slot; block `QUICK_MOVE`, `SWAP` (hotbar keys), `CLONE`, `THROW`, `PICKUP_ALL`, drag across non-editable slots; icons carry NBT tag `odyssey:icon` and are purged from player inventories on close/login; editable slots validated server-side on every change; cursor item returned on close (or Mailbox if no space).
- Lifecycle: one open screen per player tracked in `UiManager`; `refresh()` re-renders in place; screens may register `everySecond()`; closed on logout, module disable, server stop.
- **Edit locks**: `UiLock.acquire(key, player)` for objects edited by several admins (e.g. same recipe) → second admin gets read-only view with "Locked by X".
- **Web renderer** (Web Controls) consumes the same `OdysseyScreen` model via `ScreenSnapshot` (slots → icon JSON, actions → ids); clicks from the web are dispatched as if from the linked admin.
- Shared widgets: `ConfirmScreen`, `PlayerPicker`, `ItemPicker`, `NumberPicker`, `DurationPicker`, `EffectPicker`, `ListEditor<T>`, `ConfigEditor` (auto from schema), `RewardEditor`.
- Shared icons/colours: `Icons` (back, next, prev, close, toggleOn/Off, info, filler).

## 9. Hub (`/odyssey`)
- Paged 6-row screen: row 1 = header (player head, Points balance, Mailbox with unread count); rows 2–5 = one icon per enabled module with `HubEntry` (paged, sorted by category then name); row 6 = nav + admin tools (Modules, Config, Audit, Perf) shown only to admins.

## 10. Messages
- Lang files `config/odyssey/lang/<module>.json` (`en_us`), defaults shipped in the jar; keys `odyssey.<module>.<key>`.
- Format: `&` colour codes + `{placeholder}`; prefix `&8[&6Odyssey&8]` (module: `&8[&6Odyssey &8| &e<Name>&8]`).
- Click/hover helpers: `Msg.button("[Open Mailbox]", "/odyssey mailbox")`.
- `MsgService.send(player, key, args…)`, `broadcast`, `title`, `actionBar`, `sound`.

## 11. Event bus
Suite events posted on `MinecraftForge.EVENT_BUS` (so any mod can listen without depending on a sibling):
`ModuleEnabledEvent`, `ModuleDisabledEvent`, `ConfigChangedEvent`, `OdysseyEventStartedEvent(module, id, Region area)`, `OdysseyEventEndedEvent`, `RewardGrantedEvent`, `MailDeliveredEvent`, `PointsChangedEvent`, `PlayerAfkChangedEvent`.
`RegionService.activeEventRegions()` lets Sweeper/Dimensions query live event areas.

## 12. Scheduler
```java
sched.later(ticks, r); sched.every(periodTicks, r) -> TaskHandle; sched.sync(r); sched.async(Callable) -> CompletableFuture
```
- Every task timed; > budget (default 5 ms/tick per module) → warning with module + task name (rate-limited), visible in `/odyssey perf`.
- Exceptions caught per task; a task failing 3× consecutively is cancelled and admins notified (never crashes the tick).

## 13. Odyssey Points
- Ledger per player (`players/<uuid>.json` in Library data): `balance`, `lifetimeEarned`, last 200 transactions (`ts, delta, source, reason`).
- API: `points().get(uuid)`, `add(uuid, amount, source, reason)`, `remove(…) -> boolean` (fails if insufficient), `set` (admin). All mutations on server thread, audited, fire `PointsChangedEvent`.
- **Earning** (config `points.json`): `intervalMinutes` (default 10), `pointsPerInterval` (default 5), `dailyLoginBonus` (default 10), `streakBonusPerDay` (default 2), `streakCap` (default 7 days), `streakResetAfterHours` (default 48).
- Active time accrues only when **not AFK**; partial intervals carried over across sessions.
- **AFK**: AFK after `afkMinutes` (default 5) with no movement (>0.5 block), rotation (>3°), chat, command, block/entity interaction. `/odyssey afk` (`/afk`) toggles manual AFK. AFK players get tab-list suffix `[AFK]` (via scoreboard team prefix/suffix, server-side) and earn nothing.

## 14. Mailbox
- Per-player list of `MailEntry { id, createdAt, source(module), title, List<ItemStack> items, long points, Optional<RewardType custom> }`. No expiry.
- `mailbox().send(uuid, MailEntry)` works offline. On delivery: online → chat `[Odyssey] You have new mail! [Open Mailbox]` + title/action bar + sound; on login with unread → same chat line.
- UI: paged list; click = claim one (items to inventory if **all** fit, else partial claim keeps remainder in mail — never drops), "Claim all" button. Points credited on claim.
- **Claim atomicity**: entry removed from data and items inserted within one server tick; data marked dirty first; double-click guarded by per-player claim lock.
- Admin: `/odyssey mailbox send <player|all>` → UI to compose (items + points + title); `/odyssey mailbox purge <player>`; view any player's mailbox. All audited.

## 15. Rewards
- `RewardSpec` = list of `RewardPart`: `ItemsPart`, `PointsPart`, `CommandPart` (run as console with `{player}`, `{uuid}`), `XpPart`, `CustomPart(typeId, json)`.
- Pools: fixed, weighted random (pick N), tiers (participation, top-N with configurable N).
- `rewards().grant(uuid, RewardSpec, source, title)` → resolves randomness at grant time → **always a MailEntry** (commands/XP run on claim).
- Custom reward types registered by modules (`rewards().registerType("odysseyorigins:origin_item", …)`).
- `RewardEditor` widget reused by every mod.

## 16. Soulbound items
- Tag `odyssey:soulbound { owner: UUID, id: UUID }` on any ItemStack.
- Guards: pickup by non-owner cancelled; item entity of a soulbound stack → on any removal not caused by owner pickup (despawn, lava/fire, void, explosion, cactus, `/kill`, sweep) the stack is re-sent to owner's Mailbox; owner death keeps it (configurable) else Mailbox; placed into containers / dropped by non-owner → Mailbox; duplicate `id` detection removes clones.

## 17. Player state snapshot
- `snapshot().capture(player, reason) -> SnapshotId` (inventory, armour, offhand, cursor, crafting grid, ender chest optional, XP, health, food, effects, gamemode, position, Curios incl. cosmetics, Sophisticated Backpacks with exact slot, Origins layers + power data) → written to disk **before** caller modifies the player.
- `restore(player, id, RestoreMode.REPLACE)`: saves current state first; order inventory+curios → next tick backpacks/curio-granted slots; fallback same-type slot → free slot → Mailbox.
- Pending restore persisted; applied at next login before Origins selection; retention 30 days; admin UI to list/restore.
- Origins pause: remove all layers (keep power data), block flight; on restore no starting-item re-grant.

## 18. Notifications
- `notify().admins(Component, Severity, Optional<ClickAction>)` → online admins (chat + sound for WARN/ALERT), queued for offline admins (shown on login, max 50), forwarded to Web Controls feed.

## 19. Audit log
- `audit().record(actor, module, action, target, details)`; actor = player / console / web:<account>.
- Daily rotating files `logs/odyssey/audit-YYYY-MM-DD.log` (async), 90-day retention; `/odyssey audit` UI shows the last 500 entries in memory.

## 20. Legacy migration
- `migrations().register(LegacyImporter)`: `detect(server) -> boolean`, `importData(ctx) -> MigrationReport`, `legacyFiles() -> List<Path>`.
- Runs once after modules enable; on success + verification (counts match) old files moved to `<world>/odyssey/<module>/legacy-backup/<timestamp>/` and a marker written; on failure nothing is moved, admins notified, module keeps running with empty/new data.

## 21. Perf guard
- Samples MSPT per tick; per-module time from scheduler/event listeners wrapped by context; `/odyssey perf` UI (TPS, MSPT avg/max, top module costs, IO queue length).

## 22. Web bridge (consumed by Web Controls)
- `WebBridge` interface: `modules()`, `screens(module)`, `openScreen(account, screenId)`, `click(sessionId, slot, click)`, `commandTree()`, `complete(account, partial)`, `execute(account, command)`, `notifications()`, `onlinePlayers()`, `stats()`.
- Implemented in Library, inert until Web Controls registers as consumer.

## 23. Library config files (`config/odyssey/`)
`library.json` (adminOpLevel, saveIntervalSeconds, perf budgets, debug), `modules.json`, `aliases.json`, `points.json`, `mailbox.json`, `lang/odysseylib.json`.

## 24. Implementation order (PRs)
1. `chore/scaffold` — Gradle, mods.toml, CI templates copied, `@Mod`, logger
2. `feature/core-runtime` — async executor, scheduler, data store, config, messages, audit
3. `feature/modules-commands-perms` — module API, `/odyssey` root, aliases, permissions
4. `feature/ui-framework` — screens, chest/anvil renderers, widgets, anti-dupe, hub
5. `feature/points-afk`
6. `feature/mailbox-rewards-soulbound`
7. `feature/player-snapshot` (+ Curios/Backpacks/Origins compat)
8. `feature/notify-perf-migration-webbridge`
9. `docs/wiki` — full wiki pages
Each PR: version bump (`1.0.0-alpha.N` until 1.0.0), CHANGELOG entry, review per checklist.

## 25. Implementation notes (1.0.0-alpha.4)
Where the code differs from the plan above, the code is authoritative; the reasons are recorded here.

| Topic | As built | Why |
|---|---|---|
| API shape | Services are static classes in `api.*` (`Scheduler`, `Mailbox`, `Points`, `Rewards`, `Perms`...). No `OdysseyApi` accessor | Fewer layers; every service is server-scoped and started/stopped by the Library |
| Permissions | Declared with `Perms.declare(module, action, default)` **during mod construction** (static fields) | Forge gathers permission nodes once at server start |
| Module commands | `OdysseyModule.commands(ModuleCommands)` is called on every command-tree build; nodes are gated by the module's enabled state | Command registration happens before modules enable and again on `/reload` |
| Points / Mailbox storage | One JSON file each (`points.json`, `mailbox.json`), loaded at server start, saved asynchronously; mailbox saved at the end of the tick after every change | Simple and fast at this server's size; immediate mailbox save prevents duplicate claims after a crash |
| Player snapshots | Active snapshot stored inside the player's save data (`ForgeData.PlayerPersisted.odyssey_snapshots`) + history on disk (`snapshots/<uuid>/*.dat`, 30 days) | Snapshot and inventory are always saved together, so a crash can never separate them |
| Soulbound | A soulbound item can never exist as a dropped item: any item entity carrying one is cancelled and the item mailed home; inventories of non-owners are scanned every 2 s | Closes every pickup / destroy / mob / hopper path at once |
| Config editing | Simple fields (numbers, booleans, text, enums) via settings UI, command or file; lists/maps via module menus or the file | A generic editor for nested data would be worse than a purpose-built module menu |
| Command metadata | Not exported separately; Web Controls reads the Brigadier tree and uses completions | Avoids a second source of truth |
| Custom reward types | Produce items at grant time (`Rewards.registerType`) | Keeps claim logic uniform; soulbound reward items are just items |
| Stop order | modules (HIGHEST) → UI (HIGH) → points/mailbox/snapshots (LOW) → flush + scheduler (LOWEST) | Disabling modules may still send mail; closing menus may return items |
