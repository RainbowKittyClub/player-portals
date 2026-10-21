package club.rainbowkitty.playerportals.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.HitResult;

import club.rainbowkitty.kalic0re.common.item.PolymerModelItem;
import club.rainbowkitty.kalic0re.common.portal.FrameFinder;
import club.rainbowkitty.kalic0re.common.portal.PortalFrame;
import club.rainbowkitty.playerportals.PlayerPortals;
import club.rainbowkitty.playerportals.portal.PortalEffects;

/**
 * An item that works on frame blocks: a sneaking click attunes it to that frame and never links,
 * a plain click acts on the attuned frame and the clicked one and never attunes. A vanilla client
 * sees a nether star under this item's name, with a lore line saying whether it is attuned and to
 * which frame corner; a client with the resource pack sees the item's own art. A click on any
 * other block passes it on, and sneak-use aimed at no block clears the attunement. Nothing is said
 * in chat; a refused click fizzles like a punched-out fire.
 */
public abstract class CatalystItem extends PolymerModelItem {
    private final ResourceKey<Item> key;
    private final String loreKey;
    private final ChatFormatting idle;

    /**
     * {@code loreKey} names the attuned and not-attuned lines, {@code <loreKey>.attuned} and
     * {@code <loreKey>.not_attuned}; {@code idle} is the colour of the second.
     */
    protected CatalystItem(ResourceKey<Item> key, Properties properties, String loreKey,
            ChatFormatting idle) {
        super(Items.NETHER_STAR, key.identifier(), properties.setId(key).stacksTo(1)
                .component(DataComponents.LORE, lore(key, loreKey, idle, null)));
        this.key = key;
        this.loreKey = loreKey;
        this.idle = idle;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        FrameFinder finder = PlayerPortals.portals().finder();
        BlockPos clicked = context.getClickedPos();
        // Off a frame block the click passes on, to this item's use and then the off hand.
        if (!finder.isFrameBlock(level.getBlockState(clicked))) {
            return InteractionResult.PASS;
        }
        if (!permitted(player)) {
            return refuse(level, clicked);
        }
        Optional<PortalFrame> frame =
                finder.findAround(level, clicked, context.getClickedFace(), maxSize());
        if (frame.isEmpty()) {
            return refuse(level, clicked);
        }
        ItemStack stack = context.getItemInHand();
        return player.isShiftKeyDown() ? onAttune(level, player, stack, frame.get(), clicked)
                : onLink(level, player, stack, frame.get(), clicked);
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
        if (player instanceof ServerPlayer) {
            attune(stack, null);
        }
        return InteractionResult.CONSUME;
    }

    // Polymer sends hasFoil() as an explicit glint, so a stack with none of its own would hide
    // the stand-in's built-in one; its default value drops out of the patch instead.
    @Override
    public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag flag, PacketContext context,
            HolderLookup.Provider lookup) {
        ItemStack out = super.getPolymerItemStack(stack, flag, context, lookup);
        if (!stack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) && !stack.isEnchanted()) {
            out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
                    out.getPrototype().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        }
        return out;
    }

    /** Whether {@code player} may use this item at all. */
    protected abstract boolean permitted(ServerPlayer player);

    /** The most interior blocks across, on either axis of its plane, a frame may have. */
    protected abstract int maxSize();

    /**
     * This item's answer to a sneaking click at {@code clicked}, a frame block beside the complete
     * {@code frame}: {@code SUCCESS_SERVER} when it attuned, so the server swings the arm for a
     * client that holds a nether star and predicts nothing, else a {@link #refuse}.
     */
    protected abstract InteractionResult onAttune(ServerLevel level, ServerPlayer player,
            ItemStack stack, PortalFrame frame, BlockPos clicked);

    /** As {@link #onAttune}, for a plain click, which links when it can. */
    protected abstract InteractionResult onLink(ServerLevel level, ServerPlayer player,
            ItemStack stack, PortalFrame frame, BlockPos clicked);

    /** {@code frame}'s corner with the smallest coordinates in its plane, for a player. */
    protected static MutableComponent origin(PortalFrame frame) {
        BlockPos corner = frame.at(-1, -1);
        return PlayerPortals.line("frame_origin", corner.getX(), corner.getY(), corner.getZ());
    }

    /** Sets, or with null clears, the frame {@code stack} is attuned to, and its lore. */
    protected void attune(ItemStack stack, @Nullable PortalFrame frame) {
        if (frame == null) {
            stack.remove(CatalystComponents.ATTUNED);
        } else {
            stack.set(CatalystComponents.ATTUNED, frame);
        }
        stack.set(DataComponents.LORE, lore(key, loreKey, idle, frame));
    }

    /** Attunes {@code stack} to {@code frame} and marks the frame; the server swings. */
    protected InteractionResult attuneTo(ServerLevel level, ItemStack stack, PortalFrame frame) {
        attune(stack, frame);
        PortalEffects.attuned(level, frame);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** A refused click: the fire-extinguish fizzle at {@code at}, and no one swings. */
    protected static InteractionResult refuse(ServerLevel level, BlockPos at) {
        level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, at, 0);
        return InteractionResult.CONSUME;
    }

    /**
     * Whether the complete {@code frame} holds a portal: its interior is air or portal, so any of
     * its blocks tells.
     */
    protected static boolean hasPortal(ServerLevel level, PortalFrame frame) {
        return !level.getBlockState(frame.interior().iterator().next()).isAir();
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

    // The item's .tooltip line around its attuned line (yellow, naming the frame corner in
    // white) or its not-attuned one (idle), split at each newline, since one lore line can't
    // break; resolved here, so in English.
    private static ItemLore lore(ResourceKey<Item> key, String loreKey, ChatFormatting idle,
            @Nullable PortalFrame frame) {
        MutableComponent state = frame == null
                ? PlayerPortals.text().of(loreKey + ".not_attuned").withStyle(idle)
                : PlayerPortals.text().of(loreKey + ".attuned",
                        origin(frame).withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.YELLOW);
        return new ItemLore(lines(PlayerPortals.text()
                .of(key.identifier().toLanguageKey("item", "tooltip"), state)));
    }

    // component's text as plain styled lines, broken at each newline, never italic.
    private static List<Component> lines(Component component) {
        List<Component> lines = new ArrayList<>();
        List<Component> line = new ArrayList<>();
        component.visit((style, text) -> {
            String[] parts = text.split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    lines.add(join(line));
                    line.clear();
                }
                if (!parts[i].isEmpty()) {
                    line.add(Component.literal(parts[i]).withStyle(style));
                }
            }
            return Optional.empty();
        }, Style.EMPTY.withItalic(false));
        lines.add(join(line));
        return lines;
    }

    private static Component join(List<Component> parts) {
        MutableComponent line = Component.empty();
        parts.forEach(line::append);
        return line;
    }
}
