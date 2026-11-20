package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEffortProfileDTO;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HyperionGenerationSettings;

/**
 * Materialises the admin-defined effort profiles once at startup and resolves the name a request sends into the settings one run may use.
 * <p>
 * Admins define, users select: everything that costs money, pins a model, or changes semantics is decided here from Spring configuration, and the only thing that crosses the API
 * boundary is a name drawn from this set. An unknown or unconfigured name fails closed with a 400 rather than falling back to the default, because a silent fallback is how an
 * instructor gets a surprise bill.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class HyperionEffortProfileService {

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    /** Bounds a pathological configuration; the selectable set is an instructor-facing choice, not a parameter sweep. */
    private static final int MAX_PROFILES = 20;

    private static final Pattern PROFILE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    private final Map<String, HyperionGenerationSettings> profilesByName;

    private final HyperionGenerationSettings defaultSettings;

    public HyperionEffortProfileService(HyperionAgentProperties properties) {
        // Validated eagerly, before any profile can inherit it: an invalid deployment default would otherwise surface only on the first run that used it.
        validateStagedContext(properties.getStagedContext());
        HyperionGenerationSettings deploymentDefault = new HyperionGenerationSettings("", null, properties.getMaxTurns(), properties.getMaxJobDuration(),
                properties.getMaxTokensPerJob(), properties.isStagedGeneration(), properties.getStagedContext(), properties.getContextWindowTokens(), agentPreset(properties), null,
                true, false);
        if (properties.getProfiles().size() > MAX_PROFILES) {
            throw new IllegalArgumentException("artemis.hyperion.agent.profiles must not configure more than " + MAX_PROFILES + " effort profiles");
        }
        Map<String, HyperionGenerationSettings> resolved = new LinkedHashMap<>();
        properties.getProfiles().forEach((name, profile) -> resolved.put(name, materialise(name, profile, properties)));
        // Order-preserving rather than Map.copyOf: the selectable list is instructor-facing, and a set that reorders itself between restarts makes "the first one" an unusable
        // convention for any client.
        this.profilesByName = Collections.unmodifiableMap(resolved);
        this.defaultSettings = resolveDefault(properties, deploymentDefault);
    }

    private static void validateStagedContext(String context) {
        if (!Set.of("CONTINUOUS", "FRESH").contains(context)) {
            throw new IllegalArgumentException("staged-context must be CONTINUOUS or FRESH");
        }
    }

    private HyperionGenerationSettings resolveDefault(HyperionAgentProperties properties, HyperionGenerationSettings deploymentDefault) {
        String defaultName = properties.getDefaultProfile() == null ? "" : properties.getDefaultProfile().strip();
        if (defaultName.isEmpty()) {
            return deploymentDefault;
        }
        HyperionGenerationSettings named = profilesByName.get(defaultName);
        if (named == null) {
            throw new IllegalArgumentException("artemis.hyperion.agent.default-profile is '" + defaultName + "', which is not configured under artemis.hyperion.agent.profiles");
        }
        return named;
    }

    private static HyperionGenerationSettings materialise(String name, HyperionAgentProperties.EffortProfileProperties profile, HyperionAgentProperties properties) {
        if (name == null || !PROFILE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Hyperion effort profile names must use lower snake_case and contain at most 64 characters");
        }
        int maxTurns = profile.getMaxTurns() == null ? properties.getMaxTurns() : profile.getMaxTurns();
        Duration maxJobDuration = profile.getMaxJobDuration() == null ? properties.getMaxJobDuration() : profile.getMaxJobDuration();
        if (maxJobDuration == null || maxJobDuration.isZero() || maxJobDuration.isNegative()) {
            throw invalidOption(name, "max-job-duration must be positive");
        }
        long maxTokensPerJob = profile.getMaxTokensPerJob() == null ? properties.getMaxTokensPerJob() : profile.getMaxTokensPerJob();
        boolean stagedGeneration = profile.getStagedGeneration() == null ? properties.isStagedGeneration() : profile.getStagedGeneration();
        String stagedContext = profile.getStagedContext() == null ? properties.getStagedContext() : profile.getStagedContext();
        validateStagedContext(stagedContext);
        int contextWindowTokens = profile.getContextWindowTokens() == null ? properties.getContextWindowTokens() : profile.getContextWindowTokens();
        // A profile with its own preset runs a different model or different options than the deployment default.
        String llmPreset = profile.getLlmPreset() != null && !profile.getLlmPreset().isBlank() ? profile.getLlmPreset().strip() : agentPreset(properties);
        boolean providerOptionsOverride = !llmPreset.equals(agentPreset(properties));
        boolean engineDefaults = !providerOptionsOverride && maxTurns == properties.getMaxTurns() && maxJobDuration.equals(properties.getMaxJobDuration())
                && stagedGeneration == properties.isStagedGeneration() && stagedContext.equals(properties.getStagedContext())
                && contextWindowTokens == properties.getContextWindowTokens();
        String label = profile.getLabel() == null || profile.getLabel().isBlank() ? name : profile.getLabel().strip();
        if (label.length() > 100) {
            throw new IllegalArgumentException("Hyperion effort profile '" + name + "': label must contain at most 100 characters");
        }
        return new HyperionGenerationSettings(name, label, maxTurns, maxJobDuration, maxTokensPerJob, stagedGeneration, stagedContext, contextWindowTokens, llmPreset, null,
                engineDefaults, providerOptionsOverride);
    }

    private static IllegalArgumentException invalidOption(String profileName, String message) {
        return new IllegalArgumentException("Hyperion effort profile '" + profileName + "': " + message);
    }

    /** The agent's preset; the startup validation of artemis.llm requires it whenever exercise generation is enabled. */
    private static String agentPreset(HyperionAgentProperties properties) {
        return properties.getLlmPreset() == null ? "" : properties.getLlmPreset().strip();
    }

    /**
     * Resolves the profile a request named, or the deployment default when it named none.
     *
     * @param requestedName the profile name from the request, or {@code null}/blank for the deployment default
     * @return the settings this run must use
     * @throws BadRequestAlertException if a name was given that this deployment does not configure
     */
    public HyperionGenerationSettings resolve(@Nullable String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return defaultSettings;
        }
        HyperionGenerationSettings settings = profilesByName.get(requestedName.strip());
        if (settings == null) {
            throw new BadRequestAlertException("Unknown generation effort profile '" + requestedName.strip() + "'. This deployment configures no such profile.", ENTITY_NAME,
                    "unknownEffortProfile");
        }
        return settings;
    }

    /**
     * The profiles an instructor may choose from, in configured order. Empty when the deployment configures none, in which case every run uses the deployment default and a
     * request may not name a profile at all.
     *
     * @return the selectable profiles as name/label pairs
     */
    public List<ExerciseGenerationEffortProfileDTO> selectableProfiles() {
        return profilesByName.values().stream()
                .map(settings -> new ExerciseGenerationEffortProfileDTO(settings.name(), settings.label() == null ? settings.name() : settings.label())).toList();
    }

    /**
     * The largest per-job token allowance any configured profile can reserve. Startup budget validation must use this rather than the deployment default, or a profile that
     * raises the ceiling would be admitted against a rolling budget too small to hold one of its jobs.
     *
     * @return the maximum {@code max-tokens-per-job} across the deployment default and every configured profile
     */
    public long largestMaxTokensPerJob() {
        return Math.max(defaultSettings.maxTokensPerJob(), profilesByName.values().stream().mapToLong(HyperionGenerationSettings::maxTokensPerJob).max().orElse(0L));
    }

    /**
     * The longest wall-clock deadline any configured profile can give a run. The duration twin of {@link #largestMaxTokensPerJob()}: the stale-slot timeout must exceed every
     * deadline a run may actually be given, or a profile that raises the deadline would have its slot reclaimed by another node while it is still legitimately running.
     *
     * @return the maximum {@code max-job-duration} across the deployment default and every configured profile
     */
    public Duration longestMaxJobDuration() {
        return profilesByName.values().stream().map(HyperionGenerationSettings::maxJobDuration).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .filter(longest -> longest.compareTo(defaultSettings.maxJobDuration()) > 0).orElseGet(defaultSettings::maxJobDuration);
    }

    /**
     * The shortest wall-clock deadline any configured profile can give a run. The ownership heartbeat must fire before this deadline for every selectable profile.
     *
     * @return the minimum {@code max-job-duration} across the deployment default and every configured profile
     */
    public Duration shortestMaxJobDuration() {
        return profilesByName.values().stream().map(HyperionGenerationSettings::maxJobDuration).filter(Objects::nonNull).min(Comparator.naturalOrder())
                .filter(shortest -> shortest.compareTo(defaultSettings.maxJobDuration()) < 0).orElseGet(defaultSettings::maxJobDuration);
    }
}
