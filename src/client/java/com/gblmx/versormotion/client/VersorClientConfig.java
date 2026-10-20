package com.gblmx.versormotion.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Client-only presentation options, stored in {@code config/versormotion-client.json}
 * and edited through Cloth Config (Mod Menu adds the button when installed).
 * Gameplay tuning stays server-authoritative in {@code data/versormotion/combat.json}.
 */
@Config(name = "versormotion-client")
public final class VersorClientConfig implements ConfigData {
	public boolean swingParticles = true;
	public boolean arcMesh = true;
	public int cameraShakePercent = 100;

	private static ConfigHolder<VersorClientConfig> holder;

	public static void init() {
		if (holder == null) {
			holder = AutoConfig.register(VersorClientConfig.class, GsonConfigSerializer::new);
		}
	}

	public static VersorClientConfig get() {
		if (holder == null) {
			init();
		}
		return holder.getConfig();
	}

	public static float cameraShakeScale() {
		return Math.clamp(get().cameraShakePercent, 0, 200) / 100.0f;
	}

	public static Screen screen(Screen parent) {
		if (holder == null) {
			init();
		}
		VersorClientConfig config = holder.getConfig();
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("config.versormotion.title"));
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory visuals = builder.getOrCreateCategory(
				Component.translatable("config.versormotion.category.visuals"));

		visuals.addEntry(entries.startBooleanToggle(
						Component.translatable("config.versormotion.swing_particles"), config.swingParticles)
				.setDefaultValue(true)
				.setTooltip(Component.translatable("config.versormotion.swing_particles.tooltip"))
				.setSaveConsumer(value -> config.swingParticles = value)
				.build());
		visuals.addEntry(entries.startBooleanToggle(
						Component.translatable("config.versormotion.arc_mesh"), config.arcMesh)
				.setDefaultValue(true)
				.setTooltip(Component.translatable("config.versormotion.arc_mesh.tooltip"))
				.setSaveConsumer(value -> config.arcMesh = value)
				.build());
		visuals.addEntry(entries.startIntSlider(
						Component.translatable("config.versormotion.camera_shake"), config.cameraShakePercent, 0, 200)
				.setDefaultValue(100)
				.setTooltip(Component.translatable("config.versormotion.camera_shake.tooltip"))
				.setSaveConsumer(value -> config.cameraShakePercent = value)
				.build());

		builder.setSavingRunnable(() -> {
			config.cameraShakePercent = Math.clamp(config.cameraShakePercent, 0, 200);
			holder.save();
		});
		return builder.build();
	}

	@Override
	public void validatePostLoad() {
		cameraShakePercent = Math.clamp(cameraShakePercent, 0, 200);
	}
}
