package de.tum.cit.aet.artemis.core.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisDefaultCategory;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.ParserPolicy;
import de.tum.cit.aet.artemis.programming.service.localci.scaparser.strategy.sarif.ClippyCategorizer;

/**
 * Provides hard-coded programming language specific static code analysis default categories as an unmodifiable Map
 */
public class StaticCodeAnalysisConfigurer {

    // @formatter:off
    private static final List<String> CATEGORY_NAMES_RUFF = List.of(
        "Pyflakes",
        "pycodestyle",
        "mccabe",
        "isort",
        "pep8-naming",
        "pydocstyle",
        "pyupgrade",
        "flake8-2020",
        "flake8-annotations",
        "flake8-async",
        "flake8-bandit",
        "flake8-blind-except",
        "flake8-boolean-trap",
        "flake8-bugbear",
        "flake8-builtins",
        "flake8-commas",
        "flake8-copyright",
        "flake8-comprehensions",
        "flake8-datetimez",
        "flake8-debugger",
        "flake8-django",
        "flake8-errmsg",
        "flake8-executable",
        "flake8-future-annotations",
        "flake8-implicit-str-concat",
        "flake8-import-conventions",
        "flake8-logging",
        "flake8-logging-format",
        "flake8-no-pep420",
        "flake8-pie",
        "flake8-print",
        "flake8-pyi",
        "flake8-pytest-style",
        "flake8-quotes",
        "flake8-raise",
        "flake8-return",
        "flake8-self",
        "flake8-slots",
        "flake8-simplify",
        "flake8-tidy-imports",
        "flake8-type-checking",
        "flake8-gettext",
        "flake8-unused-arguments",
        "flake8-use-pathlib",
        "flake8-todos",
        "flake8-fixme",
        "eradicate",
        "pandas-vet",
        "pygrep-hooks",
        "Pylint",
        "tryceratops",
        "flynt",
        "NumPy-specific rules",
        "FastAPI",
        "Airflow",
        "Perflint",
        "refurb",
        "pydoclint",
        "Ruff-specific rules",
        "Unknown"
    );
    // @formatter:on

    private static final List<String> CATEGORY_NAMES_RUBOCOP = List.of("Bundler", "Gemspec", "Layout", "Lint", "Metrics", "Migration", "Naming", "Security", "Style");

    private static final List<String> CATEGORY_NAMES_DART_ANALYZE = List.of("TODO", "HINT", "COMPILE_TIME_ERROR", "CHECKED_MODE_COMPILE_TIME_ERROR", "STATIC_WARNING",
            "SYNTACTIC_ERROR", "LINT");

    // @formatter:off
    private static final Map<ProgrammingLanguage, List<StaticCodeAnalysisDefaultCategory>> languageToDefaultCategories;

    static {
        Map<ProgrammingLanguage, List<StaticCodeAnalysisDefaultCategory>> map = new EnumMap<>(ProgrammingLanguage.class);
        map.put(ProgrammingLanguage.C, createDefaultCategoriesForC());
        map.put(ProgrammingLanguage.DART, createDefaultCategoriesSingleTool(CATEGORY_NAMES_DART_ANALYZE, StaticCodeAnalysisTool.DART_ANALYZE));
        map.put(ProgrammingLanguage.JAVA, createDefaultCategoriesForJava());
        map.put(ProgrammingLanguage.JAVASCRIPT, createDefaultCategoriesSingleTool(List.of(ParserPolicy.GENERIC_LINT_CATEGORY), StaticCodeAnalysisTool.ESLINT));
        map.put(ProgrammingLanguage.PYTHON, createDefaultCategoriesSingleTool(CATEGORY_NAMES_RUFF, StaticCodeAnalysisTool.RUFF));
        map.put(ProgrammingLanguage.R, createDefaultCategoriesSingleTool(List.of(ParserPolicy.GENERIC_LINT_CATEGORY), StaticCodeAnalysisTool.LINTR));
        map.put(ProgrammingLanguage.RUBY, createDefaultCategoriesSingleTool(CATEGORY_NAMES_RUBOCOP, StaticCodeAnalysisTool.RUBOCOP));
        map.put(ProgrammingLanguage.RUST, createDefaultCategoriesSingleTool(ClippyCategorizer.CATEGORY_NAMES, StaticCodeAnalysisTool.CLIPPY));
        map.put(ProgrammingLanguage.SWIFT, createDefaultCategoriesForSwift());
        map.put(ProgrammingLanguage.TYPESCRIPT, createDefaultCategoriesSingleTool(List.of(ParserPolicy.GENERIC_LINT_CATEGORY), StaticCodeAnalysisTool.ESLINT));

        languageToDefaultCategories = Collections.unmodifiableMap(map);
    }
    // @formatter:on

    /**
     * Create an unmodifiable List of default static code analysis categories for Java
     *
     * @return unmodifiable static code analysis categories
     */
    private static List<StaticCodeAnalysisDefaultCategory> createDefaultCategoriesForJava() {
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
    private static List<StaticCodeAnalysisDefaultCategory> createDefaultCategoriesForSwift() {
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
    private static List<StaticCodeAnalysisDefaultCategory> createDefaultCategoriesForC() {
        return List.of(new StaticCodeAnalysisDefaultCategory("Bad Practice", 0.2D, 2D, CategoryState.FEEDBACK, List.of(createMapping(StaticCodeAnalysisTool.GCC, "BadPractice"))),
                new StaticCodeAnalysisDefaultCategory("Memory Management", 0.2D, 2D, CategoryState.FEEDBACK, List.of(createMapping(StaticCodeAnalysisTool.GCC, "Memory"))),
                new StaticCodeAnalysisDefaultCategory("Undefined Behavior", 0.2D, 2D, CategoryState.FEEDBACK,
                        List.of(createMapping(StaticCodeAnalysisTool.GCC, "UndefinedBehavior"))),
                new StaticCodeAnalysisDefaultCategory("Security", 0.2D, 2D, CategoryState.FEEDBACK, List.of(createMapping(StaticCodeAnalysisTool.GCC, "Security"))),
                new StaticCodeAnalysisDefaultCategory("Miscellaneous", 0.2D, 2D, CategoryState.INACTIVE, List.of(createMapping(StaticCodeAnalysisTool.GCC, "Misc"))));
    }

    private static List<StaticCodeAnalysisDefaultCategory> createDefaultCategoriesSingleTool(List<String> categories, StaticCodeAnalysisTool tool) {
        return categories.stream().map(name -> new StaticCodeAnalysisDefaultCategory(name, 0.0, 1.0, CategoryState.FEEDBACK, List.of(createMapping(tool, name)))).toList();
    }

    public static Map<ProgrammingLanguage, List<StaticCodeAnalysisDefaultCategory>> staticCodeAnalysisConfiguration() {
        return languageToDefaultCategories;
    }

    private static StaticCodeAnalysisDefaultCategory.CategoryMapping createMapping(StaticCodeAnalysisTool tool, String category) {
        return new StaticCodeAnalysisDefaultCategory.CategoryMapping(tool, category);
    }
}
