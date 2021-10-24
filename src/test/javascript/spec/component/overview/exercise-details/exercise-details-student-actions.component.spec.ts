import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { SinonStub, stub } from 'sinon';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { Subject, of } from 'rxjs';
import { MockComponent, MockPipe } from 'ng-mocks';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { InitializationState } from 'app/entities/participation/participation.model';
import { MockFeatureToggleService } from '../../../helpers/mocks/service/mock-feature-toggle.service';
import { ExerciseMode, ExerciseType, ParticipationStatus } from 'app/entities/exercise.model';
import { MockCourseExerciseService } from '../../../helpers/mocks/service/mock-course-exercise.service';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ArtemisTestModule } from '../../../test.module';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { Team } from 'app/entities/team.model';
import { ClipboardModule } from 'ngx-clipboard';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { By } from '@angular/platform-browser';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { CloneRepoButtonComponent } from 'app/shared/components/clone-repo-button/clone-repo-button.component';
import { LocalStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { MockRouterLinkDirective } from '../../lecture-unit/lecture-unit-management.component.spec';

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

    const team = { id: 1, students: [{ id: 99 } as User] } as Team;
    const teamExerciseWithoutTeamAssigned = {
        id: 42,
        type: ExerciseType.PROGRAMMING,
        mode: ExerciseMode.TEAM,
        teamMode: true,
        studentAssignedTeamIdComputed: true,
        studentParticipations: [],
    } as unknown as ProgrammingExercise;
    const teamExerciseWithTeamAssigned = { ...teamExerciseWithoutTeamAssigned, studentAssignedTeamId: team.id, allowOfflineIde: true } as ProgrammingExercise;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbModule, FeatureToggleModule, ClipboardModule],
            declarations: [
                ExerciseDetailsStudentActionsComponent,
                MockComponent(ExerciseActionButtonComponent),
                MockComponent(CloneRepoButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                ExtensionPointDirective,
                MockRouterLinkDirective,
            ],
            providers: [
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        })
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
        const cloneRepositoryButton = fixture.debugElement.query(By.css('jhi-clone-repo-button'));
        expect(cloneRepositoryButton).to.exist;

        fixture.destroy();
        flush();
    }));
});
