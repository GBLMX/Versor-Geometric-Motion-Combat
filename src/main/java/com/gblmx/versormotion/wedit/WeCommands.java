package com.gblmx.versormotion.wedit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gblmx.versormotion.VersorMotion;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;

/**
 * Minimal WorldEdit-style editing core.
 *
 * <p>Edits are server-authoritative and bounded: every brush is capped at
 * {@link #MAX_VOLUME} blocks and undo history is capped at
 * {@link History#DEPTH} entries per player.
 *
 * <p>The commands are registered as {@code /we}; {@link #handle} also serves
 * the {@code //command} form by translating it to {@code we command}.
 */
public final class WeCommands {
	/** Maximum blocks one command may touch. */
	public static final int MAX_VOLUME = 32_768;
	public static final int MAX_RADIUS = 32;
	public static final int MAX_HEIGHT = 64;

	private static final SimpleCommandExceptionType NO_SELECTION =
			new SimpleCommandExceptionType(Component.literal("select two corners first (//wand or //pos1 //pos2)"));
	private static final SimpleCommandExceptionType TOO_LARGE =
			new SimpleCommandExceptionType(Component.literal("selection exceeds " + MAX_VOLUME + " blocks"));
	private static final SimpleCommandExceptionType NOT_PLAYER =
			new SimpleCommandExceptionType(Component.literal("this command needs a player"));
	private static final SimpleCommandExceptionType NO_CLIPBOARD =
			new SimpleCommandExceptionType(Component.literal("clipboard is empty (use //copy or //cut)"));
	private static final SimpleCommandExceptionType NO_HISTORY =
			new SimpleCommandExceptionType(Component.literal("nothing to undo or redo"));

	private static final Map<UUID, Clipboard> CLIPBOARDS = new HashMap<>();
	private static CommandBuildContext buildContext;

	private WeCommands() {}

	/** Registers the wand listeners and disconnect cleanup. */
	public static void init() {
		Wand.init();
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> clear(handler.getPlayer().getUUID()));
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
		buildContext = context;
		dispatcher.register(Commands.literal("we")
				.requires(WeCommands::allowed)
				.executes(WeCommands::help)
				.then(Commands.literal("help").executes(WeCommands::help))
				.then(Commands.literal("wand").executes(WeCommands::wand))
				.then(Commands.literal("pos1").executes(ctx -> corner(ctx, 1)))
				.then(Commands.literal("pos2").executes(ctx -> corner(ctx, 2)))
				.then(Commands.literal("size").executes(WeCommands::size))
				.then(Commands.literal("count")
						.then(Commands.argument("filter", BlockPredicateArgument.blockPredicate(context))
								.executes(WeCommands::count)))
				.then(Commands.literal("set")
						.then(Commands.argument("block", BlockStateArgument.block(context))
								.executes(WeCommands::set)))
				.then(Commands.literal("replace")
						.then(Commands.argument("args", StringArgumentType.greedyString())
								.executes(WeCommands::replace)))
				.then(Commands.literal("sphere")
						.then(Commands.argument("block", BlockStateArgument.block(context))
								.then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
										.executes(ctx -> sphere(ctx, true)))))
				.then(Commands.literal("hsphere")
						.then(Commands.argument("block", BlockStateArgument.block(context))
								.then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
										.executes(ctx -> sphere(ctx, false)))))
				.then(Commands.literal("cyl")
						.then(Commands.argument("block", BlockStateArgument.block(context))
								.then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
										.executes(ctx -> cylinder(ctx, 1, true))
										.then(Commands.argument("height", IntegerArgumentType.integer(1, MAX_HEIGHT))
												.executes(ctx -> cylinder(ctx, IntegerArgumentType.getInteger(ctx, "height"), true))))))
				.then(Commands.literal("hcyl")
						.then(Commands.argument("block", BlockStateArgument.block(context))
								.then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
										.executes(ctx -> cylinder(ctx, 1, false))
										.then(Commands.argument("height", IntegerArgumentType.integer(1, MAX_HEIGHT))
												.executes(ctx -> cylinder(ctx, IntegerArgumentType.getInteger(ctx, "height"), false))))))
				.then(Commands.literal("copy").executes(WeCommands::copy))
				.then(Commands.literal("cut").executes(WeCommands::cut))
				.then(Commands.literal("paste").executes(WeCommands::paste))
				.then(Commands.literal("undo").executes(WeCommands::undo))
				.then(Commands.literal("redo").executes(WeCommands::redo)));
	}

	/**
	 * Handles the {@code //command} form. Vanilla strips one leading slash from
	 * the chat command before dispatching, so the server sees {@code /set} for
	 * what the player typed as {@code //set}.
	 *
	 * @return true when the command was consumed
	 */
	public static boolean handle(CommandSourceStack source, String command) {
		if (!command.startsWith("/")) {
			return false;
		}
		String text = command.substring(1).trim();
		if (text.isEmpty()) {
			return false;
		}
		try {
			source.getServer().getCommands().getDispatcher().execute("we " + text, source);
		} catch (CommandSyntaxException e) {
			source.sendFailure(Component.literal(e.getMessage()));
		} catch (RuntimeException e) {
			VersorMotion.LOGGER.error("we command failed: //{}", text, e);
			source.sendFailure(Component.literal("we: " + e.getMessage()));
		}
		return true;
	}

	/** Clears per-player state on disconnect. */
	public static void clear(UUID player) {
		Selection.clear(player);
		History.clear(player);
		CLIPBOARDS.remove(player);
	}

	static boolean allowed(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private static int help(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() -> Component.literal(
				"we: pos1 pos2 wand | size count <filter> | set <block> | replace <to> | replace <from> <to>"
						+ " | sphere|hsphere <block> <r> | cyl|hcyl <block> <r> [height]"
						+ " | copy cut paste | undo redo   (also //command)"), false);
		return 1;
	}

	private static int wand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		var stack = Wand.create();
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				"wand: left click = pos1, right click = pos2"), false);
		return 1;
	}

	private static int corner(CommandContext<CommandSourceStack> ctx, int corner) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		BlockPos pos = player.blockPosition();
		Selection.setCorner(player, corner, pos);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"pos" + corner + " = " + pos.toShortString()), false);
		return 1;
	}

	private static int size(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Selection.Box box = box(ctx);
		long volume = box.volume();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"size: " + box.sizeX() + " x " + box.sizeY() + " x " + box.sizeZ()
						+ " = " + volume + " blocks"
						+ (volume > MAX_VOLUME ? " (over the " + MAX_VOLUME + " limit)" : "")), false);
		return (int) Math.min(volume, Integer.MAX_VALUE);
	}

	private static int count(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Selection.Box box = editableBox(ctx);
		ServerLevel level = ctx.getSource().getLevel();
		var filter = BlockPredicateArgument.getBlockPredicate(ctx, "filter");
		long count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
			if (filter.test(new BlockInWorld(level, pos, false))) {
				count++;
			}
		}
		long matches = count;
		ctx.getSource().sendSuccess(() -> Component.literal(
				"count: " + matches + " matching block(s)"), false);
		return (int) Math.min(matches, Integer.MAX_VALUE);
	}

	private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		Selection.Box box = editableBox(ctx);
		BlockPos[] positions = positions(box);
		BlockState state = BlockStateArgument.getBlock(ctx, "block").getState();
		int changed = apply(player, positions, state);
		ctx.getSource().sendSuccess(() -> Component.literal("set: " + changed + " block(s)"), false);
		return changed;
	}

	private static int replace(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		Selection.Box box = editableBox(ctx);
		String[] parts = splitTopLevel(StringArgumentType.getString(ctx, "args"));
		if (parts.length < 1 || parts.length > 2) {
			throw new SimpleCommandExceptionType(
					Component.literal("usage: replace <to> | replace <from> <to>")).create();
		}

		BlockState target = parseState(parts[parts.length - 1]);
		BlockPositionPredicate filter = parts.length == 1
				? (level, pos) -> !level.getBlockState(pos).isAir()
				: parsePredicate(parts[0]);

		ServerLevel level = player.level();
		List<BlockPos> matches = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
			BlockPos immutable = pos.immutable();
			if (filter.test(level, immutable)) {
				matches.add(immutable);
				if (matches.size() > MAX_VOLUME) {
					throw TOO_LARGE.create();
				}
			}
		}
		if (matches.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("replace: no blocks matched"), false);
			return 0;
		}
		int changed = apply(player, matches, target);
		ctx.getSource().sendSuccess(() -> Component.literal("replace: " + changed + " block(s)"), false);
		return changed;
	}

	private static int sphere(CommandContext<CommandSourceStack> ctx, boolean solid) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		BlockState state = BlockStateArgument.getBlock(ctx, "block").getState();
		int radius = IntegerArgumentType.getInteger(ctx, "radius");
		BlockPos center = center(ctx, player);
		List<BlockPos> positions = new ArrayList<>();
		for (int dy = -radius; dy <= radius; dy++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dx = -radius; dx <= radius; dx++) {
					boolean inside = solid
							? Shapes.sphere(dx, dy, dz, radius)
							: Shapes.hollowSphere(dx, dy, dz, radius);
					if (inside) {
						if (positions.size() >= MAX_VOLUME) {
							throw TOO_LARGE.create();
						}
						positions.add(center.offset(dx, dy, dz));
					}
				}
			}
		}
		int changed = apply(player, positions, state);
		ctx.getSource().sendSuccess(() -> Component.literal(
				(solid ? "sphere" : "hsphere") + ": " + changed + " block(s)"), false);
		return changed;
	}

	private static int cylinder(CommandContext<CommandSourceStack> ctx, int height, boolean solid)
			throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		BlockState state = BlockStateArgument.getBlock(ctx, "block").getState();
		int radius = IntegerArgumentType.getInteger(ctx, "radius");
		BlockPos center = center(ctx, player);
		int low = -(height / 2);
		int high = low + height - 1;
		List<BlockPos> positions = new ArrayList<>();
		for (int dy = low; dy <= high; dy++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dx = -radius; dx <= radius; dx++) {
					boolean inside = solid
							? Shapes.cylinder(dx, dy, dz, radius, height)
							: Shapes.hollowCylinder(dx, dy, dz, radius, height);
					if (inside) {
						if (positions.size() >= MAX_VOLUME) {
							throw TOO_LARGE.create();
						}
						positions.add(center.offset(dx, dy, dz));
					}
				}
			}
		}
		int changed = apply(player, positions, state);
		ctx.getSource().sendSuccess(() -> Component.literal(
				(solid ? "cyl" : "hcyl") + ": " + changed + " block(s)"), false);
		return changed;
	}

	private static int copy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		Selection.Box box = editableBox(ctx);
		CLIPBOARDS.put(player.getUUID(), snapshot(player, box));
		Clipboard clipboard = CLIPBOARDS.get(player.getUUID());
		ctx.getSource().sendSuccess(() -> Component.literal(
				"copy: " + clipboard.sizeX() + " x " + clipboard.sizeY() + " x " + clipboard.sizeZ()
						+ " = " + clipboard.volume() + " block(s)"), false);
		return (int) clipboard.volume();
	}

	private static int cut(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		Selection.Box box = editableBox(ctx);
		CLIPBOARDS.put(player.getUUID(), snapshot(player, box));
		BlockPos[] positions = positions(box);
		int changed = apply(player, positions, Blocks.AIR.defaultBlockState());
		ctx.getSource().sendSuccess(() -> Component.literal("cut: " + changed + " block(s)"), false);
		return changed;
	}

	private static int paste(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		Clipboard clipboard = CLIPBOARDS.get(player.getUUID());
		if (clipboard == null) {
			throw NO_CLIPBOARD.create();
		}
		ServerLevel level = player.level();
		if (!clipboard.dimension().equals(level.dimension())) {
			throw new SimpleCommandExceptionType(
					Component.literal("clipboard belongs to another dimension")).create();
		}
		BlockPos base = player.blockPosition();
		int dx = clipboard.min().getX() - clipboard.origin().getX();
		int dy = clipboard.min().getY() - clipboard.origin().getY();
		int dz = clipboard.min().getZ() - clipboard.origin().getZ();
		BlockPos[] positions = new BlockPos[(int) clipboard.volume()];
		BlockState[] states = new BlockState[positions.length];
		int index = 0;
		for (int y = 0; y < clipboard.sizeY(); y++) {
			for (int z = 0; z < clipboard.sizeZ(); z++) {
				for (int x = 0; x < clipboard.sizeX(); x++) {
					positions[index] = base.offset(dx + x, dy + y, dz + z);
					states[index] = clipboard.states()[clipboard.index(x, y, z)];
					index++;
				}
			}
		}
		int changed = applyStates(player, positions, states);
		ctx.getSource().sendSuccess(() -> Component.literal("paste: " + changed + " block(s)"), false);
		return changed;
	}

	private static int undo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		History.Edit edit = History.popUndo(player.getUUID());
		if (edit == null) {
			throw NO_HISTORY.create();
		}
		ServerLevel level = player.level();
		if (!edit.dimension().equals(level.dimension())) {
			History.pushUndoKeepRedo(player.getUUID(), edit);
			throw new SimpleCommandExceptionType(
					Component.literal("undo belongs to another dimension")).create();
		}
		BlockState[] current = new BlockState[edit.positions().length];
		for (int i = 0; i < current.length; i++) {
			current[i] = level.getBlockState(edit.positions()[i]);
		}
		History.pushRedo(player.getUUID(),
				new History.Edit(edit.dimension(), edit.positions(), current));
		for (int i = 0; i < edit.positions().length; i++) {
			level.setBlock(edit.positions()[i], edit.states()[i], Block.UPDATE_ALL);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				"undo: " + edit.positions().length + " block(s)"), false);
		return edit.positions().length;
	}

	private static int redo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		History.Edit edit = History.popRedo(player.getUUID());
		if (edit == null) {
			throw NO_HISTORY.create();
		}
		ServerLevel level = player.level();
		if (!edit.dimension().equals(level.dimension())) {
			History.pushRedo(player.getUUID(), edit);
			throw new SimpleCommandExceptionType(
					Component.literal("redo belongs to another dimension")).create();
		}
		BlockState[] current = new BlockState[edit.positions().length];
		for (int i = 0; i < current.length; i++) {
			current[i] = level.getBlockState(edit.positions()[i]);
		}
		History.pushUndoKeepRedo(player.getUUID(),
				new History.Edit(edit.dimension(), edit.positions(), current));
		for (int i = 0; i < edit.positions().length; i++) {
			level.setBlock(edit.positions()[i], edit.states()[i], Block.UPDATE_ALL);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(
				"redo: " + edit.positions().length + " block(s)"), false);
		return edit.positions().length;
	}

	private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		try {
			return ctx.getSource().getPlayerOrException();
		} catch (CommandSyntaxException e) {
			throw NOT_PLAYER.create();
		}
	}

	private static Selection.Box box(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Selection.Box box = Selection.get(player(ctx));
		if (box == null) {
			throw NO_SELECTION.create();
		}
		return box;
	}

	/** Selection that a single edit is allowed to touch. */
	private static Selection.Box editableBox(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Selection.Box box = box(ctx);
		if (box.volume() > MAX_VOLUME) {
			throw TOO_LARGE.create();
		}
		return box;
	}

	/** Selection center when one exists, otherwise the player's block position. */
	private static BlockPos center(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		Selection.Box box = Selection.get(player);
		return box == null ? player.blockPosition() : box.center();
	}

	private static BlockPos[] positions(Selection.Box box) {
		List<BlockPos> positions = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
			positions.add(pos.immutable());
		}
		return positions.toArray(new BlockPos[0]);
	}

	private static Clipboard snapshot(ServerPlayer player, Selection.Box box) {
		ServerLevel level = player.level();
		BlockState[] states = new BlockState[(int) box.volume()];
		int index = 0;
		for (int y = box.minY(); y <= box.maxY(); y++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int x = box.minX(); x <= box.maxX(); x++) {
					states[index++] = level.getBlockState(new BlockPos(x, y, z));
				}
			}
		}
		return new Clipboard(box.dimension(), box.min(), player.blockPosition(),
				box.sizeX(), box.sizeY(), box.sizeZ(), states);
	}

	private static int apply(ServerPlayer player, List<BlockPos> positions, BlockState state) {
		BlockState[] states = new BlockState[positions.size()];
		java.util.Arrays.fill(states, state);
		return applyStates(player, positions.toArray(new BlockPos[0]), states);
	}

	private static int apply(ServerPlayer player, BlockPos[] positions, BlockState state) {
		BlockState[] states = new BlockState[positions.length];
		java.util.Arrays.fill(states, state);
		return applyStates(player, positions, states);
	}

	private static int applyStates(ServerPlayer player, BlockPos[] positions, BlockState[] states) {
		ServerLevel level = player.level();
		BlockState[] before = new BlockState[positions.length];
		for (int i = 0; i < positions.length; i++) {
			before[i] = level.getBlockState(positions[i]);
		}
		History.push(player.getUUID(),
				new History.Edit(level.dimension(), positions, before));
		for (int i = 0; i < positions.length; i++) {
			level.setBlock(positions[i], states[i], Block.UPDATE_ALL);
		}
		return positions.length;
	}

	private static BlockState parseState(String text) throws CommandSyntaxException {
		StringReader reader = new StringReader(text);
		BlockState state = new BlockStateArgument(buildContext).parse(reader).getState();
		requireEnd(reader);
		return state;
	}

	private static BlockPositionPredicate parsePredicate(String text) throws CommandSyntaxException {
		StringReader reader = new StringReader(text);
		BlockPredicateArgument.Result predicate = new BlockPredicateArgument(buildContext).parse(reader);
		requireEnd(reader);
		return (level, pos) -> predicate.test(new BlockInWorld(level, pos, false));
	}

	private static void requireEnd(StringReader reader) throws CommandSyntaxException {
		if (reader.canRead()) {
			throw new SimpleCommandExceptionType(
					Component.literal("trailing characters: " + reader.getRemaining())).create();
		}
	}

	/** Splits on whitespace outside of {@code [...]} so block states stay intact. */
	static String[] splitTopLevel(String text) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int depth = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '[') {
				depth++;
			} else if (c == ']') {
				depth = Math.max(0, depth - 1);
			}
			if (Character.isWhitespace(c) && depth == 0) {
				if (current.length() > 0) {
					parts.add(current.toString());
					current.setLength(0);
				}
			} else {
				current.append(c);
			}
		}
		if (current.length() > 0) {
			parts.add(current.toString());
		}
		return parts.toArray(new String[0]);
	}

	@FunctionalInterface
	interface BlockPositionPredicate {
		boolean test(ServerLevel level, BlockPos pos);
	}
}
