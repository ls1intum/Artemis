package de.tum.cit.aet.artemis.core.service.featureusage;

/**
 * How many entities in the database have a given feature switched on.
 *
 * @param module the Artemis module the feature belongs to, for example {@code programming}
 * @param key    the feature, in kebab-case and unique within the module, for example {@code static-code-analysis}
 * @param count  how many entities have it enabled
 * @param total  how many entities of that kind exist, so the count can be read as a share
 */
public record FeatureAdoptionEntry(String module, String key, long count, long total) {
}
