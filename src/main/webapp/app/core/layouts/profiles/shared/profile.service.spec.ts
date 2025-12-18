import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PROFILE_APOLLON, PROFILE_ATHENA, PROFILE_DEV, PROFILE_JENKINS, PROFILE_PROD } from 'app/app.constants';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProfileInfo, ProgrammingLanguageFeature } from 'app/core/layouts/profiles/profile-info.model';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';

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

// eslint-disable-next-line jest/no-export
export const expectedProfileInfo: ProfileInfo = {
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
    localLLMDeploymentEnabled: false,
};

describe('ProfileService', () => {
    let service: ProfileService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                LocalStorageService,
                SessionStorageService,
                { provide: Router, useClass: MockRouter },
                { provide: BrowserFingerprintService, useValue: { initialize: jest.fn() } },
            ],
        });
        service = TestBed.inject(ProfileService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should call correct URL', () => {
            service.loadProfileInfo();

            const req = httpMock.expectOne({ method: 'GET' });
            const infoUrl = 'management/info';
            expect(req.request.url).toEqual(infoUrl);
        });

        it('should get the profile info', async () => {
            const featureSpy = jest.spyOn(service['featureToggleService'], 'initializeFeatureToggles');
            const fingerprintSpy = jest.spyOn(service['browserFingerprintService'], 'initialize');

            const promise = service.loadProfileInfo();
            const req = httpMock.expectOne('management/info');
            expect(req.request.method).toBe('GET');

            req.flush(expectedProfileInfo);

            await promise; // wait for the async method to complete

            expect(service.getProfileInfo()).toEqual(expectedProfileInfo);
            expect(featureSpy).toHaveBeenCalledWith(expectedProfileInfo.features);
            expect(fingerprintSpy).toHaveBeenCalledWith(expectedProfileInfo.studentExamStoreSessionData);
        });

        it('should return true if the profile is active', () => {
            // @ts-ignore
            service.profileInfo = { activeProfiles: [PROFILE_DEV, PROFILE_PROD] };
            expect(service.isProfileActive(PROFILE_DEV)).toBeTrue();
            expect(service.isProfileActive(PROFILE_PROD)).toBeTrue();
        });

        it('should return false if the profile is not active', () => {
            // @ts-ignore
            service.profileInfo = { activeProfiles: [PROFILE_PROD] };
            expect(service.isProfileActive(PROFILE_DEV)).toBeFalse();
        });

        it('should return false if activeProfiles is undefined', () => {
            // @ts-ignore
            service.profileInfo = {};
            expect(service.isProfileActive(PROFILE_DEV)).toBeFalse();
        });
    });
});
