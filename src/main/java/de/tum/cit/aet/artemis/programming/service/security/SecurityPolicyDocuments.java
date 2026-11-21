package de.tum.cit.aet.artemis.programming.service.security;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;

import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.CommandPermissionDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.FilePermissionDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.HiddenTestsDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.NetworkPermissionDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.OutputMirroringDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.PackagePermissionDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.PrivilegedExceptionsDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.StrictTimeoutDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.TestBehaviorDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.ThreadPermissionDTO;

/**
 * Converts an Ares policy file into the {@link SecurityPolicyDocumentDTO} the policy editor edits, and renders such a document back into a policy file.
 * <p>
 * The editor rewrites the whole file, so a file is only offered for editing if nothing but its formatting and its comments would get lost: it is read like
 * Ares reads it ({@link SecurityPolicySchema#readTree(String)}), converted into a document, rendered again, and the rendered file must read back as the
 * same tree. Otherwise it is shown read-only with the reason. The comments at the top of a file are kept when it is rendered.
 * <p>
 * Rendering is deterministic: the keys follow the order of the Ares documentation, strings are double-quoted and escaped, and empty lists are written as
 * {@code [ ]} like in the templates. Whether a pinned Ares version accepts the rendered file is decided by {@link AresCompatibilityManifest} and
 * {@link SecurityPolicySchema}, not here; {@link #validate(SecurityPolicyDocumentDTO)} only rejects values no version could accept.
 */
public final class SecurityPolicyDocuments {

    /** Why the editor shows a policy read-only. */
    public enum ReadOnlyReason {
        /** The file does not exist or cannot be read. */
        MISSING,
        /** The file is larger than the Security Framework reads. */
        TOO_LARGE,
        /** The path leaves the repository or points to something other than a regular file. */
        INVALID_PATH,
        /** The file is no valid YAML. */
        YAML_ERROR,
        /** The file uses a field or a value the editor cannot represent, so rewriting it would change the policy. */
        UNSUPPORTED_CONTENT,
        /**
         * The file lists a resource more than once, e.g. two file permissions for one path. The editor identifies a permission by its resource, so it could
         * neither show nor change the two apart.
         */
        DUPLICATE_RESOURCE
    }

    /**
     * The result of reading a policy file for the editor.
     *
     * @param document  the document; absent if the file is shown read-only
     * @param reason    why it is shown read-only; absent if it can be edited
     * @param errorLine the 1-based line of the YAML error, only for {@link ReadOnlyReason#YAML_ERROR}
     */
    public record Parsed(@Nullable SecurityPolicyDocumentDTO document, @Nullable ReadOnlyReason reason, @Nullable Integer errorLine) {

        private static Parsed readOnly(ReadOnlyReason reason) {
            return new Parsed(null, reason, null);
        }
    }

    static final String VERSION_KEY = "thisPolicyFileCompliesToThePolicyVersion";

    static final String PRESET_KEY = "basedOnTheFollowingPreset";

    private static final String SUPERVISED_CODE_KEY = "regardingTheSupervisedCode";

    private static final String LANGUAGE_KEY = "theFollowingProgrammingLanguageConfigurationIsUsed";

    private static final String PACKAGE_KEY = "theSupervisedCodeUsesTheFollowingPackage";

    private static final String MAIN_CLASS_KEY = "theMainClassInsideThisPackageIs";

    private static final String TEST_CLASSES_KEY = "theFollowingClassesAreTestClasses";

    private static final String RESOURCES_KEY = "theFollowingResourceAccessesArePermitted";

    private static final String BEHAVIOR_KEY = "theFollowingTestBehaviorIsConfigured";

    private static final String FILES_KEY = "regardingFileSystemInteractions";

    private static final String NETWORK_KEY = "regardingNetworkConnections";

    private static final String COMMANDS_KEY = "regardingCommandExecutions";

    private static final String THREADS_KEY = "regardingThreadCreations";

    private static final String PACKAGES_KEY = "regardingPackageImports";

    private static final String TIMEOUTS_KEY = "regardingTimeouts";

    private static final String STRICT_TIMEOUTS_KEY = "regardingStrictTimeouts";

    private static final String OUTPUT_MIRRORING_KEY = "regardingOutputMirroring";

    private static final String HIDDEN_TESTS_KEY = "regardingHiddenTests";

    private static final String PRIVILEGED_EXCEPTIONS_KEY = "regardingPrivilegedExceptions";

    private static final String PATH_KEY = "onThisPathAndAllPathsBelow";

    private static final String READ_KEY = "readAllFiles";

    private static final String OVERWRITE_KEY = "overwriteAllFiles";

    private static final String CREATE_KEY = "createAllFiles";

    private static final String EXECUTE_KEY = "executeAllFiles";

    private static final String DELETE_KEY = "deleteAllFiles";

    private static final String HOST_KEY = "onTheHost";

    private static final String PORT_KEY = "onThePort";

    private static final String OPEN_KEY = "openConnections";

    private static final String SEND_KEY = "sendData";

    private static final String RECEIVE_KEY = "receiveData";

    private static final String COMMAND_KEY = "executeTheCommand";

    private static final String ARGUMENTS_KEY = "withTheseArguments";

    private static final String THREAD_COUNT_KEY = "createTheFollowingNumberOfThreads";

    private static final String THREAD_CLASS_KEY = "ofThisClass";

    private static final String PACKAGE_IMPORT_KEY = "importTheFollowingPackage";

    private static final String TIMEOUT_KEY = "timeout";

    private static final String TIMEOUT_IS_KEY = "theTimeoutIs";

    private static final String TIME_UNIT_KEY = "theTimeUnitIs";

    private static final String GRACE_KEY = "theTerminationGraceIs";

    private static final String GRACE_UNIT_KEY = "theTerminationGraceUnitIs";

    private static final String MIRRORED_KEY = "theOutputIsMirrored";

    private static final String MAXIMUM_CHARACTERS_KEY = "theMaximumCharacterCountIs";

    private static final String DEADLINE_KEY = "theDeadlineIs";

    private static final String ALWAYS_RUN_BEFORE_KEY = "hiddenTestsAlwaysRunBefore";

    private static final String UNLISTED_HIDDEN_KEY = "unlistedTestsAreHidden";

    private static final String PRIVILEGED_ONLY_KEY = "onlyPrivilegedExceptionsAreReported";

    private static final String FAILURE_MESSAGE_KEY = "theFailureMessageIs";

    private static final Set<String> TIME_UNITS = Arrays.stream(TimeUnit.values()).map(TimeUnit::name).collect(Collectors.toUnmodifiableSet());

    private static final BigInteger MAX_PORT = BigInteger.valueOf(65535);

    private static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);

    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    private static final String INDENT = "  ";

    private SecurityPolicyDocuments() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Reads a policy file for the editor.
     *
     * @param policyPath the path of the file relative to the repository root
     * @param content    the content of the file; null if it cannot be read
     * @param problem    why the file cannot be read, if it cannot
     * @return the document, or why the file is shown read-only
     */
    public static Parsed parse(String policyPath, @Nullable String content, SecurityFrameworkState.@Nullable PolicyFileProblem problem) {
        if (content == null) {
            if (problem == SecurityFrameworkState.PolicyFileProblem.TOO_LARGE) {
                return Parsed.readOnly(ReadOnlyReason.TOO_LARGE);
            }
            return Parsed.readOnly(problem == SecurityFrameworkState.PolicyFileProblem.INVALID_PATH ? ReadOnlyReason.INVALID_PATH : ReadOnlyReason.MISSING);
        }
        Optional<Object> tree = SecurityPolicySchema.readTree(content);
        if (tree.isEmpty()) {
            return content.length() > SecurityPolicyYaml.MAX_POLICY_SIZE ? Parsed.readOnly(ReadOnlyReason.TOO_LARGE)
                    : new Parsed(null, ReadOnlyReason.YAML_ERROR, errorLine(content));
        }
        Optional<SecurityPolicyDocumentDTO> document = toDocument(policyPath, tree.get());
        if (document.isEmpty()) {
            return Parsed.readOnly(ReadOnlyReason.UNSUPPORTED_CONTENT);
        }
        // Rewriting must keep everything Ares reads; only formatting and comments may differ.
        Optional<Object> rendered = SecurityPolicySchema.readTree(render(document.get(), content));
        if (rendered.isEmpty() || !normalized(rendered.get()).equals(normalized(tree.get()))) {
            return Parsed.readOnly(ReadOnlyReason.UNSUPPORTED_CONTENT);
        }
        if (listsAResourceTwice(document.get())) {
            return Parsed.readOnly(ReadOnlyReason.DUPLICATE_RESOURCE);
        }
        return new Parsed(document.get(), null, null);
    }

    /**
     * @return whether the document lists a resource more than once, with the identity the editor gives a permission: the path of a file, the host and port
     *         of an endpoint, the command with its arguments, the thread class and the package
     */
    private static boolean listsAResourceTwice(SecurityPolicyDocumentDTO document) {
        return hasDuplicates(document.files().stream().map(FilePermissionDTO::path))
                || hasDuplicates(document.network().stream().map(endpoint -> Arrays.asList(endpoint.host(), endpoint.port())))
                || hasDuplicates(document.commands().stream().map(command -> Stream.concat(Stream.of(command.command()), command.arguments().stream()).toList()))
                || hasDuplicates(document.threads().stream().map(ThreadPermissionDTO::threadClass))
                || hasDuplicates(document.packages().stream().map(PackagePermissionDTO::packageName));
    }

    private static boolean hasDuplicates(Stream<?> keys) {
        Set<Object> seen = new HashSet<>();
        return keys.anyMatch(key -> !seen.add(key));
    }

    /**
     * @return the 1-based line on which SnakeYAML reports the first error of the content; null if it reports none, e.g. because the content contains several
     *         documents
     */
    private static @Nullable Integer errorLine(String content) {
        try {
            SecurityPolicyYaml.newYaml().load(content);
            return null;
        }
        catch (MarkedYAMLException e) {
            return e.getProblemMark() == null ? null : e.getProblemMark().getLine() + 1;
        }
        catch (YAMLException _) {
            return null;
        }
    }

    /**
     * Removes null values and empty mappings, which the rendering omits, so a tree can be compared with the tree of its rendering.
     */
    private static @Nullable Object normalized(@Nullable Object value) {
        if (value instanceof Map<?, ?> mapping) {
            Map<Object, Object> result = new LinkedHashMap<>();
            mapping.forEach((key, entry) -> {
                Object normalizedEntry = normalized(entry);
                if (normalizedEntry != null && !(normalizedEntry instanceof Map<?, ?> nested && nested.isEmpty())) {
                    result.put(key, normalizedEntry);
                }
            });
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(SecurityPolicyDocuments::normalized).toList();
        }
        return value;
    }

    private static Optional<SecurityPolicyDocumentDTO> toDocument(String policyPath, Object tree) {
        try {
            Map<?, ?> root = mapping(tree, Set.of(VERSION_KEY, PRESET_KEY, SUPERVISED_CODE_KEY));
            if (!BigInteger.ONE.equals(root.get(VERSION_KEY))) {
                return Optional.empty();
            }
            Map<?, ?> code = mapping(root.get(SUPERVISED_CODE_KEY), Set.of(LANGUAGE_KEY, PACKAGE_KEY, MAIN_CLASS_KEY, TEST_CLASSES_KEY, RESOURCES_KEY, BEHAVIOR_KEY));
            Map<?, ?> resources = mapping(code.get(RESOURCES_KEY), Set.of(FILES_KEY, NETWORK_KEY, COMMANDS_KEY, THREADS_KEY, PACKAGES_KEY, TIMEOUTS_KEY));
            return Optional.of(new SecurityPolicyDocumentDTO(policyPath, optionalText(root.get(PRESET_KEY)), text(code.get(LANGUAGE_KEY)), optionalText(code.get(PACKAGE_KEY)),
                    optionalText(code.get(MAIN_CLASS_KEY)), texts(code.get(TEST_CLASSES_KEY)), entries(resources.get(FILES_KEY), SecurityPolicyDocuments::filePermission),
                    entries(resources.get(NETWORK_KEY), SecurityPolicyDocuments::networkPermission),
                    entries(resources.get(COMMANDS_KEY), SecurityPolicyDocuments::commandPermission),
                    entries(resources.get(THREADS_KEY), SecurityPolicyDocuments::threadPermission),
                    entries(resources.get(PACKAGES_KEY), entry -> new PackagePermissionDTO(text(mapping(entry, Set.of(PACKAGE_IMPORT_KEY)).get(PACKAGE_IMPORT_KEY)))),
                    entries(resources.get(TIMEOUTS_KEY), entry -> integer(mapping(entry, Set.of(TIMEOUT_KEY)).get(TIMEOUT_KEY), MAX_LONG).longValue()),
                    code.get(BEHAVIOR_KEY) == null ? null : testBehavior(code.get(BEHAVIOR_KEY))));
        }
        catch (UnsupportedContentException _) {
            return Optional.empty();
        }
    }

    /**
     * Reads the resource accesses of a preset, which are written like the section {@code theFollowingResourceAccessesArePermitted} of a policy.
     *
     * @param resources the section, as read by {@link SecurityPolicySchema#readTree(String)}
     * @return the resource accesses as a document without a path; empty if the section uses something the editor cannot represent
     */
    static Optional<SecurityPolicyDocumentDTO> resourceAccesses(@Nullable Object resources) {
        Map<String, Object> code = new LinkedHashMap<>();
        code.put(LANGUAGE_KEY, "PRESET");
        code.put(TEST_CLASSES_KEY, List.of());
        code.put(RESOURCES_KEY, resources);
        return toDocument("", Map.of(VERSION_KEY, BigInteger.ONE, SUPERVISED_CODE_KEY, code));
    }

    private static FilePermissionDTO filePermission(Object entry) {
        Map<?, ?> permission = mapping(entry, Set.of(PATH_KEY, READ_KEY, OVERWRITE_KEY, CREATE_KEY, EXECUTE_KEY, DELETE_KEY));
        return new FilePermissionDTO(text(permission.get(PATH_KEY)), flag(permission.get(READ_KEY)), flag(permission.get(OVERWRITE_KEY)), flag(permission.get(CREATE_KEY)),
                flag(permission.get(EXECUTE_KEY)), flag(permission.get(DELETE_KEY)));
    }

    private static NetworkPermissionDTO networkPermission(Object entry) {
        Map<?, ?> permission = mapping(entry, Set.of(HOST_KEY, PORT_KEY, OPEN_KEY, SEND_KEY, RECEIVE_KEY));
        return new NetworkPermissionDTO(text(permission.get(HOST_KEY)), integer(permission.get(PORT_KEY), MAX_PORT).intValue(), flag(permission.get(OPEN_KEY)),
                flag(permission.get(SEND_KEY)), flag(permission.get(RECEIVE_KEY)));
    }

    private static CommandPermissionDTO commandPermission(Object entry) {
        Map<?, ?> permission = mapping(entry, Set.of(COMMAND_KEY, ARGUMENTS_KEY));
        return new CommandPermissionDTO(text(permission.get(COMMAND_KEY)), texts(permission.get(ARGUMENTS_KEY)));
    }

    private static ThreadPermissionDTO threadPermission(Object entry) {
        Map<?, ?> permission = mapping(entry, Set.of(THREAD_COUNT_KEY, THREAD_CLASS_KEY));
        return new ThreadPermissionDTO(text(permission.get(THREAD_CLASS_KEY)), integer(permission.get(THREAD_COUNT_KEY), MAX_INT).intValue());
    }

    private static TestBehaviorDTO testBehavior(Object value) {
        Map<?, ?> behavior = mapping(value, Set.of(STRICT_TIMEOUTS_KEY, OUTPUT_MIRRORING_KEY, HIDDEN_TESTS_KEY, PRIVILEGED_EXCEPTIONS_KEY));
        StrictTimeoutDTO strictTimeout = null;
        if (behavior.get(STRICT_TIMEOUTS_KEY) != null) {
            Map<?, ?> timeout = mapping(behavior.get(STRICT_TIMEOUTS_KEY), Set.of(TIMEOUT_IS_KEY, TIME_UNIT_KEY, GRACE_KEY, GRACE_UNIT_KEY));
            strictTimeout = new StrictTimeoutDTO(integer(timeout.get(TIMEOUT_IS_KEY), MAX_LONG).longValue(), timeUnit(timeout.get(TIME_UNIT_KEY)),
                    timeout.get(GRACE_KEY) == null ? null : integer(timeout.get(GRACE_KEY), MAX_LONG).longValue(),
                    timeout.get(GRACE_UNIT_KEY) == null ? null : timeUnit(timeout.get(GRACE_UNIT_KEY)));
        }
        OutputMirroringDTO outputMirroring = null;
        if (behavior.get(OUTPUT_MIRRORING_KEY) != null) {
            Map<?, ?> mirroring = mapping(behavior.get(OUTPUT_MIRRORING_KEY), Set.of(MIRRORED_KEY, MAXIMUM_CHARACTERS_KEY));
            outputMirroring = new OutputMirroringDTO(flag(mirroring.get(MIRRORED_KEY)),
                    mirroring.get(MAXIMUM_CHARACTERS_KEY) == null ? null : integer(mirroring.get(MAXIMUM_CHARACTERS_KEY), MAX_LONG).longValue());
        }
        HiddenTestsDTO hiddenTests = null;
        if (behavior.get(HIDDEN_TESTS_KEY) != null) {
            Map<?, ?> hidden = mapping(behavior.get(HIDDEN_TESTS_KEY), Set.of(DEADLINE_KEY, ALWAYS_RUN_BEFORE_KEY, UNLISTED_HIDDEN_KEY));
            // The editor hides every test of the policy; a policy that hides only some of them is kept read-only.
            if (!Boolean.TRUE.equals(hidden.get(UNLISTED_HIDDEN_KEY))) {
                throw new UnsupportedContentException();
            }
            hiddenTests = new HiddenTestsDTO(optionalText(hidden.get(ALWAYS_RUN_BEFORE_KEY)), text(hidden.get(DEADLINE_KEY)));
        }
        PrivilegedExceptionsDTO privilegedExceptions = null;
        if (behavior.get(PRIVILEGED_EXCEPTIONS_KEY) != null) {
            Map<?, ?> exceptions = mapping(behavior.get(PRIVILEGED_EXCEPTIONS_KEY), Set.of(PRIVILEGED_ONLY_KEY, FAILURE_MESSAGE_KEY));
            privilegedExceptions = new PrivilegedExceptionsDTO(flag(exceptions.get(PRIVILEGED_ONLY_KEY)), optionalText(exceptions.get(FAILURE_MESSAGE_KEY)));
        }
        return new TestBehaviorDTO(strictTimeout, outputMirroring, hiddenTests, privilegedExceptions);
    }

    /** Thrown while converting a tree that uses a field or value the editor cannot represent. */
    private static final class UnsupportedContentException extends RuntimeException {

        private UnsupportedContentException() {
            super(null, null, false, false);
        }
    }

    private static Map<?, ?> mapping(@Nullable Object value, Set<String> allowedKeys) {
        if (!(value instanceof Map<?, ?> mapping) || !allowedKeys.containsAll(mapping.keySet())) {
            throw new UnsupportedContentException();
        }
        return mapping;
    }

    private static String text(@Nullable Object value) {
        if (!(value instanceof String text)) {
            throw new UnsupportedContentException();
        }
        return text;
    }

    private static @Nullable String optionalText(@Nullable Object value) {
        return value == null ? null : text(value);
    }

    private static List<String> texts(@Nullable Object value) {
        return entries(value, SecurityPolicyDocuments::text);
    }

    private static boolean flag(@Nullable Object value) {
        if (!(value instanceof Boolean flag)) {
            throw new UnsupportedContentException();
        }
        return flag;
    }

    private static BigInteger integer(@Nullable Object value, BigInteger maximum) {
        if (!(value instanceof BigInteger integer) || integer.signum() < 0 || integer.compareTo(maximum) > 0) {
            throw new UnsupportedContentException();
        }
        return integer;
    }

    private static String timeUnit(@Nullable Object value) {
        String unit = text(value);
        if (!TIME_UNITS.contains(unit)) {
            throw new UnsupportedContentException();
        }
        return unit;
    }

    private static <T> List<T> entries(@Nullable Object value, Function<Object, T> converter) {
        if (!(value instanceof List<?> list)) {
            throw new UnsupportedContentException();
        }
        List<T> result = new ArrayList<>();
        for (Object entry : list) {
            result.add(converter.apply(entry));
        }
        return result;
    }

    /**
     * Renders a document into a policy file.
     *
     * @param document        the document
     * @param previousContent the current content of the file, whose leading comments are kept; null for a new file
     * @return the content of the policy file
     */
    public static String render(SecurityPolicyDocumentDTO document, @Nullable String previousContent) {
        StringBuilder yaml = new StringBuilder(leadingComments(previousContent));
        yaml.append(VERSION_KEY).append(": 1\n");
        if (document.preset() != null) {
            yaml.append(PRESET_KEY).append(": ").append(document.preset()).append('\n');
        }
        yaml.append(SUPERVISED_CODE_KEY).append(":\n");
        String code = INDENT;
        yaml.append(code).append(LANGUAGE_KEY).append(": ").append(document.languageConfiguration()).append('\n');
        if (document.supervisedPackage() != null) {
            yaml.append(code).append(PACKAGE_KEY).append(": ").append(quote(document.supervisedPackage())).append('\n');
        }
        if (document.mainClass() != null) {
            yaml.append(code).append(MAIN_CLASS_KEY).append(": ").append(quote(document.mainClass())).append('\n');
        }
        appendList(yaml, code, TEST_CLASSES_KEY, document.testClasses(), (entry, indent) -> indent + "- " + quote(entry) + "\n");
        yaml.append(code).append(RESOURCES_KEY).append(":\n");
        String resources = code + INDENT;
        appendList(yaml, resources, FILES_KEY, document.files(),
                (file, indent) -> indent + "- " + PATH_KEY + ": " + quote(file.path()) + "\n"
                        + flags(indent + INDENT,
                                Map.of(READ_KEY, file.read(), OVERWRITE_KEY, file.overwrite(), CREATE_KEY, file.create(), EXECUTE_KEY, file.execute(), DELETE_KEY, file.delete()),
                                READ_KEY, OVERWRITE_KEY, CREATE_KEY, EXECUTE_KEY, DELETE_KEY));
        appendList(yaml, resources, NETWORK_KEY, document.network(),
                (endpoint, indent) -> indent + "- " + HOST_KEY + ": " + quote(endpoint.host()) + "\n" + indent + INDENT + PORT_KEY + ": " + endpoint.port() + "\n"
                        + flags(indent + INDENT, Map.of(OPEN_KEY, endpoint.open(), SEND_KEY, endpoint.send(), RECEIVE_KEY, endpoint.receive()), OPEN_KEY, SEND_KEY, RECEIVE_KEY));
        appendList(yaml, resources, COMMANDS_KEY, document.commands(), (command, indent) -> indent + "- " + COMMAND_KEY + ": " + quote(command.command()) + "\n" + indent + INDENT
                + ARGUMENTS_KEY + ": [" + command.arguments().stream().map(SecurityPolicyDocuments::quote).collect(Collectors.joining(", ")) + "]\n");
        appendList(yaml, resources, THREADS_KEY, document.threads(), (thread, indent) -> indent + "- " + THREAD_COUNT_KEY + ": " + thread.maxThreads() + "\n" + indent + INDENT
                + THREAD_CLASS_KEY + ": " + quote(thread.threadClass()) + "\n");
        appendList(yaml, resources, PACKAGES_KEY, document.packages(), (permission, indent) -> indent + "- " + PACKAGE_IMPORT_KEY + ": " + quote(permission.packageName()) + "\n");
        appendList(yaml, resources, TIMEOUTS_KEY, document.timeouts(), (timeout, indent) -> indent + "- " + TIMEOUT_KEY + ": " + timeout + "\n");
        if (document.testBehavior() != null && !document.testBehavior().isEmpty()) {
            appendTestBehavior(yaml, code, document.testBehavior());
        }
        return yaml.toString();
    }

    private static void appendTestBehavior(StringBuilder yaml, String indent, TestBehaviorDTO behavior) {
        yaml.append(indent).append(BEHAVIOR_KEY).append(":\n");
        String category = indent + INDENT;
        String field = category + INDENT;
        if (behavior.strictTimeout() != null) {
            StrictTimeoutDTO timeout = behavior.strictTimeout();
            yaml.append(category).append(STRICT_TIMEOUTS_KEY).append(":\n");
            yaml.append(field).append(TIMEOUT_IS_KEY).append(": ").append(timeout.timeout()).append('\n');
            yaml.append(field).append(TIME_UNIT_KEY).append(": ").append(timeout.timeUnit()).append('\n');
            if (timeout.terminationGrace() != null) {
                yaml.append(field).append(GRACE_KEY).append(": ").append(timeout.terminationGrace()).append('\n');
            }
            if (timeout.terminationGraceUnit() != null) {
                yaml.append(field).append(GRACE_UNIT_KEY).append(": ").append(timeout.terminationGraceUnit()).append('\n');
            }
        }
        if (behavior.outputMirroring() != null) {
            OutputMirroringDTO mirroring = behavior.outputMirroring();
            yaml.append(category).append(OUTPUT_MIRRORING_KEY).append(":\n");
            yaml.append(field).append(MIRRORED_KEY).append(": ").append(mirroring.mirrored()).append('\n');
            if (mirroring.maximumCharacters() != null) {
                yaml.append(field).append(MAXIMUM_CHARACTERS_KEY).append(": ").append(mirroring.maximumCharacters()).append('\n');
            }
        }
        if (behavior.hiddenTests() != null) {
            HiddenTestsDTO hidden = behavior.hiddenTests();
            yaml.append(category).append(HIDDEN_TESTS_KEY).append(":\n");
            yaml.append(field).append(DEADLINE_KEY).append(": ").append(quote(hidden.hiddenUntil())).append('\n');
            if (hidden.hiddenFrom() != null) {
                yaml.append(field).append(ALWAYS_RUN_BEFORE_KEY).append(": ").append(quote(hidden.hiddenFrom())).append('\n');
            }
            yaml.append(field).append(UNLISTED_HIDDEN_KEY).append(": true\n");
        }
        if (behavior.privilegedExceptions() != null) {
            PrivilegedExceptionsDTO exceptions = behavior.privilegedExceptions();
            yaml.append(category).append(PRIVILEGED_EXCEPTIONS_KEY).append(":\n");
            yaml.append(field).append(PRIVILEGED_ONLY_KEY).append(": ").append(exceptions.enabled()).append('\n');
            if (exceptions.failureMessage() != null) {
                yaml.append(field).append(FAILURE_MESSAGE_KEY).append(": ").append(quote(exceptions.failureMessage())).append('\n');
            }
        }
    }

    /** Renders one list entry, starting at the given indentation and ending with a line break. */
    @FunctionalInterface
    private interface EntryRenderer<T> {

        String render(T entry, String indent);
    }

    private static <T> void appendList(StringBuilder yaml, String indent, String key, List<T> entries, EntryRenderer<T> renderer) {
        if (entries.isEmpty()) {
            yaml.append(indent).append(key).append(": [ ]\n");
            return;
        }
        yaml.append(indent).append(key).append(":\n");
        entries.forEach(entry -> yaml.append(renderer.render(entry, indent + INDENT)));
    }

    private static String flags(String indent, Map<String, Boolean> values, String... order) {
        StringBuilder rendered = new StringBuilder();
        for (String key : order) {
            rendered.append(indent).append(key).append(": ").append(values.get(key)).append('\n');
        }
        return rendered.toString();
    }

    /**
     * @return the comment and blank lines at the top of the content, each ending with a line break; empty for null
     */
    private static String leadingComments(@Nullable String content) {
        if (content == null) {
            return "";
        }
        StringBuilder comments = new StringBuilder();
        for (String line : content.lines().toList()) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                break;
            }
            comments.append(line.stripTrailing()).append('\n');
        }
        return comments.toString();
    }

    /**
     * Quotes a string for YAML: double quotes, with backslashes, quotes and control characters escaped, so every value reads back exactly.
     */
    static String quote(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (Character.isISOControl(codePoint) || codePoint == 0x2028 || codePoint == 0x2029 || codePoint == 0xFEFF) {
                        quoted.append(String.format("\\u%04x", codePoint));
                    }
                    else {
                        quoted.appendCodePoint(codePoint);
                    }
                }
            }
        });
        return quoted.append('"').toString();
    }

    /**
     * Checks the values of a document that no Ares version accepts, independently of the pinned version.
     *
     * @param document the document
     * @return one message per problem; empty if there is none
     */
    public static List<String> validate(SecurityPolicyDocumentDTO document) {
        List<String> problems = new ArrayList<>();
        if (isBlank(document.languageConfiguration()) || !isPlainToken(document.languageConfiguration())) {
            problems.add("The programming language configuration is missing.");
        }
        if (document.preset() != null && !isPlainToken(document.preset())) {
            problems.add("The preset " + document.preset() + " is not a valid preset name.");
        }
        validateTestClasses(document.testClasses(), problems);
        validateFiles(document.files(), problems);
        validateNetwork(document.network(), problems);
        validateCommands(document.commands(), problems);
        validateThreads(document.threads(), problems);
        for (PackagePermissionDTO permission : document.packages()) {
            if (isBlank(permission.packageName()) || containsWhitespace(permission.packageName())) {
                problems.add("A package of the package imports is not valid.");
            }
        }
        if (document.timeouts().stream().anyMatch(timeout -> timeout == null || timeout <= 0)) {
            problems.add("A timeout must be positive.");
        }
        if (listsAResourceTwice(document)) {
            problems.add("A resource is listed more than once; the editor could not tell the entries apart afterwards.");
        }
        if (document.testBehavior() != null) {
            validateTestBehavior(document.testBehavior(), problems);
        }
        return problems;
    }

    private static void validateTestClasses(List<String> testClasses, List<String> problems) {
        Set<String> seenTestClasses = new HashSet<>();
        for (String testClass : testClasses) {
            if (isBlank(testClass) || containsWhitespace(testClass)) {
                problems.add("The test class entry \"" + testClass + "\" is not a valid name.");
            }
            else if (!seenTestClasses.add(testClass)) {
                problems.add("The test class " + testClass + " is listed twice.");
            }
        }
    }

    private static void validateFiles(List<FilePermissionDTO> files, List<String> problems) {
        for (FilePermissionDTO file : files) {
            if (isBlank(file.path()) || containsLineBreak(file.path())) {
                problems.add("A path of the file system permissions is empty.");
            }
            if (!(file.read() || file.overwrite() || file.create() || file.execute() || file.delete())) {
                problems.add("The path " + file.path() + " grants no permission.");
            }
        }
    }

    private static void validateNetwork(List<NetworkPermissionDTO> network, List<String> problems) {
        for (NetworkPermissionDTO endpoint : network) {
            if (isBlank(endpoint.host()) || containsWhitespace(endpoint.host())) {
                problems.add("A host of the network permissions is not valid.");
            }
            if (endpoint.port() < 0 || endpoint.port() > MAX_PORT.intValue()) {
                problems.add("The port " + endpoint.port() + " is not between 0 and 65535.");
            }
            if (!(endpoint.open() || endpoint.send() || endpoint.receive())) {
                problems.add("The endpoint " + endpoint.host() + ":" + endpoint.port() + " grants no permission.");
            }
        }
    }

    private static void validateCommands(List<CommandPermissionDTO> commands, List<String> problems) {
        for (CommandPermissionDTO command : commands) {
            if (isBlank(command.command()) || containsLineBreak(command.command()) || command.arguments().stream().anyMatch(SecurityPolicyDocuments::containsLineBreak)) {
                problems.add("A command of the command permissions is not valid.");
            }
        }
    }

    private static void validateThreads(List<ThreadPermissionDTO> threads, List<String> problems) {
        for (ThreadPermissionDTO thread : threads) {
            if (isBlank(thread.threadClass()) || containsWhitespace(thread.threadClass())) {
                problems.add("A class of the thread permissions is not valid.");
            }
            if (thread.maxThreads() < 1) {
                problems.add("The thread class " + thread.threadClass() + " must allow at least one thread.");
            }
        }
    }

    private static void validateTestBehavior(TestBehaviorDTO behavior, List<String> problems) {
        StrictTimeoutDTO timeout = behavior.strictTimeout();
        if (timeout != null && (timeout.timeout() <= 0 || !TIME_UNITS.contains(timeout.timeUnit()) || (timeout.terminationGrace() != null && timeout.terminationGrace() < 0)
                || (timeout.terminationGraceUnit() != null && !TIME_UNITS.contains(timeout.terminationGraceUnit())))) {
            problems.add("The strict timeout must be positive and use a valid time unit.");
        }
        OutputMirroringDTO mirroring = behavior.outputMirroring();
        if (mirroring != null && mirroring.maximumCharacters() != null && mirroring.maximumCharacters() <= 0) {
            problems.add("The maximum number of mirrored characters must be positive.");
        }
        HiddenTestsDTO hidden = behavior.hiddenTests();
        if (hidden != null
                && (isBlank(hidden.hiddenUntil()) || containsLineBreak(hidden.hiddenUntil()) || (hidden.hiddenFrom() != null && containsLineBreak(hidden.hiddenFrom())))) {
            problems.add("The dates of the hidden tests are not valid.");
        }
        PrivilegedExceptionsDTO exceptions = behavior.privilegedExceptions();
        if (exceptions != null && exceptions.failureMessage() != null && containsLineBreak(exceptions.failureMessage())) {
            problems.add("The failure message must be a single line.");
        }
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static boolean isPlainToken(String value) {
        return value.chars().allMatch(character -> Character.isLetterOrDigit(character) || character == '_');
    }

    private static boolean containsWhitespace(String value) {
        return value.chars().anyMatch(Character::isWhitespace);
    }

    private static boolean containsLineBreak(String value) {
        return value.chars().anyMatch(character -> character == '\n' || character == '\r' || character == 0);
    }
}
