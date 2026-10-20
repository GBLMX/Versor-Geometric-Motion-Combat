package com.gblmx.versormotion.wedit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded undo/redo stacks, one pair per player. */
public final class History {
	/** Undo history depth per player. */
	public static final int DEPTH = 5;

	public record Edit(ResourceKey<Level> dimension, BlockPos[] positions, BlockState[] states) {
		public Edit {
			if (positions.length != states.length) {
				throw new IllegalArgumentException("positions and states must match");
			}
		}
	}

	private static final Map<UUID, Deque<Edit>> UNDO = new HashMap<>();
	private static final Map<UUID, Deque<Edit>> REDO = new HashMap<>();

	private History() {}

	public static void push(UUID player, Edit edit) {
		Deque<Edit> stack = UNDO.computeIfAbsent(player, id -> new ArrayDeque<>());
		stack.push(edit);
		while (stack.size() > DEPTH) {
			stack.removeLast();
		}
		REDO.remove(player);
	}

	/** Pushes without clearing the redo stack; used when replaying redo. */
	public static void pushUndoKeepRedo(UUID player, Edit edit) {
		Deque<Edit> stack = UNDO.computeIfAbsent(player, id -> new ArrayDeque<>());
		stack.push(edit);
		while (stack.size() > DEPTH) {
			stack.removeLast();
		}
	}

	public static Edit popUndo(UUID player) {
		Deque<Edit> stack = UNDO.get(player);
		return stack == null ? null : stack.poll();
	}

	public static void pushRedo(UUID player, Edit edit) {
		Deque<Edit> stack = REDO.computeIfAbsent(player, id -> new ArrayDeque<>());
		stack.push(edit);
		while (stack.size() > DEPTH) {
			stack.removeLast();
		}
	}

	public static Edit popRedo(UUID player) {
		Deque<Edit> stack = REDO.get(player);
		return stack == null ? null : stack.poll();
	}

	public static void clear(UUID player) {
		UNDO.remove(player);
		REDO.remove(player);
	}
}
