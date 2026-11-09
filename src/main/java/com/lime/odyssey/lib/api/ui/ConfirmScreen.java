package com.lime.odyssey.lib.api.ui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Yes/no confirmation. {@code onNo} runs when "No" is clicked (null = just close); ESC simply closes. */
public class ConfirmScreen extends Screen {
    private final ItemStack subject;
    private final String question;
    private final Runnable onYes;
    private final Runnable onNo;

    public ConfirmScreen(String owner, ServerPlayer viewer, String question, ItemStack subject, Runnable onYes, Runnable onNo) {
        super(owner, viewer, 3, "&8" + question);
        this.question = question;
        this.subject = subject;
        this.onYes = onYes;
        this.onNo = onNo;
    }

    @Override
    protected void draw() {
        fill(Items.GRAY_STAINED_GLASS_PANE);
        set(11, Icons.confirm("Yes"), c -> onYes.run());
        set(13, subject != null && !subject.isEmpty() ? subject : Icons.info(question));
        set(15, Icons.cancel("No"), c -> {
            if (onNo != null) onNo.run();
            else close();
        });
    }
}
