package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Reads the syntax of the build files of a tests repository as far as the Security Framework needs it: which parts are comments, strings or Maven
 * profiles, so that a pinned Ares version, the Ares agent or a library in a comment is not mistaken for code, see {@link SecurityPolicyRepositoryFiles}.
 */
final class BuildFileSyntax {

    /**
     * The Groovy keywords after which an expression, and therefore a slashy string, may follow; any other word is an operand, such as a variable,
     * {@code this} or {@code null}.
     */
    private static final Set<String> GROOVY_KEYWORDS_BEFORE_EXPRESSIONS = Set.of("return", "throw", "assert", "case", "in", "yield", "else", "do");

    /** The name of a configuration that {@code add} of the dependency handler takes, e.g. {@code add('aresAgent', ...)}, as group 2. */
    private static final String ADDED_TO_CONFIGURATION = "(?<![\\w$])add\\s*+\\(\\s*+['\"]([A-Za-z_$][\\w$]*+)['\"]\\s*+,";

    /**
     * The Ares agent as a Gradle dependency of a configuration, e.g. {@code aresAgent "de.tum.cit.ase:ares:${aresVersion}:agent"} or
     * {@code dependencies.add('aresAgent', "de.tum.cit.ase:ares:${aresVersion}:agent")}; the configuration is group 1 or 2, see {@link #configurationOf}.
     */
    private static final Pattern GRADLE_ARES_AGENT = Pattern
            .compile("(?:([A-Za-z_$][\\w$]*+)\\s*+\\(?|" + ADDED_TO_CONFIGURATION + ")\\s*+['\"]de\\.tum\\.cit\\.ase:ares:[^'\"\\s]+:agent['\"]");

    /** The variable, property or object an interpolation in a Gradle string starts with, e.g. {@code agentJar} in {@code ${agentJar.singleFile}}. */
    private static final Pattern INTERPOLATED_NAME = Pattern.compile("\\$\\{?\\s*+([A-Za-z_]\\w*+)");

    /** How a statement gives a variable or property its value right after its name: an assignment or a call such as {@code .from(...)}. */
    private static final Pattern TAKES_VALUE = Pattern.compile("\\s*+(?:\\+?=(?!=)|\\.\\s*+(?:from|setFrom|set|convention)\\s*+\\()");

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*+");

    /** A reference to a Gradle configuration, e.g. {@code configurations.aresAgent}. */
    private static final Pattern CONFIGURATION_REFERENCE = Pattern.compile("configurations\\s*+\\.\\s*+([A-Za-z_$][\\w$]*+)");

    /** A dependency written after the name of its configuration, e.g. {@code aresAgent "..."} or {@code agent("...")}; group 1 is the configuration. */
    private static final String NAMED_DEPENDENCY = "(?!add\\s*+\\()([A-Za-z_$][\\w$]*+)[ \\t]*+(?:\\(\\s*+)?(?:['\"]|\\w++[ \\t]*+[(:])";

    /** A dependency that {@code add} of the dependency handler declares, e.g. {@code dependencies.add('aresAgent', "...")}; group 2 is the configuration. */
    private static final String ADDED_DEPENDENCY = "(?:dependencies\\s*+\\.\\s*+)?" + ADDED_TO_CONFIGURATION;

    /**
     * A dependency declaration at the start of a line, after a semicolon or after the brace that opens a block, e.g. {@code aresAgent "de.tum.cit.ase:ares:1.0:agent"}
     * or {@code dependencies { agent("de.tum.cit.ase:ares:1.0:agent") }} or {@code add('agent', "de.tum.cit.ase:ares:1.0:agent")}, with the name of its
     * configuration as group 1 or 2, see {@link #configurationOf}; the arguments in parentheses may start on the next line.
     */
    private static final Pattern DEPENDENCY_DECLARATION = Pattern.compile("(?m)(?:^|[;{])[ \\t]*+(?:" + NAMED_DEPENDENCY + "|" + ADDED_DEPENDENCY + ")");

    /** An assignment of an empty file collection. */
    private static final Pattern EMPTY_COLLECTION = Pattern.compile("\\s*+=\\s*+(?:files|objects\\s*+\\.\\s*+fileCollection)\\s*+\\(\\s*+\\)\\s*+");

    /** An interpolation in a Gradle or Maven string, e.g. {@code ${agentJar.singleFile}} or {@code $agentJar}, also one that is not closed. */
    private static final Pattern INTERPOLATION = Pattern.compile("\\$\\{[^}'\"\\n]*+}?|\\$[A-Za-z_]\\w*+");

    /** The last characters of a line after which a Gradle statement continues on the next line. */
    private static final String CONTINUING_ENDS = "=(,.+:";

    /** Ares at the start of a word of a file name, e.g. in {@code ares-agent.jar}, but not in {@code shares.jar}. */
    private static final Pattern ARES_IN_FILE_NAME = Pattern.compile("(?<![a-z])ares");

    /** The argument of a {@code -javaagent} option; group 1 is the agent, up to the end of the string or argument. */
    private static final Pattern JAVA_AGENT = Pattern.compile("-javaagent:((?:\\$\\{[^}'\"\\n]*+}?|[^'\"\\s,)<$]|\\$(?!\\{))*+)");

    /** The Ares agent as a Maven artifact: the {@code agent} classifier of the {@code ares} artifact. */
    private static final Pattern MAVEN_ARES_AGENT = Pattern
            .compile("<artifactId>\\s*ares\\s*</artifactId>\\s*(<version>[^<]*</version>\\s*)?(<type>[^<]*</type>\\s*)?<classifier>\\s*agent\\s*</classifier>");

    /** The {@code forkCount} of a Surefire configuration or its property; group 1 is its value, e.g. {@code 0}, {@code 1C} or {@code ${test.forks}}. */
    private static final Pattern MAVEN_FORK_COUNT = Pattern.compile("<forkCount\\s*+>([^<]*+)</forkCount\\s*+>");

    /** A {@code forkCount} of 0, also as {@code 0C}, a multiple of the number of cores: Surefire then runs the tests in the Maven JVM and ignores its {@code argLine}. */
    private static final Pattern IN_PROCESS_FORK_COUNT = Pattern.compile("\\s*+0++(?:\\.0*+)?C?\\s*+");

    private static final String ARES_1_ARTIFACT = "artemis-java-test-sandbox";

    /** The Ares 1 library in a Gradle dependency, e.g. {@code 'de.tum.in.ase:artemis-java-test-sandbox:1.13.0'} or {@code name: 'artemis-java-test-sandbox'}. */
    private static final Pattern GRADLE_ARES_1_DEPENDENCY = Pattern.compile("[:'\"]\\s*+" + ARES_1_ARTIFACT + "\\s*+[:'\"]");

    /** The Ares 1 library as the artifact of a Maven dependency. */
    private static final Pattern MAVEN_ARES_1_DEPENDENCY = Pattern.compile("<artifactId>\\s*+" + ARES_1_ARTIFACT + "\\s*+</artifactId>");

    /** A keyword or type right before a Gradle variable that declares it, e.g. {@code def}, matched at the end of the code before the variable. */
    private static final Pattern GRADLE_DECLARATION_BEFORE = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})(?:def|var|final|String|Object)\\s++$");

    /** A closed Gradle block comment, e.g. {@code /* Ares 2 *}{@code /}, also one that spans several lines. */
    private static final String GRADLE_BLOCK_COMMENT = "/\\*(?:[^*]|\\*(?!/))*+\\*/";

    /**
     * The Ares version as a complete string literal on the right of {@code aresVersion =}, e.g. {@code aresVersion = '2.2.1'}. An expression such as
     * {@code '2.2' + '.1'} or an interpolation such as {@code "${base}.1"} pins no version Artemis could read or replace, so it is no pin. Block comments
     * after the literal are skipped, so {@code '2.2.' /* note *}{@code / + '1'} is an expression as well.
     */
    private static final Pattern GRADLE_ARES_VERSION = Pattern
            .compile("(?<!\\p{javaJavaIdentifierPart})(aresVersion\\s*=\\s*['\"])([^'\"$]+)(['\"])(?=(?:[ \\t]|" + GRADLE_BLOCK_COMMENT + ")*+(?:[;}\\r\\n]|//|$))");

    /** The Ares version as the text of {@code <ares.version>}; a reference to another property, e.g. {@code ${base.version}}, is no pin. */
    private static final Pattern MAVEN_ARES_VERSION = Pattern.compile("(<ares\\.version\\s*+>)([^<$]+)(</ares\\.version\\s*+>)");

    /** Any assignment of the Ares version in Gradle, whatever its value, e.g. also {@code aresVersion = "${base}.1"}; {@code ==} compares and is none. */
    private static final Pattern GRADLE_ARES_VERSION_ASSIGNMENT = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})aresVersion\\s*+=(?!=)");

    /**
     * Any definition of the Ares version in a POM, whatever its value, e.g. also {@code <ares.version>${next.ares.version}</ares.version>}; XML allows
     * whitespace before the {@code >} of both tags.
     */
    private static final Pattern MAVEN_ARES_VERSION_DEFINITION = Pattern
            .compile("<ares\\.version\\s*+(?:/>|>(?:[^<]|<!\\[CDATA\\[(?:[^\\]]|](?!]>))*+]]>)*+</ares\\.version\\s*+>)");

    /** The start tag of the profiles of a POM; XML allows whitespace before its {@code >}. */
    private static final Pattern MAVEN_PROFILES_START = Pattern.compile("<profiles\\s*+>");

    private static final Pattern MAVEN_PROFILES_END = Pattern.compile("</profiles\\s*+>");

    /** A single profile of a POM, from its start tag to its end tag. */
    private static final Pattern MAVEN_PROFILE = Pattern.compile("<profile\\s*+>.*?</profile\\s*+>", Pattern.DOTALL);

    /** The activation of a profile, e.g. through {@code activeByDefault} or a property, which can make the profile apply without being named in the build. */
    private static final Pattern MAVEN_ACTIVATION = Pattern.compile("<activation\\s*+>");

    /** A plugin of a POM, from its start tag to its end tag. */
    private static final Pattern MAVEN_PLUGIN = Pattern.compile("<plugin\\s*+>.*?</plugin\\s*+>", Pattern.DOTALL);

    private static final Pattern MAVEN_SUREFIRE = Pattern.compile("<artifactId>\\s*+maven-surefire-plugin\\s*+</artifactId>");

    /** The executions of a plugin, from their start tag to their end tag. */
    private static final Pattern MAVEN_EXECUTIONS = Pattern.compile("<executions\\s*+>.*?</executions\\s*+>", Pattern.DOTALL);

    /** A single execution of a plugin, from its start tag to its end tag. */
    private static final Pattern MAVEN_EXECUTION = Pattern.compile("<execution\\s*+>.*?</execution\\s*+>", Pattern.DOTALL);

    /** The execution that Maven binds Surefire to by default; a POM that declares it changes its configuration instead of adding another one. */
    private static final Pattern MAVEN_DEFAULT_TEST_EXECUTION = Pattern.compile("<id>\\s*+default-test\\s*+</id>");

    /** An execution that runs no tests: bound to no phase or told to skip them. */
    private static final Pattern MAVEN_SKIPPED_EXECUTION = Pattern.compile("<phase>\\s*+none\\s*+</phase>|<(skip|skipTests|skipExec)\\s*+>\\s*+true\\s*+</\\1\\s*+>");

    /** The properties of a POM; group 1 is their content. */
    private static final Pattern MAVEN_PROPERTIES = Pattern.compile("<properties\\s*+>(.*?)</properties\\s*+>", Pattern.DOTALL);

    /** The attributes of an element, e.g. {@code combine.self="override"}, which do not change what it is. */
    private static final String MAVEN_ATTRIBUTES = "(?:\\s[^<>/]*+)?";

    /** A property with a plain value, e.g. {@code <aresAgentArgument>-javaagent:ares-agent.jar</aresAgentArgument>}; group 1 is its name, group 2 its value. */
    private static final Pattern MAVEN_PROPERTY = Pattern.compile("<([A-Za-z_][\\w.\\-]*+)" + MAVEN_ATTRIBUTES + ">([^<]*+)</\\1\\s*+>");

    /** An empty element, e.g. the property {@code <aresAgentArgument/>}, which Maven reads as an empty value; group 1 is its name. */
    private static final Pattern MAVEN_EMPTY_ELEMENT = Pattern.compile("<([A-Za-z_][\\w.\\-]*+)" + MAVEN_ATTRIBUTES + "/>");

    /** A reference to a property, e.g. {@code ${aresAgentArgument}} or the late one of Surefire, {@code @{argLine}}; group 1 is the name. */
    private static final Pattern MAVEN_PROPERTY_REFERENCE = Pattern.compile("[$@]\\{([A-Za-z_][\\w.\\-]*+)}");

    /** The arguments Surefire passes to the test JVM, in its configuration or as its property; group 1 is their text. */
    private static final Pattern MAVEN_ARG_LINE = Pattern.compile("<argLine" + MAVEN_ATTRIBUTES + ">([^<]*+)</argLine\\s*+>");

    /** How often a property reference is replaced by its value at most, so that properties that refer to each other end. */
    private static final int MAVEN_PROPERTY_DEPTH = 8;

    /** A call that passes arguments to a JVM that Gradle starts, e.g. {@code jvmArgs "-javaagent:..."} or {@code jvmArgs += [...]}. */
    private static final Pattern GRADLE_JVM_ARGS = Pattern.compile("(?<![\\w$])(?:jvmArgs|setJvmArgs|allJvmArgs|setAllJvmArgs)(?![\\w$])");

    /** An assignment right after {@code jvmArgs} or {@code allJvmArgs}, which replaces the arguments set so far instead of adding to them. */
    private static final Pattern GRADLE_REPLACING_ASSIGNMENT = Pattern.compile("\\s*+=(?!=)");

    /**
     * The code before the brace of a block that configures the test tasks, without whitespace: {@code test}, {@code tasks.test},
     * {@code tasks.named('test')}, {@code tasks.getByName('test')} or {@code tasks.withType(Test)}, also followed by {@code configure},
     * {@code configureEach} or {@code all}.
     */
    private static final Pattern GRADLE_TEST_TASK_BLOCK = Pattern
            .compile("(?:tasks\\.)?test|tasks\\.(?:named|getByName)\\(['\"]test['\"](?:,Test)?\\)(?:\\.configure)?|tasks\\.withType\\(Test\\)(?:\\.(?:configureEach|all))?");

    /** The code right before a call on the test task itself, e.g. {@code test.} of {@code test.jvmArgs ...} or {@code tasks.test.} */
    private static final Pattern GRADLE_TEST_TASK_PREFIX = Pattern.compile("(?<![\\w$.])(?:tasks\\s*+\\.\\s*+)?test\\s*+\\.\\s*+$");

    /** The code before the brace of a method declaration, e.g. {@code def configureAgent()} or {@code void configure(Test task)}; group 1 is the method. */
    private static final Pattern GRADLE_METHOD_DECLARATION = Pattern.compile("(?<![\\w$.])[A-Za-z_$][\\w$<>\\[\\],?]*+\\s++([A-Za-z_$][\\w$]*+)\\s*+\\([^()]*+\\)\\s*+$");

    /** The code before the brace of a block that runs only when something calls it later: a class or a closure that a variable is assigned. */
    private static final Pattern GRADLE_DEFINITION_BLOCK = Pattern.compile("(?<![\\w$])(?:class|interface|enum)\\s[^{]*+$|(?<![=!<>])=\\s*+$");

    /** Words that may stand before parentheses and a brace without declaring a method, e.g. {@code else if (...)}. */
    private static final Set<String> GROOVY_CONTROL_WORDS = Set.of("if", "for", "while", "switch", "catch", "synchronized");

    /** A class whose instances provide JVM arguments, like {@code AresJvmArguments} of the templates; group 1 is its name. */
    private static final Pattern GRADLE_ARGUMENT_PROVIDER = Pattern
            .compile("(?<![\\w$])class\\s++([A-Za-z_$][\\w$]*+)[^{]*?(?<![\\w$])CommandLineArgumentProvider(?![\\w$])[^{]*+\\{");

    /** The registration of an argument provider with a task, e.g. {@code jvmArgumentProviders.add(aresJvmArguments)}. */
    private static final Pattern GRADLE_PROVIDER_REGISTRATION = Pattern.compile("(?<![\\w$])jvmArgumentProviders\\s*+(?:\\.\\s*+(?:add|addAll)\\s*+\\(|<<|\\+=)");

    /** An assignment to a variable or property, e.g. {@code def aresJvmArguments = objects.newInstance(AresJvmArguments)}; group 1 is its name. */
    private static final Pattern GRADLE_ASSIGNMENT = Pattern.compile("(?<![\\w$])([A-Za-z_$][\\w$]*+)\\s*+=(?![=~])");

    private BuildFileSyntax() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @param buildFiles the build files at the root of a repository
     * @return whether a build file declares a dependency on the Ares 1 library {@code artemis-java-test-sandbox}; a mention in a comment, e.g. one that
     *         records the migration to Ares 2, does not count
     */
    static boolean declaresAres1Dependency(SecurityPolicyRepositoryFiles.BuildFiles buildFiles) {
        String gradleBuild = buildFiles.gradleBuild();
        String mavenPom = buildFiles.mavenPom();
        return (gradleBuild != null && GRADLE_ARES_1_DEPENDENCY.matcher(withoutMarked(gradleBuild, gradleComments(gradleBuild))).find())
                || (mavenPom != null && MAVEN_ARES_1_DEPENDENCY.matcher(withoutMarked(mavenPom, mavenComments(mavenPom))).find());
    }

    /**
     * Whether the build attaches the Ares agent with {@code -javaagent}. Only then does Ares enforce a policy whose language configuration uses
     * {@code INSTRUMENTATION}; the build files of the Artemis templates attach it. A {@code -javaagent} option counts if it names the Ares agent, e.g.
     * {@code ares-agent.jar}, or, in Gradle, takes its file from the configuration that declares the Ares agent, like the templates do with
     * {@code configurations.aresAgent}. Another agent, such as JaCoCo, and text in comments do not count. Only the build file of the build tool that pins
     * the Ares version counts, see {@link SecurityPolicyRepositoryFiles#detectBuildTool(String, String)}, since another build file the repository keeps,
     * e.g. a {@code pom.xml} next to the {@code build.gradle}, does not run; without a pin, the Gradle build counts if there is one. Only an option that
     * reaches the test JVM counts, see {@link #surefireAttachesAres(String)} and {@link #passedJavaAgents(String)}, and a Maven build that runs the tests in
     * its own JVM attaches no agent, see {@link #runsTestsInMavenJvm(String)}.
     *
     * @param buildFiles the build files at the root of a repository
     * @return whether the build file of the repository's build tool attaches the Ares agent
     */
    static boolean attachesAresAgent(SecurityPolicyRepositoryFiles.BuildFiles buildFiles) {
        Optional<SecurityPolicyRepositoryFiles.BuildTool> buildTool = SecurityPolicyRepositoryFiles.detectBuildTool(buildFiles.gradleBuild(), buildFiles.mavenPom());
        boolean maven = buildTool.map(SecurityPolicyRepositoryFiles.BuildTool.MAVEN::equals).orElse(buildFiles.gradleBuild() == null);
        if (maven) {
            return Stream.ofNullable(buildFiles.mavenPom()).map(content -> withoutMarked(content, mavenComments(content)))
                    .anyMatch(code -> MAVEN_ARES_AGENT.matcher(code).find() && !runsTestsInMavenJvm(code) && surefireAttachesAres(code));
        }
        return gradleAttachesAresAgent(withoutMarked(buildFiles.gradleBuild(), gradleComments(buildFiles.gradleBuild())));
    }

    /**
     * Whether Surefire passes the Ares agent to every test JVM it starts: each execution that runs tests needs a {@code -javaagent} option that names Ares
     * in its {@code argLine}, since an execution without one, e.g. a second execution with its own {@code argLine}, runs its tests without Ares. An
     * execution takes the {@code argLine} of its own configuration, or else the one of the plugin, or else Surefire's {@code argLine} property; the
     * properties they refer to are replaced by their values, e.g. {@code @{argLine}} or {@code ${aresAgentArgument}}. An {@code argLine} in a property
     * that none of these reads, in the configuration of another plugin, e.g. Failsafe, or elsewhere in the POM does not count. A profile that can apply
     * on its own and sets an {@code argLine}, or a property one of these refers to, may replace them, so then the agent does not count.
     *
     * @param code the POM without comments
     */
    private static boolean surefireAttachesAres(String code) {
        boolean[] profiles = mavenProfiles(code, new boolean[code.length()]);
        Stream<MatchResult> profileArgLines = Stream.concat(MAVEN_ARG_LINE.matcher(code).results(),
                MAVEN_EMPTY_ELEMENT.matcher(code).results().filter(element -> "argLine".equals(element.group(1))));
        if (profileArgLines.anyMatch(argLine -> profiles[argLine.start()] && inProfileWithActivation(code, argLine))) {
            return false;
        }
        MavenProperties properties = mavenProperties(code, profiles);
        List<String> plugins = surefirePlugins(code, profiles, false);
        // The argLine of the plugin applies to every execution that sets none; without one, Surefire reads its argLine property.
        List<String> pluginArgLines = plugins.stream().flatMap(plugin -> argLines(MAVEN_EXECUTIONS.matcher(plugin).replaceAll(""))).toList();
        List<String> defaultArgLines = pluginArgLines.isEmpty()
                ? MAVEN_ARG_LINE.matcher(code).results().filter(argLine -> properties.declared()[argLine.start()]).map(argLine -> argLine.group(1)).toList()
                : pluginArgLines;
        List<List<String>> runs = new ArrayList<>();
        boolean declaresDefaultExecution = false;
        for (String execution : plugins.stream().flatMap(plugin -> MAVEN_EXECUTION.matcher(plugin).results().map(MatchResult::group)).toList()) {
            declaresDefaultExecution |= MAVEN_DEFAULT_TEST_EXECUTION.matcher(execution).find();
            if (!MAVEN_SKIPPED_EXECUTION.matcher(execution).find()) {
                List<String> own = argLines(execution).toList();
                runs.add(own.isEmpty() ? defaultArgLines : own);
            }
        }
        if (!declaresDefaultExecution) {
            runs.add(defaultArgLines);
        }
        return runs.stream().allMatch(argLines -> !argLines.isEmpty() && argLines.stream().allMatch(argLine -> passesAresAgent(argLine, properties)));
    }

    private static boolean passesAresAgent(String argLine, MavenProperties properties) {
        boolean replaceable = referencedProperties(argLine, properties.values()).stream().anyMatch(properties.replaceable()::contains);
        return !replaceable && javaAgents(withPropertyValues(argLine, properties.values())).anyMatch(BuildFileSyntax::namesAres);
    }

    private static Stream<String> argLines(String configuration) {
        return MAVEN_ARG_LINE.matcher(configuration).results().map(argLine -> argLine.group(1));
    }

    /**
     * @param withActiveProfiles whether to include the declarations in profiles that can apply on their own, see {@link #inProfileWithActivation}
     * @return the declarations of Surefire, each from its start tag to its end tag
     */
    private static List<String> surefirePlugins(String code, boolean[] profiles, boolean withActiveProfiles) {
        return MAVEN_PLUGIN.matcher(code).results().filter(plugin -> MAVEN_SUREFIRE.matcher(plugin.group()).find())
                .filter(plugin -> !profiles[plugin.start()] || (withActiveProfiles && inProfileWithActivation(code, plugin))).map(MatchResult::group).toList();
    }

    /**
     * The properties of a POM.
     *
     * @param values      the values of the properties of the POM itself
     * @param replaceable the names of the properties that a profile that can apply on its own sets, which may then replace their values
     * @param declared    for each character of the POM whether it lies in the properties of the POM itself
     */
    private record MavenProperties(Map<String, String> values, Set<String> replaceable, boolean[] declared) {
    }

    private static MavenProperties mavenProperties(String code, boolean[] profiles) {
        boolean[] declared = new boolean[code.length()];
        Map<String, String> values = new HashMap<>();
        Set<String> replaceable = new HashSet<>();
        MAVEN_PROPERTIES.matcher(code).results().forEach(section -> {
            if (!profiles[section.start()]) {
                Arrays.fill(declared, section.start(), section.end(), true);
                MAVEN_PROPERTY.matcher(section.group(1)).results().forEach(property -> values.put(property.group(1), property.group(2)));
                MAVEN_EMPTY_ELEMENT.matcher(section.group(1)).results().forEach(property -> values.put(property.group(1), ""));
            }
            else if (inProfileWithActivation(code, section)) {
                Stream.concat(MAVEN_PROPERTY.matcher(section.group(1)).results(), MAVEN_EMPTY_ELEMENT.matcher(section.group(1)).results())
                        .forEach(property -> replaceable.add(property.group(1)));
            }
        });
        return new MavenProperties(values, replaceable, declared);
    }

    /** The names of the properties the text refers to, also through the values of the properties it refers to. */
    private static Set<String> referencedProperties(String text, Map<String, String> values) {
        Set<String> names = new HashSet<>();
        List<String> texts = List.of(text);
        for (int round = 0; round < MAVEN_PROPERTY_DEPTH && !texts.isEmpty(); round++) {
            texts = texts.stream().flatMap(part -> MAVEN_PROPERTY_REFERENCE.matcher(part).results()).map(reference -> reference.group(1)).filter(names::add).map(values::get)
                    .filter(value -> value != null).toList();
        }
        return names;
    }

    private static String withPropertyValues(String text, Map<String, String> values) {
        String replaced = text;
        for (int round = 0; round < MAVEN_PROPERTY_DEPTH; round++) {
            String next = MAVEN_PROPERTY_REFERENCE.matcher(replaced).replaceAll(reference -> Matcher.quoteReplacement(values.getOrDefault(reference.group(1), reference.group())));
            if (next.equals(replaced)) {
                break;
            }
            replaced = next;
        }
        return replaced;
    }

    /**
     * The {@code -javaagent} options the Gradle build passes to the JVM of its test tasks: in the arguments of {@code jvmArgs}, e.g.
     * {@code test { jvmArgs "-javaagent:..." }}, or in a {@code CommandLineArgumentProvider} whose instance {@code jvmArgumentProviders} receives, like the
     * templates register {@code AresJvmArguments}. Only a call that configures the test tasks counts, see {@link #configuresTestTasks}, so an option in a
     * variable, in a method that may never run or in a provider that no test task receives does not count. The calls count in the order of the build file:
     * {@code setJvmArgs} or {@code jvmArgs = [...]} replaces the arguments set so far, and {@code setAllJvmArgs} or {@code allJvmArgs = [...]} replaces the
     * arguments of the providers as well.
     *
     * @param code the build file without comments
     */
    private static Stream<String> passedJavaAgents(String code) {
        boolean[] strings = gradleCommentsAndStrings(code);
        List<MatchResult> providers = GRADLE_ARGUMENT_PROVIDER.matcher(code).results().filter(provider -> !strings[provider.start()]).toList();
        Stream<MatchResult> calls = Stream.concat(GRADLE_JVM_ARGS.matcher(code).results(), GRADLE_PROVIDER_REGISTRATION.matcher(code).results());
        List<String> arguments = new ArrayList<>();
        List<String> provided = new ArrayList<>();
        for (MatchResult call : calls.filter(call -> !strings[call.start()] && configuresTestTasks(code, strings, call.start())).sorted(Comparator.comparingInt(MatchResult::start))
                .toList()) {
            if (call.group().startsWith("jvmArgumentProviders")) {
                registeredProviders(code, call, providers).forEach(provider -> provided.addAll(javaAgentsIn(code, provider.end(), endOfBlock(code, provider.end()))));
                continue;
            }
            boolean assigned = GRADLE_REPLACING_ASSIGNMENT.matcher(code).region(call.end(), code.length()).lookingAt();
            if (call.group().startsWith("set") || assigned) {
                arguments.clear();
                if (call.group().toLowerCase(Locale.ROOT).contains("all")) {
                    provided.clear();
                }
            }
            arguments.addAll(javaAgentsIn(code, call.start(), endOfArguments(code, call.end())));
        }
        return Stream.concat(arguments.stream(), provided.stream());
    }

    private static List<String> javaAgentsIn(String code, int from, int to) {
        return JAVA_AGENT.matcher(code).region(from, to).results().map(agent -> agent.group(1)).toList();
    }

    /**
     * Whether the call at the offset configures the test tasks: it lies in a block that configures them, e.g. {@code test { ... }} or
     * {@code tasks.withType(Test).configureEach { ... }}, or calls the test task itself, e.g. {@code test.jvmArgs ...}, and no block around it runs only when
     * something calls it later, such as a method, a class or a closure that a variable is assigned.
     *
     * @param strings for each character of the build file whether it belongs to a string
     */
    private static boolean configuresTestTasks(String code, boolean[] strings, int offset) {
        boolean testTask = GRADLE_TEST_TASK_PREFIX.matcher(code.substring(Math.max(0, offset - 100), offset)).find();
        for (int brace : enclosingBraces(code, strings, offset)) {
            String opener = blockOpener(code, strings, brace);
            Matcher method = GRADLE_METHOD_DECLARATION.matcher(opener);
            if (GRADLE_DEFINITION_BLOCK.matcher(opener).find() || (method.find() && !GROOVY_CONTROL_WORDS.contains(method.group(1)))) {
                return false;
            }
            testTask |= GRADLE_TEST_TASK_BLOCK.matcher(opener.replaceAll("\\s++", "")).matches();
        }
        return testTask;
    }

    /** The offsets of the braces of the blocks around the offset, innermost first. */
    private static List<Integer> enclosingBraces(String code, boolean[] strings, int offset) {
        Deque<Integer> open = new ArrayDeque<>();
        for (int index = 0; index < offset; index++) {
            if (!strings[index] && code.charAt(index) == '{') {
                open.push(index);
            }
            else if (!strings[index] && code.charAt(index) == '}' && !open.isEmpty()) {
                open.pop();
            }
        }
        return List.copyOf(open);
    }

    /**
     * @return the code of the statement before the brace that opens a block, e.g. {@code tasks.withType(Test).configureEach} or {@code def configureAgent()}:
     *         from the end of the previous statement, on the last line that does not continue the line before with a method call such as {@code .configureEach}
     */
    private static String blockOpener(String code, boolean[] strings, int brace) {
        int start = brace;
        while (start > 0 && (strings[start - 1] || ";{}".indexOf(code.charAt(start - 1)) < 0)) {
            start--;
        }
        String[] lines = code.substring(start, brace).split("\n", -1);
        int first = lines.length - 1;
        while (first > 0 && lines[first].strip().startsWith(".")) {
            first--;
        }
        return String.join("\n", Arrays.copyOfRange(lines, first, lines.length)).strip();
    }

    /**
     * @return the provider classes whose instance the registration passes to {@code jvmArgumentProviders}, directly or through a variable that holds one,
     *         e.g. {@code jvmArgumentProviders.add(aresJvmArguments)} with {@code def aresJvmArguments = objects.newInstance(AresJvmArguments)}
     */
    private static List<MatchResult> registeredProviders(String code, MatchResult registration, List<MatchResult> providers) {
        String arguments = code.substring(registration.end(), endOfArguments(code, registration.start()));
        return providers.stream().filter(provider -> {
            Set<String> holders = new HashSet<>(Set.of(provider.group(1)));
            GRADLE_ASSIGNMENT.matcher(code).results().filter(assignment -> mentions(statementAfter(code, assignment.end()), Set.of(provider.group(1))))
                    .forEach(assignment -> holders.add(assignment.group(1)));
            return mentions(arguments, holders);
        }).toList();
    }

    private static boolean mentions(String text, Set<String> names) {
        return IDENTIFIER.matcher(text).results().map(MatchResult::group).anyMatch(names::contains);
    }

    /** The end of the statement that starts at the offset, extended to the line on which every bracket that it opens is closed again. */
    private static int endOfArguments(String code, int offset) {
        int end = offset + statementAfter(code, offset).length();
        int open = openBrackets(code, offset, end);
        while (open > 0 && end < code.length()) {
            int next = code.indexOf('\n', end + 1);
            next = next < 0 ? code.length() : next;
            open += openBrackets(code, end, next);
            end = next;
        }
        return end;
    }

    /** The offset of the brace that closes the block whose content starts at the offset. */
    private static int endOfBlock(String code, int offset) {
        int open = 1;
        for (int index = offset; index < code.length(); index++) {
            open += bracketChange(code.charAt(index));
            if (open == 0) {
                return index;
            }
        }
        return code.length();
    }

    private static int openBrackets(String code, int from, int to) {
        return code.substring(from, to).chars().map(character -> bracketChange((char) character)).sum();
    }

    private static int bracketChange(char character) {
        return switch (character) {
            case '(', '[', '{' -> 1;
            case ')', ']', '}' -> -1;
            default -> 0;
        };
    }

    /**
     * Whether Surefire may run the tests in the Maven JVM, where it ignores its {@code argLine}: with a {@code forkCount} of 0 in its configuration or, if
     * that sets none, in its {@code forkCount} property. A {@code forkCount} that refers to a property counts with the value of that property, e.g.
     * {@code ${test.forks}}; one whose value Artemis cannot tell, because the property is unknown or a profile that can apply on its own may set it, counts
     * as 0. The configuration of another plugin, e.g. Failsafe, does not count. A profile without an activation only applies if the build names it, which
     * the build of the Artemis templates, {@code mvn clean test}, does not do.
     *
     * @param code the POM without comments
     */
    private static boolean runsTestsInMavenJvm(String code) {
        boolean[] profiles = mavenProfiles(code, new boolean[code.length()]);
        MavenProperties properties = mavenProperties(code, profiles);
        List<String> forkCounts = surefirePlugins(code, profiles, true).stream().flatMap(plugin -> MAVEN_FORK_COUNT.matcher(plugin).results()).map(forkCount -> forkCount.group(1))
                .toList();
        if (forkCounts.isEmpty()) {
            if (properties.replaceable().contains("forkCount")) {
                return true;
            }
            forkCounts = Stream.ofNullable(properties.values().get("forkCount")).toList();
        }
        return forkCounts.stream().anyMatch(forkCount -> {
            if (referencedProperties(forkCount, properties.values()).stream().anyMatch(properties.replaceable()::contains)) {
                return true;
            }
            String value = withPropertyValues(forkCount, properties.values());
            return MAVEN_PROPERTY_REFERENCE.matcher(value).find() || IN_PROCESS_FORK_COUNT.matcher(value).matches();
        });
    }

    /** Whether the match lies in a profile that can apply on its own through an activation, e.g. {@code activeByDefault}, see {@link #runsTestsInMavenJvm}. */
    private static boolean inProfileWithActivation(String code, MatchResult match) {
        return MAVEN_PROFILE.matcher(code).results()
                .anyMatch(profile -> profile.start() < match.start() && match.end() <= profile.end() && MAVEN_ACTIVATION.matcher(profile.group()).find());
    }

    /**
     * @param declaration a match of {@link #GRADLE_ARES_AGENT} or {@link #DEPENDENCY_DECLARATION}
     * @return the configuration it declares a dependency of, written before the dependency or as the first argument of {@code add}
     */
    private static String configurationOf(MatchResult declaration) {
        return declaration.group(declaration.group(1) != null ? 1 : 2);
    }

    /**
     * Checks every configuration that declares the Ares agent, e.g. both {@code smokeAgent} and {@code testAgent}, since the test JVM may take its agent from
     * any of them.
     */
    private static boolean gradleAttachesAresAgent(String code) {
        return GRADLE_ARES_AGENT.matcher(code).results().map(BuildFileSyntax::configurationOf).distinct().anyMatch(configuration -> {
            // A configuration with more dependencies than the Ares agent leaves open which file an agent takes from it, e.g. JaCoCo, so the agent then has
            // to name Ares itself.
            boolean onlyTheAresAgent = DEPENDENCY_DECLARATION.matcher(code).results().filter(declaration -> configurationOf(declaration).equals(configuration)).count() == 1;
            return passedJavaAgents(code).anyMatch(agent -> namesAres(agent) || (onlyTheAresAgent && takesFileFrom(code, agent, configuration)));
        });
    }

    /**
     * Whether an interpolated agent takes its file from the configuration: directly, as in {@code "-javaagent:${configurations.aresAgent.singleFile}"}, or
     * through the variable or property it starts with, as the templates do with {@code "-javaagent:${agentJar.singleFile.absolutePath}"} and
     * {@code aresJvmArguments.agentJar.from(configurations.aresAgent)}.
     */
    private static boolean takesFileFrom(String code, String agent, String configuration) {
        Matcher interpolation = INTERPOLATED_NAME.matcher(agent);
        if (!interpolation.find()) {
            return false;
        }
        if (referencesConfiguration(agent, configuration)) {
            return true;
        }
        // Every statement that gives the variable a value has to take it from the configuration, so a later reassignment to another file does not count.
        String name = interpolation.group(1);
        Matcher identifier = IDENTIFIER.matcher(code);
        boolean takesValue = false;
        while (identifier.find()) {
            if (!identifier.group().equals(name)) {
                continue;
            }
            String statement = statementAfter(code, identifier.end());
            // An empty file collection, e.g. "def agentJar = files()", only gets its content from the next statements.
            if (TAKES_VALUE.matcher(statement).lookingAt() && !EMPTY_COLLECTION.matcher(statement).matches()) {
                if (!referencesConfiguration(statement, configuration)) {
                    return false;
                }
                takesValue = true;
            }
        }
        return takesValue;
    }

    private static boolean referencesConfiguration(String text, String configuration) {
        return CONFIGURATION_REFERENCE.matcher(text).results().anyMatch(reference -> reference.group(1).equals(configuration));
    }

    /**
     * @return the rest of the statement after the offset, up to a semicolon: the rest of its line and every following line while the statement continues,
     *         because a line ends with an operator such as {@code =} or the next one starts with a method call, as in {@code def agentJar =} followed by
     *         {@code configurations.aresAgent} on the next line
     */
    private static String statementAfter(String code, int offset) {
        String statement = continuedLinesAfter(code, offset);
        return statement.substring(0, endOfStatement(statement));
    }

    /** The offset of the first semicolon outside a quoted string, e.g. not the one of {@code jvmArgs '-Dseparator=;', ...}; the length if there is none. */
    private static int endOfStatement(String statement) {
        char quote = 0;
        for (int index = 0; index < statement.length(); index++) {
            char character = statement.charAt(index);
            if (quote != 0) {
                if (character == '\\') {
                    index++;
                }
                else if (character == quote) {
                    quote = 0;
                }
            }
            else if (character == '\'' || character == '"') {
                quote = character;
            }
            else if (character == ';') {
                return index;
            }
        }
        return statement.length();
    }

    private static String continuedLinesAfter(String code, int offset) {
        int end = code.indexOf('\n', offset);
        while (end >= 0) {
            String statement = code.substring(offset, end).stripTrailing();
            int next = end + 1;
            while (next < code.length() && Character.isWhitespace(code.charAt(next))) {
                next++;
            }
            boolean continues = (!statement.isEmpty() && CONTINUING_ENDS.indexOf(statement.charAt(statement.length() - 1)) >= 0)
                    || (next < code.length() && code.charAt(next) == '.');
            if (!continues) {
                return code.substring(offset, end);
            }
            end = code.indexOf('\n', next);
        }
        return code.substring(offset);
    }

    private static Stream<String> javaAgents(String code) {
        return JAVA_AGENT.matcher(code).results().map(match -> match.group(1));
    }

    /**
     * Whether the file name of the agent JAR names Ares, e.g. {@code ares-agent.jar} or {@code ares-2.2.1-agent.jar}. Only the literal part of the file name
     * counts: neither its directories, e.g. {@code /shares/jacocoagent.jar}, nor the options after {@code =}, e.g. {@code jacocoagent.jar=destfile=ares.exec},
     * nor an interpolation, because a variable is named freely, e.g. {@code ${aresAgentJar}} may hold JaCoCo; an interpolated agent counts only through
     * {@link #takesFileFrom(String, String, String)}.
     */
    private static boolean namesAres(String agent) {
        String path = INTERPOLATION.matcher(agent).replaceAll("");
        int options = path.indexOf('=');
        String jar = options < 0 ? path : path.substring(0, options);
        String fileName = jar.substring(Math.max(jar.lastIndexOf('/'), jar.lastIndexOf('\\')) + 1);
        return ARES_IN_FILE_NAME.matcher(fileName.toLowerCase(Locale.ROOT)).find();
    }

    /**
     * @param marked for each character of the content whether to remove it
     * @return the content with the marked characters replaced by spaces, so offsets stay the same, e.g. those of {@link #mavenProfiles(String, boolean[])}
     */
    private static String withoutMarked(String content, boolean[] marked) {
        char[] characters = content.toCharArray();
        for (int index = 0; index < characters.length; index++) {
            if (marked[index]) {
                characters[index] = ' ';
            }
        }
        return new String(characters);
    }

    /**
     * @return for each character of a Gradle build file whether it belongs to a {@code //} or {@code /* *}{@code /} comment, to a quoted string or to a
     *         slashy string ({@code /.../} or {@code $/.../$}); a URL such as {@code 'https://example.org'} therefore starts no comment
     */
    static boolean[] gradleCommentsAndStrings(String content) {
        return gradleNonCode(content, true);
    }

    /**
     * @return for each character of a Gradle build file whether it belongs to a {@code //} or {@code /* *}{@code /} comment; strings are code here
     */
    static boolean[] gradleComments(String content) {
        return gradleNonCode(content, false);
    }

    private static boolean[] gradleNonCode(String content, boolean strings) {
        boolean[] comment = new boolean[content.length()];
        int index = 0;
        // The last token of code before the current one, without whitespace and comments: a word, ++, -- or a single character; a string counts as an operand.
        String previous = ";";
        while (index < content.length()) {
            int end = endOfComment(content, index);
            boolean marked = true;
            if (end < 0) {
                end = endOfStringLiteral(content, index, previous);
                if (end >= 0) {
                    previous = "\"";
                    marked = strings;
                }
            }
            if (end >= 0) {
                Arrays.fill(comment, index, end, marked);
            }
            else {
                end = endOfCodeToken(content, index);
                previous = tokenAfter(previous, content.substring(index, end));
            }
            index = end;
        }
        return comment;
    }

    /**
     * @return the offset after a {@code //} or {@code /* *}{@code /} comment that starts at {@code index}, or -1 if none starts there
     */
    private static int endOfComment(String content, int index) {
        if (content.startsWith("//", index)) {
            return endOfLine(content, index);
        }
        if (content.startsWith("/*", index)) {
            int close = content.indexOf("*/", index + 2);
            return close < 0 ? content.length() : close + 2;
        }
        return -1;
    }

    /**
     * @param previous the last token of code before {@code index}, which decides whether a {@code /} opens a slashy string
     * @return the offset after a quoted or slashy string that starts at {@code index}, or -1 if none starts there
     */
    private static int endOfStringLiteral(String content, int index, String previous) {
        char character = content.charAt(index);
        if (character == '\'' || character == '"') {
            String quote = String.valueOf(character);
            String delimiter = content.startsWith(quote.repeat(3), index) ? quote.repeat(3) : quote;
            return endOfString(content, index + delimiter.length(), delimiter);
        }
        if ((character == '/' || content.startsWith("$/", index)) && slashyStringMayStartAfter(previous)) {
            return endOfSlashyString(content, index);
        }
        return -1;
    }

    /**
     * @return the offset after the word that starts at {@code index}, or after its single character if no word starts there
     */
    private static int endOfCodeToken(String content, int index) {
        int end = index + 1;
        if (Character.isJavaIdentifierStart(content.charAt(index))) {
            while (end < content.length() && Character.isJavaIdentifierPart(content.charAt(end))) {
                end++;
            }
        }
        return end;
    }

    /**
     * @param previous the last token of code so far
     * @param token    the next token of code: a word or a single character
     * @return the last token of code after it; whitespace keeps the previous one, and a second {@code +} or {@code -} joins the first
     */
    private static String tokenAfter(String previous, String token) {
        char first = token.charAt(0);
        if (Character.isJavaIdentifierStart(first)) {
            return token;
        }
        if ((first == '+' || first == '-') && previous.equals(token)) {
            return token.repeat(2);
        }
        return Character.isWhitespace(first) ? previous : token;
    }

    /**
     * A {@code /} after an operand divides, as in {@code a / b}, {@code (a) / b} or {@code a++ / b}; elsewhere, as after {@code =}, {@code (}, {@code ,} or
     * a keyword such as {@code return}, it opens a slashy string.
     */
    private static boolean slashyStringMayStartAfter(String previous) {
        if (Character.isJavaIdentifierStart(previous.charAt(0))) {
            return GROOVY_KEYWORDS_BEFORE_EXPRESSIONS.contains(previous);
        }
        char last = previous.charAt(previous.length() - 1);
        return previous.length() == 1 && !Character.isJavaIdentifierPart(last) && last != ')' && last != ']' && last != '}' && last != '"';
    }

    /**
     * @param from the offset of the opening {@code /} or {@code $/}
     * @return the offset after the closing delimiter, or {@code -1} if the string is not closed, in which case the {@code /} is read as code
     */
    private static int endOfSlashyString(String content, int from) {
        boolean dollar = content.charAt(from) == '$';
        int index = from + (dollar ? 2 : 1);
        while (index < content.length()) {
            if (dollar && (content.startsWith("$$", index) || content.startsWith("$/", index))) {
                index += 2;
            }
            else if (dollar && content.startsWith("/$", index)) {
                return index + 2;
            }
            else if (!dollar && content.startsWith("\\/", index)) {
                index += 2;
            }
            else if (!dollar && content.charAt(index) == '/') {
                return index + 1;
            }
            else {
                index++;
            }
        }
        return -1;
    }

    /**
     * @param from      the offset after the opening delimiter
     * @param delimiter the quote that closes the string
     * @return the offset after the closing delimiter; a string with a single quote also ends at the end of its line
     */
    private static int endOfString(String content, int from, String delimiter) {
        int index = from;
        while (index < content.length()) {
            if (content.charAt(index) == '\\') {
                index += 2;
            }
            else if (content.startsWith(delimiter, index)) {
                return index + delimiter.length();
            }
            else if (delimiter.length() == 1 && content.charAt(index) == '\n') {
                return index + 1;
            }
            else {
                index++;
            }
        }
        return content.length();
    }

    private static int endOfLine(String content, int from) {
        int lineEnd = content.indexOf('\n', from);
        return lineEnd < 0 ? content.length() : lineEnd;
    }

    /**
     * @return for each character of a Maven POM whether it belongs to an XML comment
     */
    static boolean[] mavenComments(String content) {
        boolean[] comment = new boolean[content.length()];
        int start = content.indexOf("<!--");
        while (start >= 0) {
            int close = content.indexOf("-->", start + 4);
            int end = close < 0 ? content.length() : close + 3;
            Arrays.fill(comment, start, end, true);
            start = content.indexOf("<!--", end);
        }
        return comment;
    }

    /**
     * @param comments for each character of the POM whether it belongs to a comment
     * @return for each character of a Maven POM whether it lies in its {@code <profiles>} section, whose properties apply only while their profile is active
     */
    private static boolean[] mavenProfiles(String content, boolean[] comments) {
        // Both tags are searched outside comments, e.g. not in <!-- Example: <profiles> -->; the masked text keeps the offsets of the content.
        String code = withoutMarked(content, comments);
        boolean[] profiles = new boolean[content.length()];
        Matcher start = MAVEN_PROFILES_START.matcher(code);
        Matcher end = MAVEN_PROFILES_END.matcher(code);
        int from = 0;
        while (start.find(from)) {
            from = end.find(start.end()) ? end.end() : content.length();
            Arrays.fill(profiles, start.start(), from, true);
        }
        return profiles;
    }

    /**
     * The places that pin the Ares version in a build file. A pin inside a comment, e.g. {@code // aresVersion = '2.3.0'}, or inside a Gradle string, e.g.
     * {@code "aresVersion = '2.3.0'"}, has no effect on the build, so it is neither read nor replaced. Neither is a pin in a Maven profile, which applies
     * only while its profile is active, so the pin of the POM itself is the one the build uses, nor a local variable of a Gradle method or closure, e.g.
     * {@code def aresVersion = '2.3.0'} in a helper method. If another assignment that counts has no readable value, e.g. {@code aresVersion = "${base}.1"}
     * or {@code <ares.version>${next}</ares.version>}, or if the pins name different versions, the version the build uses is unknown, so there are no pins.
     *
     * @param content   the content of the build file
     * @param buildTool the build tool the file belongs to
     * @return the pins outside comments and strings, in the order of the file
     */
    static List<MatchResult> aresVersionPins(String content, SecurityPolicyRepositoryFiles.BuildTool buildTool) {
        boolean gradle = buildTool == SecurityPolicyRepositoryFiles.BuildTool.GRADLE;
        // The literal is matched only where the build reads an assignment, so a pin-shaped line in a comment is not followed to the end of the comment.
        Matcher literal = (gradle ? GRADLE_ARES_VERSION : MAVEN_ARES_VERSION).matcher(content).useTransparentBounds(true);
        List<MatchResult> pins = new ArrayList<>();
        for (int start : assignments(content, gradle, readByTheBuild(content, gradle)).toArray()) {
            if (!literal.region(start, content.length()).lookingAt()) {
                return List.of();
            }
            pins.add(literal.toMatchResult());
        }
        // Pins of different versions leave open which one the dependency reads, e.g. after a later assignment of project.ext.aresVersion.
        boolean oneVersion = pins.stream().map(pin -> pin.group(2).trim()).distinct().count() <= 1;
        return oneVersion ? List.copyOf(pins) : List.of();
    }

    /**
     * @return whether a Gradle build file assigns the Ares version where the build reads it, whether Artemis can read the value or not, e.g. also
     *         {@code aresVersion = "${base}.1"} or two pins of different versions
     */
    static boolean assignsGradleAresVersion(String gradleBuild) {
        return assignments(gradleBuild, true, readByTheBuild(gradleBuild, true)).findAny().isPresent();
    }

    /**
     * @return the offsets of the assignments of the Ares version that the build reads, whatever their value
     */
    private static IntStream assignments(String content, boolean gradle, IntPredicate counts) {
        return (gradle ? GRADLE_ARES_VERSION_ASSIGNMENT : MAVEN_ARES_VERSION_DEFINITION).matcher(content).results().mapToInt(MatchResult::start).filter(counts);
    }

    /**
     * @return whether the build reads an assignment of the Ares version at an offset: not in a comment or a Gradle string, not in a Maven profile and not
     *         as a local variable of a Gradle method or closure
     */
    private static IntPredicate readByTheBuild(String content, boolean gradle) {
        boolean[] noCode = gradle ? gradleCommentsAndStrings(content) : mavenComments(content);
        boolean[] profiles = gradle ? new boolean[content.length()] : mavenProfiles(content, noCode);
        return start -> !noCode[start] && !profiles[start] && !(gradle && declaresLocalGradleVariable(content, noCode, start));
    }

    /**
     * @return whether a profile of the POM pins the Ares version; while such a profile is active, e.g. through {@code activeByDefault}, Maven uses its pin
     *         instead of the one of the POM itself
     */
    static boolean pinsInMavenProfile(String pom) {
        boolean[] comments = mavenComments(pom);
        boolean[] profiles = mavenProfiles(pom, comments);
        // Whatever its value, also a reference to another property, the definition of the profile replaces the one of the POM itself.
        return MAVEN_ARES_VERSION_DEFINITION.matcher(pom).results().anyMatch(definition -> !comments[definition.start()] && profiles[definition.start()]);
    }

    /**
     * @param noCode for each character of the build file whether it belongs to a comment or a string
     * @return whether the Gradle code at the offset declares a local variable of a method or closure, e.g. {@code def aresVersion = '2.3.0'} in a helper
     *         method, which sets no project property; a declaration at the top level of the script declares a script variable the build may use
     */
    private static boolean declaresLocalGradleVariable(String content, boolean[] noCode, int offset) {
        int depth = 0;
        for (int index = 0; index < offset; index++) {
            if (!noCode[index] && content.charAt(index) == '{') {
                depth++;
            }
            else if (!noCode[index] && content.charAt(index) == '}') {
                depth--;
            }
        }
        return depth > 0 && GRADLE_DECLARATION_BEFORE.matcher(withoutMarked(content.substring(0, offset), noCode)).find();
    }
}
