package com.gblmx.versormotion;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.gblmx.versormotion.combat.CombatRules;
import com.gblmx.versormotion.field.FieldBurst;
import com.gblmx.versormotion.field.Tornado;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import vsr.Calculus;
import vsr.E3;
import vsr.Field;
import vsr.Motion;
import vsr.Multivector;

/**
 * In-game interface. Query and effect commands all run off the command source,
 * so they work from command blocks, functions and the server console; only the
 * actor commands ({@code attack}, {@code roll}) need a player executor.
 * Positions accept relative coordinates (~).
 */
public final class VersorCommands {
	private static final Field SWIRL = (x, y, z) -> E3.vec(-y, x, 0);

	private VersorCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> root = dispatcher.register(
				Commands.literal("versor")
						.executes(ctx -> usage(ctx.getSource()))
						.then(Commands.literal("attack")
								.executes(VersorCommands::attack))
						.then(Commands.literal("roll")
								.executes(VersorCommands::roll))
						.then(Commands.literal("rules")
								.executes(VersorCommands::rules))
						.then(Commands.literal("field")
								.then(Commands.literal("at")
										.then(Commands.argument("pos", Vec3Argument.vec3(false))
												.executes(VersorCommands::fieldAt)))
								.then(Commands.literal("burst")
										.then(Commands.argument("pos", Vec3Argument.vec3(false))
												.executes(VersorCommands::fieldBurst)))
								.then(Commands.literal("tornado")
										.then(Commands.argument("pos", Vec3Argument.vec3(false))
												.executes(VersorCommands::fieldTornado))))
						.then(Commands.literal("motion")
								.then(Commands.literal("rotation")
										.then(Commands.argument("axis", Vec3Argument.vec3(false))
												.then(Commands.argument("omega", DoubleArgumentType.doubleArg())
														.then(Commands.argument("time", DoubleArgumentType.doubleArg())
																.then(Commands.argument("point", Vec3Argument.vec3(false))
																		.executes(VersorCommands::motionRotation)))))))
						.then(Commands.literal("sweep")
								.then(Commands.literal("rotation")
										.then(Commands.argument("axis", Vec3Argument.vec3(false))
												.then(Commands.argument("omega", DoubleArgumentType.doubleArg())
														.then(Commands.argument("t0", DoubleArgumentType.doubleArg())
																.then(Commands.argument("t1", DoubleArgumentType.doubleArg())
																		.then(Commands.argument("samples",
																						IntegerArgumentType.integer(2, 4096))
																				.then(Commands.argument("point",
																								Vec3Argument.vec3(false))
																						.executes(VersorCommands::sweepRotation))))))))));

		dispatcher.register(Commands.literal("vm").redirect(root));
	}

	private static int usage(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal(
				"versor: field at <pos> | field burst <pos> | field tornado <pos>"
						+ " | motion rotation <axis> <omega> <time> <point>"
						+ " | sweep rotation <axis> <omega> <t0> <t1> <samples> <point> | attack | roll | rules"), false);
		return 1;
	}

	private static int fieldBurst(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Vec3 p = Vec3Argument.getVec3(ctx, "pos");
		int samples = FieldBurst.burst(ctx.getSource().getLevel(), p);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"field burst: " + samples + " particles"), false);
		return samples;
	}

	private static int fieldTornado(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Vec3 p = Vec3Argument.getVec3(ctx, "pos");
		int points = Tornado.spawn(ctx.getSource().getLevel(), p);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"tornado: " + points + " funnel points, " + Tornado.TICKS + " ticks"), false);
		return points;
	}

	private static int attack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		int hits = CombatManager.swing(player, null);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"swing: " + hits + " target(s) in arc"), false);
		return hits;
	}

	private static int roll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!RollManager.start(player)) {
			ctx.getSource().sendFailure(Component.literal("roll is on cooldown"));
			return 0;
		}
		return 1;
	}

	private static int rules(CommandContext<CommandSourceStack> ctx) {
		CombatRules rules = CombatRules.get();
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"combat: damage=%.2f knockback=%.2f cooldown=%d swing=%dt reach=%.1f..%.1f"
						+ " parry=%s@%.2f field=%dx%d/%dt",
				rules.arcDamage(), rules.knockback(), rules.swingCooldownTicks(), rules.swingTicks(),
				rules.minReach(), rules.maxReach(), rules.parry(), rules.parryReach(),
				rules.fieldSeeds(), rules.fieldSteps(), rules.fieldTicks())), false);
		return 1;
	}

	private static int fieldAt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Vec3 p = Vec3Argument.getVec3(ctx, "pos");
		Multivector f = SWIRL.at(p.x, p.y, p.z);
		double divergence = Calculus.divergence(SWIRL, p.x, p.y, p.z);
		double[] curl = E3.coords(Calculus.curl(SWIRL, p.x, p.y, p.z));
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"F(%.3f, %.3f, %.3f) = (%.3f, %.3f, %.3f); div = %.3f; curl = (%.3f, %.3f, %.3f)",
				p.x, p.y, p.z, f.get(1), f.get(2), f.get(4),
				divergence, curl[0], curl[1], curl[2])), false);
		return 1;
	}

	private static int motionRotation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Vec3 axis = Vec3Argument.getVec3(ctx, "axis");
		double omega = DoubleArgumentType.getDouble(ctx, "omega");
		double time = DoubleArgumentType.getDouble(ctx, "time");
		Vec3 point = Vec3Argument.getVec3(ctx, "point");

		Motion motion;
		try {
			motion = Motion.rotation(axis.x, axis.y, axis.z, omega);
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}

		double[] position = motion.position(time, point.x, point.y, point.z);
		double[] velocity = motion.velocity(time, point.x, point.y, point.z);
		double[] acceleration = motion.acceleration(time, point.x, point.y, point.z);
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"motion t=%.3f: pos = (%.3f, %.3f, %.3f); vel = (%.3f, %.3f, %.3f); acc = (%.3f, %.3f, %.3f)",
				time, position[0], position[1], position[2],
				velocity[0], velocity[1], velocity[2],
				acceleration[0], acceleration[1], acceleration[2])), false);
		return 1;
	}

	private static int sweepRotation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Vec3 axis = Vec3Argument.getVec3(ctx, "axis");
		double omega = DoubleArgumentType.getDouble(ctx, "omega");
		double t0 = DoubleArgumentType.getDouble(ctx, "t0");
		double t1 = DoubleArgumentType.getDouble(ctx, "t1");
		int samples = IntegerArgumentType.getInteger(ctx, "samples");
		Vec3 point = Vec3Argument.getVec3(ctx, "point");

		Motion motion;
		try {
			motion = Motion.rotation(axis.x, axis.y, axis.z, omega);
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}

		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
		double length = 0;
		double[] previous = null;
		for (int i = 0; i < samples; i++) {
			double t = t0 + (t1 - t0) * i / (samples - 1.0);
			double[] p = motion.position(t, point.x, point.y, point.z);
			minX = Math.min(minX, p[0]);
			minY = Math.min(minY, p[1]);
			minZ = Math.min(minZ, p[2]);
			maxX = Math.max(maxX, p[0]);
			maxY = Math.max(maxY, p[1]);
			maxZ = Math.max(maxZ, p[2]);
			if (previous != null) {
				double dx = p[0] - previous[0];
				double dy = p[1] - previous[1];
				double dz = p[2] - previous[2];
				length += Math.sqrt(dx * dx + dy * dy + dz * dz);
			}
			previous = p;
		}

		final double lx = minX, ly = minY, lz = minZ, hx = maxX, hy = maxY, hz = maxZ;
		final double path = length;
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
				"sweep %d samples: aabb = [(%.3f, %.3f, %.3f), (%.3f, %.3f, %.3f)]; length = %.3f",
				samples, lx, ly, lz, hx, hy, hz, path)), false);
		return samples;
	}
}
