import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockRouter } from '../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { BrowserFingerprintService } from 'app/shared/fingerprint/browser-fingerprint.service';

describe('Profile Service', () => {
    let service: ProfileService;
    let httpMock: HttpTestingController;

    const serverResponse = {
        externalCredentialProvider: '',
        externalPasswordResetLinkMap: new Map<string, string>([
            ['en', ''],
            ['de', ''],
        ]),
        useExternal: false,
        contact: 'artemis.in@tum.de',
        imprint: 'https://ase.in.tum.de/lehrstuhl_1/component/content/article/179-imprint',
        'guided-tour': {
            courseShortName: 'artemistutorial',
            tours: {
                0: {
                    cancel_tour: '',
                },
                1: {
                    code_editor_tour: 'test',
                },
                2: {
                    course_overview_tour: '',
                },
                3: {
                    course_exercise_overview_tour: 'test',
                },
                4: {
                    modeling_tour: 'UML Class Diagram',
                },
                5: {
                    programming_exercise_fail_tour: 'test',
                },
                6: {
                    programming_exercise_success_tour: 'test',
                },
                7: {
                    tutor_assessment_tour: 'Patterns in Software Engineering',
                },
            },
        },
        'test-server': true,
        sentry: {
            dsn: 'https://ceeb3e72ec094684aefbb132f87231f2@sentry.ase.in.tum.de/2',
        },
        'display-ribbon-on-profiles': 'dev',
        'allowed-minimum-orion-version': '1.0.0',
        build: {
            artifact: 'Artemis',
            name: 'Artemis',
            time: '2021-05-26T23:13:30.212Z',
            version: '5.0.0',
            group: 'de.tum.in.www1.artemis',
        },
        features: ['ProgrammingExercises', 'PlagiarismChecks'],
        programmingLanguageFeatures: [
            {
                programmingLanguage: 'KOTLIN',
                sequentialTestRuns: true,
                staticCodeAnalysis: false,
                plagiarismCheckSupported: false,
                packageNameRequired: true,
                checkoutSolutionRepositoryAllowed: false,
                projectTypes: [],
            },
            {
                programmingLanguage: 'PYTHON',
                sequentialTestRuns: false,
                staticCodeAnalysis: false,
                plagiarismCheckSupported: true,
                packageNameRequired: false,
                checkoutSolutionRepositoryAllowed: false,
                projectTypes: [],
            },
            {
                programmingLanguage: 'SWIFT',
                sequentialTestRuns: false,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: false,
                packageNameRequired: true,
                checkoutSolutionRepositoryAllowed: false,
                projectTypes: ['PLAIN', 'XCODE'],
            },
            {
                programmingLanguage: 'C',
                sequentialTestRuns: false,
                staticCodeAnalysis: false,
                plagiarismCheckSupported: true,
                packageNameRequired: false,
                checkoutSolutionRepositoryAllowed: false,
                projectTypes: [],
            },
            {
                programmingLanguage: 'JAVA',
                sequentialTestRuns: true,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: true,
                packageNameRequired: true,
                checkoutSolutionRepositoryAllowed: false,
                projectTypes: ['PLAIN_MAVEN', 'MAVEN_MAVEN'],
            },
        ],
        versionControlUrl: 'https://artemistest2gitlab.ase.in.tum.de',
        commitHashURLTemplate: 'https://artemistest2gitlab.ase.in.tum.de/{projectKey}/{repoSlug}/-/commit/{commitHash}',
        sshCloneURLTemplate: 'ssh://git@artemistest2gitlab.ase.in.tum.de:2222/',
        sshKeysURL: 'https://artemistest2gitlab.ase.in.tum.de/profile/keys',
        buildPlanURLTemplate: 'https://artemistest2jenkins.ase.in.tum.de/job/{projectKey}/job/{buildPlanId}',
        registrationEnabled: true,
        needsToAcceptTerms: false,
        allowedEmailPattern: '([a-zA-Z0-9_\\-\\.\\+]+)@((tum\\.de)|(in\\.tum\\.de)|(mytum\\.de))',
        allowedEmailPatternReadable: '@tum.de, @in.tum.de, @mytum.de',
        activeProfiles: ['prod', 'jenkins', 'gitlab', 'athene', 'openapi', 'apollon'],
    };

    const expectedProfileInfo: ProfileInfo = {
        externalCredentialProvider: '',
        externalPasswordResetLinkMap: new Map<string, string>([
            ['en', ''],
            ['de', ''],
        ]),
        useExternal: false,
        activeProfiles: ['prod', 'jenkins', 'gitlab', 'athene', 'openapi', 'apollon'],
        allowedMinimumOrionVersion: '1.0.0',
        testServer: true,
        ribbonEnv: '',
        guidedTourMapping: {
            courseShortName: 'artemistutorial',
            tours: {
                cancel_tour: '',
                code_editor_tour: 'test',
                course_exercise_overview_tour: 'test',
                course_overview_tour: '',
                modeling_tour: 'UML Class Diagram',
                programming_exercise_fail_tour: 'test',
                programming_exercise_success_tour: 'test',
                tutor_assessment_tour: 'Patterns in Software Engineering',
            },
        },
        inProduction: true,
        openApiEnabled: true,
        sentry: { dsn: 'https://ceeb3e72ec094684aefbb132f87231f2@sentry.ase.in.tum.de/2' },
        features: [FeatureToggle.ProgrammingExercises, FeatureToggle.PlagiarismChecks],
        buildPlanURLTemplate: 'https://artemistest2jenkins.ase.in.tum.de/job/{projectKey}/job/{buildPlanId}',
        commitHashURLTemplate: 'https://artemistest2gitlab.ase.in.tum.de/{projectKey}/{repoSlug}/-/commit/{commitHash}',
        sshCloneURLTemplate: 'ssh://git@artemistest2gitlab.ase.in.tum.de:2222/',
        sshKeysURL: 'https://artemistest2gitlab.ase.in.tum.de/profile/keys',
        externalUserManagementName: '',
        externalUserManagementURL: '',
        contact: 'artemis.in@tum.de',
        registrationEnabled: true,
        needsToAcceptTerms: false,
        allowedEmailPattern: '([a-zA-Z0-9_\\-\\.\\+]+)@((tum\\.de)|(in\\.tum\\.de)|(mytum\\.de))',
        allowedEmailPatternReadable: '@tum.de, @in.tum.de, @mytum.de',
        allowedLdapUsernamePattern: undefined,
        allowedCourseRegistrationUsernamePattern: undefined,
        accountName: undefined,
        versionControlUrl: 'https://artemistest2gitlab.ase.in.tum.de',
        programmingLanguageFeatures: [
            {
                checkoutSolutionRepositoryAllowed: false,
                packageNameRequired: true,
                plagiarismCheckSupported: false,
                programmingLanguage: ProgrammingLanguage.KOTLIN,
                projectTypes: [],
                sequentialTestRuns: true,
                staticCodeAnalysis: false,
            },
            {
                checkoutSolutionRepositoryAllowed: false,
                packageNameRequired: false,
                plagiarismCheckSupported: true,
                programmingLanguage: ProgrammingLanguage.PYTHON,
                projectTypes: [],
                sequentialTestRuns: false,
                staticCodeAnalysis: false,
            },
            {
                checkoutSolutionRepositoryAllowed: false,
                packageNameRequired: true,
                plagiarismCheckSupported: false,
                programmingLanguage: ProgrammingLanguage.SWIFT,
                projectTypes: [ProjectType.PLAIN, ProjectType.XCODE],
                sequentialTestRuns: false,
                staticCodeAnalysis: true,
            },
            {
                checkoutSolutionRepositoryAllowed: false,
                packageNameRequired: false,
                plagiarismCheckSupported: true,
                programmingLanguage: ProgrammingLanguage.C,
                projectTypes: [],
                sequentialTestRuns: false,
                staticCodeAnalysis: false,
            },
            {
                checkoutSolutionRepositoryAllowed: false,
                packageNameRequired: true,
                plagiarismCheckSupported: true,
                programmingLanguage: ProgrammingLanguage.JAVA,
                projectTypes: [ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN],
                sequentialTestRuns: true,
                staticCodeAnalysis: true,
            },
        ],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
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
            service.getProfileInfo().subscribe(() => {});

            const req = httpMock.expectOne({ method: 'GET' });
            const infoUrl = SERVER_API_URL + 'management/info';
            expect(req.request.url).toEqual(infoUrl);
        });

        it('should get the profile info', fakeAsync(() => {
            service.getProfileInfo().subscribe((received) => {
                expect(received).toEqual(expectedProfileInfo);
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(serverResponse);
            tick();
        }));
    });
});
