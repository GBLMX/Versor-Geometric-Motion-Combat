package com.gblmx.versormotion;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersorMotion implements ModInitializer {
	public static final String MOD_ID = "versormotion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Versor Geometric Motion Combat initialized");
	}
}
