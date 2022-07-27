import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { of } from 'rxjs';
import { mockTeam, MockTeamService } from '../../helpers/mocks/service/mock-team.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { TeamParticipationTableComponent } from 'app/exercises/shared/team/team-participation-table/team-participation-table.component';
import { Exercise, ExerciseMode, ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { Router, RouterModule } from '@angular/router';
import { MockDirective, MockModule, MockPipe, MockProvider, MockComponent } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';

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
        endDate: dayjs().subtract(5, 'minutes'),
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
        submissionDate: dayjs().subtract(10, 'minutes'),
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
        submissionDate: dayjs().subtract(10, 'minutes'),
    } as Submission;
    const exercise3 = {
        id: 3,
        type: ExerciseType.FILE_UPLOAD,
        mode: ExerciseMode.TEAM,
        dueDate: dayjs().subtract(5, 'minutes'),
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
        submissionDate: dayjs().subtract(10, 'minutes'),
        results: [
            {
                id: 2,
                successful: true,
                completionDate: dayjs().subtract(5, 'minutes'),
            },
        ],
    } as Submission;
    const exercise4 = {
        id: 3,
        type: ExerciseType.PROGRAMMING,
        mode: ExerciseMode.TEAM,
        dueDate: dayjs().add(5, 'minutes'),
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
        submissionDate: dayjs().subtract(10, 'minutes'),
        results: [
            {
                id: 2,
                successful: true,
                completionDate: dayjs().subtract(5, 'minutes'),
            },
        ],
    } as Submission;
    const exercise5 = {
        id: 5,
        type: ExerciseType.MODELING,
        mode: ExerciseMode.TEAM,
        dueDate: dayjs().add(5, 'minutes'),
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

    let router: Router;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgxDatatableModule), MockModule(RouterModule), MockModule(NgbModule)],
            declarations: [
                TeamParticipationTableComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
                MockComponent(AssessmentWarningComponent),
                MockComponent(DataTableComponent),
            ],
            providers: [
                MockProvider(TranslateService),
                { provide: TeamService, useClass: MockTeamService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TeamParticipationTableComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                teamService = TestBed.inject(TeamService);
                router = TestBed.inject(Router);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    beforeEach(fakeAsync(() => {
        jest.spyOn(teamService, 'findCourseWithExercisesAndParticipationsForTeam').mockReturnValue(of(new HttpResponse({ body: course })));
        comp.course = course;
        comp.exercise = exercise4;
        comp.team = mockTeam;
        jest.spyOn(router, 'navigate').mockImplementation();
        comp.ngOnInit();
        tick();
    }));

    it('Exercises for one team are loaded correctly', () => {
        // Make sure that all 3 exercises were received for exercise
        expect(comp.exercises).toHaveLength(course.exercises!.length);

        // Check that ngx-datatable is present
        const datatable = debugElement.query(By.css('jhi-data-table'));
        expect(datatable).not.toBeNull();
    });

    it('Assessment Action "continue" is triggered', () => {
        const expectedAssessmentAction = comp.assessmentAction(submission2);
        expect(expectedAssessmentAction).toBe('continue');
    });

    it('Assessment Action "start" is triggered', () => {
        const expectedAssessmentAction = comp.assessmentAction(submission3);
        expect(expectedAssessmentAction).toBe('start');
    });

    it('Assessment Action "open" is triggered', () => {
        const expectedAssessmentAction = comp.assessmentAction(submission4);
        expect(expectedAssessmentAction).toBe('open');
    });

    it('Navigate to assessment editor when opening exercise submission', fakeAsync(() => {
        const participation = exercise2.studentParticipations![0];
        comp.openAssessmentEditor(exercise2, participation, 'new');
        tick();
        expect(router.navigate).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith([
            '/course-management',
            course.id!.toString(),
            exercise2.type! + '-exercises',
            exercise2.id!.toString(),
            'submissions',
            'new',
            'assessment',
        ]);
    }));

    it('Check enabled assessment button for exercises without due date', () => {
        const expectedAssessmentActionButtonDisabled = comp.isAssessmentButtonDisabled(exercise2, submission2);
        expect(expectedAssessmentActionButtonDisabled).toBeFalse();
    });

    it('Check enabled assessment button for exercises with submission and passed due date', () => {
        const expectedAssessmentActionButtonDisabled = comp.isAssessmentButtonDisabled(exercise3, submission3);
        expect(expectedAssessmentActionButtonDisabled).toBeFalse();
    });

    it('Check disabled assessment button for exercises without submission', () => {
        const expectedAssessmentActionButtonDisabled = comp.isAssessmentButtonDisabled(exercise1, undefined);
        expect(expectedAssessmentActionButtonDisabled).toBeTrue();
    });

    it('Check disabled assessment button for exercises before due date as tutor', () => {
        const expectedAssessmentActionButtonDisabled = comp.isAssessmentButtonDisabled(
            {
                ...exercise4,
                isAtLeastInstructor: false,
            },
            submission4,
        );
        expect(expectedAssessmentActionButtonDisabled).toBeTrue();
    });

    it('Check disabled assessment button for programming exercises before due date as instructor', () => {
        const expectedAssessmentActionButtonDisabled = comp.isAssessmentButtonDisabled(
            {
                ...exercise4,
                isAtLeastInstructor: true,
            },
            submission4,
        );
        expect(expectedAssessmentActionButtonDisabled).toBeTrue();
    });

    it('Check enabled assessment button for exercises before due date as instructor', () => {
        const expectedAssessmentActionButtonDisabled = comp.isAssessmentButtonDisabled(
            {
                ...exercise5,
                isAtLeastInstructor: true,
            },
            submission5,
        );
        expect(expectedAssessmentActionButtonDisabled).toBeFalse();
    });
});
