package com.gblmx.versormotion;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import com.gblmx.versormotion.combat.CombatRulesReloadListener;
import com.gblmx.versormotion.net.CameraShakePayload;
import com.gblmx.versormotion.net.FieldBurstPayload;
import com.gblmx.versormotion.net.RollPayload;
import com.gblmx.versormotion.net.SwingPayload;
import com.gblmx.versormotion.net.TornadoPayload;
import com.gblmx.versormotion.wedit.WeCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersorMotion implements ModInitializer {
	public static final String MOD_ID = "versormotion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		VersorParticles.init();
		PayloadTypeRegistry.clientboundPlay().register(RollPayload.TYPE, RollPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SwingPayload.TYPE, SwingPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FieldBurstPayload.TYPE, FieldBurstPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TornadoPayload.TYPE, TornadoPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CameraShakePayload.TYPE, CameraShakePayload.CODEC);
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new CombatRulesReloadListener());
		RollManager.init();
		CombatManager.init();
		WeCommands.init();
		// CommandRegistrationCallback runs again after /reload; registering on
		// server start alone would drop the commands when datapacks reload.
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) -> {
			LOGGER.info("Registering versor commands");
			VersorCommands.register(dispatcher);
			WeCommands.register(dispatcher, registry);
			LOGGER.info("versor commands registered");
		});
		LOGGER.info("Versor Geometric Motion Combat initialized");
	}
}
