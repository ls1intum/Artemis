package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Filters repository files based on programming language conventions for context rendering.
 * Extensible via a simple strategy registry; currently ships with Java support.
 */
@Component
@Profile(PROFILE_HYPERION)
public class ProgrammingLanguageContextFilter {

    /** Pluggable strategy contract for language-specific file filtering. */
    public interface Strategy {

        ProgrammingLanguage language();

        Map<String, String> filter(Map<String, String> files);
    }

    private final Map<ProgrammingLanguage, Strategy> strategies = new EnumMap<>(ProgrammingLanguage.class);

    private static final Strategy IDENTITY = new Strategy() {

        @Override
        public ProgrammingLanguage language() {
            return null;
        }

        @Override
        public Map<String, String> filter(Map<String, String> files) {
            return files == null ? Map.of() : files;
        }
    };

    public ProgrammingLanguageContextFilter() {
        // Register built-ins
        register(new JavaStrategy());
    }

    /**
     * Register or override a strategy for a language.
     *
     * @param strategy the strategy to register
     * @return this filter for chaining
     */
    public ProgrammingLanguageContextFilter register(Strategy strategy) {
        if (strategy != null && strategy.language() != null) {
            strategies.put(strategy.language(), strategy);
        }
        return this;
    }

    /**
     * Filter a files map according to the given language.
     *
     * @param files    the map of path to content
     * @param language the programming language
     * @return a filtered view of the input map
     */
    public Map<String, String> filter(Map<String, String> files, ProgrammingLanguage language) {
        if (files == null || files.isEmpty()) {
            return Map.of();
        }
        if (language == null) {
            return files;
        }
        Strategy strategy = strategies.getOrDefault(language, IDENTITY);
        return strategy.filter(files);
    }

    /** Java language-aware filtering (src/* .java, no hidden, <= ~50KB). */
    private static final class JavaStrategy implements Strategy {

        @Override
        public ProgrammingLanguage language() {
            return ProgrammingLanguage.JAVA;
        }

        @Override
        public Map<String, String> filter(Map<String, String> files) {
            Map<String, String> result = new LinkedHashMap<>();
            for (var e : files.entrySet()) {
                String p = e.getKey();
                String content = e.getValue();
                if (p == null || !p.endsWith(".java")) {
                    continue;
                }
                List<String> parts = Arrays.asList(p.split("/"));
                if (parts.stream().noneMatch("src"::equals)) {
                    continue;
                }
                if (parts.stream().anyMatch(s -> s.startsWith("."))) {
                    continue;
                }
                if (content != null && content.length() > 50 * 1024) {
                    continue;
                }
                result.put(p, content);
            }
            return result;
        }
    }
}
