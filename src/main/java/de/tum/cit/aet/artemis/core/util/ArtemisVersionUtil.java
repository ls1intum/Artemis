package de.tum.cit.aet.artemis.core.util;

import java.util.Objects;

import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.SemverException;

/**
 * Utility for parsing Artemis version strings into {@link Semver} objects.
 * <p>
 * Artemis canonical versions are two-part (e.g. {@code "9.2"}), with optional three-part
 * hotfixes (e.g. {@code "10.4.1"}). The semver4j library requires three components in strict mode;
 * this helper pads two-part inputs to {@code x.y.0} internally so strict semantics hold.
 * <p>
 * <b>For comparison only.</b> Never use {@link Semver#toString()} on the returned object for display,
 * storage, headers, release tags, or any observable output — the padded form would leak. Pass the
 * original version string through to anything user-visible or persisted.
 */
public final class ArtemisVersionUtil {

    private ArtemisVersionUtil() {
    }

    /**
     * Parses an Artemis version string for comparison.
     * <p>
     * Two-part inputs are padded to {@code x.y.0} internally; three-part inputs pass through.
     *
     * @param version the version string, e.g. {@code "9.2"} or {@code "10.4.1"}
     * @return a strict {@link Semver} suitable for comparison
     * @throws NullPointerException if {@code version} is null
     * @throws SemverException      if {@code version} cannot be parsed even after padding
     */
    public static Semver parseForComparison(String version) {
        String trimmed = Objects.requireNonNull(version, "version must not be null").trim();
        if (trimmed.matches("\\d+\\.\\d+")) {
            return new Semver(trimmed + ".0");
        }
        // semver4j's strict mode silently tolerates trailing dotted segments (e.g. "9.2.3.4");
        // reject anything that is not exactly three dotted numeric components before delegating.
        if (!trimmed.matches("\\d+\\.\\d+\\.\\d+")) {
            throw new SemverException("Invalid Artemis version: " + version);
        }
        return new Semver(trimmed);
    }
}
