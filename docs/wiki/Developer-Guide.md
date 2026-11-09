# Developer Guide

How to build an Odyssey mod on the Library. Full API reference: `docs/SPEC.md` in the repository.

## Dependency
```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies {
    implementation fg.deobf("com.github.twistylime:OdysseyLibrary:v1.0.0")
}
```
In `mods.toml` add a mandatory dependency on `odysseylib` (side `SERVER`).

## A module
```java
@Mod("odysseyexample")
public final class ExampleMod {
    public static final OdysseyPerm USE = Perms.declare("example", "use", PermDefault.ALL);

    public ExampleMod() {
        Modules.register(new ExampleModule());
    }
}

final class ExampleModule implements OdysseyModule {
    private ConfigFile<ExampleConfig> config;

    public ModuleInfo info() {
        return new ModuleInfo("example", "Example", ModuleCategory.IMPROVEMENT, "1.0.0",
                "Shows how a module works", () -> new ItemStack(Items.ENCHANTED_BOOK));
    }

    public void commands(ModuleCommands c) {
        c.add(Commands.literal("example").requires(ExampleMod.USE.requirement()).executes(ctx -> 1));
    }

    public void onEnable(ModuleContext ctx) {
        config = ctx.config(ExampleConfig.class);  // config/odyssey/example.json, live
        ctx.listen(new ExampleListener());         // removed automatically on disable
        ctx.every(20, () -> {});                   // timed, failure-isolated task
    }
}
```

## Rules
- Never block the server thread: use `Scheduler.async` for IO and `Scheduler.sync` to come back.
- Rewards: `Rewards.grant(uuid, spec, "example", "Title")`; they always go to the Mailbox.
- Menus: extend `Screen` or `PagedScreen`; use `suspend()` before opening a sub-screen that comes back.
- Events with an area: `EventRegions.start(...)` / `end(...)` so other mods (e.g. Sweeper) can respect them.
- Old data from a replaced mod: implement `LegacyImporter` and `Migrations.register(...)`.
