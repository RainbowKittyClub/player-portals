package club.rainbowkitty.playerportals.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import club.rainbowkitty.kalic0re.common.item.PolymerModelItem;
import club.rainbowkitty.kalic0re.common.portal.PortalFrame;
import club.rainbowkitty.playerportals.PlayerPortals;
import club.rainbowkitty.playerportals.portal.PortalEffects;

/**
 * An item that works in two clicks on frame blocks: the first attunes it to that frame, the
 * second acts on the pair. A vanilla client sees a heart of the sea under this item's name, with
 * the attuned frame as a lore line; a client with the resource pack sees the item's own art.
 * A click on any other block passes it on, and sneak-use aimed at no block clears the
 * attunement. Every line is said in chat.
 */
public abstract class CatalystItem extends PolymerModelItem {
    private static final Style HINT = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false);
    private static final Style ATTUNED =
            Style.EMPTY.withColor(ChatFormatting.AQUA).withItalic(false);

    private final ResourceKey<Item> key;

    protected CatalystItem(ResourceKey<Item> key, Properties properties) {
        super(Items.HEART_OF_THE_SEA, key.identifier(), properties.setId(key).stacksTo(1)
                .component(DataComponents.LORE, new ItemLore(List.of(hint(key)))));
        this.key = key;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        // Off a frame block the click passes on, to this item's use and then the off hand.
        if (!PlayerPortals.portals().finder().isFrameBlock(
                level.getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }
        if (!permitted(player)) {
            return refuse(player, deniedKey());
        }
        Optional<PortalFrame> frame = PlayerPortals.portals().finder()
                .findAround(level, context.getClickedPos(), context.getClickedFace());
        if (frame.isEmpty()) {
            return refuse(player, "catalyst.no_frame");
        }
        return onFrame(level, player, context.getItemInHand(), frame.get());
    }

    // CONSUME, not SUCCESS: Polymer turns a success into a server-sent swing, which a vanilla
    // client echoes back as a left-click. A sneaking click at a block passes, attunement kept.
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || !stack.has(CatalystComponents.ATTUNED)
                || aimsAtBlock(player)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            attune(stack, null);
            PlayerPortals.say(serverPlayer, "catalyst.cleared");
        }
        return InteractionResult.CONSUME;
    }

    /** Whether {@code player} may use this item at all. */
    protected abstract boolean permitted(ServerPlayer player);

    /** The message key, under {@code playerportals.}, for a player who may not. */
    protected abstract String deniedKey();

    /**
     * This item's answer to a click on a frame block beside {@code frame}, a complete frame:
     * {@code SUCCESS_SERVER} when it did something, so the server swings the arm for a client that
     * holds a heart of the sea and predicts nothing, else {@code CONSUME}, which swings for no one.
     */
    protected abstract InteractionResult onFrame(ServerLevel level, ServerPlayer player,
            ItemStack stack, PortalFrame frame);

    /** {@code frame} for a player: size, orientation, origin and dimension. */
    protected static MutableComponent describe(PortalFrame frame) {
        return PlayerPortals.line("frame", frame.width(), frame.height(),
                PlayerPortals.line("plane." + frame.plane().getSerializedName()),
                frame.origin().getX(), frame.origin().getY(), frame.origin().getZ(),
                frame.dimension().identifier().toShortString());
    }

    /** Sets, or with null clears, the frame {@code stack} is attuned to, and its lore line. */
    protected void attune(ItemStack stack, @Nullable PortalFrame frame) {
        List<Component> lines = new ArrayList<>(2);
        lines.add(hint(key));
        if (frame == null) {
            stack.remove(CatalystComponents.ATTUNED);
        } else {
            stack.set(CatalystComponents.ATTUNED, frame);
            lines.add(PlayerPortals.text().of("item." + PlayerPortals.MOD_ID + ".catalyst.attuned",
                    describe(frame)).withStyle(ATTUNED));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    /**
     * Attunes {@code stack} to {@code frame}, marks the frame, and tells {@code player} the line
     * under {@code playerportals.<lineKey>}, which names it; the server swings.
     */
    protected InteractionResult attuneTo(ServerLevel level, ServerPlayer player, ItemStack stack,
            PortalFrame frame, String lineKey) {
        attune(stack, frame);
        PortalEffects.attuned(level, frame);
        PlayerPortals.say(player, lineKey, describe(frame));
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Tells {@code player} the line under {@code playerportals.<lineKey>}; no one swings. */
    protected static InteractionResult refuse(ServerPlayer player, String lineKey) {
        PlayerPortals.say(player, lineKey);
        return InteractionResult.CONSUME;
    }

    /**
     * Whether the complete {@code frame} holds a portal: its interior is air or portal, so its
     * origin tells.
     */
    protected static boolean hasPortal(ServerLevel level, PortalFrame frame) {
        return !level.getBlockState(frame.origin()).isAir();
    }

    /** The level {@code frame} stands complete in, or null when its dimension or ring is gone. */
    protected static @Nullable ServerLevel standingLevel(MinecraftServer server,
            PortalFrame frame) {
        ServerLevel level = server.getLevel(frame.dimension());
        return level != null && PlayerPortals.portals().finder().isComplete(level, frame)
                ? level : null;
    }

    // Whether a block within reach is under the crosshair, by the outline pick a client makes.
    private static boolean aimsAtBlock(Player player) {
        return player.pick(player.blockInteractionRange(), 1.0F, false).getType()
                == HitResult.Type.BLOCK;
    }

    // This item's tooltip line, item.playerportals.<path>.tooltip.
    private static Component hint(ResourceKey<Item> key) {
        return PlayerPortals.text().of(key.identifier().toLanguageKey("item", "tooltip"))
                .withStyle(HINT);
    }
}
