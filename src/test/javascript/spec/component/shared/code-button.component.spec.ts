import { ClipboardModule } from '@angular/cdk/clipboard';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { CodeButtonComponent } from 'app/shared/components/code-button/code-button.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SafeUrlPipe } from 'app/shared/pipes/safe-url.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService } from 'ngx-webstorage';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideRouter } from '@angular/router';
import { SshUserSettingsService } from 'app/shared/user-settings/ssh-settings/ssh-user-settings.service';
import { MockSshUserSettingsService } from '../../helpers/mocks/service/mock-ssh-user-settings.service';
import { UserSshPublicKey } from 'app/entities/programming/user-ssh-public-key.model';

describe('CodeButtonComponent', () => {
    let component: CodeButtonComponent;
    let fixture: ComponentFixture<CodeButtonComponent>;
    let profileService: ProfileService;
    let accountService: AccountService;
    let sshUserSettingsService: SshUserSettingsService;

    let localStorageUseSshStoreStub: jest.SpyInstance;
    let getVcsAccessTokenSpy: jest.SpyInstance;
    let createVcsAccessTokenSpy: jest.SpyInstance;
    let getCachedSshKeysSpy: jest.SpyInstance;

    const vcsToken: string = 'vcpat-xlhBs26D4F2CGlkCM59KVU8aaV9bYdX5Mg4IK6T8W3aT';

    const user = { login: 'user1', guidedTourSettings: [], internal: true, vcsAccessToken: 'token' };

    const info: ProfileInfo = {
        externalCredentialProvider: '',
        externalPasswordResetLinkMap: new Map<string, string>([
            ['en', ''],
            ['de', ''],
        ]),
        useExternal: false,
        activeProfiles: ['localvc'],
        allowedMinimumOrionVersion: '',
        buildPlanURLTemplate: '',
        commitHashURLTemplate: '',
        contact: '',
        externalUserManagementName: '',
        externalUserManagementURL: '',
        features: [],
        inProduction: false,
        inDevelopment: true,
        programmingLanguageFeatures: [],
        ribbonEnv: '',
        sshCloneURLTemplate: 'ssh://git@gitlab.ase.in.tum.de:7999/',
        sshKeysURL: 'sshKeysURL',
        testServer: false,
        versionControlUrl: 'https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git',
        useVersionControlAccessToken: true,
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
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ClipboardModule, NgbPopoverModule],
            declarations: [
                CodeButtonComponent,
                MockComponent(ExerciseActionButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(SafeUrlPipe),
                MockDirective(FeatureToggleDirective),
                MockComponent(HelpIconComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                provideRouter([]),
                MockProvider(AlertService),
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: SshUserSettingsService, useClass: MockSshUserSettingsService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CodeButtonComponent);
        component = fixture.componentInstance;
        profileService = TestBed.inject(ProfileService);
        accountService = TestBed.inject(AccountService);
        sshUserSettingsService = TestBed.inject(SshUserSettingsService);

        const localStorageMock = fixture.debugElement.injector.get(LocalStorageService);
        localStorageUseSshStoreStub = jest.spyOn(localStorageMock, 'store');
        getVcsAccessTokenSpy = jest.spyOn(accountService, 'getVcsAccessToken');
        getCachedSshKeysSpy = jest.spyOn(sshUserSettingsService, 'getCachedSshKeys');
        createVcsAccessTokenSpy = jest.spyOn(accountService, 'createVcsAccessToken');

        participation = {};
        component.user = user;
    });

    // Mock the functions after the TestBed setup
    beforeEach(() => {
        getVcsAccessTokenSpy = jest.spyOn(accountService, 'getVcsAccessToken').mockReturnValue(of(new HttpResponse({ body: vcsToken })));
        getCachedSshKeysSpy = jest.spyOn(sshUserSettingsService, 'getCachedSshKeys').mockImplementation(() => Promise.resolve([{ id: 99, publicKey: 'key' } as UserSshPublicKey]));

        createVcsAccessTokenSpy = jest
            .spyOn(accountService, 'createVcsAccessToken')
            .mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, statusText: 'Bad Request' })));
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    it('should initialize', async () => {
        stubServices();

        await component.ngOnInit();

        expect(component.sshSettingsUrl).toBe(`${window.location.origin}/user-settings/ssh`);
        expect(component.sshTemplateUrl).toBe(info.sshCloneURLTemplate);
        expect(component.sshEnabled).toBe(!!info.sshCloneURLTemplate);
        expect(component.versionControlUrl).toBe(info.versionControlUrl);
        expect(getCachedSshKeysSpy).toHaveBeenCalled();
    });

    it('should create new vcsAccessToken when it does not exist', async () => {
        createVcsAccessTokenSpy = jest.spyOn(accountService, 'createVcsAccessToken').mockReturnValue(of(new HttpResponse({ body: vcsToken })));
        getVcsAccessTokenSpy = jest.spyOn(accountService, 'getVcsAccessToken').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not found' })));
        stubServices();
        participation.id = 1;
        component.useParticipationVcsAccessToken = true;
        component.participations = [participation];
        await component.ngOnInit();
        component.ngOnChanges();

        expect(component.accessTokensEnabled).toBeTrue();
        expect(component.user.vcsAccessToken).toEqual(vcsToken);
        expect(getVcsAccessTokenSpy).toHaveBeenCalled();
        expect(createVcsAccessTokenSpy).toHaveBeenCalled();
    });

    it('should not create new vcsAccessToken when it exists', async () => {
        participation.id = 1;
        component.participations = [participation];
        component.useParticipationVcsAccessToken = true;
        stubServices();
        await component.ngOnInit();
        component.ngOnChanges();

        expect(component.accessTokensEnabled).toBeTrue();
        expect(component.user.vcsAccessToken).toEqual(vcsToken);
        expect(getVcsAccessTokenSpy).toHaveBeenCalled();
        expect(createVcsAccessTokenSpy).not.toHaveBeenCalled();
    });

    it('should get ssh url (same url for team and individual participation)', () => {
        participation.repositoryUri = 'https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.git';
        participation.team = {};
        component.participations = [participation];
        component.sshTemplateUrl = 'ssh://git@gitlab.ase.in.tum.de:7999/';
        component.isTeamParticipation = true;
        component.ngOnInit();
        component.ngOnChanges();

        component.useSsh = true;
        component.sshEnabled = true;

        expect(component.getHttpOrSshRepositoryUri()).toBe('ssh://git@gitlab.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');

        participation.team = undefined;
        component.isTeamParticipation = false;
        expect(component.getHttpOrSshRepositoryUri()).toBe('ssh://git@gitlab.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');
    });

    it('should not use ssh when ssh is not enabled (even if useSsh is set)', () => {
        participation.repositoryUri = `https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        component.participations = [participation];
        component.useParticipationVcsAccessToken = true;
        component.useSsh = false;
        component.isTeamParticipation = false;
        component.accessTokensEnabled = true;
        component.ngOnInit();
        component.ngOnChanges();
        component.useToken = true;

        const url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}:**********@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should get html url (not the same url for team and individual participation)', () => {
        participation.repositoryUri = info.versionControlUrl!;
        participation.team = {};
        component.participations = [participation];
        component.sshTemplateUrl = 'ssh://git@gitlab.ase.in.tum.de:7999/';
        component.useSsh = false;
        component.isTeamParticipation = true;
        component.ngOnInit();
        component.ngOnChanges();

        let url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        participation.team = undefined;
        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should get copy the repository uri', () => {
        participation.repositoryUri = info.versionControlUrl!;
        participation.team = {};
        component.participations = [participation];
        component.useSsh = false;
        component.isTeamParticipation = true;
        component.ngOnInit();
        component.ngOnChanges();

        let url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        participation.team = undefined;
        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should insert the correct token in the repository uri', () => {
        participation.repositoryUri = `https://${component.user.login}@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        component.participations = [participation];
        component.useParticipationVcsAccessToken = true;
        component.useSsh = false;
        component.isTeamParticipation = false;
        component.accessTokensEnabled = true;
        component.ngOnInit();
        component.ngOnChanges();
        component.useToken = true;

        // Placeholder is shown
        let url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}:**********@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        url = component.getHttpOrSshRepositoryUri(false);
        expect(url).toBe(`https://${component.user.login}:token@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        // Team participation does not include user name
        component.repositoryUri = `https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        participation.team = {};
        component.isTeamParticipation = true;

        // Placeholder is shown
        url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}:**********@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        url = component.getHttpOrSshRepositoryUri(false);
        expect(url).toBe(`https://${component.user.login}:token@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should add the user login and token to the URL', () => {
        participation.repositoryUri = `https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        component.participations = [participation];
        component.useParticipationVcsAccessToken = true;
        component.useSsh = false;
        component.isTeamParticipation = false;
        component.accessTokensEnabled = true;
        component.ngOnInit();
        component.ngOnChanges();
        component.useToken = true;

        const url = component.getHttpOrSshRepositoryUri();
        expect(url).toBe(`https://${component.user.login}:**********@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should handle multiple participations', () => {
        const participation1: ProgrammingExerciseStudentParticipation = {
            repositoryUri: 'https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.git',
            testRun: false,
        };
        const participation2: ProgrammingExerciseStudentParticipation = {
            repositoryUri: 'https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-practice.git',
            testRun: true,
        };
        component.participations = [participation1, participation2];
        component.ngOnInit();
        component.ngOnChanges();

        expect(component.activeParticipation).toEqual(participation1);
        expect(component.isTeamParticipation).toBeFalse();
        expect(component.getHttpOrSshRepositoryUri()).toBe('https://user1@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.git');
        expect(component.cloneHeadline).toBe('artemisApp.exerciseActions.cloneRatedRepository');

        component.switchPracticeMode();

        expect(component.activeParticipation).toEqual(participation2);
        expect(component.getHttpOrSshRepositoryUri()).toBe('https://user1@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-practice.git');
        expect(component.cloneHeadline).toBe('artemisApp.exerciseActions.clonePracticeRepository');
    });

    it('should handle no participation', () => {
        component.repositoryUri = 'https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.solution.git';
        component.participations = [];
        component.activeParticipation = undefined;
        component.ngOnInit();

        expect(component.isTeamParticipation).toBeFalsy();
        expect(component.getHttpOrSshRepositoryUri()).toBe('https://user1@gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.solution.git');
    });

    it('should set wasCopied to true and back to false after 3 seconds on successful copy', () => {
        component.ngOnInit();
        jest.useFakeTimers();
        component.onCopyFinished(true);
        expect(component.wasCopied).toBeTrue();
        jest.advanceTimersByTime(3000);
        expect(component.wasCopied).toBeFalse();
        jest.useRealTimers();
    });

    it('should not change wasCopied if copy is unsuccessful', () => {
        component.ngOnInit();
        component.onCopyFinished(false);
        expect(component.wasCopied).toBeFalse();
    });

    it('should fetch and store ssh preference', fakeAsync(() => {
        stubServices();

        participation.repositoryUri = `https://gitlab.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        component.participations = [participation];
        component.activeParticipation = participation;
        component.sshEnabled = true;
        component.accessTokensEnabled = true;

        fixture.detectChanges();
        tick();

        expect(component.useSsh).toBeFalse();

        fixture.debugElement.query(By.css('.code-button')).nativeElement.click();
        tick();
        fixture.debugElement.query(By.css('#useSSHButton')).nativeElement.click();
        tick();
        expect(localStorageUseSshStoreStub).toHaveBeenNthCalledWith(1, 'useSsh', true);
        expect(component.useSsh).toBeTrue();

        fixture.debugElement.query(By.css('#useHTTPSButton')).nativeElement.click();
        tick();
        expect(localStorageUseSshStoreStub).toHaveBeenCalledWith('useSsh', false);
        expect(component.useSsh).toBeFalse();

        fixture.debugElement.query(By.css('#useHTTPSWithTokenButton')).nativeElement.click();
        tick();
        expect(localStorageUseSshStoreStub).toHaveBeenCalledWith('useSsh', false);
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
    ])('should correctly choose active participation', (participations: ProgrammingExerciseStudentParticipation[], exercise: Exercise, expected: number) => {
        component.participations = participations;
        component.exercise = exercise;
        component.ngOnChanges();
        expect(component.activeParticipation?.id).toBe(expected);
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
