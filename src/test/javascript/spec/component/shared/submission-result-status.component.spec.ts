import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { QuizBatch, QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { ArtemisTestModule } from '../../test.module';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-student-trigger-build-button.component';

describe('SubmissionResultStatusComponent', () => {
    let comp: SubmissionResultStatusComponent;
    let fixture: ComponentFixture<SubmissionResultStatusComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                SubmissionResultStatusComponent,
                MockComponent(UpdatingResultComponent),
                MockComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
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

    describe('ngOnChanges', () => {
        it.each([
            [{ type: ExerciseType.QUIZ, quizBatches: [{ started: false }, { started: true }] } as QuizExercise, true],
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] } as QuizExercise, false],
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, false],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, true],
            [{ dueDate: dayjs().subtract(1, 'hour'), studentParticipations: [{}] } as Exercise, false],
            [{ dueDate: dayjs().add(1, 'hour'), studentParticipations: [{}] } as Exercise, false],
        ])('should determine if it is uninitialized', (exercise: Exercise, expected: boolean) => {
            comp.exercise = exercise;
            comp.studentParticipation = exercise.studentParticipations?.[0];
            comp.ngOnChanges();
            expect(comp.uninitialized).toBe(expected);
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
            comp.ngOnChanges();
            expect(comp.quizNotStarted).toBe(expected);
        });

        it.each([
            [{} as Exercise, false],
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, true],
            [{ dueDate: dayjs().subtract(1, 'hour'), studentParticipations: [{}] } as Exercise, false],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, false],
            [{ dueDate: dayjs().add(1, 'hour'), studentParticipations: [{}] } as Exercise, false],
        ])('should determine if missed due date', (exercise: Exercise, expected: boolean) => {
            comp.exercise = exercise;
            comp.studentParticipation = exercise.studentParticipations?.[0];
            comp.ngOnChanges();
            expect(comp.exerciseMissedDueDate).toBe(expected);
        });

        it.each([
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, false],
            [{ dueDate: dayjs().subtract(1, 'hour'), studentParticipations: [{}] } as Exercise, true],
            [{ dueDate: dayjs().subtract(1, 'hour'), studentParticipations: [{ submissions: [{}] }] } as Exercise, false],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, false],
            [{ dueDate: dayjs().add(1, 'hour'), studentParticipations: [{}] } as Exercise, false],
        ])('should determine if it is notSubmitted', (exercise: Exercise, expected: boolean) => {
            comp.exercise = exercise;
            comp.studentParticipation = exercise.studentParticipations?.[0];
            comp.ngOnChanges();
            expect(comp.notSubmitted).toBe(expected);
        });

        it.each([
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] }, {}, false],
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] }, { results: [] }, false],
            [{ type: ExerciseType.QUIZ, quizBatches: [] as QuizBatch[] }, { results: [{}] }, true],
            [{ type: ExerciseType.MODELING }, { initializationState: InitializationState.INITIALIZED }, false],
            [{ type: ExerciseType.MODELING }, { initializationState: InitializationState.FINISHED }, true],
            [{ type: ExerciseType.PROGRAMMING }, { initializationState: InitializationState.UNINITIALIZED }, false],
            [{ type: ExerciseType.PROGRAMMING }, { initializationState: InitializationState.INITIALIZED }, true],
            [{ type: ExerciseType.PROGRAMMING }, { initializationState: InitializationState.INACTIVE }, true],
            [{ type: ExerciseType.PROGRAMMING }, { initializationState: InitializationState.FINISHED }, true],
            [{ type: ExerciseType.PROGRAMMING, dueDate: dayjs().subtract(1, 'hour') }, { initializationState: InitializationState.INITIALIZED }, false],
            [{ type: ExerciseType.PROGRAMMING, dueDate: dayjs().subtract(1, 'hour') }, { initializationState: InitializationState.INITIALIZED, results: [{}] }, true],
        ])(
            'should determine if results should be shown',
            fakeAsync((exercise: Exercise, participation: StudentParticipation, expected: boolean) => {
                comp.exercise = exercise;
                comp.studentParticipation = participation;
                comp.ngOnChanges();
                expect(comp.shouldShowResult).toBe(expected);

                fixture.detectChanges();
                tick();

                const updatingResult = fixture.debugElement.query(By.css('#submission-result-graded'));
                if (expected) {
                    expect(updatingResult).not.toBeNull();
                } else {
                    expect(updatingResult).toBeNull();
                }
            }),
        );
    });
});
