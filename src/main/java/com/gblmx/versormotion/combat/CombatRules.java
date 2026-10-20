package com.gblmx.versormotion.combat;

import com.gblmx.versormotion.field.StreamBake;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Server-authoritative tuning loaded from {@code data/versormotion/combat.json}.
 *
 * <p>The record is reloaded with {@code /reload} and applies to every later
 * swing, parry and field burst. Missing or malformed entries fall back to the
 * matching default, and every number is clamped so a datapack cannot make the
 * adjudicator travel farther than its search box or allocate absurd payloads.
 * Geometry itself (arc samples, blade radius) stays fixed: the client replays
 * the exact points the server tested.
 */
public record CombatRules(
		double arcDamage,
		double knockback,
		int swingCooldownTicks,
		int swingTicks,
		double minReach,
		double maxReach,
		boolean parry,
		double parryReach,
		int fieldSeeds,
		int fieldSteps,
		int fieldTicks) {

	public static final CombatRules DEFAULT = new CombatRules(
			2.0, 0.3, 4, 6,
			ArcSweep.MIN_RADIUS, ArcSweep.MAX_RADIUS,
			true, GuardPlane.REACH,
			StreamBake.SEEDS, StreamBake.STEPS, 10);

	private static volatile CombatRules current = DEFAULT;

	public static CombatRules get() {
		return current;
	}

	public static void set(CombatRules rules) {
		current = rules == null ? DEFAULT : rules;
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("arc_damage", arcDamage);
		json.addProperty("knockback", knockback);
		json.addProperty("swing_cooldown_ticks", swingCooldownTicks);
		json.addProperty("swing_ticks", swingTicks);
		json.addProperty("min_reach", minReach);
		json.addProperty("max_reach", maxReach);
		json.addProperty("parry", parry);
		json.addProperty("parry_reach", parryReach);
		json.addProperty("field_seeds", fieldSeeds);
		json.addProperty("field_steps", fieldSteps);
		json.addProperty("field_ticks", fieldTicks);
		return json;
	}

	public static CombatRules fromJson(JsonObject json) {
		double minReach = number(json, "min_reach", DEFAULT.minReach, 1.0, 6.0);
		double maxReach = number(json, "max_reach", DEFAULT.maxReach, 2.0, 8.0);
		if (maxReach < minReach) {
			maxReach = minReach;
		}
		return new CombatRules(
				number(json, "arc_damage", DEFAULT.arcDamage, 0.0, 100.0),
				number(json, "knockback", DEFAULT.knockback, 0.0, 5.0),
				integer(json, "swing_cooldown_ticks", DEFAULT.swingCooldownTicks, 0, 200),
				integer(json, "swing_ticks", DEFAULT.swingTicks, 1, 40),
				minReach,
				maxReach,
				flag(json, "parry", DEFAULT.parry),
				number(json, "parry_reach", DEFAULT.parryReach, 0.5, 4.0),
				integer(json, "field_seeds", DEFAULT.fieldSeeds, 1, 64),
				integer(json, "field_steps", DEFAULT.fieldSteps, 1, 32),
				integer(json, "field_ticks", DEFAULT.fieldTicks, 1, 60));
	}

	private static double number(JsonObject json, String key, double fallback, double min, double max) {
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		double value = element.getAsDouble();
		return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
	}

	private static int integer(JsonObject json, String key, int fallback, int min, int max) {
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		double value = element.getAsDouble();
		if (!Double.isFinite(value)) {
			return fallback;
		}
		return (int) Math.clamp(Math.round(value), min, max);
	}

	private static boolean flag(JsonObject json, String key, boolean fallback) {
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}
}
