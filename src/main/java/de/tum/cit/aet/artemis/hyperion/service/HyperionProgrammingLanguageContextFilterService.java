package de.tum.cit.aet.artemis.hyperion.service;

import java.nio.charset.StandardCharsets;
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
 * Decides which repository files are worth putting into a Hyperion prompt.
 * <p>
 * A language without a registered strategy is filtered by the global rules alone, so an unknown language yields a smaller but never a wrong context.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionProgrammingLanguageContextFilterService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProgrammingLanguageContextFilterService.class);

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    public interface Strategy {

        ProgrammingLanguage language();

        Map<String, String> filter(Map<String, String> files);
    }

    private final Map<ProgrammingLanguage, Strategy> strategies = new EnumMap<>(ProgrammingLanguage.class);

    private static final int MAX_FILE_CHARACTERS = 100 * 1024;

    private static final List<String> GLOBAL_EXCLUSIONS = List.of("glob:{**/,}.git/**", "glob:{**/,}.idea/**", "glob:{**/,}.vscode/**", "glob:{**/,}.DS_Store", "glob:{**/,}bin/**",
            "glob:{**/,}obj/**", "glob:{**/,}out/**", "glob:{**/,}target/**", "glob:{**/,}build/**", "glob:{**/,}node_modules/**", "glob:{**/,}__pycache__/**",
            "glob:{**/,}dist/**", "glob:{**/,}coverage/**", "glob:**/*.class", "glob:**/*.jar", "glob:**/*.war", "glob:**/*.o", "glob:**/*.obj", "glob:**/*.dll", "glob:**/*.exe",
            "glob:**/*.so", "glob:**/*.dylib", "glob:**/*.db", "glob:**/*.sqlite", "glob:**/*.png", "glob:**/*.jpg", "glob:**/*.jpeg", "glob:**/*.svg", "glob:**/*.zip",
            "glob:**/*.tar.gz",
            // Artemis' own exports, listed twice each because "**/" does not match a file sitting at the repository root.
            "glob:exercise-details.json", "glob:**/exercise-details.json", "glob:problem-statement.md", "glob:**/problem-statement.md", "glob:Exercise-Details-*.json",
            "glob:**/Exercise-Details-*.json", "glob:Problem-Statement-*.md", "glob:**/Problem-Statement-*.md");

    /** Allowlist rather than a binary-extension blocklist: an unrecognised extension is dropped, so a new binary format cannot reach a prompt just because nobody excluded it. */
    private static final Set<String> SAFE_EXTENSIONS = Set.of(".java", ".py", ".c", ".h", ".cpp", ".hpp", ".cs", ".js", ".ts", ".html", ".css", ".scss", ".kt", ".swift", ".php",
            ".rb", ".go", ".rs", ".dart", ".asm", ".s", ".inc", ".vhd", ".vhdl", ".hs", ".ml", ".lua", ".pl", ".sh", ".bat", ".cmd", ".ps1", ".xml", ".json", ".yaml", ".yml",
            ".toml", ".properties", ".gradle", ".sql", ".ini", ".conf", ".config", ".md", ".txt", ".csv", ".adoc", ".rst");

    /** Extensionless files the allowlist above would otherwise drop. */
    private static final Set<String> SAFE_FILENAMES = Set.of("Dockerfile", "Makefile", "Jenkinsfile", "LICENSE", "LICENSE.md", "LICENSE.txt", "NOTICE", "CONTRIBUTING.md",
            "README.md");

    private static final Strategy DEFAULT_STRATEGY = new ExclusionStrategy(null, List.of());

    public HyperionProgrammingLanguageContextFilterService() {
        register(new ExclusionStrategy(ProgrammingLanguage.JAVA,
                List.of("glob:gradlew*", "glob:**/gradlew*", "glob:mvnw*", "glob:**/mvnw*", "glob:{**/,}.settings/**", "glob:{**/,}.classpath", "glob:{**/,}.project")));

        register(new ExclusionStrategy(ProgrammingLanguage.PYTHON,
                List.of("glob:{**/,}venv/**", "glob:{**/,}.venv/**", "glob:{**/,}env/**", "glob:{**/,}.env/**", "glob:**/*.pyc", "glob:**/*.egg-info/**")));

        register(new ExclusionStrategy(ProgrammingLanguage.C, List.of("glob:**/cmake-build-*/**", "glob:**/CMakeCache.txt")));

        register(new ExclusionStrategy(ProgrammingLanguage.SWIFT, List.of("glob:**/.swiftpm/**", "glob:**/Package.resolved")));
    }

    /**
     * Registers a strategy for its language, replacing any strategy already registered for it.
     *
     * @param strategy the strategy to register; ignored when it or its language is null
     * @return this filter, so registrations can be chained
     */
    public HyperionProgrammingLanguageContextFilterService register(Strategy strategy) {
        if (strategy != null && strategy.language() != null) {
            strategies.put(strategy.language(), strategy);
        }
        return this;
    }

    /**
     * Filters a file map for the given language, falling back to the global rules for a language with no registered strategy.
     *
     * @param files    path to file content
     * @param language the exercise's programming language, may be null
     * @return the entries worth putting into a prompt
     */
    public Map<String, String> filter(Map<String, String> files, ProgrammingLanguage language) {
        if (files == null || files.isEmpty()) {
            return Map.of();
        }
        Strategy strategy = strategies.getOrDefault(language, DEFAULT_STRATEGY);

        return strategy.filter(files);
    }

    private static final class ExclusionStrategy implements Strategy {

        private final ProgrammingLanguage language;

        private final List<PathMatcher> excludeMatchers;

        /** A null language yields the global-only strategy. */
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

                if (filePath == null) {
                    continue;
                }

                HyperionSecretMaterialPolicy.Assessment secretAssessment = SECRET_MATERIAL_POLICY.assess(filePath,
                        content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8), HyperionSecretMaterialPolicy.Origin.CLASSIC_CONTEXT);
                if (!secretAssessment.isSafe()) {
                    log.debug("Skipping Hyperion context file [{}]: {}", secretAssessment.category().orElseThrow(), secretAssessment.safePath());
                    continue;
                }

                Path path = Path.of(filePath);
                if (excludeMatchers.stream().anyMatch(matcher -> matcher.matches(path))) {
                    continue;
                }

                String fileName = path.getFileName().toString();
                int lastDotIndex = fileName.lastIndexOf('.');
                // A dotfile such as ".env" has its dot at index 0 and is treated as being all extension, which is what the allowlist should judge it on.
                String extension = lastDotIndex == -1 ? "" : fileName.substring(lastDotIndex);

                if (!SAFE_EXTENSIONS.contains(extension) && !SAFE_FILENAMES.contains(fileName)) {
                    log.debug("Skipping potentially binary or unknown file: {}", secretAssessment.safePath());
                    continue;
                }
                if (content != null && content.length() > MAX_FILE_CHARACTERS) {
                    continue;
                }

                result.put(filePath, content);
            }
            return result;
        }
    }

}
