import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { SubmissionType } from 'app/entities/submission.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ExamResultSummaryExerciseCardHeaderComponent } from 'app/exam/participate/summary/exercises/header/exam-result-summary-exercise-card-header.component';
import { ResultSummaryExerciseInfo } from 'app/exam/participate/summary/exam-result-summary.component';

let fixture: ComponentFixture<ExamResultSummaryExerciseCardHeaderComponent>;
let component: ExamResultSummaryExerciseCardHeaderComponent;

const user = { id: 1, name: 'Test User' } as User;

const exam = {
    id: 1,
    title: 'ExamForTesting',
} as Exam;

const exerciseGroup = {
    exam,
    title: 'exercise group',
} as ExerciseGroup;

const programmingSubmission = { id: 1 } as ProgrammingSubmission;

const programmingParticipation = { id: 4, student: user, submissions: [programmingSubmission] } as StudentParticipation;

const programmingExercise = { id: 4, type: ExerciseType.PROGRAMMING, studentParticipations: [programmingParticipation], exerciseGroup } as ProgrammingExercise;

describe('ExamResultSummaryExerciseCardHeaderComponent', () => {
    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [ExamResultSummaryExerciseCardHeaderComponent, MockComponent(FaIconComponent), MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamResultSummaryExerciseCardHeaderComponent);
                component = fixture.componentInstance;
                component.index = 3;
                component.exercise = programmingExercise;
                component.exerciseInfo = { isCollapsed: false } as ResultSummaryExerciseInfo;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([
        [{}, false],
        [{ studentParticipations: null }, false],
        [{ studentParticipations: undefined }, false],
        [{ studentParticipations: [] }, false],
        [{ studentParticipations: [{}] }, false],
        [{ studentParticipations: [{ submissions: null }] }, false],
        [{ studentParticipations: [{ submissions: undefined }] }, false],
        [{ studentParticipations: [{ submissions: [{ type: SubmissionType.MANUAL }] }] }, false],
        [{ studentParticipations: [{ submissions: [{ type: SubmissionType.ILLEGAL }] }] }, true],
    ])('should handle missing/empty fields correctly for %o when displaying illegal submission badge', (exercise, shouldBeNonNull) => {
        component.exercise = exercise as Exercise;

        fixture.detectChanges();
        const span = fixture.debugElement.query(By.css('.badge.bg-danger'));
        if (shouldBeNonNull) {
            expect(span).not.toBeNull();
        } else {
            expect(span).toBeNull();
        }
    });

    it('should show exercise group title', () => {
        fixture.detectChanges();

        const exerciseTitleElement: HTMLElement = fixture.nativeElement.querySelector('#exercise-group-title-' + programmingExercise.id);
        expect(exerciseTitleElement.textContent).toContain('#' + (component.index + 1));
        expect(exerciseTitleElement.textContent).toContain(programmingExercise.exerciseGroup?.title);
    });
});
