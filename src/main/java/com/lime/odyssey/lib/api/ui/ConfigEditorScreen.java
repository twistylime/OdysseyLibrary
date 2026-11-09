package com.lime.odyssey.lib.api.ui;

import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.config.Comment;
import com.lime.odyssey.lib.api.config.ConfigFile;
import com.lime.odyssey.lib.api.config.Range;
import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.perm.Perms;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Editor generated from a {@link ConfigFile}: booleans toggle, enums cycle, numbers and text use anvil input.
 * Complex fields (lists, maps, items) are shown read-only; modules edit those in their own screens.
 */
public class ConfigEditorScreen extends PagedScreen<Field> {
    private final ConfigFile<?> config;

    public ConfigEditorScreen(ServerPlayer viewer, ConfigFile<?> config) {
        super(config.module(), viewer, "&8Settings: " + config.name());
        this.config = config;
    }

    @Override
    protected List<Field> entries() {
        return config.fields();
    }

    @Override
    protected ItemStack icon(Field f) {
        Object v = config.valueOf(f.getName());
        Class<?> t = f.getType();
        Comment c = f.getAnnotation(Comment.class);
        Range r = f.getAnnotation(Range.class);
        ItemBuilder b;
        if (t == boolean.class || t == Boolean.class) {
            b = ItemBuilder.of(Boolean.TRUE.equals(v) ? Items.LIME_DYE : Items.GRAY_DYE);
        } else if (t.isEnum()) {
            b = ItemBuilder.of(Items.COMPARATOR);
        } else if (Number.class.isAssignableFrom(box(t))) {
            b = ItemBuilder.of(Items.CLOCK);
        } else if (t == String.class) {
            b = ItemBuilder.of(Items.NAME_TAG);
        } else {
            b = ItemBuilder.of(Items.CHEST);
        }
        b.name("&e" + f.getName()).lore("&7Value: &f" + preview(v));
        if (c != null) b.lore("&7" + c.value());
        if (r != null) b.lore("&8Range: " + fmt(r.min()) + " - " + fmt(r.max()));
        if (ConfigFile.isSimple(t)) b.lore("", t == boolean.class || t == Boolean.class ? "&eClick to toggle"
                : t.isEnum() ? "&eClick for next value" : "&eClick to edit");
        else b.lore("", "&8Edited in the module's own menu");
        return b.build();
    }

    @Override
    protected void onClick(Field f, Click click) {
        if (!Perms.ADMIN.check(viewer)) return;
        Class<?> t = f.getType();
        if (!ConfigFile.isSimple(t)) return;
        Object v = config.valueOf(f.getName());
        String actor = Audit.actor(viewer);
        if (t == boolean.class || t == Boolean.class) {
            report(config.setFromString(f.getName(), String.valueOf(!Boolean.TRUE.equals(v)), actor));
            refresh();
        } else if (t.isEnum()) {
            Object[] values = t.getEnumConstants();
            int i = 0;
            for (int k = 0; k < values.length; k++) if (values[k] == v) i = k;
            report(config.setFromString(f.getName(), ((Enum<?>) values[(i + 1) % values.length]).name(), actor));
            refresh();
        } else {
            AnvilInput.text(viewer, "&8" + f.getName(), String.valueOf(v), (p, s) -> {
                report(config.setFromString(f.getName(), s, actor));
                open();
            }, p -> open());
        }
    }

    private void report(List<String> errors) {
        if (errors.isEmpty()) Msg.send(viewer, "&aSaved.");
        else Msg.send(viewer, "&c" + String.join("; ", errors));
    }

    private static Class<?> box(Class<?> t) {
        if (t == int.class) return Integer.class;
        if (t == long.class) return Long.class;
        if (t == double.class) return Double.class;
        if (t == float.class) return Float.class;
        return t;
    }

    private static String preview(Object v) {
        String s = String.valueOf(v);
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
