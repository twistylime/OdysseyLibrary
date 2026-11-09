package com.lime.odyssey.lib.api.ui;

import com.lime.odyssey.lib.api.msg.Msg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Anvil text prompts. Click the output item to confirm, press ESC to cancel. */
public final class AnvilInput {
    final String initial;
    final BiConsumer<ServerPlayer, String> onSubmit;
    final Consumer<ServerPlayer> onCancel;

    private AnvilInput(String initial, BiConsumer<ServerPlayer, String> onSubmit, Consumer<ServerPlayer> onCancel) {
        this.initial = initial == null || initial.isBlank() ? "Type here" : initial;
        this.onSubmit = onSubmit;
        this.onCancel = onCancel;
    }

    public static void text(ServerPlayer p, String title, String initial, BiConsumer<ServerPlayer, String> onSubmit, Consumer<ServerPlayer> onCancel) {
        AnvilInput in = new AnvilInput(initial, onSubmit, onCancel);
        UiManager.closeCurrentQuietly(p);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new AnvilInputMenu(id, inv, in), Msg.text(title)));
    }

    /** Whole-number prompt; invalid input shows a message and returns to {@code onCancel}. */
    public static void number(ServerPlayer p, String title, long current, long min, long max,
                              BiConsumer<ServerPlayer, Long> onValue, Consumer<ServerPlayer> onCancel) {
        text(p, title, String.valueOf(current), (pl, s) -> {
            try {
                long v = Long.parseLong(s.replaceAll("[^0-9-]", ""));
                if (v < min || v > max) {
                    Msg.send(pl, "&cValue must be between " + min + " and " + max + ".");
                    if (onCancel != null) onCancel.accept(pl);
                    return;
                }
                onValue.accept(pl, v);
            } catch (NumberFormatException e) {
                Msg.send(pl, "&cThat is not a whole number.");
                if (onCancel != null) onCancel.accept(pl);
            }
        }, onCancel);
    }

    /** Decimal prompt. */
    public static void decimal(ServerPlayer p, String title, double current, double min, double max,
                               BiConsumer<ServerPlayer, Double> onValue, Consumer<ServerPlayer> onCancel) {
        text(p, title, String.valueOf(current), (pl, s) -> {
            try {
                double v = Double.parseDouble(s.replace(',', '.').replaceAll("[^0-9.eE-]", ""));
                if (Double.isNaN(v) || v < min || v > max) {
                    Msg.send(pl, "&cValue must be between " + min + " and " + max + ".");
                    if (onCancel != null) onCancel.accept(pl);
                    return;
                }
                onValue.accept(pl, v);
            } catch (NumberFormatException e) {
                Msg.send(pl, "&cThat is not a number.");
                if (onCancel != null) onCancel.accept(pl);
            }
        }, onCancel);
    }
}
