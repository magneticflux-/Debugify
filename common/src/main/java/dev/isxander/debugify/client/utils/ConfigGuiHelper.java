package dev.isxander.debugify.client.utils;

import dev.isxander.debugify.client.DebugifyClient;
import dev.isxander.debugify.config.DebugifyConfig;
import dev.isxander.debugify.fixes.BugFix;
import dev.isxander.debugify.fixes.FixCategory;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConfigGuiHelper {
    public static Screen createConfigGui(DebugifyConfig config, Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setTitle(Text.literal("Debugify"))
                .setSavingRunnable(config::save)
                .setParentScreen(parent);

        Map<FixCategory, ConfigCategory> fixCategories = new HashMap<>();
        Map<ConfigCategory, Map<BugFix.Env, SubCategoryBuilder>> fixSubCategories = new HashMap<>();
        for (FixCategory fixCategory : FixCategory.values()) {
            var configCategory = builder.getOrCreateCategory(Text.literal(fixCategory.getDisplayName()));
            if (fixCategory == FixCategory.GAMEPLAY) {
                configCategory.addEntry(builder.entryBuilder()
                        .startTextDescription(Text.literal("WARNING: This category contains fixes that may be an unfair advantage!").formatted(Formatting.RED))
                        .build()
                );
                configCategory.addEntry(builder.entryBuilder()
                        .startBooleanToggle(Text.literal("Enable In Multiplayer"), config.gameplayFixesInMultiplayer)
                        .setSaveConsumer((enabled) -> config.gameplayFixesInMultiplayer = enabled)
                        .build()
                );
            }

            fixCategories.put(fixCategory, configCategory);

            Map<BugFix.Env, SubCategoryBuilder> subCategories = new HashMap<>();
            for (BugFix.Env env : BugFix.Env.values()) {
                var subCategoryBuilder = builder.entryBuilder().startSubCategory(Text.literal(env.getDisplayName()));
                subCategories.put(env, subCategoryBuilder);
            }
            fixSubCategories.put(configCategory, subCategories);
        }

        config.getBugFixes().forEach((bug, enabled) -> {
            SubCategoryBuilder subcategory = fixSubCategories.get(fixCategories.get(bug.category())).get(bug.env());

            BooleanToggleBuilder entry = builder.entryBuilder()
                    .startBooleanToggle(Text.literal(bug.bugId()), enabled)
                    .setSaveConsumer((toggled) -> config.getBugFixes().replace(bug, toggled))
                    .setDefaultValue(bug.enabledByDefault())
                    .setErrorSupplier((b) -> {
                        List<String> conflicts = bug.getActiveConflicts();
                        if (!b || conflicts.isEmpty())
                            return Optional.empty();

                        return Optional.of(Text.literal(bug.bugId() + " is conflicting with " + (conflicts.size() == 1 ? conflicts.get(0) : "mods " + String.join(", ", conflicts))));
                    })
                    .requireRestart();

            if (DebugifyClient.bugFixDescriptionCache.has(bug.bugId()))
                entry.setTooltip(Text.literal(DebugifyClient.bugFixDescriptionCache.get(bug.bugId())));

            subcategory.add(entry.build());
        });
        fixSubCategories.forEach((category, subCategories) ->
                subCategories.forEach((env, subCategoryBuilder) -> category.addEntry(subCategoryBuilder.build())));

        ConfigCategory miscCategory = builder.getOrCreateCategory(Text.literal("Misc"));
        AbstractConfigListEntry<?> optOutUpdaterEntry = builder.entryBuilder()
                .startBooleanToggle(Text.literal("Opt Out Updater"), config.optOutUpdater)
                .setTooltip(Text.literal("Stop Debugify checking for updates on launch."))
                .setSaveConsumer((toggled) -> config.optOutUpdater = toggled)
                .setDefaultValue(false)
                .build();
        miscCategory.addEntry(optOutUpdaterEntry);

        AbstractConfigListEntry<?> defaultDisabledEntry = builder.entryBuilder()
                .startBooleanToggle(Text.literal("Default to Disabled"), config.defaultDisabled)
                .setTooltip(Text.literal("Default new bug fixes to be disabled rather than enabled."))
                .setSaveConsumer((toggled) -> config.defaultDisabled = toggled)
                .setDefaultValue(false)
                .build();
        miscCategory.addEntry(defaultDisabledEntry);

        return builder.build();
    }
}
