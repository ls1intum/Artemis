import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { User } from 'app/account/user/user.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ExamResultSummaryExerciseCardHeaderComponent } from 'app/exam/overview/summary/exercises/header/exam-result-summary-exercise-card-header.component';
import { ResultSummaryExerciseInfo } from 'app/exam/overview/summary/exam-result-summary.component';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

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

const programmingExercise = {
    id: 4,
    type: ExerciseType.PROGRAMMING,
    studentParticipations: [programmingParticipation],
    exerciseGroup,
} as ProgrammingExercise;

describe('ExamResultSummaryExerciseCardHeaderComponent', () => {
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamResultSummaryExerciseCardHeaderComponent],
        })
            .overrideComponent(ExamResultSummaryExerciseCardHeaderComponent, {
                set: {
                    imports: [MockComponent(FaIconComponent), MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(ExamResultSummaryExerciseCardHeaderComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('index', 3);
        fixture.componentRef.setInput('exercise', programmingExercise);
        fixture.componentRef.setInput('exerciseInfo', { isCollapsed: false } as ResultSummaryExerciseInfo);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should show exercise group title', () => {
        fixture.detectChanges();

        const exerciseTitleElement: HTMLElement = fixture.nativeElement.querySelector('#exercise-group-title-' + programmingExercise.id);
        expect(exerciseTitleElement.textContent).toContain('#' + (component.index() + 1));
        expect(exerciseTitleElement.textContent).toContain(programmingExercise.exerciseGroup?.title);
    });
});
