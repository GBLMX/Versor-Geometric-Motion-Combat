package com.gblmx.versormotion.combat;

import java.io.IOException;
import java.util.Optional;

import com.gblmx.versormotion.VersorMotion;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Reads {@code data/versormotion/combat.json} on every server data reload.
 * The mod ships the file with the default values; datapacks can replace it.
 */
public final class CombatRulesReloadListener implements SimpleSynchronousResourceReloadListener {
	public static final Identifier ID =
			Identifier.fromNamespaceAndPath(VersorMotion.MOD_ID, "combat_rules");

	private static final Identifier FILE =
			Identifier.fromNamespaceAndPath(VersorMotion.MOD_ID, "combat.json");

	@Override
	public Identifier getFabricId() {
		return ID;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		CombatRules rules = CombatRules.DEFAULT;
		Optional<Resource> resource = manager.getResource(FILE);
		if (resource.isPresent()) {
			try {
				rules = CombatRules.fromJson(JsonParser.parseString(resource.get().readAllAsString()).getAsJsonObject());
			} catch (IOException | JsonParseException | IllegalStateException e) {
				VersorMotion.LOGGER.error("Could not read {}, keeping the previous combat rules", FILE, e);
				return;
			}
		}
		CombatRules.set(rules);
		VersorMotion.LOGGER.info(
				"Combat rules: damage={} knockback={} cooldown={} swing={} reach={}..{} parry={}@{} field={}x{}/{}t",
				rules.arcDamage(), rules.knockback(), rules.swingCooldownTicks(), rules.swingTicks(),
				rules.minReach(), rules.maxReach(), rules.parry(), rules.parryReach(),
				rules.fieldSeeds(), rules.fieldSteps(), rules.fieldTicks());
	}
}
