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
import { ClipboardModule } from 'ngx-clipboard';
import { AlertService } from 'app/core/util/alert.service';
import { of, BehaviorSubject, Subject } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { By } from '@angular/platform-browser';
import { ArtemisTestModule } from '../../test.module';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';
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
                { provide: AlertService, useClass: MockAlertService },
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
        sourceTreeService = TestBed.inject(SourceTreeService);
        accountService = TestBed.inject(AccountService);

        const localStorageMock = fixture.debugElement.injector.get(LocalStorageService);
        localStorageUseSshRetrieveStub = jest.spyOn(localStorageMock, 'retrieve');
        localStorageUseSshObserveStub = jest.spyOn(localStorageMock, 'observe');
        localStorageUseSshStoreStub = jest.spyOn(localStorageMock, 'store');
        localStorageUseSshObserveStubSubject = new Subject();
        localStorageUseSshObserveStub.mockReturnValue(localStorageUseSshObserveStubSubject);
    });

    afterEach(function () {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        stubServices();

        component.ngOnInit();
        tick();
        expect(component.sshKeysUrl).toEqual(info.sshKeysURL);
        expect(component.sshTemplateUrl).toEqual(info.sshCloneURLTemplate);
        expect(component.sshEnabled).toEqual(!!info.sshCloneURLTemplate);
        expect(component.repositoryPassword).toEqual('repository_password');
        expect(component.versionControlUrl).toEqual(info.versionControlUrl);
    }));

    it('should save repository password if SourceTree returns one', () => {
        const fakeSourceTreeResponse = { password: 'repository_password' };
        jest.spyOn(sourceTreeService, 'getRepositoryPassword').mockReturnValue(of(fakeSourceTreeResponse));

        component.getRepositoryPassword();
        expect(component.repositoryPassword).toEqual('repository_password');
    });

    it('should not save repository password if SourceTree doesnt return one', () => {
        const fakeSourceTreeResponse = { error: 'Some password not found error' };
        jest.spyOn(sourceTreeService, 'getRepositoryPassword').mockReturnValue(of(fakeSourceTreeResponse));

        component.repositoryPassword = 'password';
        component.getRepositoryPassword();
        expect(component.repositoryPassword).toEqual('password');
    });

    it('should get ssh url (same url for team and individual participation)', () => {
        component.repositoryUrl = 'https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.git';
        component.sshTemplateUrl = 'ssh://git@bitbucket.ase.in.tum.de:7999/';
        component.useSsh = true;

        component.isTeamParticipation = true;
        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).toEqual('ssh://git@bitbucket.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');

        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).toEqual('ssh://git@bitbucket.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');
    });

    it('should get html url (not the same url for team and individual participation)', () => {
        component.repositoryUrl = info.versionControlUrl!;
        component.sshTemplateUrl = 'ssh://git@bitbucket.ase.in.tum.de:7999/';
        component.useSsh = false;

        component.user = { login: 'user1', guidedTourSettings: [] };
        component.isTeamParticipation = true;
        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).toEqual(`https://${component.user.login}@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).toEqual(info.versionControlUrl!);
    });

    it('should get copy the repository url', () => {
        component.repositoryUrl = info.versionControlUrl!;
        component.useSsh = false;

        component.user = { login: 'user1', guidedTourSettings: [] };
        component.isTeamParticipation = true;
        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).toEqual(`https://${component.user.login}@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).toEqual(info.versionControlUrl!);
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
        fixture.debugElement.query(By.css('.use-ssh'));
        fixture.debugElement.query(By.css('.use-ssh')).nativeElement.click();
        tick();
        expect(localStorageUseSshStoreStub).toHaveBeenNthCalledWith(1, 'useSsh', true);
        expect(component.useSsh).toBeTruthy();

        fixture.debugElement.query(By.css('.use-ssh')).nativeElement.click();
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
        identityStub.mockReturnValue(Promise.resolve({ guidedTourSettings: [], login: 'edx_userLogin' }));

        const getRepositoryPasswordStub = jest.spyOn(sourceTreeService, 'getRepositoryPassword');
        getRepositoryPasswordStub.mockReturnValue(of({ password: 'repository_password' }));

        const getProfileInfoStub = jest.spyOn(profileService, 'getProfileInfo');
        getProfileInfoStub.mockReturnValue(new BehaviorSubject(info));
    }
});
