package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The content of one Ares security policy file, as the policy editor of the Security Framework shows and edits it (Objective 2). Each field maps to one key
 * of the policy file; the server renders the file from this record, so the client never writes YAML itself.
 *
 * @param policyPath            the path of the policy file relative to the root of the tests repository, as the {@code @Policy} annotations name it
 * @param preset                the Ares preset the policy is based on ({@code basedOnTheFollowingPreset}); absent for none
 * @param languageConfiguration {@code theFollowingProgrammingLanguageConfigurationIsUsed}, e.g. {@code JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ}
 * @param supervisedPackage     {@code theSupervisedCodeUsesTheFollowingPackage}
 * @param mainClass             {@code theMainClassInsideThisPackageIs}; absent for none
 * @param testClasses           {@code theFollowingClassesAreTestClasses}: the packages, test classes and test cases Ares treats as trusted test code
 * @param files                 {@code regardingFileSystemInteractions}
 * @param network               {@code regardingNetworkConnections}
 * @param commands              {@code regardingCommandExecutions}
 * @param threads               {@code regardingThreadCreations}
 * @param packages              {@code regardingPackageImports}
 * @param timeouts              {@code regardingTimeouts}, in milliseconds
 * @param testBehavior          {@code theFollowingTestBehaviorIsConfigured}; absent if the policy configures no test behavior
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityPolicyDocumentDTO(String policyPath, @Nullable String preset, @Nullable String languageConfiguration, @Nullable String supervisedPackage,
        @Nullable String mainClass, List<String> testClasses, List<FilePermissionDTO> files, List<NetworkPermissionDTO> network, List<CommandPermissionDTO> commands,
        List<ThreadPermissionDTO> threads, List<PackagePermissionDTO> packages, List<Long> timeouts, @Nullable TestBehaviorDTO testBehavior) {

    /**
     * Replaces lists that the client omitted, because they are empty, by empty lists.
     */
    public SecurityPolicyDocumentDTO {
        testClasses = Objects.requireNonNullElse(testClasses, List.of());
        files = Objects.requireNonNullElse(files, List.of());
        network = Objects.requireNonNullElse(network, List.of());
        commands = Objects.requireNonNullElse(commands, List.of());
        threads = Objects.requireNonNullElse(threads, List.of());
        packages = Objects.requireNonNullElse(packages, List.of());
        timeouts = Objects.requireNonNullElse(timeouts, List.of());
    }

    /**
     * One entry of {@code regardingFileSystemInteractions}.
     *
     * @param path      {@code onThisPathAndAllPathsBelow}
     * @param read      {@code readAllFiles}
     * @param overwrite {@code overwriteAllFiles}
     * @param create    {@code createAllFiles}
     * @param execute   {@code executeAllFiles}
     * @param delete    {@code deleteAllFiles}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FilePermissionDTO(String path, boolean read, boolean overwrite, boolean create, boolean execute, boolean delete) {
    }

    /**
     * One entry of {@code regardingNetworkConnections}.
     *
     * @param host    {@code onTheHost}
     * @param port    {@code onThePort}
     * @param open    {@code openConnections}
     * @param send    {@code sendData}
     * @param receive {@code receiveData}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record NetworkPermissionDTO(String host, int port, boolean open, boolean send, boolean receive) {
    }

    /**
     * One entry of {@code regardingCommandExecutions}.
     *
     * @param command   {@code executeTheCommand}
     * @param arguments {@code withTheseArguments}, which must match exactly and in this order
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CommandPermissionDTO(String command, List<String> arguments) {

        /**
         * Replaces omitted arguments by an empty list.
         */
        public CommandPermissionDTO {
            arguments = Objects.requireNonNullElse(arguments, List.of());
        }
    }

    /**
     * One entry of {@code regardingThreadCreations}.
     *
     * @param threadClass {@code ofThisClass}
     * @param maxThreads  {@code createTheFollowingNumberOfThreads}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ThreadPermissionDTO(String threadClass, int maxThreads) {
    }

    /**
     * One entry of {@code regardingPackageImports}.
     *
     * @param packageName {@code importTheFollowingPackage}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record PackagePermissionDTO(String packageName) {
    }

    /**
     * {@code theFollowingTestBehaviorIsConfigured}. Its categories are proposed in Ares (#236, #303, #304, #307) but in no released version yet, so the
     * compatibility manifest decides whether the pinned version accepts them.
     *
     * @param strictTimeout        {@code regardingStrictTimeouts}
     * @param outputMirroring      {@code regardingOutputMirroring}
     * @param hiddenTests          {@code regardingHiddenTests}
     * @param privilegedExceptions {@code regardingPrivilegedExceptions}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TestBehaviorDTO(@Nullable StrictTimeoutDTO strictTimeout, @Nullable OutputMirroringDTO outputMirroring, @Nullable HiddenTestsDTO hiddenTests,
            @Nullable PrivilegedExceptionsDTO privilegedExceptions) {

        /**
         * @return whether no category is configured, in which case the policy file gets no {@code theFollowingTestBehaviorIsConfigured} section
         */
        public boolean isEmpty() {
            return strictTimeout == null && outputMirroring == null && hiddenTests == null && privilegedExceptions == null;
        }
    }

    /**
     * {@code regardingStrictTimeouts}.
     *
     * @param timeout              {@code theTimeoutIs}
     * @param timeUnit             {@code theTimeUnitIs}, a {@link java.util.concurrent.TimeUnit} name
     * @param terminationGrace     {@code theTerminationGraceIs}; absent for the Ares default
     * @param terminationGraceUnit {@code theTerminationGraceUnitIs}, a {@link java.util.concurrent.TimeUnit} name; absent for the Ares default
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StrictTimeoutDTO(long timeout, String timeUnit, @Nullable Long terminationGrace, @Nullable String terminationGraceUnit) {
    }

    /**
     * {@code regardingOutputMirroring}.
     *
     * @param mirrored          {@code theOutputIsMirrored}
     * @param maximumCharacters {@code theMaximumCharacterCountIs}; absent for the Ares default
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record OutputMirroringDTO(boolean mirrored, @Nullable Long maximumCharacters) {
    }

    /**
     * {@code regardingHiddenTests}: the tests of the policy are hidden from students between the two dates. Every test the policy covers is hidden
     * ({@code unlistedTestsAreHidden: true}).
     *
     * @param hiddenFrom  {@code hiddenTestsAlwaysRunBefore}, e.g. {@code 2026-12-24 23:59 Europe/Berlin}; absent to hide them from the start
     * @param hiddenUntil {@code theDeadlineIs}, in the same format
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record HiddenTestsDTO(@Nullable String hiddenFrom, String hiddenUntil) {
    }

    /**
     * {@code regardingPrivilegedExceptions}: replaces the failure messages shown to students.
     *
     * @param enabled        {@code onlyPrivilegedExceptionsAreReported}
     * @param failureMessage {@code theFailureMessageIs}; absent for the Ares default
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record PrivilegedExceptionsDTO(boolean enabled, @Nullable String failureMessage) {
    }
}
