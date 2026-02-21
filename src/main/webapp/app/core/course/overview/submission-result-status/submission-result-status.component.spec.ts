import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { QuizBatch, QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { SubmissionResultStatusComponent } from 'app/core/course/overview/submission-result-status/submission-result-status.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('SubmissionResultStatusComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: SubmissionResultStatusComponent;
    let fixture: ComponentFixture<SubmissionResultStatusComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [SubmissionResultStatusComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).overrideComponent(SubmissionResultStatusComponent, {
            remove: { imports: [UpdatingResultComponent, ProgrammingExerciseStudentTriggerBuildButtonComponent] },
            add: { imports: [MockComponent(UpdatingResultComponent), MockComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent), MockPipe(ArtemisTranslatePipe)] },
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(SubmissionResultStatusComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('effect on input changes', () => {
        it.each([
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: false }, { started: true }] } as QuizExercise, undefined, true],
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] } as QuizExercise, undefined, false],
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, undefined, false],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, undefined, true],
            [{ dueDate: dayjs().subtract(1, 'hour'), studentParticipations: [{}] } as Exercise, {} as StudentParticipation, false],
            [{ dueDate: dayjs().add(1, 'hour'), studentParticipations: [{}] } as Exercise, {} as StudentParticipation, false],
        ])('should determine if it is uninitialized', (exercise: Exercise, participation: StudentParticipation | undefined, expected: boolean) => {
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('studentParticipation', participation ?? exercise.studentParticipations?.[0]);
            TestBed.tick();
            expect(comp.uninitialized()).toBe(expected);
        });

        it.each([
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] } as QuizExercise, true],
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: true }], studentParticipations: [] as StudentParticipation[] } as QuizExercise, false],
            [
                { type: ExerciseType.QUIZ, quizBatches: [{ started: true }], studentParticipations: [{ initializationState: InitializationState.UNINITIALIZED }] } as QuizExercise,
                false,
            ],
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: true }], studentParticipations: [{ initializationState: InitializationState.FINISHED }] } as QuizExercise, false],
        ])('should determine if quiz is not started', (exercise: Exercise, expected: boolean) => {
            fixture.componentRef.setInput('exercise', exercise);
            TestBed.tick();
            expect(comp.quizNotStarted()).toBe(expected);
        });

        it.each([
            [{} as Exercise, undefined, false],
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, undefined, true],
            [{ dueDate: dayjs().subtract(1, 'hour'), studentParticipations: [{}] } as Exercise, {} as StudentParticipation, false],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, undefined, false],
            [{ dueDate: dayjs().add(1, 'hour'), studentParticipations: [{}] } as Exercise, {} as StudentParticipation, false],
        ])('should determine if missed due date', (exercise: Exercise, participation: StudentParticipation | undefined, expected: boolean) => {
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('studentParticipation', participation ?? exercise.studentParticipations?.[0]);
            TestBed.tick();
            expect(comp.exerciseMissedDueDate()).toBe(expected);
        });

        it.each([
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, undefined, false],
            [{ dueDate: dayjs().subtract(1, 'hour'), studentParticipations: [{}] } as Exercise, {} as StudentParticipation, true],
            [{ dueDate: dayjs().subtract(1, 'hour'), studentParticipations: [{ submissions: [{}] }] } as Exercise, { submissions: [{}] } as StudentParticipation, false],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, undefined, false],
            [{ dueDate: dayjs().add(1, 'hour'), studentParticipations: [{}] } as Exercise, {} as StudentParticipation, false],
        ])('should determine if it is notSubmitted', (exercise: Exercise, participation: StudentParticipation | undefined, expected: boolean) => {
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('studentParticipation', participation ?? exercise.studentParticipations?.[0]);
            TestBed.tick();
            expect(comp.notSubmitted()).toBe(expected);
        });

        it.each([
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] } as unknown as Exercise, {} as StudentParticipation, false],
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] } as unknown as Exercise, { submissions: [{ results: [] }] } as StudentParticipation, false],
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] } as unknown as Exercise, { submissions: [{ results: [{}] }] } as StudentParticipation, true],
            [{ type: ExerciseType.MODELING } as Exercise, { initializationState: InitializationState.INITIALIZED } as StudentParticipation, false],
            [{ type: ExerciseType.MODELING } as Exercise, { initializationState: InitializationState.FINISHED } as StudentParticipation, true],
            [{ type: ExerciseType.PROGRAMMING } as Exercise, { initializationState: InitializationState.UNINITIALIZED } as StudentParticipation, false],
            [{ type: ExerciseType.PROGRAMMING } as Exercise, { initializationState: InitializationState.INITIALIZED } as StudentParticipation, true],
            [{ type: ExerciseType.PROGRAMMING } as Exercise, { initializationState: InitializationState.INACTIVE } as StudentParticipation, true],
            [{ type: ExerciseType.PROGRAMMING } as Exercise, { initializationState: InitializationState.FINISHED } as StudentParticipation, true],
            [
                { type: ExerciseType.PROGRAMMING, dueDate: dayjs().subtract(1, 'hour') } as Exercise,
                { initializationState: InitializationState.INITIALIZED } as StudentParticipation,
                false,
            ],
            [
                { type: ExerciseType.PROGRAMMING, dueDate: dayjs().subtract(1, 'hour') } as Exercise,
                { initializationState: InitializationState.INITIALIZED, submissions: [{ results: [{}] }] } as StudentParticipation,
                true,
            ],
        ])('should determine if results should be shown', async (exercise: Exercise, participation: StudentParticipation, expected: boolean) => {
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('studentParticipation', participation);
            TestBed.tick();
            expect(comp.shouldShowResult()).toBe(expected);

            fixture.detectChanges();
            await fixture.whenStable();

            const updatingResult = fixture.debugElement.query(By.css('#submission-result-graded'));
            if (expected) {
                expect(updatingResult).not.toBeNull();
            } else {
                expect(updatingResult).toBeNull();
            }
        });
    });
});
