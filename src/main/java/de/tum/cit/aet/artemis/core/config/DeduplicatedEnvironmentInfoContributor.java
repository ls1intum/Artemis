package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Contributes everything under the {@code info.*} configuration namespace to the /management/info endpoint, with duplicate case-variants of the same property collapsed
 * into a single camelCase key.
 * <p>
 * <b>Why this exists.</b> Spring Boot ships a built-in {@code EnvironmentInfoContributor} (enabled via {@code management.info.env.enabled}) that map-binds the entire
 * {@code info.*} namespace from <em>every</em> property source onto the endpoint. That contributor emits the same logical property twice whenever it is defined both in
 * YAML and overridden by an environment variable:
 * <ul>
 * <li>The YAML {@code info:} blocks define keys in camelCase, e.g. {@code info.testServer}, which the binder keeps verbatim &rarr; JSON key {@code testServer}.</li>
 * <li>Deployments override the same property with an uppercase environment variable, e.g. {@code INFO_TESTSERVER}. Spring's environment-variable &rarr; property-name
 * mapping is <em>case-lossy</em>: {@code INFO_TESTSERVER} can only ever map to the all-lowercase canonical name {@code info.testserver} (camelCase is unrecoverable from
 * an env-var name) &rarr; JSON key {@code testserver}.</li>
 * </ul>
 * Because {@code testServer} and {@code testserver} are distinct map keys, both end up in the response (both carrying the env-override value, since the env var outranks
 * the YAML default for either spelling). This affected {@code testServer}, {@code operatorName}, {@code textAssessmentAnalyticsEnabled} and
 * {@code studentExamStoreSessionData} — exactly the properties that are set via {@code INFO_*} env vars in the (multi-node / Playwright) run configs and on the
 * production server.
 * <p>
 * <b>Why output-normalization rather than unifying the inputs.</b> The inputs cannot be cleanly unified: an env var can never carry camelCase, so they will always
 * disagree in spelling. Renaming the YAML keys to all-lowercase would be a breaking change to the endpoint's JSON contract (the Angular client reads the camelCase keys)
 * and would silently break the case-sensitive {@code @Value("${info.testServer}")} placeholder lookups. Instead this contributor replaces the built-in one (which is
 * therefore disabled via {@code management.info.env.enabled: false}) and normalizes the <em>output</em>: it performs the same {@code info.*} map binding, then keeps the
 * camelCase spelling and drops the all-lowercase duplicate. New {@code info.*} properties are still exposed automatically with no per-property code.
 *
 * @see "src/main/resources/config/application.yml (management.info.env.enabled)"
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class DeduplicatedEnvironmentInfoContributor implements InfoContributor {

    private final Environment environment;

    public DeduplicatedEnvironmentInfoContributor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Bindable<Map<String, Object>> target = Bindable.mapOf(String.class, Object.class);
        Map<String, Object> info = Binder.get(environment).bind("info", target).orElseGet(LinkedHashMap::new);
        deduplicateCaseVariants(info).forEach(builder::withDetail);
    }

    /**
     * Collapses keys that differ only by case. When both a mixed-case key (the camelCase spelling from YAML, e.g. {@code testServer}) and an all-lowercase key (the
     * spelling produced by an {@code INFO_*} environment variable, e.g. {@code testserver}) exist for the same logical property, the mixed-case key is kept — it is the
     * contract the Angular client consumes — and the all-lowercase duplicate is dropped. A property that exists <em>only</em> in its all-lowercase form (set purely via
     * an env var, with no YAML default) keeps that lowercase key. Recurses into nested maps (e.g. {@code sentry}).
     *
     * @param source the bound {@code info.*} map, potentially containing case-variant duplicates
     * @return a map with case-variant duplicates collapsed to their preferred (camelCase) spelling
     */
    private static Map<String, Object> deduplicateCaseVariants(Map<String, Object> source) {
        Map<String, String> preferredKeyByLowerCase = new LinkedHashMap<>();
        for (String key : source.keySet()) {
            String lowerCaseKey = key.toLowerCase(Locale.ROOT);
            String existing = preferredKeyByLowerCase.get(lowerCaseKey);
            // Prefer the spelling containing upper-case letters (camelCase from YAML) over the all-lower-case spelling (which originates from an INFO_* env var).
            if (existing == null || (!containsUpperCase(existing) && containsUpperCase(key))) {
                preferredKeyByLowerCase.put(lowerCaseKey, key);
            }
        }
        Map<String, Object> deduplicated = new LinkedHashMap<>();
        for (String preferredKey : preferredKeyByLowerCase.values()) {
            Object value = source.get(preferredKey);
            if (value instanceof Map<?, ?> nestedMap) {
                deduplicated.put(preferredKey, deduplicateCaseVariants(castToStringObjectMap(nestedMap)));
            }
            else {
                deduplicated.put(preferredKey, value);
            }
        }
        return deduplicated;
    }

    private static boolean containsUpperCase(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isUpperCase(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToStringObjectMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
