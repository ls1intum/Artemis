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
 * Filters repository files based on programming language conventions for context rendering.
 * Extensible via a simple strategy registry; currently ships with Java support.
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

    // Default exclusions common to ALL languages (git, IDE configs, basic build folders)
    private static final List<String> GLOBAL_EXCLUSIONS = List.of("glob:**/.git/**", "glob:**/.idea/**", "glob:**/.vscode/**", "glob:**/.DS_Store", "glob:**/bin/**",
            "glob:**/obj/**", "glob:**/out/**", "glob:**/target/**", "glob:**/build/**", "glob:**/node_modules/**", "glob:**/__pycache__/**", "glob:**/*.class", "glob:**/*.jar",
            "glob:**/*.war", "glob:**/*.o", "glob:**/*.obj", "glob:**/*.dll", "glob:**/*.exe", "glob:**/*.so", "glob:**/*.dylib", "glob:**/*.db", "glob:**/*.sqlite",
            "glob:**/*.png", "glob:**/*.jpg", "glob:**/*.jpeg", "glob:**/*.svg", "glob:**/*.zip", "glob:**/*.tar.gz");

    // "Safety Net": Broad list of text-based extensions we are willing to read
    private static final Set<String> SAFE_TEXT_EXTENSIONS = Set.of(
            // Code
            ".java", ".py", ".c", ".h", ".cpp", ".hpp", ".cs", ".js", ".ts", ".html", ".css", ".scss", ".kt", ".swift", ".php", ".rb", ".go", ".rs", ".dart", ".asm", ".s", ".inc",
            ".vhd", ".vhdl", ".hs", ".ml", ".lua", ".pl", ".sh", ".bat", ".cmd", ".ps1",
            // Data / Config
            ".xml", ".json", ".yaml", ".yml", ".toml", ".properties", ".gradle", ".sql", ".ini", ".conf", ".config", ".env", "Dockerfile", "Makefile", "Jenkinsfile",
            // Docs
            ".md", ".txt", ".csv", ".adoc", ".rst");

    public HyperionProgrammingLanguageContextFilterService() {
        register(new ExclusionStrategy(ProgrammingLanguage.JAVA,
                List.of("glob:**/gradlew*", "glob:**/mvnw*", "glob:**/.settings/**", "glob:**/.classpath", "glob:**/.project", "glob:**/exercise-details.json")));

        register(new ExclusionStrategy(ProgrammingLanguage.PYTHON, List.of("glob:**/venv/**", "glob:**/.venv/**", "glob:**/env/**", "glob:**/*.pyc", "glob:**/*.egg-info/**")));

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
        Strategy strategy = strategies.get(language);
        if (strategy == null) {
            // Applies only global exclusions + safety net
            strategy = new ExclusionStrategy(null, List.of());
        }
        return strategy.filter(files);
    }

    private static final class ExclusionStrategy implements Strategy {

        private final ProgrammingLanguage language;

        private final List<PathMatcher> excludeMatchers;

        private final long maxFileSizeKb = 100;

        public ExclusionStrategy(ProgrammingLanguage language, List<String> specificExclusions) {
            this.language = language;
            // Merge GLOBAL exclusions with language-specific ones
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

                // 2. Binary Safety Net: Check Extension OR Filename
                boolean isSafeText = SAFE_TEXT_EXTENSIONS.stream().anyMatch(filePath::endsWith);
                if (!isSafeText) {
                    log.debug("Skipping potentially binary or unknown file: {}", filePath);
                    continue;
                }
                // 3. File Size check
                if (content != null && content.length() > maxFileSizeKb * 1024) {
                    continue;
                }

                result.put(filePath, content);
            }
            return result;
        }
    }

}
