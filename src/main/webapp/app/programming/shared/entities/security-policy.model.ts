import { SecurityFrameworkConfig, SecurityVersionCompatibilityVerdict } from 'app/programming/shared/entities/security-framework-config.model';

/**
 * The security policies of a programming exercise as the policy editor of the Security Framework edits them (Objective 2). The types mirror the server's
 * {@code SecurityPolicy*DTO} records. The server omits empty lists, so every list is optional.
 */

/** One entry of {@code regardingFileSystemInteractions}: the actions granted on this path and every path below it. */
export interface SecurityFilePermission {
    path: string;
    read: boolean;
    overwrite: boolean;
    create: boolean;
    execute: boolean;
    delete: boolean;
}

/** One entry of {@code regardingNetworkConnections}: the actions granted on exactly this host and port. */
export interface SecurityNetworkPermission {
    host: string;
    port: number;
    open: boolean;
    send: boolean;
    receive: boolean;
}

/** One entry of {@code regardingCommandExecutions}: exactly this program with exactly these arguments. */
export interface SecurityCommandPermission {
    command: string;
    arguments?: string[];
}

/** One entry of {@code regardingThreadCreations}: up to this many threads of exactly this class. */
export interface SecurityThreadPermission {
    threadClass: string;
    maxThreads: number;
}

/** One entry of {@code regardingPackageImports}: student code may import this package. */
export interface SecurityPackagePermission {
    packageName: string;
}

/** {@code regardingStrictTimeouts}; the units are names of Java's {@code TimeUnit}. */
export interface SecurityStrictTimeout {
    timeout: number;
    timeUnit: string;
    terminationGrace?: number;
    terminationGraceUnit?: string;
}

/** {@code regardingOutputMirroring}. */
export interface SecurityOutputMirroring {
    mirrored: boolean;
    maximumCharacters?: number;
}

/** {@code regardingHiddenTests}: the tests of the policy are hidden from students between the two dates, written like {@code 2026-12-24 23:59 Europe/Berlin}. */
export interface SecurityHiddenTests {
    hiddenFrom?: string;
    hiddenUntil: string;
}

/** {@code regardingPrivilegedExceptions}: replaces the failure messages shown to students. */
export interface SecurityPrivilegedExceptions {
    enabled: boolean;
    failureMessage?: string;
}

/** {@code theFollowingTestBehaviorIsConfigured}. */
export interface SecurityTestBehavior {
    strictTimeout?: SecurityStrictTimeout;
    outputMirroring?: SecurityOutputMirroring;
    hiddenTests?: SecurityHiddenTests;
    privilegedExceptions?: SecurityPrivilegedExceptions;
}

/** The content of one policy file. */
export interface SecurityPolicyDocument {
    policyPath: string;
    preset?: string;
    languageConfiguration?: string;
    supervisedPackage?: string;
    mainClass?: string;
    /** {@code theFollowingClassesAreTestClasses}: packages, test classes ({@code pkg.Class}) and test cases ({@code pkg.Class#method}). */
    testClasses?: string[];
    files?: SecurityFilePermission[];
    network?: SecurityNetworkPermission[];
    commands?: SecurityCommandPermission[];
    threads?: SecurityThreadPermission[];
    packages?: SecurityPackagePermission[];
    timeouts?: number[];
    testBehavior?: SecurityTestBehavior;
}

/** Why the editor shows a policy read-only, mirroring the server's {@code SecurityPolicyDocuments.ReadOnlyReason}. */
export enum SecurityPolicyReadOnlyReason {
    MISSING = 'MISSING',
    TOO_LARGE = 'TOO_LARGE',
    INVALID_PATH = 'INVALID_PATH',
    YAML_ERROR = 'YAML_ERROR',
    UNSUPPORTED_CONTENT = 'UNSUPPORTED_CONTENT',
    DUPLICATE_RESOURCE = 'DUPLICATE_RESOURCE',
}

/** A policy file as the server loaded it. */
export interface SecurityPolicyFile {
    policyPath: string;
    /** The content as committed; absent if it cannot be read. */
    content?: string;
    /** The content as the editor edits it; absent if the policy is shown read-only. */
    document?: SecurityPolicyDocument;
    readOnlyReason?: SecurityPolicyReadOnlyReason;
    /** The 1-based line of the YAML error. */
    errorLine?: number;
    /** The tests that use the policy: a test class for all of its test cases, or {@code Class#method} for a single test case. */
    assignedTests?: string[];
}

/** A commit of the tests repository. */
export interface SecurityCommit {
    hash: string;
    authorName?: string;
    /** ISO date-time. */
    date: string;
}

/** A preset of Ares a policy can be based on; the permissions it adds are shown as locked rows. */
export interface SecurityPreset {
    name: string;
    label: string;
    files?: SecurityFilePermission[];
    network?: SecurityNetworkPermission[];
    commands?: SecurityCommandPermission[];
    threads?: SecurityThreadPermission[];
    packages?: SecurityPackagePermission[];
}

/** The policies of an exercise, with the commit they were read from. */
export interface SecurityPolicyEditor {
    baseCommitHash?: string;
    lastCommit?: SecurityCommit;
    frameworkVersion?: string;
    policies?: SecurityPolicyFile[];
    presets?: SecurityPreset[];
    /** Whether the build attaches the Ares agent, which dependency scanning needs. */
    scanDependenciesAvailable?: boolean;
    /** The supervised code a new policy starts with when no policy is left to copy it from. */
    newPolicyTemplate?: SecurityPolicyDocument;
}

/** Changed policies to preview or commit. */
export interface SecurityPolicyChange {
    baseCommitHash?: string;
    commitMessage?: string;
    /** The Ares version to pin with the commit; absent to keep the pinned one. */
    frameworkVersion?: string;
    /** The policies to write or reassign; policies that are not listed stay as they are. */
    policies: SecurityPolicyChangeEntry[];
    /** The policy files to delete. */
    removedPolicyPaths: string[];
}

/** One policy file to write. */
export interface SecurityPolicyChangeEntry {
    /** The path the file has in the repository; absent for a new policy, another path than the document's when it is renamed. */
    previousPolicyPath?: string;
    document: SecurityPolicyDocument;
    /** The tests that use the policy, see {@link SecurityPolicyFile.assignedTests}. */
    assignedTests: string[];
}

/** A file before and after a change. */
export interface SecurityPolicyPreviewFile {
    path: string;
    /** The path the file had before; differs for a renamed policy and is absent for a new one. */
    previousPath?: string;
    /** Absent for a new file. */
    before?: string;
    /** Absent for a deleted file. */
    after?: string;
}

export interface SecurityPolicyPreview {
    files?: SecurityPolicyPreviewFile[];
    frameworkVersion?: string;
    /** Whether the version accepts the policies; absent while the change has problems, because the version is checked only for a complete change. */
    verdict?: SecurityVersionCompatibilityVerdict;
    problems?: string[];
}

/** The result of a commit: the policies and the Security Framework state the tests repository now has. */
export interface SecurityPolicyCommitResult {
    editor: SecurityPolicyEditor;
    config: SecurityFrameworkConfig;
}

/** A test class with its test cases. */
export interface SecurityTestClass {
    qualifiedName: string;
    testCases?: string[];
    /** Whether it is a test class whose test cases can be assigned; false for a helper class without tests. */
    testClass?: boolean;
    /** Whether its {@code @Policy} annotation is switched off, so its test cases run without the sandbox. */
    switchedOff?: boolean;
    /** The test cases whose own {@code @Policy} annotation switches the sandbox off, so they cannot be assigned either. */
    fixedCases?: string[];
    /** The test cases that more than one test method has; they use a policy only together with their whole class. */
    overloadedCases?: string[];
}

/** The test classes of the tests repository. */
export interface SecurityTestStructure {
    testClasses?: SecurityTestClass[];
}

/** The files of the template repository. */
export interface SecurityTemplateFiles {
    paths?: string[];
    truncated?: boolean;
}
