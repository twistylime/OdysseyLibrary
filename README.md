# Odyssey Library

Core library of the **Odyssey Suite**: the Forge 1.20.1 server mods of the Odyssey Minecraft server.
Every Odyssey mod depends on it; it is **server-side only**, so players don't need to install it.

![Odyssey Library](src/main/resources/logo.png)

## What it provides
- **`/odyssey`**: one command root for every Odyssey mod, plus a paged hub UI
- **Live config**: JSON files in `config/odyssey/`, editable in game, by command or on disk; changes apply instantly (no restart, no `/reload`)
- **Crash-safe data store**: atomic writes, rolling backups, quarantine for broken files
- **Chest UI framework**: anti-dupe, anvil text input, paged lists, confirm dialogs, auto-generated settings editor
- **Odyssey Points**: playtime currency with daily login and streak bonuses; AFK time earns nothing
- **Mailbox**: every reward from every mod is delivered here, never lost to a full inventory
- **Rewards and soulbound items**, **player state snapshots** (Curios and Origins aware)
- **Admin notifications**, **audit log**, **performance guard**, **legacy data migration**, **Web Controls bridge**

Documentation: see the [wiki](https://github.com/twistylime/OdysseyLibrary/wiki).

## Requirements
- Minecraft 1.20.1, Forge 47.4.28+
- Optional: Curios, Origins (Forge)

## Building
```
./gradlew build
```
The jar is written to `build/libs/`. Java 17 is required.

## For Odyssey mod developers
Depend on the Library (JitPack in CI, sibling folder locally) and register your module:
```java
@Mod("odysseyexample")
public final class ExampleMod {
    public ExampleMod() {
        Modules.register(new ExampleModule());
    }
}
```
See `docs/SPEC.md` and the wiki page *Developer Guide*.

## Licence
MIT. Optional integration jars in `libs/` keep their own licences (see `libs/README.md`).
