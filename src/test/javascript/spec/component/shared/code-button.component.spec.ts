import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { CodeButtonComponent, RepositoryAuthenticationMethod } from 'app/shared/components/code-button/code-button.component';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { LocalStorageService } from 'ngx-webstorage';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { SshUserSettingsService } from 'app/shared/user-settings/ssh-settings/ssh-user-settings.service';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { PROFILE_THEIA } from 'app/app.constants';
import { ProgrammingExerciseTheiaConfig } from 'app/programming/shared/entities/programming-exercise-theia.config';

describe('CodeButtonComponent', () => {
    let component: CodeButtonComponent;
    let fixture: ComponentFixture<CodeButtonComponent>;
    let profileService: ProfileService;
    let accountService: AccountService;
    let sshUserSettingsService: SshUserSettingsService;
    let localStorageMock: LocalStorageService;
    let programmingExerciseService: ProgrammingExerciseService;

    let getVcsAccessTokenSpy: jest.SpyInstance;
    let createVcsAccessTokenSpy: jest.SpyInstance;
    let getCachedSshKeysSpy: jest.SpyInstance;
    const vcsToken: string = 'vcpat-xlhBs26D4F2CGlkCM59KVU8aaV9bYdX5Mg4IK6T8W3aT';

    const user = { login: 'user1', guidedTourSettings: [], internal: true, vcsAccessToken: 'token' };
    const route = { snapshot: { url: of('courses') } } as any as ActivatedRoute;

    let localStorageState: RepositoryAuthenticationMethod = RepositoryAuthenticationMethod.SSH;
    let router: MockRouter;

    const info: ProfileInfo = {
        externalCredentialProvider: '',
        externalPasswordResetLinkMap: new Map<string, string>([
            ['en', ''],
            ['de', ''],
        ]),
        useExternal: false,
        activeProfiles: ['localvc'],
        buildPlanURLTemplate: '',
        commitHashURLTemplate: '',
        contact: '',
        externalUserManagementName: '',
        externalUserManagementURL: '',
        repositoryAuthenticationMechanisms: ['ssh', 'token', 'password'],
        features: [],
        inProduction: false,
        inDevelopment: true,
        programmingLanguageFeatures: [],
        ribbonEnv: '',
        sshCloneURLTemplate: 'ssh://git@gitlab.ase.in.tum.de:7999/',
        sshKeysURL: 'sshKeysURL',
        testServer: false,
        versionControlUrl: 'https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git',
        git: {
            branch: 'code-button',
            commit: {
                id: {
                    abbrev: '95ef2a',
                },
                time: '2022-11-20T20:35:01Z',
                user: {
                    name: 'Max Musterman',
                },
            },
        },
        theiaPortalURL: 'https://theia-test.k8s.ase.cit.tum.de',
        operatorName: 'TUM',
    };

    let participation: ProgrammingExerciseStudentParticipation = new ProgrammingExerciseStudentParticipation();

    beforeEach(() => {
        router = new MockRouter();
        router.setUrl('a');

        TestBed.configureTestingModule({
            imports: [ExerciseActionButtonComponent],
            providers: [
                MockProvider(AlertService),
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: router },
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeButtonComponent);
                component = fixture.componentInstance;
                profileService = TestBed.inject(ProfileService);
                accountService = TestBed.inject(AccountService);
                sshUserSettingsService = TestBed.inject(SshUserSettingsService);
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);

                localStorageMock = fixture.debugElement.injector.get(LocalStorageService);

                getVcsAccessTokenSpy = jest.spyOn(accountService, 'getVcsAccessToken');
                getCachedSshKeysSpy = jest.spyOn(sshUserSettingsService, 'getCachedSshKeys');
                createVcsAccessTokenSpy = jest.spyOn(accountService, 'createVcsAccessToken');

                participation = { id: 5 };
                component.user = user;

                getVcsAccessTokenSpy = jest.spyOn(accountService, 'getVcsAccessToken').mockReturnValue(of(new HttpResponse({ body: vcsToken })));
                getCachedSshKeysSpy = jest
                    .spyOn(sshUserSettingsService, 'getCachedSshKeys')
                    .mockImplementation(() => Promise.resolve([{ id: 99, publicKey: 'key' } as UserSshPublicKey]));
                fixture.componentRef.setInput('repositoryUri', '');
                jest.spyOn(localStorageMock, 'retrieve').mockImplementation((key) => {
                    return localStorageState;
                });
                jest.spyOn(localStorageMock, 'store').mockImplementation(() => {});
                createVcsAccessTokenSpy = jest
                    .spyOn(accountService, 'createVcsAccessToken')
                    .mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, statusText: 'Bad Request' })));

                // Reset input properties
                fixture.componentRef.setInput('repositoryUri', '');
                fixture.componentRef.setInput('participations', []);
                fixture.componentRef.setInput('smallButtons', true);
                fixture.componentRef.setInput('routerLinkForRepositoryView', []);

                stubServices();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        localStorageState = RepositoryAuthenticationMethod.SSH;
    });

    it('should initialize', async () => {
        fixture.componentRef.setInput('participations', [participation]);

        await component.ngOnInit();
        component.onClick();

        expect(component.sshSettingsUrl).toBe(`${window.location.origin}/user-settings/ssh`);
        expect(component.sshTemplateUrl).toBe(info.sshCloneURLTemplate);
        expect(component.versionControlUrl).toBe(info.versionControlUrl);
        expect(getCachedSshKeysSpy).toHaveBeenCalled();
    });

    it('should not load participation vcsAccessToken when it already exists in participation', async () => {
        participation.vcsAccessToken = 'vcpat-1234';
        fixture.componentRef.setInput('participations', [participation]);

        await component.ngOnInit();
        component.onClick();

        expect(component.user.vcsAccessToken).toEqual(vcsToken);
        expect(getVcsAccessTokenSpy).not.toHaveBeenCalled();
        expect(createVcsAccessTokenSpy).not.toHaveBeenCalled();
    });

    it('should load participation vcsAccessToken if it exists on the server', async () => {
        fixture.componentRef.setInput('participations', [participation]);

        await component.ngOnInit();
        fixture.detectChanges();
        await fixture.whenStable();
        component.onClick();

        expect(component.user.vcsAccessToken).toEqual(vcsToken);
        expect(getVcsAccessTokenSpy).toHaveBeenCalled();
        expect(createVcsAccessTokenSpy).not.toHaveBeenCalled();
    });

    it('should only display available authentication mechanisms', async () => {
        fixture.componentRef.setInput('participations', [participation]);
        localStorageState = RepositoryAuthenticationMethod.Password;
        await component.ngOnInit();

        component.authenticationMechanisms = [RepositoryAuthenticationMethod.Token, RepositoryAuthenticationMethod.SSH];
        component.onClick();

        expect(component.selectedAuthenticationMechanism).toEqual(RepositoryAuthenticationMethod.Token);
    });

    it('should create new vcsAccessToken when it does not exist', async () => {
        createVcsAccessTokenSpy = jest.spyOn(accountService, 'createVcsAccessToken').mockReturnValue(of(new HttpResponse({ body: vcsToken })));
        getVcsAccessTokenSpy = jest.spyOn(accountService, 'getVcsAccessToken').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not found' })));

        participation.id = 1;
        fixture.componentRef.setInput('participations', [participation]);
        await component.ngOnInit();
        fixture.detectChanges();
        await fixture.whenStable();
        component.onClick();

        expect(component.user.vcsAccessToken).toEqual(vcsToken);
        expect(getVcsAccessTokenSpy).toHaveBeenCalled();
        expect(createVcsAccessTokenSpy).toHaveBeenCalled();
    });

    it('should get ssh url (same url for team and individual participation)', async () => {
        participation.repositoryUri = 'https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.git';
        participation.team = {};
        fixture.componentRef.setInput('participations', [participation]);
        component.sshTemplateUrl = 'ssh://git@gitlab.ase.in.tum.de:7999/';
        component.isTeamParticipation = true;
        fixture.detectChanges();
        component.onClick();

        expect(component.getHttpOrSshRepositoryUri()).toBe('ssh://git@gitlab.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');

        participation.team = undefined;
        component.isTeamParticipation = false;
        expect(component.getHttpOrSshRepositoryUri()).toBe('ssh://git@gitlab.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');
    });

    it('should get copy the repository uri', async () => {
        participation.repositoryUri = info.versionControlUrl!;
        participation.team = {};
        fixture.componentRef.setInput('participations', [participation]);
        localStorageState = RepositoryAuthenticationMethod.Password;
        component.isTeamParticipation = true;
        fixture.detectChanges();

        let url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        participation.team = undefined;
        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should insert the correct token in the repository uri', async () => {
        participation.repositoryUri = `https://${component.user.login}@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        fixture.componentRef.setInput('participations', [participation]);
        localStorageState = RepositoryAuthenticationMethod.Token;

        await component.ngOnInit();
        fixture.detectChanges();
        await fixture.whenStable();

        component.onClick();

        // Placeholder is shown
        let url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}:**********@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        url = component.getHttpOrSshRepositoryUri(false);
        expect(url).toBe(`https://${component.user.login}:${vcsToken}@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        // Team participation does not include user name
        fixture.componentRef.setInput('repositoryUri', `https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
        participation.team = {};
        component.isTeamParticipation = true;

        // Placeholder is shown
        url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}:**********@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        url = component.getHttpOrSshRepositoryUri(false);
        expect(url).toBe(`https://${component.user.login}:${vcsToken}@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should add the user login and token to the URL', async () => {
        participation.repositoryUri = `https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        fixture.componentRef.setInput('participations', [participation]);

        component.isTeamParticipation = false;
        component.selectedAuthenticationMechanism = RepositoryAuthenticationMethod.Token;
        fixture.detectChanges();

        const url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}:**********@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should handle multiple participations', async () => {
        const participation1: ProgrammingExerciseStudentParticipation = {
            repositoryUri: 'https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.git',
            testRun: false,
        };
        const participation2: ProgrammingExerciseStudentParticipation = {
            repositoryUri: 'https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-practice.git',
            testRun: true,
        };
        fixture.componentRef.setInput('participations', [participation1, participation2]);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.activeParticipation).toEqual(participation1);
        expect(component.getHttpOrSshRepositoryUri()).toBe('https://edx_userLogin@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.git');
        expect(component.cloneHeadline).toBe('artemisApp.exerciseActions.cloneRatedRepository');

        component.switchPracticeMode();

        expect(component.activeParticipation).toEqual(participation2);
        expect(component.getHttpOrSshRepositoryUri()).toBe('https://edx_userLogin@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-practice.git');
        expect(component.cloneHeadline).toBe('artemisApp.exerciseActions.clonePracticeRepository');
    });

    it('should handle no participation', () => {
        fixture.componentRef.setInput('repositoryUri', 'https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.solution.git');
        fixture.componentRef.setInput('participations', []);
        component.activeParticipation = undefined;
        fixture.detectChanges();

        expect(component.isTeamParticipation).toBeFalsy();
        expect(component.getHttpOrSshRepositoryUri()).toBe('https://user1@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.solution.git');
    });

    it('should set wasCopied to true and back to false after 3 seconds on successful copy', () => {
        fixture.detectChanges();
        jest.useFakeTimers();
        component.onCopyFinished(true);
        expect(component.wasCopied).toBeTrue();
        jest.advanceTimersByTime(3000);
        expect(component.wasCopied).toBeFalse();
        jest.useRealTimers();
    });

    it('should not change wasCopied if copy is unsuccessful', () => {
        fixture.detectChanges();
        component.onCopyFinished(false);
        expect(component.wasCopied).toBeFalse();
    });

    it('should fetch and store ssh preference', fakeAsync(() => {
        participation.repositoryUri = `https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        fixture.componentRef.setInput('participations', [participation]);

        component.activeParticipation = participation;
        component.sshEnabled = true;

        fixture.detectChanges();

        expect(component.useSsh).toBeFalse();

        fixture.debugElement.query(By.css('.code-button')).nativeElement.click();
        tick();
        fixture.debugElement.query(By.css('#useSSHButton')).nativeElement.click();
        tick();
        expect(localStorageMock.store).toHaveBeenNthCalledWith(2, 'code-button-state', 'ssh');
        expect(component.useSsh).toBeTrue();

        fixture.debugElement.query(By.css('#useHTTPSButton')).nativeElement.click();
        tick();
        expect(localStorageMock.store).toHaveBeenNthCalledWith(3, 'code-button-state', 'password');
        expect(component.useSsh).toBeFalse();

        fixture.debugElement.query(By.css('#useHTTPSWithTokenButton')).nativeElement.click();
        tick();
        expect(localStorageMock.store).toHaveBeenNthCalledWith(4, 'code-button-state', 'token');
        expect(component.useSsh).toBeFalse();
        expect(component.useToken).toBeTrue();
    }));

    it.each([
        [
            [
                { id: 1, testRun: true },
                { id: 2, testRun: false },
            ],
            { dueDate: dayjs().subtract(1, 'hour') } as Exercise,
            1,
        ],
        [[{ id: 1, testRun: true }], { dueDate: dayjs().subtract(1, 'hour') } as Exercise, 1],
        [[{ id: 2, testRun: false }], { dueDate: dayjs().subtract(1, 'hour') } as Exercise, 2],
        [
            [
                { id: 1, testRun: true },
                { id: 2, testRun: false },
            ],
            { dueDate: dayjs().add(1, 'hour') } as Exercise,
            2,
        ],
        [[{ id: 2, testRun: false }], { dueDate: dayjs().add(1, 'hour') } as Exercise, 2],
        [
            [
                { id: 1, testRun: true },
                { id: 2, testRun: false },
            ],
            { dueDate: undefined } as Exercise,
            2,
        ],
        [[{ id: 2, testRun: false }], { dueDate: undefined } as Exercise, 2],
        [[{ id: 1, testRun: true }], { exerciseGroup: {} } as Exercise, 1],
    ])('should correctly choose active participation', async (participations: ProgrammingExerciseStudentParticipation[], exercise: Exercise, expected: number) => {
        fixture.componentRef.setInput('participations', participations);
        fixture.componentRef.setInput('exercise', exercise);

        fixture.detectChanges();

        expect(component.activeParticipation?.id).toBe(expected);
    });

    it.each([
        [
            'start theia button should be visible when profile is active and theia is configured',
            {
                activeProfiles: [PROFILE_THEIA],
                theiaPortalURL: 'https://theia.test',
            },
            {
                allowOnlineIde: true,
            },
            {
                theiaImage: 'this-is-a-theia-image',
            },
            true,
        ],
        [
            'start theia button should not be visible when profile is active but theia is ill-configured',
            {
                activeProfiles: [PROFILE_THEIA],
                theiaPortalURL: 'https://theia.test',
            },
            {
                allowOnlineIde: true,
            },
            {
                theiaImage: undefined,
            },
            false,
        ],
        [
            'start theia button should not be visible when profile is active but onlineIde is not activated',
            {
                activeProfiles: [PROFILE_THEIA],
                theiaPortalURL: 'https://theia.test',
            },
            {
                allowOnlineIde: false,
            },
            {
                theiaImage: 'this-is-an-old-image',
            },
            false,
        ],
        [
            'start theia button should not be visible when profile is active but url is not set',
            {
                activeProfiles: [PROFILE_THEIA],
            },
            {
                allowOnlineIde: true,
            },
            {
                theiaImage: 'this-is-a-theia-image',
            },
            false,
        ],
        [
            'start theia button should not be visible when profile is not active but url is set',
            {
                theiaPortalURL: 'https://theia.test',
            },
            {
                allowOnlineIde: true,
            },
            {
                theiaImage: 'this-is-a-theia-image',
            },
            false,
        ],
    ])('%s', async (description, profileInfo, programmingExercise, theiaConfig, expectedVisibility) => {
        const getProfileInfoStub = jest.spyOn(profileService, 'getProfileInfo');
        getProfileInfoStub.mockReturnValue(of(profileInfo as ProfileInfo));

        const getTheiaConfigStub = jest.spyOn(programmingExerciseService, 'getTheiaConfig');
        getTheiaConfigStub.mockReturnValue(of(theiaConfig as ProgrammingExerciseTheiaConfig));

        // Expand the programmingExercise by given properties
        fixture.componentRef.setInput('exercise', {
            ...programmingExercise,
        } as any);

        await component.ngOnInit();

        expect(component.theiaEnabled).toBe(expectedVisibility);
    });

    it('should include the correct data in the form submission when startOnlineIDE is called', async () => {
        const windowOpenSpy = jest.spyOn(window, 'open').mockReturnValue({ name: '' } as any);
        const documentAppendChildSpy = jest.spyOn(document.body, 'appendChild');
        const documentRemoveChildSpy = jest.spyOn(document.body, 'removeChild');
        const formSubmitSpy = jest.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => {});

        const getToolTokenSpy = jest.spyOn(accountService, 'getToolToken');

        fixture.componentRef.setInput('exercise', { buildConfig: { theiaImage: 'theia-image' } } as any);
        component.activeParticipation = { repositoryUri: 'https://repo.uri', vcsAccessToken: 'token' } as any;
        component.theiaPortalURL = 'https://theia.portal.url';

        fixture.detectChanges();

        await component.startOnlineIDE();
        expect(getToolTokenSpy).toHaveBeenCalledOnce();
        expect(windowOpenSpy).toHaveBeenCalledExactlyOnceWith('', '_blank');
        expect(documentAppendChildSpy).toHaveBeenCalledOnce();
        expect(formSubmitSpy).toHaveBeenCalledOnce();

        const form = documentAppendChildSpy.mock.calls[0]?.[0] as HTMLFormElement;
        if (!form) {
            throw new Error('Form element is undefined');
        }
        expect(form.method.toUpperCase()).toBe('GET');
        expect(form.action).toBe('https://theia.portal.url/');
        expect(form.target).toBe('Theia-IDE');

        const inputs = form.getElementsByTagName('input');
        const data: { [key: string]: string } = {
            appDef: 'theia-image',
            gitUri: 'https://user1:token@repo.uri',
            gitToken: 'token',
        };

        expect(Array.from(inputs).find((input) => input.name === 'gitUri')).toBeDefined();

        const gitUriTest = Array.from(inputs).find((input) => input.name === 'gitUri');
        expect(gitUriTest).toBeDefined();
        expect(gitUriTest!.value).toBe(data.gitUri);

        windowOpenSpy.mockRestore();
        documentAppendChildSpy.mockRestore();
        documentRemoveChildSpy.mockRestore();
        formSubmitSpy.mockRestore();
    });

    function stubServices() {
        const identityStub = jest.spyOn(accountService, 'identity');
        identityStub.mockReturnValue(
            Promise.resolve({
                guidedTourSettings: [],
                login: 'edx_userLogin',
                internal: true,
                vcsAccessToken: vcsToken,
            }),
        );

        const getProfileInfoStub = jest.spyOn(profileService, 'getProfileInfo');
        getProfileInfoStub.mockReturnValue(new BehaviorSubject(info));
    }
});
