package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.stream.Collectors;
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

    /** The argument of a {@code -javaagent} option; group 1 is the agent, up to the end of the string or argument. */
    private static final Pattern JAVA_AGENT = Pattern.compile("-javaagent:((?:\\$\\{[^}'\"\\n]*+}?|[^'\"\\s,)<$]|\\$(?!\\{))*+)");

    /** The Ares agent as a Maven artifact: the {@code agent} classifier of the {@code ares} artifact. */
    private static final Pattern MAVEN_ARES_AGENT = Pattern
            .compile("<artifactId>\\s*ares\\s*</artifactId>\\s*(<version>[^<]*</version>\\s*)?(<type>[^<]*</type>\\s*)?<classifier>\\s*agent\\s*</classifier>");

    /**
     * A {@code forkCount} of 0 in the configuration of Surefire or as its property, e.g. {@code <forkCount>0</forkCount>}, also as {@code 0C}, a multiple of the
     * number of cores. Surefire then runs the tests in the Maven JVM and ignores its {@code argLine}.
     */
    private static final Pattern MAVEN_TESTS_IN_PROCESS = Pattern.compile("<forkCount>\\s*+0++(?:\\.0*+)?C?\\s*+</forkCount>");

    private static final String ARES_1_ARTIFACT = "artemis-java-test-sandbox";

    /** The Ares 1 library in a Gradle dependency, e.g. {@code 'de.tum.in.ase:artemis-java-test-sandbox:1.13.0'} or {@code name: 'artemis-java-test-sandbox'}. */
    private static final Pattern GRADLE_ARES_1_DEPENDENCY = Pattern.compile("[:'\"]\\s*+" + ARES_1_ARTIFACT + "\\s*+[:'\"]");

    /** The Ares 1 library as the artifact of a Maven dependency. */
    private static final Pattern MAVEN_ARES_1_DEPENDENCY = Pattern.compile("<artifactId>\\s*+" + ARES_1_ARTIFACT + "\\s*+</artifactId>");

    /** A keyword or type right before a Gradle variable that declares it, e.g. {@code def}, matched at the end of the code before the variable. */
    private static final Pattern GRADLE_DECLARATION_BEFORE = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})(?:def|var|final|String|Object)\\s++$");

    /**
     * The Ares version as a complete string literal on the right of {@code aresVersion =}, e.g. {@code aresVersion = '2.2.1'}. An expression such as
     * {@code '2.2' + '.1'} or an interpolation such as {@code "${base}.1"} pins no version Artemis could read or replace, so it is no pin.
     */
    private static final Pattern GRADLE_ARES_VERSION = Pattern
            .compile("(?<!\\p{javaJavaIdentifierPart})(aresVersion\\s*=\\s*['\"])([^'\"$]+)(['\"])(?=[ \\t]*+(?:[;}\\r\\n]|//|/\\*|$))");

    /** The Ares version as the text of {@code <ares.version>}; a reference to another property, e.g. {@code ${base.version}}, is no pin. */
    private static final Pattern MAVEN_ARES_VERSION = Pattern.compile("(<ares\\.version\\s*+>)([^<$]+)(</ares\\.version\\s*+>)");

    /** Any assignment of the Ares version in Gradle, whatever its value, e.g. also {@code aresVersion = "${base}.1"}; {@code ==} compares and is none. */
    private static final Pattern GRADLE_ARES_VERSION_ASSIGNMENT = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})aresVersion\\s*+=(?!=)");

    /**
     * Any definition of the Ares version in a POM, whatever its value, e.g. also {@code <ares.version>${next.ares.version}</ares.version>}; XML allows
     * whitespace before the {@code >} of both tags.
     */
    private static final Pattern MAVEN_ARES_VERSION_DEFINITION = Pattern.compile("<ares\\.version\\s*+>[^<]*+</ares\\.version\\s*+>");

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
     * reaches the test JVM counts, see {@link #surefireJavaAgents(String)} and {@link #passedJavaAgents(String)}, and a Maven build that runs the tests in its
     * own JVM attaches no agent, see {@link #runsTestsInMavenJvm(String)}.
     *
     * @param buildFiles the build files at the root of a repository
     * @return whether the build file of the repository's build tool attaches the Ares agent
     */
    static boolean attachesAresAgent(SecurityPolicyRepositoryFiles.BuildFiles buildFiles) {
        Optional<SecurityPolicyRepositoryFiles.BuildTool> buildTool = SecurityPolicyRepositoryFiles.detectBuildTool(buildFiles.gradleBuild(), buildFiles.mavenPom());
        boolean maven = buildTool.map(SecurityPolicyRepositoryFiles.BuildTool.MAVEN::equals).orElse(buildFiles.gradleBuild() == null);
        if (maven) {
            return Stream.ofNullable(buildFiles.mavenPom()).map(content -> withoutMarked(content, mavenComments(content)))
                    .anyMatch(code -> MAVEN_ARES_AGENT.matcher(code).find() && !runsTestsInMavenJvm(code) && surefireJavaAgents(code).anyMatch(BuildFileSyntax::namesAres));
        }
        return gradleAttachesAresAgent(withoutMarked(buildFiles.gradleBuild(), gradleComments(buildFiles.gradleBuild())));
    }

    /**
     * The {@code -javaagent} options Surefire passes to the test JVM: those of the {@code argLine} in its configuration or, if it has none, of its
     * {@code argLine} property, with the properties they refer to replaced by their values, e.g. {@code @{argLine}} or {@code ${aresAgentArgument}}. An
     * option in a property that no such {@code argLine} reads, in the configuration of another plugin, e.g. Failsafe, or elsewhere in the POM does not
     * count. A profile that can apply on its own and sets an {@code argLine}, or a property one of these refers to, may replace them, so then no option
     * counts.
     *
     * @param code the POM without comments
     */
    private static Stream<String> surefireJavaAgents(String code) {
        boolean[] profiles = mavenProfiles(code, new boolean[code.length()]);
        Stream<MatchResult> profileArgLines = Stream.concat(MAVEN_ARG_LINE.matcher(code).results(),
                MAVEN_EMPTY_ELEMENT.matcher(code).results().filter(element -> "argLine".equals(element.group(1))));
        if (profileArgLines.anyMatch(argLine -> profiles[argLine.start()] && inProfileWithActivation(code, argLine))) {
            return Stream.empty();
        }
        boolean[] surefire = new boolean[code.length()];
        MAVEN_PLUGIN.matcher(code).results().filter(plugin -> MAVEN_SUREFIRE.matcher(plugin.group()).find())
                .forEach(plugin -> Arrays.fill(surefire, plugin.start(), plugin.end(), true));
        boolean[] properties = new boolean[code.length()];
        Map<String, String> values = new HashMap<>();
        Set<String> replaceable = new HashSet<>();
        MAVEN_PROPERTIES.matcher(code).results().forEach(section -> {
            if (!profiles[section.start()]) {
                Arrays.fill(properties, section.start(), section.end(), true);
                MAVEN_PROPERTY.matcher(section.group(1)).results().forEach(property -> values.put(property.group(1), property.group(2)));
                MAVEN_EMPTY_ELEMENT.matcher(section.group(1)).results().forEach(property -> values.put(property.group(1), ""));
            }
            else if (inProfileWithActivation(code, section)) {
                Stream.concat(MAVEN_PROPERTY.matcher(section.group(1)).results(), MAVEN_EMPTY_ELEMENT.matcher(section.group(1)).results())
                        .forEach(property -> replaceable.add(property.group(1)));
            }
        });
        List<MatchResult> argLines = MAVEN_ARG_LINE.matcher(code).results().filter(argLine -> !profiles[argLine.start()]).toList();
        List<MatchResult> configured = argLines.stream().filter(argLine -> surefire[argLine.start()]).toList();
        // The argLine of the configuration replaces the property, which then only counts where @{argLine} or ${argLine} refers to it.
        List<MatchResult> used = configured.isEmpty() ? argLines.stream().filter(argLine -> properties[argLine.start()]).toList() : configured;
        if (used.stream().anyMatch(argLine -> referencedProperties(argLine.group(1), values).stream().anyMatch(replaceable::contains))) {
            return Stream.empty();
        }
        return used.stream().map(argLine -> withPropertyValues(argLine.group(1), values)).flatMap(BuildFileSyntax::javaAgents);
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
     * The {@code -javaagent} options the Gradle build passes to a JVM it starts: in the arguments of {@code jvmArgs}, e.g. {@code test { jvmArgs "-javaagent:..." }},
     * or in a {@code CommandLineArgumentProvider} whose instance {@code jvmArgumentProviders} receives, like the templates register {@code AresJvmArguments}.
     * An option in a variable, a method or a provider that no task receives does not count.
     *
     * @param code the build file without comments
     */
    private static Stream<String> passedJavaAgents(String code) {
        List<int[]> passed = new ArrayList<>();
        GRADLE_JVM_ARGS.matcher(code).results().forEach(call -> passed.add(new int[] { call.start(), endOfArguments(code, call.end()) }));
        GRADLE_ARGUMENT_PROVIDER.matcher(code).results().filter(provider -> receivesProvider(code, provider.group(1)))
                .forEach(provider -> passed.add(new int[] { provider.end(), endOfBlock(code, provider.end()) }));
        return JAVA_AGENT.matcher(code).results().filter(agent -> passed.stream().anyMatch(range -> range[0] <= agent.start() && agent.start() < range[1]))
                .map(agent -> agent.group(1));
    }

    /** Whether {@code jvmArgumentProviders} receives an instance of the provider class, directly or through a variable that holds one. */
    private static boolean receivesProvider(String code, String providerClass) {
        Set<String> holders = new HashSet<>(Set.of(providerClass));
        GRADLE_ASSIGNMENT.matcher(code).results().filter(assignment -> mentions(statementAfter(code, assignment.end()), Set.of(providerClass)))
                .forEach(assignment -> holders.add(assignment.group(1)));
        return GRADLE_PROVIDER_REGISTRATION.matcher(code).results()
                .anyMatch(registration -> mentions(code.substring(registration.end(), endOfArguments(code, registration.start())), holders));
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
     * Whether Surefire runs the tests in the Maven JVM, where it ignores its {@code argLine}: with a {@code forkCount} of 0 in the POM itself or in a profile
     * that can apply on its own through an activation, e.g. {@code activeByDefault}. A profile without an activation only applies if the build names it,
     * which the build of the Artemis templates, {@code mvn clean test}, does not do.
     *
     * @param code the POM without comments
     */
    private static boolean runsTestsInMavenJvm(String code) {
        boolean[] profiles = mavenProfiles(code, new boolean[code.length()]);
        return MAVEN_TESTS_IN_PROCESS.matcher(code).results().anyMatch(forkCount -> !profiles[forkCount.start()] || inProfileWithActivation(code, forkCount));
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
        int semicolon = statement.indexOf(';');
        return semicolon < 0 ? statement : statement.substring(0, semicolon);
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
     * Whether the literal part of an agent path names Ares, e.g. {@code ares-agent.jar}. Interpolations are left out, because a variable is named freely, e.g.
     * {@code ${aresAgentJar}} may hold JaCoCo; an interpolated agent counts only through {@link #takesFileFrom(String, String, String)}.
     */
    private static boolean namesAres(String agent) {
        return INTERPOLATION.matcher(agent).replaceAll("").toLowerCase(Locale.ROOT).contains("ares");
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
        boolean[] noCode = gradle ? gradleCommentsAndStrings(content) : mavenComments(content);
        boolean[] profiles = gradle ? new boolean[content.length()] : mavenProfiles(content, noCode);
        IntPredicate counts = start -> !noCode[start] && !profiles[start] && !(gradle && declaresLocalGradleVariable(content, noCode, start));
        List<MatchResult> pins = (gradle ? GRADLE_ARES_VERSION : MAVEN_ARES_VERSION).matcher(content).results().filter(pin -> counts.test(pin.start())).toList();
        Set<Integer> readable = pins.stream().map(MatchResult::start).collect(Collectors.toSet());
        boolean allReadable = (gradle ? GRADLE_ARES_VERSION_ASSIGNMENT : MAVEN_ARES_VERSION_DEFINITION).matcher(content).results().mapToInt(MatchResult::start).filter(counts)
                .allMatch(readable::contains);
        // Pins of different versions leave open which one the dependency reads, e.g. after a later assignment of project.ext.aresVersion.
        boolean oneVersion = pins.stream().map(pin -> pin.group(2).trim()).distinct().count() <= 1;
        return allReadable && oneVersion ? pins : List.of();
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
