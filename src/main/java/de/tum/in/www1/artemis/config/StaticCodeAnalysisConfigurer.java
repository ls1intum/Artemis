package de.tum.in.www1.artemis.config;

import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tum.in.www1.artemis.domain.StaticCodeAnalysisDefaultCategory;
import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;

/**
 * Provides hard-coded programming language specific static code analysis default categories as an unmodifiable Map
 */
@Configuration
public class StaticCodeAnalysisConfigurer {

    private final Logger log = LoggerFactory.getLogger(StaticCodeAnalysisConfigurer.class);

    private Map<ProgrammingLanguage, List<StaticCodeAnalysisDefaultCategory>> languageToDefaultCategories;

    public StaticCodeAnalysisConfigurer() {
    }

    @PostConstruct
    private void init() {
        languageToDefaultCategories = Map.of(ProgrammingLanguage.JAVA, createDefaultCategoriesForJava(), ProgrammingLanguage.SWIFT, createDefaultCategoriesForSwift(),
                ProgrammingLanguage.C, createDefaultCategoriesForC());

        log.debug("Initialized default static code analysis categories for: {}", languageToDefaultCategories.keySet());
    }

    /**
     * Create an unmodifiable List of default static code analysis categories for Java
     *
     * @return unmodifiable static code analysis categories
     */
    private List<StaticCodeAnalysisDefaultCategory> createDefaultCategoriesForJava() {
        return List.of(
                new StaticCodeAnalysisDefaultCategory("Bad Practice", 0.5D, 5D, CategoryState.FEEDBACK,
                        List.of(createMapping(StaticCodeAnalysisTool.SPOTBUGS, "BAD_PRACTICE"), createMapping(StaticCodeAnalysisTool.SPOTBUGS, "I18N"),
                                createMapping(StaticCodeAnalysisTool.PMD, "Best Practices"))),
                new StaticCodeAnalysisDefaultCategory("Code Style", 0.2D, 2D, CategoryState.FEEDBACK,
                        List.of(createMapping(StaticCodeAnalysisTool.SPOTBUGS, "STYLE"), createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "blocks"),
                                createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "coding"), createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "modifier"),
                                createMapping(StaticCodeAnalysisTool.PMD, "Code Style"))),
                new StaticCodeAnalysisDefaultCategory("Potential Bugs", 0.5D, 5D, CategoryState.FEEDBACK,
                        List.of(createMapping(StaticCodeAnalysisTool.SPOTBUGS, "CORRECTNESS"), createMapping(StaticCodeAnalysisTool.SPOTBUGS, "MT_CORRECTNESS"),
                                createMapping(StaticCodeAnalysisTool.PMD, "Error Prone"), createMapping(StaticCodeAnalysisTool.PMD, "Multithreading"))),
                new StaticCodeAnalysisDefaultCategory("Copy/Paste Detection", 1D, 5D, CategoryState.FEEDBACK,
                        List.of(createMapping(StaticCodeAnalysisTool.PMD_CPD, "Copy/Paste Detection"))),
                new StaticCodeAnalysisDefaultCategory("Security", 2.5D, 10D, CategoryState.FEEDBACK,
                        List.of(createMapping(StaticCodeAnalysisTool.SPOTBUGS, "MALICIOUS_CODE"), createMapping(StaticCodeAnalysisTool.SPOTBUGS, "SECURITY"),
                                createMapping(StaticCodeAnalysisTool.PMD, "Security"))),
                new StaticCodeAnalysisDefaultCategory("Performance", 1D, 2D, CategoryState.FEEDBACK,
                        List.of(createMapping(StaticCodeAnalysisTool.SPOTBUGS, "PERFORMANCE"), createMapping(StaticCodeAnalysisTool.PMD, "Performance"))),
                new StaticCodeAnalysisDefaultCategory("Design", 5D, 5D, CategoryState.FEEDBACK,
                        List.of(createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "design"), createMapping(StaticCodeAnalysisTool.PMD, "Design"))),
                new StaticCodeAnalysisDefaultCategory("Code Metrics", 0D, 0D, CategoryState.INACTIVE,
                        List.of(createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "metrics"), createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "sizes"))),
                new StaticCodeAnalysisDefaultCategory("Documentation", 0D, 0D, CategoryState.INACTIVE,
                        List.of(createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "javadoc"), createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "annotation"),
                                createMapping(StaticCodeAnalysisTool.PMD, "Documentation"))),
                new StaticCodeAnalysisDefaultCategory("Naming & Formatting", 0D, 0D, CategoryState.INACTIVE,
                        List.of(createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "imports"), createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "indentation"),
                                createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "naming"), createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "whitespace"))),
                new StaticCodeAnalysisDefaultCategory("Miscellaneous", 0D, 0D, CategoryState.INACTIVE,
                        List.of(createMapping(StaticCodeAnalysisTool.CHECKSTYLE, "miscellaneous"), createMapping(StaticCodeAnalysisTool.PMD, "miscellaneous"),
                                createMapping(StaticCodeAnalysisTool.SPOTBUGS, "miscellaneous"), createMapping(StaticCodeAnalysisTool.PMD_CPD, "miscellaneous"))));
    }

    /**
     * Create an unmodifiable List of default static code analysis categories for Swift
     *
     * @return unmodifiable static code analysis categories
     */
    private List<StaticCodeAnalysisDefaultCategory> createDefaultCategoriesForSwift() {
        return List.of(
                // TODO: rene: add correct category rules
                new StaticCodeAnalysisDefaultCategory("Code Style", 0.2D, 2D, CategoryState.FEEDBACK, List.of(createMapping(StaticCodeAnalysisTool.SWIFTLINT, "swiftLint"))),
                new StaticCodeAnalysisDefaultCategory("Idiomatic", 0D, 0D, CategoryState.INACTIVE, List.of(createMapping(StaticCodeAnalysisTool.SWIFTLINT, "n/a"))),
                new StaticCodeAnalysisDefaultCategory("Code Metrics", 0D, 0D, CategoryState.INACTIVE, List.of(createMapping(StaticCodeAnalysisTool.SWIFTLINT, "n/a"))),
                new StaticCodeAnalysisDefaultCategory("Lint", 0D, 0D, CategoryState.INACTIVE, List.of(createMapping(StaticCodeAnalysisTool.SWIFTLINT, "n/a"))),
                new StaticCodeAnalysisDefaultCategory("Performance", 0D, 0D, CategoryState.INACTIVE, List.of(createMapping(StaticCodeAnalysisTool.SWIFTLINT, "n/a"))),
                new StaticCodeAnalysisDefaultCategory("Miscellaneous", 0D, 0D, CategoryState.INACTIVE, List.of(createMapping(StaticCodeAnalysisTool.SWIFTLINT, "n/a"))));
    }

    /**
     * Create an unmodifiable List of default static code analysis categories for C
     *
     * @return unmodifiable static code analysis categories
     */
    private List<StaticCodeAnalysisDefaultCategory> createDefaultCategoriesForC() {
        return List.of(new StaticCodeAnalysisDefaultCategory("Bad Practice", 0.2D, 2D, CategoryState.FEEDBACK, List.of(createMapping(StaticCodeAnalysisTool.GCC, "BadPractice"))),
                new StaticCodeAnalysisDefaultCategory("Memory Management", 0.2D, 2D, CategoryState.FEEDBACK, List.of(createMapping(StaticCodeAnalysisTool.GCC, "Memory"))),
                new StaticCodeAnalysisDefaultCategory("Undefined Behavior", 0.2D, 2D, CategoryState.FEEDBACK,
                        List.of(createMapping(StaticCodeAnalysisTool.GCC, "UndefinedBehavior"))),
                new StaticCodeAnalysisDefaultCategory("Security", 0.2D, 2D, CategoryState.FEEDBACK, List.of(createMapping(StaticCodeAnalysisTool.GCC, "Security"))),
                new StaticCodeAnalysisDefaultCategory("Miscellaneous", 0.2D, 2D, CategoryState.INACTIVE, List.of(createMapping(StaticCodeAnalysisTool.GCC, "Misc"))));
    }

    @Bean(name = "staticCodeAnalysisConfiguration")
    public Map<ProgrammingLanguage, List<StaticCodeAnalysisDefaultCategory>> staticCodeAnalysisConfiguration() {
        return languageToDefaultCategories;
    }

    private StaticCodeAnalysisDefaultCategory.CategoryMapping createMapping(StaticCodeAnalysisTool tool, String category) {
        return new StaticCodeAnalysisDefaultCategory.CategoryMapping(tool, category);
    }
}
