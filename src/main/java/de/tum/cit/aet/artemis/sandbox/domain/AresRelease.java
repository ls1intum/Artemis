package de.tum.cit.aet.artemis.sandbox.domain;

import java.util.Set;

/**
 * What one Ares release accepts.
 *
 * @param aresVersion          the release, as it is pinned in the build file
 * @param minimumFormatVersion the oldest policy-format version it accepts
 * @param maximumFormatVersion the newest policy-format version it accepts
 * @param fields               the field paths it accepts
 */
public record AresRelease(String aresVersion, int minimumFormatVersion, int maximumFormatVersion, Set<String> fields) {
}
