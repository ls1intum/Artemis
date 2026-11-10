package de.tum.cit.aet.artemis.programming.service.security;

import java.io.StringReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * Checks a policy file against the rules Ares {@value #VERIFIED_ARES_VERSION} applies when the tests run, so an exercise whose policy Ares would refuse is
 * reported as misconfigured instead of configured.
 * <p>
 * Ares is not a dependency of the server, so the rules are copied from its {@code SecurityPolicySchemaValidator}, {@code PolicyValueValidator} and the
 * constructors of its policy records. They are only valid for that version, which is why {@link SecurityPolicyRepositoryFiles} applies them only to a
 * repository that pins it. Ares reads the file with the Jackson 2 YAML parser, which is built on the events of SnakeYAML. The server uses Jackson 3, whose
 * parser reads values such as {@code True}, {@code 1_000} or {@code 0x10} differently, so this class reads the same events and resolves them like Jackson 2.
 * <p>
 * There are three deliberate differences. A missing package is rejected, although the schema of Ares permits it, because Ares then fails when it builds the
 * tests. Placeholders such as <code>${PROJECT_ROOT}</code> are checked as written and not expanded, because their values depend on the build agent. The
 * patterns are possessive, which accepts the same values without recursing, and {@link #MAX_PATTERN_VALUE_LENGTH} stands in for the stack overflow in Ares.
 */
final class SecurityPolicySchema {

    /** The Ares version these rules were copied from. */
    static final String VERIFIED_ARES_VERSION = "2.2.1";

    private static final String JAVA_RESERVED_WORD = "abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|false|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|null|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|true|try|void|volatile|while|_";

    private static final String JAVA_IDENTIFIER = "(?!(?:" + JAVA_RESERVED_WORD + ")(?=\\.|$))\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";

    private static final String JAVA_TYPE_IDENTIFIER = "(?!(?:var|yield|record|sealed|permits)$)" + JAVA_IDENTIFIER;

    private static final String JAVA_QUALIFIED_IDENTIFIER = JAVA_IDENTIFIER + "(?:\\." + JAVA_IDENTIFIER + ")*+";

    private static final String JAVA_CLASS_PATH = "(?:" + JAVA_IDENTIFIER + "\\.)*+" + JAVA_TYPE_IDENTIFIER;

    private static final String IPV4_COMPONENT = "(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)";

    private static final String IPV4_ADDRESS = "(?:" + IPV4_COMPONENT + "\\.){3}" + IPV4_COMPONENT;

    private static final String IPV6_COMPONENT = "[0-9A-Fa-f]{1,4}";

    private static final String IPV6_ADDRESS = "(?:" + "(?:" + IPV6_COMPONENT + ":){7}" + IPV6_COMPONENT + "|(?:" + IPV6_COMPONENT + ":){1,7}:" + "|(?:" + IPV6_COMPONENT
            + ":){1,6}:" + IPV6_COMPONENT + "|(?:" + IPV6_COMPONENT + ":){1,5}(?::" + IPV6_COMPONENT + "){1,2}" + "|(?:" + IPV6_COMPONENT + ":){1,4}(?::" + IPV6_COMPONENT
            + "){1,3}" + "|(?:" + IPV6_COMPONENT + ":){1,3}(?::" + IPV6_COMPONENT + "){1,4}" + "|(?:" + IPV6_COMPONENT + ":){1,2}(?::" + IPV6_COMPONENT + "){1,5}" + "|"
            + IPV6_COMPONENT + ":(?:(?::" + IPV6_COMPONENT + "){1,6})" + "|:(?:(?::" + IPV6_COMPONENT + "){1,7}|:)" + "|(?:" + IPV6_COMPONENT + ":){6}" + IPV4_ADDRESS
            + "|::(?:ffff(?::0{1,4})?:)?" + IPV4_ADDRESS + "|(?:" + IPV6_COMPONENT + ":){1,4}:" + IPV4_ADDRESS + ")";

    private static final String DNS_LABEL = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?";

    private static final String DNS_NAME = "(?!\\d+\\.\\d+\\.\\d+\\.\\d+\\.?$)(?=.{1,253}\\.?$)" + DNS_LABEL + "(?:\\." + DNS_LABEL + ")*+\\.?";

    private static final String RECOGNISED_PLACEHOLDER = "\\$\\{(?:PROJECT_ROOT|java\\.home|user\\.home|java\\.io\\.tmpdir)\\}";

    private static final Pattern LANGUAGE_CONFIGURATION = Pattern.compile("^JAVA_USING_(?:MAVEN|GRADLE)_(?:ARCHUNIT|WALA)_AND_(?:ASPECTJ|INSTRUMENTATION)$");

    private static final Pattern JAVA_PACKAGE = Pattern.compile("^" + JAVA_QUALIFIED_IDENTIFIER + "$");

    private static final Pattern JAVA_CLASS_NAME = Pattern.compile("^" + JAVA_TYPE_IDENTIFIER + "$");

    private static final Pattern JAVA_CLASS = Pattern.compile("^" + JAVA_CLASS_PATH + "$");

    private static final Pattern FILE_PATH = Pattern
            .compile("^(?:\\*|(?=.+$)(?=.*\\S)(?!(?:.*[\\\\/])?\\.\\.(?:[\\\\/]|$))(?:(?:" + RECOGNISED_PLACEHOLDER + ")|(?!\\$\\{)[^*\\x00])++)$", Pattern.DOTALL);

    private static final Pattern HOST = Pattern.compile("^(?:\\*|localhost|" + IPV4_ADDRESS + "|" + IPV6_ADDRESS + "|" + DNS_NAME + ")$");

    private static final Pattern THREAD_CLASS = Pattern
            .compile("^(?:" + JAVA_CLASS_PATH + "|\\*|Lambda-Expression|<implicit-thread-op:(?:parallelStream|parallel|Thread\\.sleep|SubmissionPublisher\\.(?:submit|offer))>)$");

    private static final String VERSION_KEY = "thisPolicyFileCompliesToThePolicyVersion";

    private static final String SUPERVISED_CODE_KEY = "regardingTheSupervisedCode";

    private static final String LANGUAGE_KEY = "theFollowingProgrammingLanguageConfigurationIsUsed";

    private static final String PACKAGE_KEY = "theSupervisedCodeUsesTheFollowingPackage";

    private static final String MAIN_CLASS_KEY = "theMainClassInsideThisPackageIs";

    private static final String TEST_CLASSES_KEY = "theFollowingClassesAreTestClasses";

    private static final String RESOURCES_KEY = "theFollowingResourceAccessesArePermitted";

    private static final String TEST_BEHAVIOR_KEY = "theFollowingTestBehaviorIsConfigured";

    private static final String FILES_KEY = "regardingFileSystemInteractions";

    private static final String NETWORK_KEY = "regardingNetworkConnections";

    private static final String COMMANDS_KEY = "regardingCommandExecutions";

    private static final String THREADS_KEY = "regardingThreadCreations";

    private static final String PACKAGES_KEY = "regardingPackageImports";

    private static final String TIMEOUTS_KEY = "regardingTimeouts";

    private static final String FILE_PATH_KEY = "onThisPathAndAllPathsBelow";

    private static final String HOST_KEY = "onTheHost";

    private static final String PORT_KEY = "onThePort";

    private static final String COMMAND_KEY = "executeTheCommand";

    private static final String ARGUMENTS_KEY = "withTheseArguments";

    private static final String THREAD_COUNT_KEY = "createTheFollowingNumberOfThreads";

    private static final String THREAD_CLASS_KEY = "ofThisClass";

    private static final String PACKAGE_IMPORT_KEY = "importTheFollowingPackage";

    private static final String TIMEOUT_KEY = "timeout";

    private static final Set<String> ROOT_FIELDS = Set.of(VERSION_KEY, SUPERVISED_CODE_KEY);

    private static final Set<String> SUPERVISED_CODE_FIELDS = Set.of(LANGUAGE_KEY, PACKAGE_KEY, MAIN_CLASS_KEY, TEST_CLASSES_KEY, RESOURCES_KEY, TEST_BEHAVIOR_KEY);

    private static final Set<String> SUPERVISED_CODE_REQUIRED_FIELDS = Set.of(LANGUAGE_KEY, PACKAGE_KEY, TEST_CLASSES_KEY, RESOURCES_KEY);

    private static final Set<String> RESOURCE_FIELDS = Set.of(FILES_KEY, NETWORK_KEY, COMMANDS_KEY, THREADS_KEY, PACKAGES_KEY, TIMEOUTS_KEY);

    private static final Set<String> FILE_FLAGS = Set.of("readAllFiles", "overwriteAllFiles", "createAllFiles", "executeAllFiles", "deleteAllFiles");

    private static final Set<String> FILE_FIELDS = withField(FILE_FLAGS, FILE_PATH_KEY);

    private static final Set<String> NETWORK_FLAGS = Set.of("openConnections", "sendData", "receiveData");

    private static final Set<String> NETWORK_FIELDS = withField(withField(NETWORK_FLAGS, HOST_KEY), PORT_KEY);

    private static final Set<String> COMMAND_FIELDS = Set.of(COMMAND_KEY, ARGUMENTS_KEY);

    private static final Set<String> THREAD_FIELDS = Set.of(THREAD_COUNT_KEY, THREAD_CLASS_KEY);

    private static final Set<String> PACKAGE_FIELDS = Set.of(PACKAGE_IMPORT_KEY);

    private static final Set<String> TIMEOUT_FIELDS = Set.of(TIMEOUT_KEY);

    private static final BigInteger MAX_PORT = BigInteger.valueOf(65535);

    private static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);

    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    /** Deeper than any valid policy, so a deeply nested file is rejected without exhausting the stack. */
    private static final int MAX_NESTING = 20;

    private static final String YAML_TAG_PREFIX = "tag:yaml.org,2002:";

    private static final Resolver RESOLVER = new Resolver();

    /** A float or binary value, which no field of a policy accepts. */
    private static final Object UNACCEPTED_SCALAR = new Object();

    /** The longest integer Jackson 2 reads by default. */
    private static final int MAX_INTEGER_LENGTH = 1000;

    /**
     * The longest value checked against a pattern. The patterns of Ares recurse once per repetition, so on a fresh JVM they overflow the stack of the test run at
     * a value of about 2000 characters. The exact length depends on the JVM, so this limit lies safely below it. No usable value comes close to it.
     */
    static final int MAX_PATTERN_VALUE_LENGTH = 1024;

    private static final SecurityPolicyYaml.ReadPolicy INVALID_POLICY = new SecurityPolicyYaml.ReadPolicy(false, Optional.empty());

    private SecurityPolicySchema() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static Set<String> withField(Set<String> fields, String field) {
        return Stream.concat(fields.stream(), Stream.of(field)).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @param yaml the content of a policy file
     * @return what could be read from it; valid only if Ares {@value #VERIFIED_ARES_VERSION} accepts the policy
     */
    static SecurityPolicyYaml.ReadPolicy read(String yaml) {
        if (yaml.length() > SecurityPolicyYaml.MAX_POLICY_SIZE) {
            return INVALID_POLICY;
        }
        try {
            if (readDocument(yaml) instanceof Map<?, ?> root && isRoot(root)) {
                return new SecurityPolicyYaml.ReadPolicy(true, SecurityPolicyYaml.testClasses(root));
            }
            return INVALID_POLICY;
        }
        catch (YAMLException | NumberFormatException e) {
            return INVALID_POLICY;
        }
    }

    /**
     * Reads a policy file the way Ares reads it, without checking it against the rules of any Ares version.
     *
     * @param yaml the content of a policy file
     * @return the only document of the file as nested maps, lists and scalars; empty if it is too large or is no single YAML document Ares could read
     */
    static Optional<Object> readTree(String yaml) {
        if (yaml.length() > SecurityPolicyYaml.MAX_POLICY_SIZE) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(readDocument(yaml));
        }
        catch (YAMLException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * @return the only document of the file as nested maps, lists and scalars
     * @throws YAMLException if the file is no single YAML document Ares could read
     */
    private static @Nullable Object readDocument(String yaml) {
        Iterator<Event> events = SecurityPolicyYaml.newYaml().parse(new StringReader(yaml)).iterator();
        events.next();
        Event start = events.next();
        if (!start.is(Event.ID.DocumentStart)) {
            throw new YAMLException("The file contains no document");
        }
        Object document = readValue(events, events.next(), 0);
        events.next();
        if (!events.next().is(Event.ID.StreamEnd)) {
            throw new YAMLException("The file contains more than one document");
        }
        return document;
    }

    private static @Nullable Object readValue(Iterator<Event> events, Event event, int depth) {
        if (depth > MAX_NESTING) {
            throw new YAMLException("The file is nested too deeply");
        }
        return switch (event) {
            case ScalarEvent scalar -> resolveScalar(scalar);
            // Jackson 2 does not resolve aliases; it reports the name of the anchor as a string.
            case AliasEvent alias -> alias.getAnchor();
            case MappingStartEvent _ -> readMapping(events, depth);
            case SequenceStartEvent _ -> readSequence(events, depth);
            default -> throw new YAMLException("Unexpected event " + event);
        };
    }

    private static Map<String, Object> readMapping(Iterator<Event> events, int depth) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        for (Event key = events.next(); !key.is(Event.ID.MappingEnd); key = events.next()) {
            if (!(key instanceof ScalarEvent name) || mapping.containsKey(name.getValue())) {
                throw new YAMLException("Field names must be unique scalars");
            }
            mapping.put(name.getValue(), readValue(events, events.next(), depth + 1));
        }
        return mapping;
    }

    private static List<Object> readSequence(Iterator<Event> events, int depth) {
        List<Object> sequence = new ArrayList<>();
        for (Event entry = events.next(); !entry.is(Event.ID.SequenceEnd); entry = events.next()) {
            sequence.add(readValue(events, entry, depth + 1));
        }
        return sequence;
    }

    /**
     * Resolves a scalar like the Jackson 2 YAML parser with {@code PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS}, which Ares enables.
     *
     * @return a {@link String}, {@link BigInteger}, {@link Boolean}, {@code null} or {@link #UNACCEPTED_SCALAR}
     */
    private static @Nullable Object resolveScalar(ScalarEvent scalar) {
        String value = scalar.getValue();
        String tag = scalar.getTag();
        if (tag == null || "!".equals(tag)) {
            tag = RESOLVER.resolve(NodeId.scalar, value, scalar.getImplicit().canOmitTagInPlainScalar()).getValue();
        }
        else if (value.isEmpty()) {
            // Jackson 2 ignores the explicit tag of an empty scalar and reports a string, which no field accepts.
            return value;
        }
        return switch (typeOfTag(tag)) {
            case "int" -> resolveInteger(value);
            case "float", "binary" -> UNACCEPTED_SCALAR;
            case "null" -> null;
            case "bool" -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) ? (Object) Boolean.valueOf(value) : value;
            default -> value;
        };
    }

    /**
     * @return the name of a standard YAML type, without anything Jackson 2 ignores after a comma; any other tag as it is
     */
    private static String typeOfTag(String tag) {
        if (!tag.startsWith(YAML_TAG_PREFIX)) {
            return tag;
        }
        String type = tag.substring(YAML_TAG_PREFIX.length());
        int comma = type.indexOf(',');
        return comma < 0 ? type : type.substring(0, comma);
    }

    /**
     * @return the integer, in the notations of YAML 1.1 (sign, underscores, and the prefixes {@code 0b}, {@code 0x} and {@code 0}); the text itself if it is no
     *         integer
     * @throws NumberFormatException if a tagged scalar has a prefix but no valid digits
     */
    private static Object resolveInteger(String value) {
        boolean negative = value.startsWith("-");
        String digits = negative || value.startsWith("+") ? value.substring(1) : value;
        int radix = 10;
        if (digits.equals("0")) {
            return BigInteger.ZERO;
        }
        if (digits.startsWith("0")) {
            char marker = digits.charAt(1);
            if (marker == 'b' || marker == 'B') {
                radix = 2;
                digits = digits.substring(2);
            }
            else if (marker == 'x' || marker == 'X') {
                radix = 16;
                digits = digits.substring(2);
            }
            else if ((marker >= '0' && marker <= '9') || marker == '_') {
                radix = 8;
                digits = digits.substring(1);
            }
            else {
                return value;
            }
        }
        else if (!digits.chars().allMatch(digit -> (digit >= '0' && digit <= '9') || digit == '_')) {
            return value;
        }
        String cleanedDigits = digits.replace("_", "");
        // Jackson 2 counts the minus sign of a decimal number, but no sign in front of a prefixed one.
        int countedLength = cleanedDigits.length() + (negative && radix == 10 ? 1 : 0);
        if (countedLength > MAX_INTEGER_LENGTH) {
            throw new NumberFormatException("The integer is too long");
        }
        BigInteger integer = new BigInteger(cleanedDigits, radix);
        return negative ? integer.negate() : integer;
    }

    private static boolean isRoot(Map<?, ?> root) {
        return hasFields(root, ROOT_FIELDS, ROOT_FIELDS) && isIntegerBetween(root.get(VERSION_KEY), BigInteger.ONE, BigInteger.ONE)
                && isSupervisedCode(root.get(SUPERVISED_CODE_KEY));
    }

    private static boolean isSupervisedCode(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> code) || !hasFields(code, SUPERVISED_CODE_FIELDS, SUPERVISED_CODE_REQUIRED_FIELDS)) {
            return false;
        }
        boolean validMainClass = code.get(MAIN_CLASS_KEY) == null || isText(code.get(MAIN_CLASS_KEY), JAVA_CLASS_NAME);
        boolean validTestBehavior = !code.containsKey(TEST_BEHAVIOR_KEY) || (code.get(TEST_BEHAVIOR_KEY) instanceof Map<?, ?> behavior && behavior.isEmpty());
        return validMainClass && validTestBehavior && isText(code.get(LANGUAGE_KEY), LANGUAGE_CONFIGURATION) && isText(code.get(PACKAGE_KEY), JAVA_PACKAGE)
                && allMatch(code.get(TEST_CLASSES_KEY), entry -> isText(entry, JAVA_CLASS)) && isResources(code.get(RESOURCES_KEY));
    }

    private static boolean isResources(@Nullable Object value) {
        return value instanceof Map<?, ?> resources && hasFields(resources, RESOURCE_FIELDS, RESOURCE_FIELDS)
                && allMatch(resources.get(FILES_KEY), SecurityPolicySchema::isFilePermission) && allMatch(resources.get(NETWORK_KEY), SecurityPolicySchema::isNetworkPermission)
                && allMatch(resources.get(COMMANDS_KEY), SecurityPolicySchema::isCommandPermission)
                && allMatch(resources.get(THREADS_KEY), SecurityPolicySchema::isThreadPermission)
                && allMatch(resources.get(PACKAGES_KEY), SecurityPolicySchema::isPackagePermission)
                && allMatch(resources.get(TIMEOUTS_KEY), SecurityPolicySchema::isTimeoutPermission);
    }

    private static boolean isFilePermission(@Nullable Object value) {
        return value instanceof Map<?, ?> permission && hasFields(permission, FILE_FIELDS, FILE_FIELDS) && isText(permission.get(FILE_PATH_KEY), FILE_PATH)
                && FILE_FLAGS.stream().allMatch(flag -> permission.get(flag) instanceof Boolean);
    }

    private static boolean isNetworkPermission(@Nullable Object value) {
        return value instanceof Map<?, ?> permission && hasFields(permission, NETWORK_FIELDS, NETWORK_FIELDS) && isText(permission.get(HOST_KEY), HOST)
                && isIntegerBetween(permission.get(PORT_KEY), BigInteger.ZERO, MAX_PORT) && NETWORK_FLAGS.stream().allMatch(flag -> permission.get(flag) instanceof Boolean);
    }

    private static boolean isCommandPermission(@Nullable Object value) {
        if (value instanceof String command) {
            return !command.isBlank();
        }
        return value instanceof Map<?, ?> permission && hasFields(permission, COMMAND_FIELDS, COMMAND_FIELDS) && isNonBlankText(permission.get(COMMAND_KEY))
                && allMatch(permission.get(ARGUMENTS_KEY), SecurityPolicySchema::isNonBlankText);
    }

    private static boolean isThreadPermission(@Nullable Object value) {
        return value instanceof Map<?, ?> permission && hasFields(permission, THREAD_FIELDS, THREAD_FIELDS)
                && isIntegerBetween(permission.get(THREAD_COUNT_KEY), BigInteger.ZERO, MAX_INT) && isText(permission.get(THREAD_CLASS_KEY), THREAD_CLASS);
    }

    private static boolean isPackagePermission(@Nullable Object value) {
        return value instanceof Map<?, ?> permission && hasFields(permission, PACKAGE_FIELDS, PACKAGE_FIELDS)
                && ("*".equals(permission.get(PACKAGE_IMPORT_KEY)) || isText(permission.get(PACKAGE_IMPORT_KEY), JAVA_PACKAGE));
    }

    private static boolean isTimeoutPermission(@Nullable Object value) {
        return value instanceof Map<?, ?> permission && hasFields(permission, TIMEOUT_FIELDS, TIMEOUT_FIELDS)
                && isIntegerBetween(permission.get(TIMEOUT_KEY), BigInteger.ONE, MAX_LONG);
    }

    /**
     * @return whether the mapping has no unknown field and every required field has a value
     */
    private static boolean hasFields(Map<?, ?> mapping, Set<String> allowedFields, Set<String> requiredFields) {
        return allowedFields.containsAll(mapping.keySet()) && requiredFields.stream().allMatch(field -> mapping.get(field) != null);
    }

    private static boolean allMatch(@Nullable Object value, Predicate<Object> rule) {
        return value instanceof List<?> entries && entries.stream().allMatch(rule);
    }

    private static boolean isNonBlankText(@Nullable Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private static boolean isText(@Nullable Object value, Pattern pattern) {
        return isNonBlankText(value) && ((String) value).length() <= MAX_PATTERN_VALUE_LENGTH && pattern.matcher((String) value).matches();
    }

    /**
     * Ares reads these values into {@code int} and {@code long} fields and only accepts integral YAML numbers, so a value such as {@code 1.0} is not an integer.
     */
    private static boolean isIntegerBetween(@Nullable Object value, BigInteger minimum, BigInteger maximum) {
        return value instanceof BigInteger integer && integer.compareTo(minimum) >= 0 && integer.compareTo(maximum) <= 0;
    }
}
