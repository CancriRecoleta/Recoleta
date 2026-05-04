package com.github.recoleta.forge.mixin.client;

import com.github.recoleta.memory.SlackTrimmer;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers {@link ChatComponent}'s three long-lived message lists
 * with {@link SlackTrimmer}'s client-side trim cadence.
 *
 * <p>Vanilla holds three growing-but-never-shrinking lists that
 * accumulate over a play session:</p>
 *
 * <ul>
 *   <li>{@code recentChat} &mdash; player chat input history.</li>
 *   <li>{@code allMessages} &mdash; received chat messages bounded
 *       at 100 by vanilla, but a "100-entry" {@link ArrayList} can
 *       carry a backing capacity of 128, and individual
 *       {@code GuiMessage} objects pin {@code Component} trees.</li>
 *   <li>{@code trimmedMessages} &mdash; lines after wrap; depends on
 *       chat width and font, can balloon under {@code tellraw}-heavy
 *       servers.</li>
 * </ul>
 *
 * <p>None of these lists is mutated outside the client thread, so
 * registering with the client trimmer is race-free. The mixin is gated
 * to the client {@code Dist} via the {@code client} array of
 * {@code recoleta.mixins.json}.</p>
 *
 * <p>{@code messageDeletionQueue} is intentionally not tracked: it
 * is drained every tick by {@code processMessageDeletionQueue} so
 * its slack is naturally bounded.</p>
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentSlackTrackMixin {

    @Shadow @Final private List<String> recentChat;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$registerChatLists(final CallbackInfo ci) {
        if (this.recentChat instanceof ArrayList<?> a) {
            SlackTrimmer.trackClientArrayList(a);
        }
        if (this.allMessages instanceof ArrayList<?> a) {
            SlackTrimmer.trackClientArrayList(a);
        }
        if (this.trimmedMessages instanceof ArrayList<?> a) {
            SlackTrimmer.trackClientArrayList(a);
        }
    }
}
