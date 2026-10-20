package com.gblmx.versormotion.wedit;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Standalone checks for the WorldEdit-style shapes, selection, clipboard and undo. */
public final class WeChecks {
	private static int checks;
	private static int failures;

	public static void main(String[] args) {
		if (run() > 0) {
			System.exit(1);
		}
	}

	public static int run() {
		checks = 0;
		failures = 0;

		check("sphere center", Shapes.sphere(0, 0, 0, 3));
		check("sphere boundary", Shapes.sphere(3, 0, 0, 3));
		check("sphere outside", !Shapes.sphere(4, 0, 0, 3));
		check("sphere corner", !Shapes.sphere(3, 3, 3, 3));

		check("hollow sphere shell", Shapes.hollowSphere(3, 0, 0, 3));
		check("hollow sphere inside", !Shapes.hollowSphere(2, 0, 0, 3));
		check("hollow sphere center", !Shapes.hollowSphere(0, 0, 0, 3));
		check("hollow sphere outside", !Shapes.hollowSphere(4, 0, 0, 3));

		check("cylinder radius", Shapes.cylinder(2, 0, 0, 2, 5));
		check("cylinder outside", !Shapes.cylinder(3, 0, 0, 2, 5));
		check("cylinder top", Shapes.cylinder(0, 2, 0, 2, 5));
		check("cylinder above top", !Shapes.cylinder(0, 3, 0, 2, 5));

		check("hollow cylinder rim", Shapes.hollowCylinder(3, 0, 0, 3, 1));
		check("hollow cylinder interior", !Shapes.hollowCylinder(2, 0, 0, 3, 1));
		check("hollow cylinder axis", !Shapes.hollowCylinder(0, 0, 0, 3, 1));

		int solidBlocks = 0;
		int solidCylinder = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (Shapes.sphere(dx, dy, dz, 2)) {
						solidBlocks++;
					}
					if (Shapes.cylinder(dx, dy, dz, 1, 1)) {
						solidCylinder++;
					}
				}
			}
		}
		check("solid sphere volume", solidBlocks == 31);
		check("solid cylinder volume", solidCylinder == 5);

		Selection.Box box = new Selection.Box(ResourceKey.create(Registries.DIMENSION,
				Identifier.withDefaultNamespace("overworld")),
				new BlockPos(5, 9, 3), new BlockPos(2, 4, 6));
		check("box min", box.min().equals(new BlockPos(2, 4, 3)));
		check("box max", box.max().equals(new BlockPos(5, 9, 6)));
		check("box sizes", box.sizeX() == 4 && box.sizeY() == 6 && box.sizeZ() == 4);
		check("box volume", box.volume() == 96);
		check("box center", box.center().equals(new BlockPos(3, 6, 4)));

		Clipboard clipboard = new Clipboard(box.dimension(), box.min(), new BlockPos(0, 0, 0),
				2, 3, 4, new net.minecraft.world.level.block.state.BlockState[24]);
		check("clipboard index origin", clipboard.index(0, 0, 0) == 0);
		check("clipboard index x", clipboard.index(1, 0, 0) == 1);
		check("clipboard index z", clipboard.index(0, 0, 1) == 2);
		check("clipboard index y", clipboard.index(0, 1, 0) == 8);
		check("clipboard last", clipboard.index(1, 2, 3) == 23);
		check("clipboard rejects bad size", throwsIllegal(() ->
				new Clipboard(box.dimension(), box.min(), box.min(), 2, 2, 2,
						new net.minecraft.world.level.block.state.BlockState[7])));

		check("split one", WeCommands.splitTopLevel("stone").length == 1);
		String[] two = WeCommands.splitTopLevel("oak_stairs[facing=north, half=top] stone");
		check("split two", two.length == 2);
		check("split keeps bracket spaces", two[0].equals("oak_stairs[facing=north, half=top]"));
		check("split tag then block", WeCommands.splitTopLevel("#minecraft:logs air").length == 2);
		check("split trims", WeCommands.splitTopLevel("  stone   ").length == 1);

		UUID player = UUID.randomUUID();
		History.clear(player);
		for (int i = 0; i < History.DEPTH + 3; i++) {
			History.push(player, edit(box, i));
		}
		int depth = 0;
		History.Edit popped = null;
		while ((popped = History.popUndo(player)) != null) {
			depth++;
		}
		check("undo history capped", depth == History.DEPTH);
		check("undo drains", popped == null);

		History.push(player, edit(box, 99));
		check("undo returns newest", History.popUndo(player) != null);
		check("redo empty after push", History.popRedo(player) == null);

		History.push(player, edit(box, 1));
		History.Edit undone = History.popUndo(player);
		History.pushRedo(player, undone);
		check("redo after undo", History.popRedo(player) == undone);
		History.clear(player);

		System.out.println(failures == 0
				? "OK: " + checks + " we checks passed"
				: "FAILED: " + failures + "/" + checks + " we checks");
		return failures;
	}

	private static History.Edit edit(Selection.Box box, int marker) {
		return new History.Edit(box.dimension(),
				new BlockPos[]{new BlockPos(marker, 0, 0)},
				new net.minecraft.world.level.block.state.BlockState[]{null});
	}

	private static boolean throwsIllegal(Runnable runnable) {
		try {
			runnable.run();
			return false;
		} catch (IllegalArgumentException e) {
			return true;
		}
	}

	private static void check(String name, boolean condition) {
		checks++;
		if (!condition) {
			failures++;
			System.out.println("FAIL " + name);
		}
	}
}
