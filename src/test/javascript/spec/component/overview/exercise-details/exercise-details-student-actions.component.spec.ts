import * as chai from 'chai';

import * as sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { SinonStub, stub } from 'sinon';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { Subject, of } from 'rxjs';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockComponent } from 'ng-mocks';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { InitializationState } from 'app/entities/participation/participation.model';
import { MockFeatureToggleService } from '../../../helpers/mocks/service/mock-feature-toggle.service';
import { ExerciseMode, ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import { MockCourseExerciseService } from '../../../helpers/mocks/service/mock-course-exercise.service';
import { ExerciseActionButtonComponent } from 'app/overview/exercise-details/exercise-action-button.component';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ArtemisTestModule } from '../../../test.module';
import { JhiAlertService } from 'ng-jhipster';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { Team } from 'app/entities/team.model';
import { RouterModule } from '@angular/router';
import { ClipboardModule } from 'ngx-clipboard';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { By } from '@angular/platform-browser';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExerciseDetailsStudentActionsComponent', () => {
    let comp: ExerciseDetailsStudentActionsComponent;
    let fixture: ComponentFixture<ExerciseDetailsStudentActionsComponent>;
    let debugElement: DebugElement;
    let courseExerciseService: CourseExerciseService;
    let profileService: ProfileService;
    let startExerciseStub: SinonStub;
    let getProfileInfoSub: SinonStub;

    let localStorageUseSshRetrieveStub: SinonStub;
    let localStorageUseSshObserveStub: SinonStub;
    let localStorageUseSshObserveStubSubject: Subject<boolean|undefined>;
    let localStorageUseSshStoreStub: SinonStub;

    const team = { id: 1, students: [{ id: 99 } as User] } as Team;
    const teamExerciseWithoutTeamAssigned = ({
        id: 42,
        type: ExerciseType.PROGRAMMING,
        mode: ExerciseMode.TEAM,
        teamMode: true,
        studentAssignedTeamIdComputed: true,
        studentParticipations: [],
    } as unknown) as ProgrammingExercise;
    const teamExerciseWithTeamAssigned = { ...teamExerciseWithoutTeamAssigned, studentAssignedTeamId: team.id, allowOfflineIde: true } as ProgrammingExercise;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), NgbModule, ArtemisSharedModule, FeatureToggleModule, RouterModule, ClipboardModule],
            declarations: [ExerciseDetailsStudentActionsComponent, MockComponent(ExerciseActionButtonComponent)],
            providers: [
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseDetailsStudentActionsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                courseExerciseService = debugElement.injector.get(CourseExerciseService);
                profileService = debugElement.injector.get(ProfileService);

                getProfileInfoSub = stub(profileService, 'getProfileInfo');
                getProfileInfoSub.returns(of({ inProduction: false, sshCloneURLTemplate: 'ssh://git@testserver.com:1234/' } as ProfileInfo));
                startExerciseStub = stub(courseExerciseService, 'startExercise');

                const localStorageMock = fixture.debugElement.injector.get(LocalStorageService);
                localStorageUseSshRetrieveStub = stub(localStorageMock, 'retrieve');
                localStorageUseSshObserveStub = stub(localStorageMock, 'observe');
                localStorageUseSshStoreStub = stub(localStorageMock, 'store');
                localStorageUseSshObserveStubSubject = new Subject();
                localStorageUseSshObserveStub.returns(localStorageUseSshObserveStubSubject);
            });
    });

    afterEach(() => {
        startExerciseStub.restore();
        getProfileInfoSub.restore();
    });

    it('should not show the buttons "Team" and "Start exercise" for a team exercise when not assigned to a team yet', fakeAsync(() => {
        comp.exercise = teamExerciseWithoutTeamAssigned;

        fixture.detectChanges();
        tick();

        const viewTeamButton = fixture.debugElement.query(By.css('.view-team'));
        expect(viewTeamButton).to.not.exist;

        const startExerciseButton = fixture.debugElement.query(By.css('.start-exercise'));
        expect(startExerciseButton).to.not.exist;
    }));

    it('should reflect the correct participation state when a team was assigned to the student', fakeAsync(() => {
        comp.exercise = teamExerciseWithoutTeamAssigned;
        fixture.detectChanges();
        tick();

        expect(comp.participationStatusWrapper()).to.be.equal(ParticipationStatus.NO_TEAM_ASSIGNED);

        comp.exercise.studentAssignedTeamId = team.id;
        fixture.detectChanges();
        tick();

        expect(comp.participationStatusWrapper()).to.be.equal(ParticipationStatus.UNINITIALIZED);
    }));

    it('should show the button "Team" for a team exercise for a student to view his team when assigned to a team', fakeAsync(() => {
        comp.exercise = teamExerciseWithTeamAssigned;

        fixture.detectChanges();
        tick();

        const viewTeamButton = fixture.debugElement.query(By.css('.view-team'));
        expect(viewTeamButton).to.exist;
    }));

    it('should show the button "Start exercise" for a team exercise when assigned to a team', fakeAsync(() => {
        comp.exercise = teamExerciseWithTeamAssigned;

        fixture.detectChanges();
        tick();

        const startExerciseButton = fixture.debugElement.query(By.css('.start-exercise'));
        expect(startExerciseButton).to.exist;
    }));

    it('should generate the correct clone urls for https and ssh', fakeAsync(() => {
        comp.ngOnInit();

        const participation = ({
            repositoryUrl: 'https://testserver.com/scm/exercise/repository.git',
        } as unknown) as ProgrammingExerciseStudentParticipation;

        comp.useSsh = true;
        expect(comp.repositoryUrl(participation)).to.be.equal('ssh://git@testserver.com:1234/exercise/repository.git');

        comp.useSsh = false;
        expect(comp.repositoryUrl(participation)).to.be.equal(participation.repositoryUrl);
    }));

    it('should reflect the correct participation state when team exercise was started', fakeAsync(() => {
        const inactivePart = { id: 2, initializationState: InitializationState.UNINITIALIZED } as StudentParticipation;
        const initPart = { id: 2, initializationState: InitializationState.INITIALIZED } as StudentParticipation;
        const participationSubject = new Subject<StudentParticipation>();

        comp.exercise = teamExerciseWithTeamAssigned;
        startExerciseStub.returns(participationSubject);
        comp.startExercise();
        participationSubject.next(inactivePart);

        fixture.detectChanges();
        tick();

        expect(comp.participationStatusWrapper()).to.be.equal(ParticipationStatus.UNINITIALIZED);
        expect(startExerciseStub).to.have.been.calledOnce;
        participationSubject.next(initPart);

        fixture.detectChanges();
        tick();

        expect(comp.participationStatusWrapper()).to.be.equal(ParticipationStatus.INITIALIZED);

        // Check that button "Start exercise" is no longer shown
        const startExerciseButton = fixture.debugElement.query(By.css('.start-exercise'));
        expect(startExerciseButton).to.not.exist;

        // Check that button "Clone repository" is shown
        const cloneRepositoryButton = fixture.debugElement.query(By.css('.clone-repository'));
        expect(cloneRepositoryButton).to.exist;

        fixture.destroy();
        flush();
    }));

    it('should fetch and store ssh preference', fakeAsync(() => {
        comp.exercise = teamExerciseWithTeamAssigned;
        comp.sshEnabled = true;

        fixture.detectChanges();
        tick();

        expect(localStorageUseSshRetrieveStub).to.have.been.calledOnceWithExactly('useSsh');
        expect(localStorageUseSshObserveStub).to.have.been.calledOnceWithExactly('useSsh');
        expect(comp.useSsh).to.be.false;

        fixture.debugElement.query(By.css('.clone-repository')).nativeElement.click();
        tick();
        fixture.debugElement.query(By.css('.use-ssh')).nativeElement.click();
        tick();
        expect(localStorageUseSshStoreStub).to.have.been.calledOnceWithExactly('useSsh', true);
        expect(comp.useSsh).to.be.true;

        fixture.debugElement.query(By.css('.use-ssh')).nativeElement.click();
        tick();
        expect(localStorageUseSshStoreStub).to.have.been.calledWithExactly('useSsh', false);
        expect(comp.useSsh).to.be.false;

        localStorageUseSshObserveStubSubject.next(true);
        tick();
        expect(comp.useSsh).to.be.true;

        localStorageUseSshObserveStubSubject.next(false);
        tick();
        expect(comp.useSsh).to.be.false;
    }));
});
