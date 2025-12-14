import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { Saml2Config } from 'app/core/home/saml2-login/saml2.config';
import { ActiveFeatureToggles } from 'app/shared/feature-toggle/feature-toggle.service';

export class SentryConfig {
    public dsn?: string;
}

export class GitCommitId {
    public describe?: string;
    public abbrev: string;
    public full?: string;
}

export class GitCommitMessage {
    public full?: string;
    public short?: string;
}

export class GitCommitUser {
    public name: string;
    public email: string;
}

export class GitCommit {
    public id: GitCommitId;
    public message?: GitCommitMessage;
    public user: GitCommitUser;
    public time: string;
}

export class GitBuildUser {
    public name?: string;
    public email?: string;
}

export class GitBuild {
    public version?: string;
    public user?: GitBuildUser;
    public host?: string;
}

export class GitTotalCommit {
    public count?: string;
}

export class GitClosestTagCommit {
    public count?: string;
}

export class GitClosestTag {
    public name?: string;
    public commit?: GitClosestTagCommit;
}

export class GitRemoteOrigin {
    public url?: string;
}

export class GitRemote {
    public origin?: GitRemoteOrigin;
}

export class Git {
    public branch: string;
    public commit: GitCommit;
    public build?: GitBuild;
    public dirty?: string;
    public tags?: string;
    public total?: { commit?: GitTotalCommit };
    public closest?: { tag?: GitClosestTag };
    public remote?: GitRemote;
}

export class Build {
    public artifact?: string;
    public name?: string;
    public time?: string;
    public version?: string;
    public group?: string;
}

export class JavaVendor {
    public name?: string;
}

export class JavaRuntime {
    public name?: string;
    public version?: string;
}

export class JavaJvm {
    public name?: string;
    public vendor?: string;
    public version?: string;
}

export class Java {
    public version?: string;
    public vendor?: JavaVendor;
    public runtime?: JavaRuntime;
    public jvm?: JavaJvm;
}

export class MobileVersion {
    public min?: string;
    public recommended?: string;
}

export class CompatibleVersions {
    public android?: MobileVersion;
    public ios?: MobileVersion;
}

export class ProgrammingLanguageFeature {
    public programmingLanguage: ProgrammingLanguage;
    public sequentialTestRuns: boolean;
    public staticCodeAnalysis: boolean;
    public plagiarismCheckSupported: boolean;
    public packageNameRequired: boolean;
    public checkoutSolutionRepositoryAllowed: boolean;
    public projectTypes: ProjectType[];
    public auxiliaryRepositoriesSupported: boolean;
}

export class ProfileInfo {
    public accountName?: string;
    public activeModuleFeatures: string[] = [];
    public activeProfiles: string[] = [];
    public allowedCourseRegistrationUsernamePattern?: string;
    public allowedEmailPattern?: string;
    public allowedEmailPatternReadable?: string;
    public allowedLdapUsernamePattern?: string;
    public build: Build;
    public buildPlanURLTemplate?: string; // only available on Artemis instances with Jenkins
    public buildTimeoutDefault: number;
    public buildTimeoutMax: number;
    public buildTimeoutMin: number;
    public compatibleVersions: CompatibleVersions;
    public contact: string;
    public continuousIntegrationName: string;
    public defaultContainerCpuCount: number;
    public defaultContainerMemoryLimitInMB: number;
    public defaultContainerMemorySwapLimitInMB: number;
    public externalCredentialProvider: string;
    public externalPasswordResetLinkMap: { [key: string]: string };
    public features: ActiveFeatureToggles;
    public git: Git;
    public java: Java;
    public needsToAcceptTerms?: boolean;
    public operatorAdminName: string;
    public operatorName: string;
    public programmingLanguageFeatures: ProgrammingLanguageFeature[] = [];
    public registrationEnabled?: boolean;
    public repositoryAuthenticationMechanisms: string[];
    public saml2?: Saml2Config;
    public sentry: SentryConfig;
    public sshCloneURLTemplate: string;
    public studentExamStoreSessionData: boolean;
    public testServer: boolean;
    public textAssessmentAnalyticsEnabled: boolean;
    public theiaPortalURL?: string;
    public useExternal: boolean;
    public versionControlName: string;
    public versionControlUrl: string;
    public localLLMDeploymentEnabled: boolean;
}
