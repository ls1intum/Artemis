import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { CloneRepoButtonComponent } from 'app/shared/components/clone-repo-button/clone-repo-button.component';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { NgbPopoverModule } from '@ng-bootstrap/ng-bootstrap';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { AlertService } from 'app/core/util/alert.service';
import { BehaviorSubject, Subject } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { By } from '@angular/platform-browser';
import { ArtemisTestModule } from '../../test.module';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SafeUrlPipe } from 'app/shared/pipes/safe-url.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';

describe('JhiCloneRepoButtonComponent', () => {
    let component: CloneRepoButtonComponent;
    let fixture: ComponentFixture<CloneRepoButtonComponent>;
    let profileService: ProfileService;
    let sourceTreeService: SourceTreeService;
    let accountService: AccountService;

    let localStorageUseSshRetrieveStub: jest.SpyInstance;
    let localStorageUseSshObserveStub: jest.SpyInstance;
    let localStorageUseSshObserveStubSubject: Subject<boolean | undefined>;
    let localStorageUseSshStoreStub: jest.SpyInstance;

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
                MockProvider(SourceTreeService, {}),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CloneRepoButtonComponent);
        component = fixture.componentInstance;
        profileService = TestBed.inject(ProfileService);
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        sourceTreeService = TestBed.inject(SourceTreeService);
        accountService = TestBed.inject(AccountService);

        const localStorageMock = fixture.debugElement.injector.get(LocalStorageService);
        localStorageUseSshRetrieveStub = jest.spyOn(localStorageMock, 'retrieve');
        localStorageUseSshObserveStub = jest.spyOn(localStorageMock, 'observe');
        localStorageUseSshStoreStub = jest.spyOn(localStorageMock, 'store');
        localStorageUseSshObserveStubSubject = new Subject();
        localStorageUseSshObserveStub.mockReturnValue(localStorageUseSshObserveStubSubject);
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
        component.repositoryUrl = 'https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.git';
        component.sshTemplateUrl = 'ssh://git@bitbucket.ase.in.tum.de:7999/';
        component.useSsh = true;

        component.isTeamParticipation = true;
        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe('ssh://git@bitbucket.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');

        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe('ssh://git@bitbucket.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');
    });

    it('should get html url (not the same url for team and individual participation)', () => {
        component.repositoryUrl = info.versionControlUrl!;
        component.sshTemplateUrl = 'ssh://git@bitbucket.ase.in.tum.de:7999/';
        component.useSsh = false;

        component.user = { login: 'user1', guidedTourSettings: [], internal: true };
        component.isTeamParticipation = true;
        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(`https://${component.user.login}@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(info.versionControlUrl!);
    });

    it('should get copy the repository url', () => {
        component.repositoryUrl = info.versionControlUrl!;
        component.useSsh = false;

        component.user = { login: 'user1', guidedTourSettings: [], internal: true };
        component.isTeamParticipation = true;
        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(`https://${component.user.login}@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(info.versionControlUrl!);
    });

    it('should insert the correct token in the repository url', () => {
        component.user = { login: 'user1', guidedTourSettings: [], internal: true, vcsAccessToken: 'token' };
        component.repositoryUrl = `https://${component.user.login}@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        component.useSsh = false;
        component.isTeamParticipation = false;

        component.versionControlAccessTokenRequired = true;

        // Placeholder is shown
        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(`https://${component.user.login}:**********@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        url = component.getHttpOrSshRepositoryUrl(false);
        expect(url).toBe(`https://${component.user.login}:token@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        // Team participation does not include user name
        component.repositoryUrl = `https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        component.isTeamParticipation = true;

        // Placeholder is shown
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(`https://${component.user.login}:**********@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        url = component.getHttpOrSshRepositoryUrl(false);
        expect(url).toBe(`https://${component.user.login}:token@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should add the user login and token to the URL', () => {
        component.user = { login: 'user1', guidedTourSettings: [], internal: true, vcsAccessToken: 'token' };
        component.repositoryUrl = `https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`;
        component.useSsh = false;
        component.isTeamParticipation = false;
        component.versionControlAccessTokenRequired = true;

        const url = component.getHttpOrSshRepositoryUrl();
        expect(url).toBe(`https://${component.user.login}:**********@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);
    });

    it('should fetch and store ssh preference', fakeAsync(() => {
        stubServices();

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
