package de.tum.cit.aet.artemis.metis.service;

import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Decides which changed files of a student's repository are no own code to ask about: version control, IDE and tool metadata, build output and dependencies,
 * build files, tests, exercise metadata and documentation.
 * <p>
 * Every pattern is a glob over the path of the file in the repository. Most match wherever the directory or the file sits: {@code test/**} excludes
 * {@code test/SortTest.java} as well as {@code src/test/java/SortTest.java}, and {@code *.md} excludes a Markdown file in any directory. Build output only
 * matches where build tools write it, at the root or one directory below it: {@code build/**} excludes {@code build/generated/Parser.java} and
 * {@code app/build/generated/Parser.java} but keeps {@code src/de/tum/build/Builder.java} of a package named {@code build}.
 * <p>
 * The globs are matched against the path as text, with {@code /} as the only separator: matching is case-sensitive like the paths in git, and does not depend on
 * the file system or the locale of the server. In a glob, {@code **} matches any characters including {@code /}, {@code *} and {@code ?} match any characters
 * and one character within a directory or file name, and {@code {a,b}} matches one of the alternatives.
 */
public final class MetisChangedFileFilter {

    /** Lets a pattern match in any directory: either nothing or any path ending with a slash comes before it. */
    private static final String IN_ANY_DIRECTORY = "{**/,}";

    /** Lets a pattern match at the root of the repository or in a directory directly below it, where a build tool puts the output of a project or a module. */
    private static final String AT_THE_ROOT_OR_IN_A_MODULE = "{,*/}";

    /** Version control, IDE, formatter and linter metadata. */
    private static final String VERSION_CONTROL_AND_TOOLS = """
            .git/**
            .idea/**
            .vscode/**
            .settings/**
            .classpath
            .project
            .DS_Store
            .gitignore
            .gitattributes
            .editorconfig
            *.xcodeproj/**
            *.xcworkspace/**
            .clang-format
            .ocamlformat
            .rubocop.yml
            analysis_options.yaml
            """;

    /** Dependencies and caches, wherever they sit. */
    private static final String DEPENDENCIES_AND_CACHES = """
            node_modules/**
            __pycache__/**
            venv/**
            .venv/**
            .gradle/**
            cmake-build-*/**
            """;

    /**
     * Build output, matched only at the root or one directory below it: source directories of the same name exist, for example a package named {@code build}.
     * The output of .NET is matched by its configuration directories, since the Dart template keeps its source code in {@code bin/}; compiled Java and .NET
     * files are binary and therefore left out anyway.
     */
    private static final String BUILD_OUTPUT = """
            build/**
            out/**
            target/**
            dist/**
            obj/**
            coverage/**
            bin/Debug/**
            bin/Release/**
            """;

    /** Build files and lock files of the build tools and package managers of the exercise templates. */
    private static final String BUILD_FILES = """
            build.gradle
            build.gradle.kts
            settings.gradle
            settings.gradle.kts
            gradle.properties
            gradlew
            gradlew.bat
            gradle/wrapper/**
            pom.xml
            mvnw
            mvnw.cmd
            .mvn/**
            Makefile
            CMakeLists.txt
            package.json
            package-lock.json
            pnpm-lock.yaml
            yarn.lock
            tsconfig.json
            requirements.txt
            Cargo.toml
            Cargo.lock
            go.mod
            go.sum
            Package.swift
            Package.resolved
            *.csproj
            *.sln
            pubspec.yaml
            pubspec.lock
            Gemfile
            Gemfile.lock
            stack.yaml
            stack.yaml.lock
            *.cabal
            dune
            dune-project
            *.opam
            DESCRIPTION
            NAMESPACE
            """;

    /** Test directories and test files. */
    private static final String TESTS = """
            test/**
            tests/**
            __tests__/**
            spec/**
            *Test.java
            *Tests.java
            *Test.kt
            test_*.py
            *_test.py
            *_test.go
            *.spec.ts
            *.test.ts
            *.spec.js
            *.test.js
            """;

    /** Exercise metadata, including the files of an exported exercise. */
    private static final String EXERCISE_METADATA = """
            problem-statement.md
            exercise-details.json
            Exercise-Details-*.json
            Problem-Statement-*.md
            """;

    /** Documentation and plain text. */
    private static final String DOCUMENTATION = """
            *.md
            *.txt
            *.adoc
            *.rst
            """;

    /** Every glob as one alternative of one expression; {@code DOTALL} lets {@code **} also cross a line break, which git allows in a path. */
    private static final Pattern EXCLUDED_PATH;

    static {
        Stream<String> inAnyDirectory = withPrefix(IN_ANY_DIRECTORY, VERSION_CONTROL_AND_TOOLS, DEPENDENCIES_AND_CACHES, BUILD_FILES, TESTS, EXERCISE_METADATA, DOCUMENTATION);
        Stream<String> atTheRootOrInAModule = withPrefix(AT_THE_ROOT_OR_IN_A_MODULE, BUILD_OUTPUT);
        String expression = Stream.concat(inAnyDirectory, atTheRootOrInAModule).map(MetisChangedFileFilter::toRegularExpression).collect(Collectors.joining("|"));
        EXCLUDED_PATH = Pattern.compile(expression, Pattern.DOTALL);
    }

    private MetisChangedFileFilter() {
    }

    /**
     * Whether a changed file is no own code of the student, because it is metadata, build output, a dependency, a build file, a test, exercise metadata or
     * documentation.
     *
     * @param path the path of the file in the repository, with forward slashes
     * @return true if the file is left out of the own-code diff
     */
    public static boolean isExcluded(String path) {
        return EXCLUDED_PATH.matcher(path).matches();
    }

    /**
     * The globs of the given lists, one per line, each with the prefix that says where it matches.
     */
    private static Stream<String> withPrefix(String prefix, String... globLists) {
        return Stream.of(globLists).flatMap(String::lines).map(glob -> prefix + glob);
    }

    /**
     * Translates a glob into a regular expression: {@code **} becomes any characters, {@code *} any characters other than {@code /}, {@code ?} one character
     * other than {@code /}, {@code {a,b}} a group of alternatives, and every other character stands for itself. A backslash makes a character that is no
     * letter or digit literal; Java allows it before any such character.
     */
    private static String toRegularExpression(String glob) {
        StringBuilder expression = new StringBuilder("(?:");
        boolean inAlternatives = false;
        int index = 0;
        while (index < glob.length()) {
            char character = glob.charAt(index);
            if (glob.startsWith("**", index)) {
                expression.append(".*");
                index += 2;
                continue;
            }
            switch (character) {
                case '*' -> expression.append("[^/]*");
                case '?' -> expression.append("[^/]");
                case '{' -> {
                    inAlternatives = true;
                    expression.append("(?:");
                }
                case '}' -> {
                    inAlternatives = false;
                    expression.append(')');
                }
                case ',' -> expression.append(inAlternatives ? "|" : "\\,");
                default -> {
                    if (!Character.isLetterOrDigit(character)) {
                        expression.append('\\');
                    }
                    expression.append(character);
                }
            }
            index++;
        }
        return expression.append(')').toString();
    }
}
