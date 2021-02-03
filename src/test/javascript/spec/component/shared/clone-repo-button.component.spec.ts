import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';

import { CloneRepoButtonComponent } from 'app/shared/components/clone-repo-button/clone-repo-button.component';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import * as sinon from 'sinon';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ClipboardModule } from 'ngx-clipboard';
import { JhiAlertService } from 'ng-jhipster';
import { of, BehaviorSubject, Subject } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { SinonStub, stub } from 'sinon';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { By } from '@angular/platform-browser';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('JhiCloneRepoButtonComponent', () => {
    let component: CloneRepoButtonComponent;
    let fixture: ComponentFixture<CloneRepoButtonComponent>;
    let profileService: ProfileService;
    let sourceTreeService: SourceTreeService;
    let accountService: AccountService;

    let localStorageUseSshRetrieveStub: SinonStub;
    let localStorageUseSshObserveStub: SinonStub;
    let localStorageUseSshObserveStubSubject: Subject<boolean | undefined>;
    let localStorageUseSshStoreStub: SinonStub;

    const info: ProfileInfo = {
        activeProfiles: [],
        allowedMinimumOrionVersion: '',
        buildPlanURLTemplate: '',
        contact: '',
        externalUserManagementName: '',
        externalUserManagementURL: '',
        features: [],
        imprint: '',
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
            imports: [ArtemisTestModule, TranslateModule.forRoot(), NgbModule, ArtemisSharedModule, FeatureToggleModule, ClipboardModule],
            declarations: [CloneRepoButtonComponent, MockComponent(ExerciseActionButtonComponent)],
            providers: [
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                MockProvider(SourceTreeService, {}),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CloneRepoButtonComponent);
        component = fixture.componentInstance;
        profileService = TestBed.inject(ProfileService);
        sourceTreeService = TestBed.inject(SourceTreeService);
        accountService = TestBed.inject(AccountService);

        const localStorageMock = fixture.debugElement.injector.get(LocalStorageService);
        localStorageUseSshRetrieveStub = stub(localStorageMock, 'retrieve');
        localStorageUseSshObserveStub = stub(localStorageMock, 'observe');
        localStorageUseSshStoreStub = stub(localStorageMock, 'store');
        localStorageUseSshObserveStubSubject = new Subject();
        localStorageUseSshObserveStub.returns(localStorageUseSshObserveStubSubject);
    });

    afterEach(function () {
        // completely restore all fakes created through the sandbox
        sinon.restore();
    });

    it('should initialize', fakeAsync(() => {
        stubServices();

        component.ngOnInit();
        tick();
        expect(component.sshKeysUrl).to.equal(info.sshKeysURL);
        expect(component.sshTemplateUrl).to.equal(info.sshCloneURLTemplate);
        expect(component.sshEnabled).to.equal(!!info.sshCloneURLTemplate);
        expect(component.repositoryPassword).to.equal('repository_password');
        expect(component.versionControlUrl).to.equal(info.versionControlUrl);
    }));

    it('should save repository password if SourceTree returns one', () => {
        const fakeSourceTreeResponse = { password: 'repository_password' };
        sinon.replace(sourceTreeService, 'getRepositoryPassword', sinon.fake.returns(of(fakeSourceTreeResponse)));

        component.getRepositoryPassword();
        expect(component.repositoryPassword).to.equal('repository_password');
    });

    it('should not save repository password if SourceTree doesnt return one', () => {
        const fakeSourceTreeResponse = { error: 'Some password not found error' };
        sinon.replace(sourceTreeService, 'getRepositoryPassword', sinon.fake.returns(of(fakeSourceTreeResponse)));

        component.repositoryPassword = 'password';
        component.getRepositoryPassword();
        expect(component.repositoryPassword).to.equal('password');
    });

    it('should get ssh url (same url for team and individual participation)', () => {
        component.repositoryUrl = 'https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise.git';
        component.sshTemplateUrl = 'ssh://git@bitbucket.ase.in.tum.de:7999/';
        component.useSsh = true;

        component.isTeamParticipation = true;
        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).to.equal('ssh://git@bitbucket.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');

        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).to.equal('ssh://git@bitbucket.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');
    });

    it('should get html url (not the same url for team and individual participation)', () => {
        component.repositoryUrl = info.versionControlUrl!;
        component.sshTemplateUrl = 'ssh://git@bitbucket.ase.in.tum.de:7999/';
        component.useSsh = false;

        component.user = { login: 'user1', guidedTourSettings: [] };
        component.isTeamParticipation = true;
        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).to.equal(`https://${component.user.login}@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).to.equal(info.versionControlUrl!);
    });

    it('should get copy the repository url', () => {
        component.repositoryUrl = info.versionControlUrl!;
        component.useSsh = false;

        component.user = { login: 'user1', guidedTourSettings: [] };
        component.isTeamParticipation = true;
        let url = component.getHttpOrSshRepositoryUrl();
        expect(url).to.equal(`https://${component.user.login}@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        component.isTeamParticipation = false;
        url = component.getHttpOrSshRepositoryUrl();
        expect(url).to.equal(info.versionControlUrl!);
    });

    it('should fetch and store ssh preference', fakeAsync(() => {
        stubServices();

        component.sshEnabled = true;

        fixture.detectChanges();
        tick();

        expect(localStorageUseSshRetrieveStub).to.have.been.calledOnceWithExactly('useSsh');
        expect(localStorageUseSshObserveStub).to.have.been.calledOnceWithExactly('useSsh');
        expect(component.useSsh).to.be.false;

        fixture.debugElement.query(By.css('.clone-repository')).nativeElement.click();
        tick();
        fixture.debugElement.query(By.css('.use-ssh'));
        fixture.debugElement.query(By.css('.use-ssh')).nativeElement.click();
        tick();
        expect(localStorageUseSshStoreStub).to.have.been.calledOnceWithExactly('useSsh', true);
        expect(component.useSsh).to.be.true;

        fixture.debugElement.query(By.css('.use-ssh')).nativeElement.click();
        tick();
        expect(localStorageUseSshStoreStub).to.have.been.calledWithExactly('useSsh', false);
        expect(component.useSsh).to.be.false;

        localStorageUseSshObserveStubSubject.next(true);
        tick();
        expect(component.useSsh).to.be.true;

        localStorageUseSshObserveStubSubject.next(false);
        tick();
        expect(component.useSsh).to.be.false;
    }));

    function stubServices() {
        const identityStub = sinon.stub(accountService, 'identity');
        identityStub.returns(Promise.resolve({ guidedTourSettings: [], login: 'edx_userLogin' }));

        const getRepositoryPasswordStub = sinon.stub(sourceTreeService, 'getRepositoryPassword');
        getRepositoryPasswordStub.returns(of({ password: 'repository_password' }));

        const getProfileInfoStub = sinon.stub(profileService, 'getProfileInfo');
        getProfileInfoStub.returns(new BehaviorSubject(info));
    }
});
