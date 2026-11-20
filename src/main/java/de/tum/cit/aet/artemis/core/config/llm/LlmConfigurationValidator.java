package de.tum.cit.aet.artemis.core.config.llm;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Api;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Endpoint;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.EndpointType;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Preset;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Price;

/**
 * Checks the LLM catalog and the preset assignments of the modules that run on this node at startup, and reports every problem in one message.
 * <p>
 * Old configuration keys that the explicit configuration replaced only produce a warning. A node that still has only the old configuration fails because
 * its active modules miss their new assignments.
 */
@Conditional(LlmEnabled.class)
@Component
@Lazy(false)
public class LlmConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(LlmConfigurationValidator.class);

    static final String DOCUMENTATION = "https://docs.artemis.tum.de/admin/llm-configuration";

    /** Keys and key prefixes that the explicit LLM configuration replaced. */
    private static final List<String> REMOVED_KEYS = List.of("spring.ai.openai", "artemis.llm.model-costs", "artemis.atlas.chat-model", "artemis.atlas.temperature",
            "artemis.atlas.flavor-strip-model", "artemis.atlas.flavor-strip-reasoning-effort", "artemis.atlas.flavor-strip-temperature", "artemis.atlas.orchestrator.model",
            "artemis.atlas.orchestrator.temperature", "artemis.atlas.orchestrator.reasoning-effort", "artemis.atlas.orchestrator.worker-model",
            "artemis.atlas.orchestrator.worker-reasoning-effort", "artemis.atlas.orchestrator.responses-api-enabled", "artemis.deimos.llm", "artemis.hyperion.quiz-pool.base-url",
            "artemis.hyperion.quiz-pool.api-key", "artemis.hyperion.quiz-pool.allowed-models", "artemis.hyperion.quiz-pool.generator-model",
            "artemis.hyperion.quiz-pool.judge-model", "artemis.hyperion.quiz-pool.timeout", "artemis.weaviate.open-ai-base-url", "artemis.weaviate.gpu-api-key",
            "artemis.weaviate.open-ai-embedding-model");

    /** Model options that effort profiles no longer carry (they moved to presets), in uniform form so YAML and environment variable names both match. */
    private static final Set<String> REMOVED_PROFILE_FIELDS = Set.of("model", "temperature", "topp", "reasoningeffort", "verbosity", "maxcompletiontokens");

    private static final Pattern NAME = Pattern.compile(LlmProperties.NAME_PATTERN);

    private static final ConfigurationPropertyName PROFILES = ConfigurationPropertyName.of("artemis.hyperion.agent.profiles");

    private final LlmProperties properties;

    private final List<LlmPresetAssignments> assignmentProviders;

    private final Environment environment;

    public LlmConfigurationValidator(LlmProperties properties, List<LlmPresetAssignments> assignmentProviders, Environment environment) {
        this.properties = properties;
        this.assignmentProviders = assignmentProviders;
        this.environment = environment;
    }

    /**
     * Validates the configuration and logs the effective assignments.
     *
     * @throws IllegalStateException listing every problem when the configuration is invalid
     */
    @PostConstruct
    public void validate() {
        warnAboutRemovedKeys();
        List<LlmPresetAssignment> assignments = assignmentProviders.stream().flatMap(provider -> provider.llmPresetAssignments().stream()).toList();
        List<String> errors = new ArrayList<>();
        validateCatalog(errors);
        validateAssignments(assignments, errors);
        validatePresetMapKeys(assignments, errors);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid LLM configuration (see " + DOCUMENTATION + "):\n  - " + String.join("\n  - ", errors));
        }
        assignments.forEach(this::logAssignment);
    }

    private void validateCatalog(List<String> errors) {
        properties.endpoints().forEach((name, endpoint) -> validateEndpoint(name, endpoint, errors));
        properties.presets().forEach((name, preset) -> validatePreset(name, preset, errors));
    }

    private static void validateEndpoint(String name, Endpoint endpoint, List<String> errors) {
        String key = "artemis.llm.endpoints." + name;
        if (!NAME.matcher(name).matches()) {
            errors.add(key + ": endpoint names must match " + LlmProperties.NAME_PATTERN + " (environment variables cannot carry dashes or dots in map keys)");
        }
        if (endpoint.baseUrl() == null || endpoint.baseUrl().isBlank()) {
            errors.add(key + ".base-url is required");
        }
        else if (!isHttpUrl(endpoint.baseUrl())) {
            errors.add(key + ".base-url must be an http or https URL");
        }
        if (endpoint.type() == EndpointType.MICROSOFT_FOUNDRY && !endpoint.hasApiKey()) {
            errors.add(key + ".api-key is required for type microsoft-foundry");
        }
        if (endpoint.timeout() == null || !endpoint.timeout().isPositive()) {
            errors.add(key + ".timeout must be positive");
        }
        if (endpoint.maxRetries() < 0) {
            errors.add(key + ".max-retries must not be negative");
        }
        Set<String> pricedModels = new HashSet<>();
        for (int i = 0; i < endpoint.pricesUsdPerMillion().size(); i++) {
            Price price = endpoint.pricesUsdPerMillion().get(i);
            String priceKey = key + ".prices-usd-per-million[" + i + "]";
            if (price.model() == null || price.model().isBlank()) {
                errors.add(priceKey + ".model is required");
            }
            else if (!pricedModels.add(price.model())) {
                errors.add(priceKey + ": model " + price.model() + " is listed twice");
            }
            if (!isPrice(price.input()) || !isPrice(price.output())) {
                errors.add(priceKey + ": input and output are required and must not be negative");
            }
            if (price.cachedInput() != null && !isPrice(price.cachedInput()) || price.cacheWrite() != null && !isPrice(price.cacheWrite())) {
                errors.add(priceKey + ": cached-input and cache-write must not be negative");
            }
        }
    }

    private void validatePreset(String name, Preset preset, List<String> errors) {
        String key = "artemis.llm.presets." + name;
        if (!NAME.matcher(name).matches()) {
            errors.add(key + ": preset names must match " + LlmProperties.NAME_PATTERN + " (environment variables cannot carry dashes or dots in map keys)");
        }
        Endpoint endpoint = properties.endpointOf(preset);
        if (preset.endpoint() == null || preset.endpoint().isBlank()) {
            errors.add(key + ".endpoint is required");
        }
        else if (endpoint == null) {
            errors.add(key + ".endpoint names the unknown endpoint " + preset.endpoint());
        }
        if (preset.model() == null || preset.model().isBlank()) {
            errors.add(key + ".model is required");
        }
        if (preset.api() == null) {
            errors.add(key + ".api is required (responses, chat-completions or embeddings)");
        }
        if (preset.timeout() != null && !preset.timeout().isPositive()) {
            errors.add(key + ".timeout must be positive");
        }
        if (preset.maxRetries() != null && preset.maxRetries() < 0) {
            errors.add(key + ".max-retries must not be negative");
        }
        if (preset.api() == Api.EMBEDDINGS) {
            if (preset.reasoningEffort() != null || preset.temperature() != null || preset.topP() != null || preset.verbosity() != null || preset.maxTokens() != null
                    || preset.maxCompletionTokens() != null) {
                errors.add(key + ": an embeddings preset takes no reasoning-effort, temperature, top-p, verbosity or token limit");
            }
            if (endpoint != null && endpoint.type() != EndpointType.OPENAI) {
                errors.add(key + ": embeddings need an endpoint of type openai");
            }
            if (preset.timeout() != null || preset.maxRetries() != null) {
                errors.add(key + ": an embeddings preset takes no timeout or max-retries; artemis.weaviate.embedding sets the timeouts of embedding calls");
            }
        }
        if (preset.temperature() != null && preset.reasoningEffort() != null && !"none".equals(preset.reasoningEffort())) {
            errors.add(key + ": temperature is only allowed without reasoning-effort or with reasoning-effort none");
        }
        if (preset.maxTokens() != null && preset.maxCompletionTokens() != null) {
            errors.add(key + ": set at most one of max-tokens and max-completion-tokens");
        }
        if (preset.outputTokenLimit() != null && preset.outputTokenLimit() <= 0) {
            errors.add(key + ": the token limit must be positive");
        }
        if (endpoint != null && preset.model() != null && !endpoint.pricesUsdPerMillion().isEmpty() && properties.priceOf(preset) == null) {
            log.warn("LLM preset {} uses model {} on endpoint {}, whose price list has no entry for that model; calls the provider does not price stay unpriced", name,
                    preset.model(), preset.endpoint());
        }
    }

    private void validateAssignments(List<LlmPresetAssignment> assignments, List<String> errors) {
        Set<String> missing = new LinkedHashSet<>();
        for (LlmPresetAssignment assignment : assignments) {
            if (assignment.preset() == null) {
                missing.add(assignment.fallbackKey() == null ? assignment.propertyKey() + ": <preset>"
                        : assignment.propertyKey() + ": <preset>   (or " + assignment.fallbackKey() + ": <preset>)");
                continue;
            }
            String source = assignment.usesDefault() ? assignment.fallbackKey() : assignment.propertyKey();
            Preset preset = properties.preset(assignment.preset());
            if (preset == null) {
                errors.add(source + " names the unknown preset " + assignment.preset());
                continue;
            }
            boolean embedding = preset.api() == Api.EMBEDDINGS;
            if (assignment.usage() == LlmPresetAssignment.Usage.EMBEDDING && !embedding && preset.api() != null) {
                errors.add(source + " needs a preset with api embeddings, but " + assignment.preset() + " uses " + apiName(preset.api()));
            }
            if (assignment.usage() == LlmPresetAssignment.Usage.CHAT && embedding) {
                errors.add(source + " needs a chat preset (api responses or chat-completions), but " + assignment.preset() + " uses embeddings");
            }
            if (assignment.requiresNoRetry() && properties.endpointOf(preset) != null && properties.effectiveMaxRetries(preset) != 0) {
                errors.add(source + ": exercise generation retries on its own and needs max-retries 0 on preset " + assignment.preset() + " or its endpoint");
            }
        }
        if (!missing.isEmpty()) {
            errors.add("Missing preset assignments for active modules; add:\n      " + String.join("\n      ", missing));
        }
    }

    /**
     * Rejects keys under an {@code llm-presets} map that name no task. YAML keys are already checked by strict binding, but Spring binds environment
     * variables leniently, so a misspelt variable would otherwise leave its task on the map's {@code default}.
     */
    private void validatePresetMapKeys(List<LlmPresetAssignment> assignments, List<String> errors) {
        Map<ConfigurationPropertyName, Set<String>> knownKeysByMap = new HashMap<>();
        for (LlmPresetAssignment assignment : assignments) {
            if (assignment.fallbackKey() == null) {
                continue;
            }
            String mapKey = assignment.fallbackKey().substring(0, assignment.fallbackKey().length() - ".default".length());
            String task = assignment.propertyKey().substring(mapKey.length() + 1);
            knownKeysByMap.computeIfAbsent(ConfigurationPropertyName.of(mapKey), ignored -> new HashSet<>(Set.of("default"))).add(uniform(task));
        }
        if (knownKeysByMap.isEmpty()) {
            return;
        }
        Set<String> unknown = new LinkedHashSet<>();
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            if (!(source instanceof IterableConfigurationPropertySource iterable)) {
                continue;
            }
            StreamSupport.stream(iterable.spliterator(), false).forEach(name -> knownKeysByMap.forEach((map, known) -> {
                // Every key below the map must be one known direct child; an extra underscore in an environment variable nests the key one level deeper.
                if (map.isAncestorOf(name)
                        && (name.getNumberOfElements() != map.getNumberOfElements() + 1 || !known.contains(uniform(name.getLastElement(ConfigurationPropertyName.Form.UNIFORM))))) {
                    unknown.add(name + " names no task of this llm-presets map (check the spelling, also of environment variables)");
                }
                // A misspelt map path would leave every task on the configured map silently: an extra underscore anywhere in an environment variable
                // (LLM_PRESETS, ATLAS_LLM) splits the path, and llm-preset is the singular of the module's single keys; no other key of the parent starts with llm.
                ConfigurationPropertyName parent = map.getParent();
                boolean underMap = map.equals(name) || map.isAncestorOf(name);
                boolean splitPath = flat(name).startsWith(flat(map));
                boolean llmSibling = parent.isAncestorOf(name) && name.getElement(parent.getNumberOfElements(), ConfigurationPropertyName.Form.UNIFORM).startsWith("llm");
                if (!underMap && (splitPath || llmSibling)) {
                    unknown.add(name + " is no key; the presets of this module go under " + map);
                }
            }));
        }
        errors.addAll(unknown);
    }

    /** The name without any separator, so that a path split by an extra underscore compares equal to the intended one. */
    private static String flat(ConfigurationPropertyName name) {
        StringBuilder flat = new StringBuilder();
        for (int i = 0; i < name.getNumberOfElements(); i++) {
            flat.append(name.getElement(i, ConfigurationPropertyName.Form.UNIFORM));
        }
        return flat.toString();
    }

    private static String uniform(String key) {
        return key.replace("-", "").toLowerCase(Locale.ROOT);
    }

    private void logAssignment(LlmPresetAssignment assignment) {
        Preset preset = properties.preset(assignment.preset());
        Endpoint endpoint = preset == null ? null : properties.endpointOf(preset);
        if (preset == null || endpoint == null) {
            return;
        }
        log.info("LLM {} -> {}{} | {} | {} | {} | timeout {}, retries {}, auth {}{}", assignment.propertyKey(), assignment.preset(), assignment.usesDefault() ? " (default)" : "",
                preset.model(), apiName(preset.api()), host(endpoint.baseUrl()), properties.effectiveTimeout(preset), properties.effectiveMaxRetries(preset),
                endpoint.hasApiKey() ? "key" : "none", properties.priceOf(preset) == null ? ", no fallback price" : "");
    }

    private void warnAboutRemovedKeys() {
        Set<String> found = new LinkedHashSet<>();
        List<ConfigurationPropertyName> removed = REMOVED_KEYS.stream().map(ConfigurationPropertyName::of).toList();
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            if (!(source instanceof IterableConfigurationPropertySource iterable)) {
                continue;
            }
            StreamSupport.stream(iterable.spliterator(), false).forEach(name -> {
                removed.stream().filter(old -> old.equals(name) || old.isAncestorOf(name)).forEach(old -> found.add(old.toString()));
                if (PROFILES.isAncestorOf(name) && name.getNumberOfElements() > PROFILES.getNumberOfElements() + 1
                        && REMOVED_PROFILE_FIELDS.contains(uniform(name.getElement(PROFILES.getNumberOfElements() + 1, ConfigurationPropertyName.Form.UNIFORM)))) {
                    found.add(name.toString());
                }
            });
        }
        if (!found.isEmpty()) {
            log.warn("These LLM configuration keys are no longer read and are ignored: {}. Configure endpoints and presets under artemis.llm and assign them with "
                    + "llm-preset / llm-presets keys; see {}", found.stream().collect(Collectors.joining(", ")), DOCUMENTATION);
        }
    }

    private static boolean isPrice(@Nullable Double value) {
        return value != null && Double.isFinite(value) && value >= 0;
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
        }
        catch (URISyntaxException e) {
            return false;
        }
    }

    private static String host(@Nullable String baseUrl) {
        try {
            return baseUrl == null ? "" : new URI(baseUrl).getHost();
        }
        catch (URISyntaxException e) {
            return "";
        }
    }

    private static String apiName(@Nullable Api api) {
        return api == null ? "" : api.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

}
