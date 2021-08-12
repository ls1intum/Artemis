import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { NgJhipsterModule } from 'ng-jhipster';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { of } from 'rxjs';
import * as sinon from 'sinon';
import { mockTeam, MockTeamService } from '../../helpers/mocks/service/mock-team.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { TeamParticipationTableComponent } from 'app/exercises/shared/team/team-participation-table/team-participation-table.component';
import { Exercise, ExerciseMode, ExerciseType } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { MockRouter } from '../../helpers/mocks/service/mock-route.service';
import { Router, RouterModule } from '@angular/router';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('TeamParticipationTableComponent', () => {
    let comp: TeamParticipationTableComponent;
    let fixture: ComponentFixture<TeamParticipationTableComponent>;
    let debugElement: DebugElement;
    let teamService: TeamService;
    const course = {
        id: 123,
        title: 'Course Title',
        isAtLeastInstructor: true,
        isAtLeastEditor: true,
        endDate: moment().subtract(5, 'minutes'),
        courseArchivePath: 'some-path',
    } as Course;
    const exercise1 = {
        id: 1,
        type: ExerciseType.MODELING,
        mode: ExerciseMode.TEAM,
        teams: [mockTeam],
        course,
    } as Exercise;

    const submission2 = {
        id: 2,
        submitted: true,
        submissionDate: moment().subtract(10, 'minutes'),
        results: [
            {
                id: 2,
                successful: true,
            },
        ],
        submissionExerciseType: SubmissionExerciseType.PROGRAMMING,
    } as Submission;
    const exercise2 = {
        id: 2,
        type: ExerciseType.TEXT,
        mode: ExerciseMode.TEAM,
        teams: [mockTeam],
        course,
        studentParticipations: [
            {
                id: 2,
                team: mockTeam,
                submissions: [submission2],
            },
        ],
    } as Exercise;

    const submission3 = {
        id: 3,
        submitted: true,
        submissionDate: moment().subtract(10, 'minutes'),
    } as Submission;
    const exercise3 = {
        id: 3,
        type: ExerciseType.FILE_UPLOAD,
        mode: ExerciseMode.TEAM,
        dueDate: moment().subtract(5, 'minutes'),
        teams: [mockTeam],
        course,
        studentParticipations: [
            {
                id: 1,
                team: mockTeam,
                submissions: [submission3],
            },
        ],
    } as Exercise;
    const submission4 = {
        id: 4,
        submitted: true,
        submissionDate: moment().subtract(10, 'minutes'),
        results: [
            {
                id: 2,
                successful: true,
                completionDate: moment().subtract(5, 'minutes'),
            },
        ],
    } as Submission;
    const exercise4 = {
        id: 3,
        type: ExerciseType.PROGRAMMING,
        mode: ExerciseMode.TEAM,
        dueDate: moment().add(5, 'minutes'),
        teams: [mockTeam],
        course,
        studentParticipations: [
            {
                id: 1,
                team: mockTeam,
                submissions: [submission4],
            },
        ],
    } as Exercise;

    const submission5 = {
        id: 5,
        submitted: true,
        submissionDate: moment().subtract(10, 'minutes'),
        results: [
            {
                id: 2,
                successful: true,
                completionDate: moment().subtract(5, 'minutes'),
            },
        ],
    } as Submission;
    const exercise5 = {
        id: 5,
        type: ExerciseType.MODELING,
        mode: ExerciseMode.TEAM,
        dueDate: moment().add(5, 'minutes'),
        teams: [mockTeam],
        course,
        studentParticipations: [
            {
                id: 1,
                team: mockTeam,
                submissions: [submission5],
            },
        ],
    } as Exercise;
    course.exercises = [exercise1, exercise2, exercise3, exercise4, exercise5];

    let router: any;

    beforeEach(async () => {
        router = new MockRouter();
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgxDatatableModule), MockModule(RouterModule), MockModule(NgJhipsterModule), MockModule(NgbModule)],
            declarations: [TeamParticipationTableComponent, MockComponent(DataTableComponent), MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [
                MockProvider(TranslateService),
                ExerciseService,
                ParticipationService,
                { provide: TeamService, useClass: MockTeamService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TeamParticipationTableComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                teamService = TestBed.inject(TeamService);
            });
    });

    beforeEach(fakeAsync(() => {
        const exercisesStub = sinon.stub(teamService, 'findCourseWithExercisesAndParticipationsForTeam');
        exercisesStub.returns(of(new HttpResponse({ body: course })));
        comp.course = course;
        comp.exercise = exercise4;
        comp.team = mockTeam;
        comp.ngOnInit();
        tick();
    }));

    it('Exercises for one team are loaded correctly', fakeAsync(() => {
        // Make sure that all 3 exercises were received for exercise
        expect(comp.exercises).to.have.length(course.exercises?.length!);

        // Check that ngx-datatable is present
        const datatable = debugElement.query(By.css('jhi-data-table'));
        expect(datatable).to.exist;
    }));

    it('Assessment Action "continue" is triggered', fakeAsync(() => {
        const expectedAssessmentAction = comp.assessmentAction(submission2);
        expect(expectedAssessmentAction).to.be.equal('continue');
    }));

    it('Assessment Action "start" is triggered', fakeAsync(() => {
        const expectedAssessmentAction = comp.assessmentAction(submission3);
        expect(expectedAssessmentAction).to.be.equal('start');
    }));

    it('Assessment Action "open" is triggered', fakeAsync(() => {
        const expectedAssessmentAction = comp.assessmentAction(submission4);
        expect(expectedAssessmentAction).to.be.equal('open');
    }));

    it('Navigate to assessment editor when opening exercise submission', fakeAsync(() => {
        const participation = exercise2.studentParticipations![0];
        comp.openAssessmentEditor(exercise2, participation, 'new');
        tick();
        expect(router.navigate).to.have.been.calledOnce;
        const navigationUrl = router.navigate.getCall(0).args[0];
        expect(navigationUrl).to.deep.equal([
            '/course-management',
            course.id!.toString(),
            exercise2.type! + '-exercises',
            exercise2.id!.toString(),
            'submissions',
            'new',
            'assessment',
        ]);
    }));

    it('Check enabled assessment button for exercises without due date', fakeAsync(() => {
        const expectedAssessmentActionButtonDisabled = comp.isAssessmentButtonDisabled(exercise2, submission2);
        expect(expectedAssessmentActionButtonDisabled).to.be.equal(false);
    }));

    it('Check enabled assessment button for exercises with submission and passed due date', fakeAsync(() => {
        const expectedAssessmentActionButtonDisabled = comp.isAssessmentButtonDisabled(exercise3, submission3);
        expect(expectedAssessmentActionButtonDisabled).to.be.equal(false);
    }));

    it('Check disabled assessment button for exercises without submission', fakeAsync(() => {
        const expectedAssessmentActionButtonDisabled = comp.isAssessmentButtonDisabled(exercise1, undefined);
        expect(expectedAssessmentActionButtonDisabled).to.be.equal(true);
    }));

    it('Check disabled assessment button for exercises before due date as tutor', fakeAsync(() => {
        const expectedAssessmentActionButtonDisabled = comp.isAssessmentButtonDisabled(
            {
                ...exercise4,
                isAtLeastInstructor: false,
            },
            submission4,
        );
        expect(expectedAssessmentActionButtonDisabled).to.be.equal(true);
    }));

    it('Check disabled assessment button for programming exercises before due date as instructor', fakeAsync(() => {
        const expectedAssessmentActionButtonDisabled = comp.isAssessmentButtonDisabled(
            {
                ...exercise4,
                isAtLeastInstructor: true,
            },
            submission4,
        );
        expect(expectedAssessmentActionButtonDisabled).to.be.equal(true);
    }));

    it('Check enabled assessment button for exercises before due date as instructor', fakeAsync(() => {
        const expectedAssessmentActionButtonDisabled = comp.isAssessmentButtonDisabled(
            {
                ...exercise5,
                isAtLeastInstructor: true,
            },
            submission5,
        );
        expect(expectedAssessmentActionButtonDisabled).to.be.equal(false);
    }));
});
