package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Detects whether the build of a tests repository attaches the Ares agent to the JVM of its tests, which Ares needs to enforce a policy whose language
 * configuration uses {@code INSTRUMENTATION}, e.g. to scan the libraries students use. {@link BuildFileSyntax} tells which parts of a build file are comments,
 * strings or Maven profiles, so that an agent in a comment is not mistaken for one the build attaches.
 */
final class AresAgentDetection {

    /** The attributes of an element, e.g. {@code combine.self="override"}, which do not change what it is. */
    private static final String MAVEN_ATTRIBUTES = "(?:\\s[^<>/]*+)?";

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

    /**
     * The argument of a {@code -javaagent} option; group 1 is the agent, up to the end of the string or argument. The option must start an argument: after
     * whitespace, a quote, a bracket, a comma or the start of an element, not within the value of another option, e.g. not in
     * {@code -Dexample=-javaagent:ares-agent.jar}, which only sets a system property.
     */
    private static final Pattern JAVA_AGENT = Pattern.compile("(?<![^\\s'\"\\[(,>])-javaagent:((?:\\$\\{[^}'\"\\n]*+}?|[^'\"\\s,)<$]|\\$(?!\\{))*+)");

    /** The Ares agent as a Maven artifact: the {@code agent} classifier of the {@code ares} artifact. */
    private static final Pattern MAVEN_ARES_AGENT = Pattern
            .compile("<artifactId>\\s*ares\\s*</artifactId>\\s*(<version>[^<]*</version>\\s*)?(<type>[^<]*</type>\\s*)?<classifier>\\s*agent\\s*</classifier>");

    /** The {@code forkCount} of a Surefire configuration or its property; group 1 is its value, e.g. {@code 0}, {@code 1C} or {@code ${test.forks}}. */
    private static final Pattern MAVEN_FORK_COUNT = Pattern.compile("<forkCount" + MAVEN_ATTRIBUTES + ">([^<]*+)</forkCount\\s*+>");

    /** A {@code forkCount} of 0, also as {@code 0C}, a multiple of the number of cores: Surefire then runs the tests in the Maven JVM and ignores its {@code argLine}. */
    private static final Pattern IN_PROCESS_FORK_COUNT = Pattern.compile("\\s*+0++(?:\\.0*+)?C?\\s*+");

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

    /** The configuration of an execution that replaces the configuration of the plugin instead of being merged into it. */
    private static final Pattern MAVEN_OVERRIDING_CONFIGURATION = Pattern.compile("<configuration\\s[^>]*?combine\\.self\\s*+=\\s*+['\"]override['\"]");

    /** An execution that runs no tests: bound to no phase or told to skip them. */
    private static final Pattern MAVEN_SKIPPED_EXECUTION = Pattern.compile("<phase>\\s*+none\\s*+</phase>|<(skip|skipTests|skipExec)\\s*+>\\s*+true\\s*+</\\1\\s*+>");

    /** The properties of a POM; group 1 is their content. */
    private static final Pattern MAVEN_PROPERTIES = Pattern.compile("<properties\\s*+>(.*?)</properties\\s*+>", Pattern.DOTALL);

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

    private static final Pattern WHITESPACE = Pattern.compile("\\s++");

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

    /**
     * The method of an argument provider that returns its arguments, e.g. {@code Iterable<String> asArguments() { ... }} of the templates; its body starts after the
     * match.
     */
    private static final Pattern GRADLE_AS_ARGUMENTS = Pattern.compile("(?<![\\w$.])asArguments\\s*+\\(\\s*+\\)\\s*+\\{");

    /** The start of the statement that returns the value of a method, with or without {@code return}. */
    private static final Pattern GRADLE_RETURN = Pattern.compile("\\s*+(?:return(?![\\w$])\\s*+)?");

    /** The registration of an argument provider with a task, e.g. {@code jvmArgumentProviders.add(aresJvmArguments)}. */
    private static final Pattern GRADLE_PROVIDER_REGISTRATION = Pattern.compile("(?<![\\w$])jvmArgumentProviders\\s*+(?:\\.\\s*+(?:add|addAll)\\s*+\\(|<<|\\+=)");

    /** An assignment to a variable or property, e.g. {@code def aresJvmArguments = objects.newInstance(AresJvmArguments)}; group 1 is its name. */
    private static final Pattern GRADLE_ASSIGNMENT = Pattern.compile("(?<![\\w$])([A-Za-z_$][\\w$]*+)\\s*+=(?![=~])");

    private AresAgentDetection() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Whether the build attaches the Ares agent with {@code -javaagent}. Only then does Ares enforce a policy whose language configuration uses
     * {@code INSTRUMENTATION}; the build files of the Artemis templates attach it. A {@code -javaagent} option counts if it names the Ares agent, e.g.
     * {@code ares-agent.jar}, or, in Gradle, takes its file from the configuration that declares the Ares agent, like the templates do with
     * {@code configurations.aresAgent}. Another agent, such as JaCoCo, and text in comments do not count. Only the build file of the build tool that pins
     * the Ares version counts, see {@link SecurityPolicyRepositoryFiles#detectBuildTool(String, String)}, since another build file the repository keeps,
     * e.g. a {@code pom.xml} next to the {@code build.gradle}, does not run; without a pin, the Gradle build counts if there is one. Only an option that
     * reaches the test JVM counts, see {@link #surefireAttachesAres(String)} and {@link #passedJavaAgents(String)}.
     *
     * @param buildFiles the build files at the root of a repository
     * @return whether the build file of the repository's build tool attaches the Ares agent
     */
    static boolean attachesAresAgent(SecurityPolicyRepositoryFiles.BuildFiles buildFiles) {
        Optional<SecurityPolicyRepositoryFiles.BuildTool> buildTool = SecurityPolicyRepositoryFiles.detectBuildTool(buildFiles.gradleBuild(), buildFiles.mavenPom());
        boolean maven = buildTool.map(SecurityPolicyRepositoryFiles.BuildTool.MAVEN::equals).orElse(buildFiles.gradleBuild() == null);
        if (maven) {
            return Stream.ofNullable(buildFiles.mavenPom()).map(content -> BuildFileSyntax.withoutMarked(content, BuildFileSyntax.mavenComments(content)))
                    .anyMatch(code -> MAVEN_ARES_AGENT.matcher(code).find() && surefireAttachesAres(code));
        }
        return gradleAttachesAresAgent(BuildFileSyntax.withoutMarked(buildFiles.gradleBuild(), BuildFileSyntax.gradleComments(buildFiles.gradleBuild())));
    }

    /**
     * Whether Surefire passes the Ares agent to every test JVM it starts. Each execution that runs tests needs a {@code -javaagent} option that names Ares in
     * its {@code argLine}, and must fork the test JVM, since Surefire ignores the {@code argLine} with a {@code forkCount} of 0, also as {@code 0C}. An
     * execution takes each setting from its own configuration, or else from the one of the plugin, unless its configuration replaces the one of the plugin
     * with {@code combine.self="override"}, or else from Surefire's property, e.g. {@code argLine} or {@code forkCount}. The properties a value refers to
     * are replaced by their values, e.g. {@code @{argLine}} or {@code ${test.forks}}; a {@code forkCount} whose value Artemis cannot tell counts as 0. The
     * configuration of another plugin, e.g. Failsafe, or elsewhere in the POM does not count. A profile that can apply on its own and sets an
     * {@code argLine}, a {@code forkCount} of 0 or a property one of these refers to may replace them, so then the agent does not count; a profile without an
     * activation only applies if the build names it, which the build of the Artemis templates, {@code mvn clean test}, does not do.
     *
     * @param code the POM without comments
     */
    private static boolean surefireAttachesAres(String code) {
        boolean[] profiles = BuildFileSyntax.mavenProfiles(code, new boolean[code.length()]);
        Stream<MatchResult> profileArgLines = Stream.concat(MAVEN_ARG_LINE.matcher(code).results(),
                MAVEN_EMPTY_ELEMENT.matcher(code).results().filter(element -> "argLine".equals(element.group(1))));
        if (profileArgLines.anyMatch(argLine -> profiles[argLine.start()] && inProfileWithActivation(code, argLine))) {
            return false;
        }
        MavenProperties properties = mavenProperties(code, profiles);
        List<String> plugins = surefirePlugins(code, profiles, false);
        if (surefirePlugins(code, profiles, true).stream().filter(declaration -> !plugins.contains(declaration)).flatMap(declaration -> settings(declaration, MAVEN_FORK_COUNT))
                .anyMatch(forkCount -> inProcess(forkCount, properties))) {
            return false;
        }
        SurefireSettings property = new SurefireSettings(
                MAVEN_ARG_LINE.matcher(code).results().filter(argLine -> properties.declared().get(argLine.start())).map(argLine -> argLine.group(1)).toList(),
                properties.values().containsKey("forkCount") || properties.replaceable().contains("forkCount") ? List.of("${forkCount}") : List.of());
        SurefireSettings plugin = property.under(new SurefireSettings(plugins.stream().flatMap(declaration -> settings(withoutExecutions(declaration), MAVEN_ARG_LINE)).toList(),
                plugins.stream().flatMap(declaration -> settings(withoutExecutions(declaration), MAVEN_FORK_COUNT)).toList()));
        List<SurefireSettings> runs = new ArrayList<>();
        boolean declaresDefaultExecution = false;
        for (String execution : plugins.stream().flatMap(declaration -> MAVEN_EXECUTION.matcher(declaration).results().map(MatchResult::group)).toList()) {
            declaresDefaultExecution |= MAVEN_DEFAULT_TEST_EXECUTION.matcher(execution).find();
            if (!MAVEN_SKIPPED_EXECUTION.matcher(execution).find()) {
                SurefireSettings inherited = MAVEN_OVERRIDING_CONFIGURATION.matcher(execution).find() ? property : plugin;
                runs.add(inherited.under(new SurefireSettings(settings(execution, MAVEN_ARG_LINE).toList(), settings(execution, MAVEN_FORK_COUNT).toList())));
            }
        }
        if (!declaresDefaultExecution) {
            runs.add(plugin);
        }
        return runs.stream().allMatch(run -> run.forkCounts().stream().noneMatch(forkCount -> inProcess(forkCount, properties)) && !run.argLines().isEmpty()
                && run.argLines().stream().allMatch(argLine -> passesAresAgent(argLine, properties)));
    }

    /**
     * The settings a Surefire execution runs with.
     *
     * @param argLines   the {@code argLine} values; more than one if several declarations of Surefire set one, which all have to attach the agent then
     * @param forkCounts the {@code forkCount} values, none for the default of 1
     */
    private record SurefireSettings(List<String> argLines, List<String> forkCounts) {

        /** @return these settings, with each one the given settings set taking its place */
        SurefireSettings under(SurefireSettings own) {
            return new SurefireSettings(own.argLines().isEmpty() ? argLines : own.argLines(), own.forkCounts().isEmpty() ? forkCounts : own.forkCounts());
        }
    }

    private static Stream<String> settings(String configuration, Pattern setting) {
        return setting.matcher(configuration).results().map(match -> match.group(1));
    }

    private static String withoutExecutions(String plugin) {
        return MAVEN_EXECUTIONS.matcher(plugin).replaceAll("");
    }

    /** Whether a {@code forkCount} may be 0: its value, with the properties it refers to replaced, is 0, or Artemis cannot tell it. */
    private static boolean inProcess(String forkCount, MavenProperties properties) {
        if (referencedProperties(forkCount, properties.values()).stream().anyMatch(properties.replaceable()::contains)) {
            return true;
        }
        String value = withPropertyValues(forkCount, properties.values());
        return MAVEN_PROPERTY_REFERENCE.matcher(value).find() || IN_PROCESS_FORK_COUNT.matcher(value).matches();
    }

    private static boolean passesAresAgent(String argLine, MavenProperties properties) {
        boolean replaceable = referencedProperties(argLine, properties.values()).stream().anyMatch(properties.replaceable()::contains);
        return !replaceable && javaAgents(withPropertyValues(argLine, properties.values())).anyMatch(AresAgentDetection::namesAres);
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
     * @param declared    the offsets of the characters of the POM that lie in the properties of the POM itself
     */
    private record MavenProperties(Map<String, String> values, Set<String> replaceable, BitSet declared) {
    }

    private static MavenProperties mavenProperties(String code, boolean[] profiles) {
        BitSet declared = new BitSet(code.length());
        Map<String, String> values = new HashMap<>();
        Set<String> replaceable = new HashSet<>();
        MAVEN_PROPERTIES.matcher(code).results().forEach(section -> {
            if (!profiles[section.start()]) {
                declared.set(section.start(), section.end());
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
                    .filter(Objects::nonNull).toList();
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
        boolean[] strings = BuildFileSyntax.gradleCommentsAndStrings(code);
        List<MatchResult> providers = GRADLE_ARGUMENT_PROVIDER.matcher(code).results().filter(provider -> !strings[provider.start()]).toList();
        Stream<MatchResult> calls = Stream.concat(GRADLE_JVM_ARGS.matcher(code).results(), GRADLE_PROVIDER_REGISTRATION.matcher(code).results());
        List<String> arguments = new ArrayList<>();
        List<String> provided = new ArrayList<>();
        for (MatchResult call : calls.filter(call -> !strings[call.start()] && configuresTestTasks(code, strings, call.start())).sorted(Comparator.comparingInt(MatchResult::start))
                .toList()) {
            if (call.group().startsWith("jvmArgumentProviders")) {
                registeredProviders(code, call, providers).forEach(provider -> provided.addAll(providedAgents(code, strings, provider)));
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

    /**
     * The agents an argument provider passes: those in the value that its {@code asArguments()} returns as its only statement, e.g. the list of arguments of
     * the templates. Another method of the class passes nothing, and a provider whose {@code asArguments()} computes the value in several statements, or
     * that inherits the method, passes none that Artemis can tell.
     *
     * @param strings for each character of the build file whether it belongs to a string
     */
    private static List<String> providedAgents(String code, boolean[] strings, MatchResult provider) {
        Matcher method = GRADLE_AS_ARGUMENTS.matcher(code).region(provider.end(), endOfBlock(code, provider.end()));
        while (method.find()) {
            // Only the method of the provider itself, not one of a class or closure in it.
            if (strings[method.start()] || enclosingBraces(code, strings, method.start()).getFirst() != provider.end() - 1) {
                continue;
            }
            int bodyEnd = endOfBlock(code, method.end());
            Matcher returned = GRADLE_RETURN.matcher(code).region(method.end(), bodyEnd);
            int valueStart = returned.lookingAt() ? returned.end() : method.end();
            int valueEnd = Math.min(endOfArguments(code, valueStart), bodyEnd);
            return code.substring(valueEnd, bodyEnd).isBlank() ? javaAgentsIn(code, valueStart, valueEnd) : List.of();
        }
        return List.of();
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
            testTask |= GRADLE_TEST_TASK_BLOCK.matcher(WHITESPACE.matcher(opener).replaceAll("")).matches();
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

    /** Whether the match lies in a profile that can apply on its own through an activation, e.g. {@code activeByDefault}. */
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
        return GRADLE_ARES_AGENT.matcher(code).results().map(AresAgentDetection::configurationOf).distinct().anyMatch(configuration -> {
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
        boolean escaped = false;
        for (int index = 0; index < statement.length(); index++) {
            char character = statement.charAt(index);
            if (escaped) {
                escaped = false;
            }
            else if (quote != 0) {
                if (character == '\\') {
                    escaped = true;
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
}
