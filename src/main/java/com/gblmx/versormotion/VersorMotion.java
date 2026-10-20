package com.gblmx.versormotion;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersorMotion implements ModInitializer {
	public static final String MOD_ID = "versormotion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			LOGGER.info("Registering versor commands");
			try {
				VersorCommands.register(server.getCommands().getDispatcher());
				LOGGER.info("versor commands registered");
			} catch (Throwable t) {
				LOGGER.error("Command registration failed", t);
			}
		});
		LOGGER.info("Versor Geometric Motion Combat initialized");
	}
}
