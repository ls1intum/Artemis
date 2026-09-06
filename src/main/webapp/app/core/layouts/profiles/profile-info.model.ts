import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { Saml2Config } from 'app/core/home/saml2-login/saml2.config';
import { ActiveFeatureToggles } from 'app/foundation/feature-toggle/feature-toggle.service';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class SentryConfig {
    public dsn?: string;
}

export interface GitCommitId {
    describe?: string;
    abbrev: string;
    full?: string;
}

export interface GitCommitMessage {
    full?: string;
    short?: string;
}

export interface GitCommitUser {
    name: string;
    email: string;
}

export interface GitCommit {
    id: GitCommitId;
    message?: GitCommitMessage;
    user: GitCommitUser;
    time: string;
}

export interface GitBuildUser {
    name?: string;
    email?: string;
}

export interface GitBuild {
    version?: string;
    user?: GitBuildUser;
    host?: string;
}

export interface GitTotalCommit {
    count?: string;
}

export interface GitClosestTagCommit {
    count?: string;
}

export interface GitClosestTag {
    name?: string;
    commit?: GitClosestTagCommit;
}

export interface GitRemoteOrigin {
    url?: string;
}

export interface GitRemote {
    origin?: GitRemoteOrigin;
}

export interface Git {
    branch: string;
    commit: GitCommit;
    build?: GitBuild;
    dirty?: string;
    tags?: string;
    total?: { commit?: GitTotalCommit };
    closest?: { tag?: GitClosestTag };
    remote?: GitRemote;
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class Build {
    public artifact?: string;
    public name?: string;
    public time?: string;
    public version?: string;
    public group?: string;
}

export interface JavaVendor {
    name?: string;
}

export interface JavaRuntime {
    name?: string;
    version?: string;
}

export interface JavaJvm {
    name?: string;
    vendor?: string;
    version?: string;
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class Java {
    public version?: string;
    public vendor?: JavaVendor;
    public runtime?: JavaRuntime;
    public jvm?: JavaJvm;
}

export interface MobileVersion {
    min?: string;
    recommended?: string;
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class CompatibleVersions {
    public android?: MobileVersion;
    public ios?: MobileVersion;
}

export interface ProgrammingLanguageFeature {
    programmingLanguage: ProgrammingLanguage;
    sequentialTestRuns: boolean;
    staticCodeAnalysis: boolean;
    plagiarismCheckSupported: boolean;
    packageNameRequired: boolean;
    checkoutSolutionRepositoryAllowed: boolean;
    projectTypes: ProjectType[];
    auxiliaryRepositoriesSupported: boolean;
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class ProfileInfo {
    public accountName?: string;
    public activeModuleFeatures: string[] = [];
    public activeProfiles: string[] = [];
    public allowedCourseRegistrationUsernamePattern?: string;
    public allowedEmailPattern?: string;
    public allowedEmailPatternReadable?: string;
    public allowedLdapUsernamePattern?: string;
    public build!: Build;
    public buildPlanURLTemplate?: string; // only available on Artemis instances with Jenkins
    public buildTimeoutDefault!: number;
    public buildTimeoutMax!: number;
    public buildTimeoutMin!: number;
    public compatibleVersions!: CompatibleVersions;
    public contact!: string;
    public continuousIntegrationName!: string;
    public defaultContainerCpuCount!: number;
    public defaultContainerMemoryLimitInMB!: number;
    public defaultContainerMemorySwapLimitInMB!: number;
    public externalCredentialProvider!: string;
    public externalPasswordResetLinkMap!: { [key: string]: string };
    public features!: ActiveFeatureToggles;
    public gocastEnabled?: boolean;
    public git!: Git;
    public java!: Java;
    public needsToAcceptTerms?: boolean;
    public operatorAdminName!: string;
    public operatorName!: string;
    public programmingLanguageFeatures: ProgrammingLanguageFeature[] = [];
    public registrationEnabled?: boolean;
    public repositoryAuthenticationMechanisms!: string[];
    public saml2?: Saml2Config;
    public sentry!: SentryConfig;
    public sshCloneURLTemplate!: string;
    public studentExamStoreSessionData!: boolean;
    public testServer!: boolean;
    public textAssessmentAnalyticsEnabled!: boolean;
    public theiaPortalURL?: string;
    public useExternal!: boolean;
    public versionControlName!: string;
    public versionControlUrl!: string;
    public localLLMDeploymentEnabled!: boolean;
    public allowedCustomDockerNetworks!: string[];
}
