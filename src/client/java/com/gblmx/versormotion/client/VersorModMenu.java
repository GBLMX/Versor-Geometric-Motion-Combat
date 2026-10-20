package com.gblmx.versormotion.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu button; Mod Menu loads this entrypoint only when it is installed. */
public final class VersorModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return VersorClientConfig::screen;
	}
}
