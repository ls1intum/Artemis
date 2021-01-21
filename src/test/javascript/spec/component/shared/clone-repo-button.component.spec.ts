import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';

import { CloneRepoButtonComponent } from 'app/shared/components/clone-repo-button/clone-repo-button.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import * as sinon from 'sinon';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ClipboardModule } from 'ngx-clipboard';
import { SafeUrlPipe } from 'app/shared/pipes/safe-url.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JhiTranslateDirective } from 'ng-jhipster';
import { of, BehaviorSubject } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('JhiCloneRepoButtonComponent', () => {
    let component: CloneRepoButtonComponent;
    let fixture: ComponentFixture<CloneRepoButtonComponent>;
    let profileService: ProfileService;
    let sourceTreeService: SourceTreeService;
    let accountService: AccountService;

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
            imports: [TranslateModule.forRoot(), ClipboardModule],
            declarations: [
                CloneRepoButtonComponent,
                MockComponent(ButtonComponent),
                MockDirective(FeatureToggleDirective),
                MockComponent(ExerciseActionButtonComponent),
                MockDirective(NgbPopover),
                MockPipe(SafeUrlPipe),
                MockDirective(NgbTooltip),
                MockComponent(FaIconComponent),
                MockDirective(JhiTranslateDirective),
            ],
            providers: [{ provide: ProfileService, useClass: MockProfileService }, { provide: AccountService, useClass: MockAccountService }, MockProvider(SourceTreeService, {})],
        }).compileComponents();

        fixture = TestBed.createComponent(CloneRepoButtonComponent);
        component = fixture.componentInstance;
        profileService = TestBed.inject(ProfileService);
        sourceTreeService = TestBed.inject(SourceTreeService);
        accountService = TestBed.inject(AccountService);
    });

    afterEach(function () {
        // completely restore all fakes created through the sandbox
        sinon.restore();
    });

    it('should initialize', fakeAsync(() => {
        const identityStub = sinon.stub(accountService, 'identity');
        identityStub.returns(Promise.resolve({ guidedTourSettings: [], login: 'edx_userLogin' }));

        const getRepositoryPasswordStub = sinon.stub(sourceTreeService, 'getRepositoryPassword');
        getRepositoryPasswordStub.returns(of({ password: 'repository_password' }));

        const getProfileInfoStub = sinon.stub(profileService, 'getProfileInfo');
        getProfileInfoStub.returns(new BehaviorSubject(info));

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
        let url = component.getHtmlOrSshRepositoryUrl();
        expect(url).to.equal('ssh://git@bitbucket.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');

        component.isTeamParticipation = false;
        url = component.getHtmlOrSshRepositoryUrl();
        expect(url).to.equal('ssh://git@bitbucket.ase.in.tum.de:7999/ITCPLEASE1/itcplease1-exercise.git');
    });

    it('should get html url (not the same url for team and individual participation)', () => {
        component.repositoryUrl = 'https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git';
        component.sshTemplateUrl = 'ssh://git@bitbucket.ase.in.tum.de:7999/';
        component.useSsh = false;

        component.user = { login: 'user1', guidedTourSettings: [] };
        component.isTeamParticipation = true;
        let url = component.getHtmlOrSshRepositoryUrl();
        expect(url).to.equal(`https://${component.user.login}@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        component.isTeamParticipation = false;
        url = component.getHtmlOrSshRepositoryUrl();
        expect(url).to.equal('https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git');
    });

    it('should get copy the repository url', () => {
        component.repositoryUrl = 'https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git';
        component.useSsh = false;

        component.user = { login: 'user1', guidedTourSettings: [] };
        component.isTeamParticipation = true;
        let url = component.getHtmlOrSshRepositoryUrl();
        expect(url).to.equal(`https://${component.user.login}@bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git`);

        component.isTeamParticipation = false;
        url = component.getHtmlOrSshRepositoryUrl();
        expect(url).to.equal('https://bitbucket.ase.in.tum.de/scm/ITCPLEASE1/itcplease1-exercise-team1.git');
    });
});
