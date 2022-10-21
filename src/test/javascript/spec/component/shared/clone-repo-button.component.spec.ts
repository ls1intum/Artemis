import { ClipboardModule } from '@angular/cdk/clipboard';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { CloneRepoButtonComponent } from 'app/shared/components/clone-repo-button/clone-repo-button.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SafeUrlPipe } from 'app/shared/pipes/safe-url.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService } from 'ngx-webstorage';
import { BehaviorSubject, Subject } from 'rxjs';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

describe('JhiCloneRepoButtonComponent', () => {
    let component: CloneRepoButtonComponent;
    let fixture: ComponentFixture<CloneRepoButtonComponent>;
    let profileService: ProfileService;
    let accountService: AccountService;

    let localStorageUseSshRetrieveStub: jest.SpyInstance;
    let localStorageUseSshObserveStub: jest.SpyInstance;
    let localStorageUseSshObserveStubSubject: Subject<boolean | undefined>;
    let localStorageUseSshStoreStub: jest.SpyInstance;

    const user = { login: 'user1', guidedTourSettings: [], internal: true, vcsAccessToken: 'token' };

    const info: ProfileInfo = {
        externalCredentialProvider: '',
        externalPasswordResetLinkMap: new Map<string, string>([
            ['en', ''],
            ['de', ''],
        ]),
        useExternal: false,
        activeProfiles: [],
        allowedMinimumOrionVersion: '',
        buildPlanURLTemplate: '',
        commitHashURLTemplate: '',
        contact: '',
        externalUserManagementName: '',
        externalUserManagementURL: '',
        features: [],
        inProduction: false,
        programmingLanguageFeatures: [],
        ribbonEnv: '',
        sshCloneURLTemplate: 'ssh://git@bitbucket.ase.in.tum.de:7999/',
        sshKeysURL: 'sshKeysURL',
        testServer: false,
        versionControlUrl: 'https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git',
        versionControlAccessToken: true,
    };

    let participation: ProgrammingExerciseStudentParticipation = {};

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ClipboardModule, NgbPopoverModule],
            declarations: [
                CloneRepoButtonComponent,
                MockComponent(ExerciseActionButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(SafeUrlPipe),
                MockDirective(FeatureToggleDirective),
            ],
            providers: [
                MockProvider(AlertService),
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CloneRepoButtonComponent);
        component = fixture.componentInstance;
        profileService = TestBed.inject(ProfileService);
        accountService = TestBed.inject(AccountService);

        const localStorageMock = fixture.debugElement.injector.get(LocalStorageService);
        localStorageUseSshRetrieveStub = jest.spyOn(localStorageMock, 'retrieve');
        localStorageUseSshObserveStub = jest.spyOn(localStorageMock, 'observe');
        localStorageUseSshStoreStub = jest.spyOn(localStorageMock, 'store');
        localStorageUseSshObserveStubSubject = new Subject();
        localStorageUseSshObserveStub.mockReturnValue(localStorageUseSshObserveStubSubject);

        participation = {};
        component.user = user;
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        stubServices();

        component.ngOnInit();
        tick();
        expect(component.sshKeysUrl).toBe(info.sshKeysURL);
        expect(component.sshTemplateUrl).toBe(info.sshCloneURLTemplate);
        expect(component.sshEnabled).toBe(!!info.sshCloneURLTemplate);
        expect(component.versionControlUrl).toBe(info.versionControlUrl);
    }));

    it('should get ssh url (same url for team and individual participation)', () => {
        participation.repositoryUrl = 'https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.git';
        participation.team = {};
        component.participations = [participation];
        component.sshTemplateUrl = 'ssh://git@bitbucket.ase.in.tum.de:7999/';
        component.isTeamParticipation = true;
        component.ngOnInit();

        component.useSsh = true;

        expect(component.getHttpOrSshRepositoryUrl()).toBe('ssh://git@bitbucket.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');

        participation.team = undefined;
        component.isTeamParticipation = false;
        expect(component.getHttpOrSshRepositoryUrl()).toBe('ssh://git@bitbucket.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');
    });

    it('should get html url (not the same url for team and individual participation)', () => {
        participation.repositoryUrl = info.versionControlUrl!;
        participation.team = {};
        component.participations = [participation];
        component.sshTemplateUrl = 'ssh://git@bitbucket.ase.in.tum.de:7999/';
        component.useSsh = false;
        component.isTeamParticipation = true;
        component.ngOnInit();

        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(`https://${component.user.login}@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        participation.team = undefined;
        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(info.versionControlUrl!);
    });

    it('should get copy the repository url', () => {
        participation.repositoryUrl = info.versionControlUrl!;
        participation.team = {};
        component.participations = [participation];
        component.useSsh = false;
        component.isTeamParticipation = true;
        component.ngOnInit();

        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(`https://${component.user.login}@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        participation.team = undefined;
        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(info.versionControlUrl!);
    });

    it('should insert the correct token in the repository url', () => {
        participation.repositoryUrl = `https://${component.user.login}@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        component.participations = [participation];
        component.useSsh = false;
        component.isTeamParticipation = false;
        component.versionControlAccessTokenRequired = true;
        component.ngOnInit();

        // Placeholder is shown
        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(`https://${component.user.login}:**********@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        url = component.getHttpOrSshRepositoryUrl(false);
        expect(url).toBe(`https://${component.user.login}:token@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        // Team participation does not include user name
        component.repositoryUrl = `https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        participation.team = {};
        component.isTeamParticipation = true;

        // Placeholder is shown
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(`https://${component.user.login}:**********@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        url = component.getHttpOrSshRepositoryUrl(false);
        expect(url).toBe(`https://${component.user.login}:token@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should add the user login and token to the URL', () => {
        participation.repositoryUrl = `https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        component.participations = [participation];
        component.useSsh = false;
        component.isTeamParticipation = false;
        component.versionControlAccessTokenRequired = true;
        component.ngOnInit();

        const url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(`https://${component.user.login}:**********@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should handle multiple participations', () => {
        const participation1: ProgrammingExerciseStudentParticipation = {
            repositoryUrl: 'https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.git',
            testRun: false,
        };
        const participation2: ProgrammingExerciseStudentParticipation = {
            repositoryUrl: 'https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-practice.git',
            testRun: true,
        };
        component.participations = [participation1, participation2];
        component.ngOnInit();

        expect(component.isTeamParticipation).toBeFalse();
        expect(component.getHttpOrSshRepositoryUrl()).toBe('https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-practice.git');
        expect(component.cloneHeadline).toBe('artemisApp.exerciseActions.clonePracticeRepository');

        component.switchPracticeMode();

        expect(component.getHttpOrSshRepositoryUrl()).toBe('https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.git');
        expect(component.cloneHeadline).toBe('artemisApp.exerciseActions.cloneRatedRepository');
    });

    it('should handle no participation', () => {
        component.repositoryUrl = 'https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.solution.git';
        component.participations = [];
        component.activeParticipation = undefined;
        component.ngOnInit();

        expect(component.isTeamParticipation).toBeFalsy();
        expect(component.getHttpOrSshRepositoryUrl()).toBe('https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.solution.git');
    });

    it('should fetch and store ssh preference', fakeAsync(() => {
        stubServices();

        participation.repositoryUrl = `https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        component.participations = [participation];
        component.sshEnabled = true;

        fixture.detectChanges();
        tick();

        expect(localStorageUseSshRetrieveStub).toHaveBeenNthCalledWith(1, 'useSsh');
        expect(localStorageUseSshObserveStub).toHaveBeenNthCalledWith(1, 'useSsh');
        expect(component.useSsh).toBeFalsy();

        fixture.debugElement.query(By.css('.clone-repository')).nativeElement.click();
        tick();
        fixture.debugElement.query(By.css('#useSSHButton')).nativeElement.click();
        tick();
        expect(localStorageUseSshStoreStub).toHaveBeenNthCalledWith(1, 'useSsh', true);
        expect(component.useSsh).toBeTruthy();

        fixture.debugElement.query(By.css('#useHTTPSButton')).nativeElement.click();
        tick();
        expect(localStorageUseSshStoreStub).toHaveBeenCalledWith('useSsh', false);
        expect(component.useSsh).toBeFalsy();

        localStorageUseSshObserveStubSubject.next(true);
        tick();
        expect(component.useSsh).toBeTruthy();

        localStorageUseSshObserveStubSubject.next(false);
        tick();
        expect(component.useSsh).toBeFalsy();
    }));

    function stubServices() {
        const identityStub = jest.spyOn(accountService, 'identity');
        identityStub.mockReturnValue(Promise.resolve({ guidedTourSettings: [], login: 'edx_userLogin', internal: true, vcsAccessToken: 'token' }));

        const getProfileInfoStub = jest.spyOn(profileService, 'getProfileInfo');
        getProfileInfoStub.mockReturnValue(new BehaviorSubject(info));
    }
});
