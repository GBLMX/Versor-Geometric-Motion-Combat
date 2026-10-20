package com.gblmx.versormotion.combat;

import com.gblmx.versormotion.field.StreamBakeTest;
import com.gblmx.versormotion.field.TornadoBakeTest;

/** Aggregates the standalone combat/field geometry checks ({@code combatTest}). */
public final class CombatChecks {
	private CombatChecks() {}

	public static void main(String[] args) {
		int failures = ArcSweepTest.run()
				+ GuardPlaneTest.run()
				+ StreamBakeTest.run()
				+ TornadoBakeTest.run()
				+ CombatRulesTest.run();
		if (failures > 0) {
			System.out.println("FAILED: " + failures + " checks");
			System.exit(1);
		}
		System.out.println("OK: all combat checks passed");
	}
}
