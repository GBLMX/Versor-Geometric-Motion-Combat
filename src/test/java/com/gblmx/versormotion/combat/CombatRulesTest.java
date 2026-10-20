package com.gblmx.versormotion.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Standalone checks for the datapack-driven combat rules. */
public final class CombatRulesTest {
	private static final Path SHIPPED = Path.of("src/main/resources/data/versormotion/combat.json");

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

		CombatRules empty = CombatRules.fromJson(new JsonObject());
		check("empty json uses defaults", empty.equals(CombatRules.DEFAULT));
		check("default damage is vanilla-independent", empty.arcDamage() == 2.0);
		check("default swing ticks", empty.swingTicks() == 6);

		JsonObject shipped = new JsonObject();
		try {
			shipped = JsonParser.parseString(Files.readString(SHIPPED)).getAsJsonObject();
		} catch (IOException | IllegalStateException e) {
			check("shipped rules file readable", false);
		}
		check("shipped rules match the defaults", CombatRules.fromJson(shipped).equals(CombatRules.DEFAULT));

		JsonObject overrides = new JsonObject();
		overrides.addProperty("arc_damage", 5.5);
		overrides.addProperty("knockback", 0.8);
		overrides.addProperty("swing_cooldown_ticks", 9);
		overrides.addProperty("swing_ticks", 12);
		overrides.addProperty("min_reach", 2.5);
		overrides.addProperty("max_reach", 4.5);
		overrides.addProperty("parry", false);
		overrides.addProperty("parry_reach", 1.8);
		overrides.addProperty("field_seeds", 4);
		overrides.addProperty("field_steps", 8);
		overrides.addProperty("field_ticks", 20);
		CombatRules rules = CombatRules.fromJson(overrides);
		check("damage override", rules.arcDamage() == 5.5);
		check("knockback override", rules.knockback() == 0.8);
		check("cooldown override", rules.swingCooldownTicks() == 9);
		check("swing ticks override", rules.swingTicks() == 12);
		check("reach override", rules.minReach() == 2.5 && rules.maxReach() == 4.5);
		check("parry override", !rules.parry() && rules.parryReach() == 1.8);
		check("field override", rules.fieldSeeds() == 4 && rules.fieldSteps() == 8 && rules.fieldTicks() == 20);

		JsonObject clamped = new JsonObject();
		clamped.addProperty("arc_damage", -5.0);
		clamped.addProperty("knockback", 99.0);
		clamped.addProperty("swing_cooldown_ticks", -3);
		clamped.addProperty("swing_ticks", 0);
		clamped.addProperty("min_reach", 99.0);
		clamped.addProperty("max_reach", 3.0);
		clamped.addProperty("field_seeds", 9999);
		clamped.addProperty("field_steps", 0);
		clamped.addProperty("field_ticks", 1000);
		CombatRules sane = CombatRules.fromJson(clamped);
		check("damage clamps low", sane.arcDamage() == 0.0);
		check("knockback clamps high", sane.knockback() == 5.0);
		check("cooldown clamps low", sane.swingCooldownTicks() == 0);
		check("swing ticks clamps low", sane.swingTicks() == 1);
		check("inverted reach stays ordered", sane.maxReach() == sane.minReach());
		check("field seeds clamps high", sane.fieldSeeds() == 64);
		check("field steps clamps low", sane.fieldSteps() == 1);
		check("field ticks clamps high", sane.fieldTicks() == 60);
		check("reach clamps high", sane.minReach() == 6.0);

		JsonObject wrongTypes = new JsonObject();
		wrongTypes.addProperty("arc_damage", "lots");
		wrongTypes.addProperty("parry", 1);
		CombatRules fallback = CombatRules.fromJson(wrongTypes);
		check("wrong number type falls back", fallback.arcDamage() == CombatRules.DEFAULT.arcDamage());
		check("wrong flag type falls back", fallback.parry() == CombatRules.DEFAULT.parry());

		check("json round trip", CombatRules.fromJson(rules.toJson()).equals(rules));
		check("default json round trip", CombatRules.fromJson(CombatRules.DEFAULT.toJson()).equals(CombatRules.DEFAULT));

		CombatRules.set(rules);
		check("set applies", CombatRules.get().equals(rules));
		CombatRules.set(null);
		check("null resets to defaults", CombatRules.get().equals(CombatRules.DEFAULT));

		System.out.println(failures == 0
				? "OK: " + checks + " rules checks passed"
				: "FAILED: " + failures + "/" + checks + " rules checks");
		return failures;
	}

	private static void check(String name, boolean condition) {
		checks++;
		if (!condition) {
			failures++;
			System.out.println("FAIL " + name);
		}
	}
}
