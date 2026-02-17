package de.tum.cit.aet.artemis.hyperion.service;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Filters repository files for Hyperion context rendering.
 * <p>
 * The filter applies a language strategy if one is registered and falls back to a
 * global-only strategy otherwise. Each strategy combines global exclusions with
 * language-specific exclusions, then applies a safety net of allowed extensions and filenames,
 * and a size guard for large files.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionProgrammingLanguageContextFilterService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProgrammingLanguageContextFilterService.class);

    /** Pluggable strategy contract for language-specific file filtering. */
    public interface Strategy {

        ProgrammingLanguage language();

        Map<String, String> filter(Map<String, String> files);
    }

    private final Map<ProgrammingLanguage, Strategy> strategies = new EnumMap<>(ProgrammingLanguage.class);

    private static final Strategy DEFAULT_STRATEGY = new ExclusionStrategy(null, List.of());

    private static final long maxFileSizeKb = 100;

    /**
     * Default exclusions common to all languages (VCS, git, IDE configs, build outputs etc).
     */
    private static final List<String> GLOBAL_EXCLUSIONS = List.of("glob:**/.git/**", "glob:**/.idea/**", "glob:**/.vscode/**", "glob:**/.DS_Store", "glob:**/bin/**",
            "glob:**/obj/**", "glob:**/out/**", "glob:**/target/**", "glob:**/build/**", "glob:**/node_modules/**", "glob:**/__pycache__/**", "glob:**/*.class", "glob:**/*.jar",
            "glob:**/*.war", "glob:**/*.o", "glob:**/*.obj", "glob:**/*.dll", "glob:**/*.exe", "glob:**/*.so", "glob:**/*.dylib", "glob:**/*.db", "glob:**/*.sqlite",
            "glob:**/*.png", "glob:**/*.jpg", "glob:**/*.jpeg", "glob:**/*.svg", "glob:**/*.zip", "glob:**/*.tar.gz", "glob:**/exercise-details.json",
            "glob:**/Exercise-Details-*.json", "glob:**/problem-statement.md", "glob:**/Problem-Statement-*.md");

    /**
     * Safety net of text-based extensions that are allowed to pass.
     */
    private static final Set<String> SAFE_EXTENSIONS = Set.of(
            // Code
            ".java", ".py", ".c", ".h", ".cpp", ".hpp", ".cs", ".js", ".ts", ".html", ".css", ".scss", ".kt", ".swift", ".php", ".rb", ".go", ".rs", ".dart", ".asm", ".s", ".inc",
            ".vhd", ".vhdl", ".hs", ".ml", ".lua", ".pl", ".sh", ".bat", ".cmd", ".ps1",
            // Data / Config
            ".xml", ".json", ".yaml", ".yml", ".toml", ".properties", ".gradle", ".sql", ".ini", ".conf", ".config", ".env",
            // Docs
            ".md", ".txt", ".csv", ".adoc", ".rst");

    /**
     * Safety net of exact filenames that are allowed to pass.
     */
    private static final Set<String> SAFE_FILENAMES = Set.of("Dockerfile", "Makefile", "Jenkinsfile");

    public HyperionProgrammingLanguageContextFilterService() {
        register(new ExclusionStrategy(ProgrammingLanguage.JAVA, List.of("glob:**/gradlew*", "glob:**/mvnw*", "glob:**/.settings/**", "glob:**/.classpath", "glob:**/.project")));

        register(new ExclusionStrategy(ProgrammingLanguage.PYTHON,
                List.of("glob:**/venv/**", "glob:**/.venv/**", "glob:**/env/**", "glob:**/.env/**", "glob:**/*.pyc", "glob:**/*.egg-info/**")));

        register(new ExclusionStrategy(ProgrammingLanguage.C, List.of("glob:**/cmake-build-*/**", "glob:**/CMakeCache.txt")));

        register(new ExclusionStrategy(ProgrammingLanguage.ASSEMBLER, List.of())); // Global exclusions handle .o files

        register(new ExclusionStrategy(ProgrammingLanguage.SWIFT, List.of("glob:**/.swiftpm/**", "glob:**/Package.resolved")));

        register(new ExclusionStrategy(ProgrammingLanguage.SQL, List.of())); // Global exclusions handle .db files

        // ... Register others as needed
    }

    /**
     * Register or override a strategy for a language.
     *
     * @param strategy the strategy to register
     * @return this filter for chaining
     */
    public HyperionProgrammingLanguageContextFilterService register(Strategy strategy) {
        if (strategy != null && strategy.language() != null) {
            strategies.put(strategy.language(), strategy);
        }
        return this;
    }

    /**
     * Filter a file map according to the given language.
     * <p>
     * If no strategy is registered for the language, only the global exclusions
     * and safety net rules are applied.
     *
     * @param files    the map of path to content
     * @param language the programming language
     * @return a filtered view of the input map
     */
    public Map<String, String> filter(Map<String, String> files, ProgrammingLanguage language) {
        if (files == null || files.isEmpty()) {
            return Map.of();
        }
        Strategy strategy = strategies.getOrDefault(language, DEFAULT_STRATEGY);

        return strategy.filter(files);
    }

    /**
     * Applies combined exclusion patterns, a safety net on extensions, and a size guard.
     */
    private static final class ExclusionStrategy implements Strategy {

        private final ProgrammingLanguage language;

        private final List<PathMatcher> excludeMatchers;

        /**
         * Creates a strategy that merges global exclusions with language-specific ones.
         *
         * @param language           the programming language, or {@code null} for global-only
         * @param specificExclusions additional glob exclusions
         */
        public ExclusionStrategy(ProgrammingLanguage language, List<String> specificExclusions) {
            this.language = language;
            this.excludeMatchers = Stream.concat(GLOBAL_EXCLUSIONS.stream(), specificExclusions.stream()).map(pattern -> FileSystems.getDefault().getPathMatcher(pattern)).toList();
        }

        @Override
        public ProgrammingLanguage language() {
            return language;
        }

        @Override
        public Map<String, String> filter(Map<String, String> files) {
            Map<String, String> result = new LinkedHashMap<>();

            for (var entry : files.entrySet()) {
                String filePath = entry.getKey();
                String content = entry.getValue();

                if (filePath == null)
                    continue;

                // 1. Exclude based on Patterns (Global + Specific)
                Path pathObj = Path.of(filePath);
                if (excludeMatchers.stream().anyMatch(m -> m.matches(pathObj))) {
                    continue;
                }

                String fileName = pathObj.getFileName().toString();

                // 2. Binary Safety Net
                String extension = "";
                int lastDotIndex = fileName.lastIndexOf('.');
                // Extract extension (e.g., ".java") if a dot exists.
                // for ".env", lastIndex is 0, so extension becomes ".env", which also works
                if (lastDotIndex != -1) {
                    extension = fileName.substring(lastDotIndex);
                }
                boolean isSafeText = SAFE_EXTENSIONS.contains(extension) || SAFE_FILENAMES.contains(fileName);

                // 3. If not in the safety net, do a quick content check for non-text characters
                if (!isSafeText) {
                    log.debug("Skipping potentially binary or unknown file: {}", filePath);
                    continue;
                }
                // 4. File Size check
                if (content != null && content.length() > maxFileSizeKb * 1024) {
                    continue;
                }

                result.put(filePath, content);
            }
            return result;
        }
    }

}
