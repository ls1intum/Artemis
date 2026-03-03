import { ProfileInfo, ProgrammingLanguageFeature } from 'app/core/layouts/profiles/profile-info.model';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { PROFILE_APOLLON, PROFILE_ATHENA, PROFILE_JENKINS, PROFILE_PROD } from 'app/app.constants';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

const programmingLanguageFeatures: ProgrammingLanguageFeature[] = [
    {
        programmingLanguage: ProgrammingLanguage.KOTLIN,
        sequentialTestRuns: true,
        staticCodeAnalysis: false,
        plagiarismCheckSupported: false,
        packageNameRequired: true,
        checkoutSolutionRepositoryAllowed: false,
        projectTypes: [],
        auxiliaryRepositoriesSupported: true,
    },
    {
        programmingLanguage: ProgrammingLanguage.PYTHON,
        sequentialTestRuns: false,
        staticCodeAnalysis: false,
        plagiarismCheckSupported: true,
        packageNameRequired: false,
        checkoutSolutionRepositoryAllowed: false,
        projectTypes: [],
        auxiliaryRepositoriesSupported: true,
    },
    {
        programmingLanguage: ProgrammingLanguage.SWIFT,
        sequentialTestRuns: false,
        staticCodeAnalysis: true,
        plagiarismCheckSupported: false,
        packageNameRequired: true,
        checkoutSolutionRepositoryAllowed: false,
        projectTypes: [ProjectType.PLAIN, ProjectType.XCODE],
        auxiliaryRepositoriesSupported: true,
    },
    {
        programmingLanguage: ProgrammingLanguage.C,
        sequentialTestRuns: false,
        staticCodeAnalysis: false,
        plagiarismCheckSupported: true,
        packageNameRequired: false,
        checkoutSolutionRepositoryAllowed: false,
        projectTypes: [],
        auxiliaryRepositoriesSupported: true,
    },
    {
        programmingLanguage: ProgrammingLanguage.JAVA,
        sequentialTestRuns: true,
        staticCodeAnalysis: true,
        plagiarismCheckSupported: true,
        packageNameRequired: true,
        checkoutSolutionRepositoryAllowed: false,
        projectTypes: [ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN],
        auxiliaryRepositoriesSupported: true,
    },
];

const gitInformation = {
    branch: 'code-button',
    commit: {
        id: {
            abbrev: '95ef2a',
        },
        time: '2022-11-20T20:35:01Z',
        user: {
            name: 'Max Musterman',
            email: 'max@mustermann.de',
        },
    },
};

const buildInformation = {
    artifact: 'Artemis',
    name: 'Artemis',
    time: '2025-05-26T23:13:30.212Z',
    version: '8.0.0',
    group: 'de.tum.cit.aet.artemis',
};

export const expectedProfileInfo: ProfileInfo = {
    localLLMDeploymentEnabled: false,
    activeModuleFeatures: [],
    activeProfiles: [PROFILE_PROD, PROFILE_JENKINS, PROFILE_ATHENA, PROFILE_APOLLON],
    allowedEmailPattern: '([a-zA-Z0-9_\\-\\.\\+]+)@((tum\\.de)|(in\\.tum\\.de)|(mytum\\.de))',
    allowedEmailPatternReadable: '@tum.de, @in.tum.de, @mytum.de',
    build: buildInformation,
    buildPlanURLTemplate: 'https://artemistest2jenkins.ase.in.tum.de/job/{projectKey}/job/{buildPlanId}',
    buildTimeoutDefault: 0,
    buildTimeoutMax: 0,
    buildTimeoutMin: 0,
    compatibleVersions: {},
    contact: 'artemis@xcit.tum.de',
    continuousIntegrationName: '',
    defaultContainerCpuCount: 0,
    defaultContainerMemoryLimitInMB: 0,
    defaultContainerMemorySwapLimitInMB: 0,
    externalCredentialProvider: '',
    externalPasswordResetLinkMap: { en: '', de: '' },
    features: [FeatureToggle.ProgrammingExercises, FeatureToggle.PlagiarismChecks],
    git: gitInformation,
    java: {},
    needsToAcceptTerms: false,
    operatorAdminName: '',
    operatorName: 'TUM',
    programmingLanguageFeatures: programmingLanguageFeatures,
    registrationEnabled: true,
    repositoryAuthenticationMechanisms: ['ssh', 'token', 'password'],
    sentry: { dsn: 'https://e52d0b9b6b61769f50b088634b4bc781@sentry.aet.cit.tum.de/2' },
    sshCloneURLTemplate: 'ssh://git@artemistest2.aet.cit.tum.de:2222/',
    studentExamStoreSessionData: false,
    testServer: true,
    textAssessmentAnalyticsEnabled: false,
    theiaPortalURL: 'https://theia.artemis.cit.tum.de',
    useExternal: false,
    versionControlName: '',
    versionControlUrl: 'https://artemistest2.aet.cit.tum.de',
    allowedCustomDockerNetworks: [],
};
