import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { QuizBatch, QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { ArtemisTestModule } from '../../test.module';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { MockComponent } from 'ng-mocks';
import dayjs from 'dayjs/esm';

describe('SubmissionResultStatusComponent', () => {
    let comp: SubmissionResultStatusComponent;
    let fixture: ComponentFixture<SubmissionResultStatusComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SubmissionResultStatusComponent, MockComponent(UpdatingResultComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(SubmissionResultStatusComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('onInit', () => {
        it.each([
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: false }, { started: true }] } as QuizExercise, true],
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] } as QuizExercise, false],
            [{ type: ExerciseType.TEXT } as TextExercise, undefined],
        ])('should determine if it is an uninitialized quiz', (exercise: Exercise, expected: boolean) => {
            comp.exercise = exercise;
            comp.ngOnInit();
            expect(comp.uninitializedQuiz).toBe(expected);
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
            comp.exercise = exercise;
            comp.ngOnInit();
            expect(comp.quizNotStarted).toBe(expected);
        });

        it.each([
            [{} as Exercise, false],
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, true],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, false],
        ])('should determine if it is after the due date', (exercise: Exercise, expected: boolean) => {
            comp.exercise = exercise;
            comp.ngOnInit();
            expect(comp.afterDueDate).toBe(expected);
        });

        it.each([
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, false],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, true],
            [{ dueDate: dayjs().subtract(1, 'hour'), studentParticipations: [{}] } as Exercise, false],
            [{ dueDate: dayjs().add(1, 'hour'), studentParticipations: [{}] } as Exercise, false],
        ])('should determine if it is uninitialized', (exercise: Exercise, expected: boolean) => {
            comp.exercise = exercise;
            comp.ngOnInit();
            expect(comp.uninitialized).toBe(expected);
        });

        it.each([
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, false],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, false],
            [{ dueDate: dayjs().subtract(1, 'hour'), studentParticipations: [{}] } as Exercise, false],
            [{ dueDate: dayjs().add(1, 'hour'), studentParticipations: [{}] } as Exercise, true],
        ])('should determine if it is notSubmitted', (exercise: Exercise, expected: boolean) => {
            comp.exercise = exercise;
            comp.ngOnInit();
            expect(comp.notSubmitted).toBe(expected);
        });
    });
});
