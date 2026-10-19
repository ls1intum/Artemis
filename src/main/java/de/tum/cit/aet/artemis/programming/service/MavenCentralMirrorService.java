package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Knows the optional Maven Central mirror that all Java and Kotlin test repositories created by this instance are pointed at.
 * <p>
 * Maven Central rate-limits requests (HTTP 429), which breaks exercise builds, so an instance can configure a mirror that proxies it. The mirror is empty by default,
 * in which case the templates keep resolving from Maven Central directly.
 * <p>
 * The knowledge lives in its own service rather than in {@link ProgrammingExerciseRepositoryService}, because that class only needs to know <em>which</em> template
 * sections and placeholders exist, not what a mirror is.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MavenCentralMirrorService {

    /**
     * Placeholder the Java and Kotlin templates use for the mirror URL.
     */
    static final String MIRROR_URL_PLACEHOLDER = "${mavenCentralMirrorUrl}";

    /**
     * Optional template section holding the mirror declarations. Kept when a mirror is configured.
     */
    private static final String MIRROR_SECTION = "maven-central-mirror";

    /**
     * Complement of {@link #MIRROR_SECTION}. The black-box settings file mirrors {@code *} and therefore has to name a repository either way, so it falls back to
     * Maven Central instead of dropping the declaration.
     */
    private static final String FALLBACK_SECTION = "maven-central-fallback";

    @Value("${artemis.programming.maven-central-mirror-url:}")
    private String mirrorUrl;

    /**
     * @return true if this instance configured a Maven Central mirror
     */
    public boolean isConfigured() {
        return StringUtils.isNotBlank(mirrorUrl);
    }

    /**
     * Records whether the mirror declarations should be kept in or removed from the build tool template files.
     *
     * @param sections the section map passed to the template resolution, mutated in place
     */
    public void addTemplateSections(Map<String, Boolean> sections) {
        boolean configured = isConfigured();
        sections.put(MIRROR_SECTION, configured);
        sections.put(FALLBACK_SECTION, !configured);
    }

    /**
     * Adds the mirror URL replacement, if a mirror is configured. Without a mirror the placeholder is removed together with its section, so nothing is added.
     *
     * @param replacements the placeholder replacements applied to the template files, mutated in place
     */
    public void addUrlReplacement(Map<String, String> replacements) {
        if (isConfigured()) {
            replacements.put(MIRROR_URL_PLACEHOLDER, mirrorUrl.strip());
        }
    }
}
