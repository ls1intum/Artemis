package de.tum.cit.aet.artemis.core.service.featureusage;

/**
 * How many entities in the database have a given feature switched on.
 *
 * @param module  the Artemis module the setting belongs to, for example {@code programming}
 * @param key     the setting, in kebab-case and unique within the module, for example {@code static-code-analysis}
 * @param feature the catalogue feature the setting switches on, which is where the admin page shows the count
 * @param count   how many entities have it enabled
 * @param total   how many entities of that kind exist, so the count can be read as a share
 */
public record FeatureAdoptionEntry(String module, String key, UserFeature feature, long count, long total) {
}
