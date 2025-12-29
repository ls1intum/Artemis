import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { ExerciseMode, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ArtemisQuizService } from 'app/quiz/shared/service/quiz.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';

describe('Quiz Service', () => {
    setupTestBed({ zoneless: true });

    let service: ArtemisQuizService;
    const quiz = {
        mode: ExerciseMode.INDIVIDUAL,
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        numberOfAssessmentsOfCorrectionRounds: [{ inTime: 0, late: 0, total: 0 }],
        studentAssignedTeamIdComputed: false,
        secondCorrectionEnabled: false,
        type: ExerciseType.QUIZ,
        bonusPoints: 0,
        isAtLeastTutor: false,
        isAtLeastEditor: false,
        isAtLeastInstructor: false,
        teamMode: false,
        assessmentDueDateError: false,
        dueDateError: false,
        presentationScoreEnabled: false,
        course: undefined,
        randomizeQuestionOrder: true,
        releaseDate: undefined,
        isVisibleBeforeStart: false,
        isPlannedToStart: false,
        isActiveQuiz: false,
        isPracticeModeAvailable: true,
        title: 'Quiz title',
        duration: 600,
        quizQuestions: undefined,
    };
    const multipleChoice: MultipleChoiceQuestion = {
        type: QuizQuestionType.MULTIPLE_CHOICE,
        randomizeOrder: true,
        invalid: false,
        exportQuiz: false,
        title: 'Multiple Choice Quiz',
        text: 'A longer more detailed question',
        hint: 'A general hint',
        scoringType: ScoringType.PROPORTIONAL_WITHOUT_PENALTY,
        points: 10,
        answerOptions: [
            {
                isCorrect: true,
                invalid: false,
                text: 'Correct answer 1',
                hint: 'A hint',
                explanation: 'Explanation for why this is correct',
            },
            {
                isCorrect: true,
                invalid: false,
                text: 'Correct answer 2',
                hint: 'A hint',
                explanation: 'Explanation for why this is correct',
            },
            {
                isCorrect: false,
                invalid: false,
                text: 'Wrong Answer 1',
                hint: 'A hint',
                explanation: 'Explanation for why this is wrong',
            },
            {
                isCorrect: false,
                invalid: false,
                text: 'Wrong Answer 2',
                hint: 'A hint',
                explanation: 'Explanation for why this is wrong',
            },
        ],
    };
    const shortAnswer: ShortAnswerQuestion = {
        type: QuizQuestionType.SHORT_ANSWER,
        randomizeOrder: true,
        invalid: false,
        exportQuiz: false,
        matchLetterCase: false,
        similarityValue: 85,
        title: 'Short Answer Quiz',
        text: 'Never gonna [-spot 1] you up\nNever gonna [-spot 2] you down\n',
        scoringType: ScoringType.PROPORTIONAL_WITHOUT_PENALTY,
        points: 1,
        spots: [
            {
                tempID: 8562027009747859,
                invalid: false,
                width: 15,
                spotNr: 1,
            },
            {
                tempID: 1693282244205775,
                invalid: false,
                width: 15,
                spotNr: 2,
            },
        ],
        solutions: [
            {
                tempID: 7036040666954049,
                invalid: false,
                text: 'give',
            },
            {
                tempID: 94143448556475,
                invalid: false,
                text: 'let',
            },
        ],
        correctMappings: [
            {
                spot: {
                    tempID: 8562027009747859,
                    invalid: false,
                    width: 15,
                    spotNr: 1,
                },
                solution: {
                    tempID: 7036040666954049,
                    invalid: false,
                    text: 'give',
                },
                invalid: false,
            },
            {
                spot: {
                    tempID: 1693282244205775,
                    invalid: false,
                    width: 15,
                    spotNr: 2,
                },
                solution: {
                    tempID: 94143448556475,
                    invalid: false,
                    text: 'let',
                },
                invalid: false,
            },
        ],
    };
    const dragAndDrop: DragAndDropQuestion = {
        type: QuizQuestionType.DRAG_AND_DROP,
        randomizeOrder: true,
        invalid: false,
        exportQuiz: false,
        title: 'Drag and Drop Quiz',
        scoringType: ScoringType.PROPORTIONAL_WITHOUT_PENALTY,
        points: 5,
        dragItems: [
            { id: 1, text: 'Item 1', invalid: false },
            { id: 2, text: 'Item 2', invalid: false },
            { id: 3, text: 'Item 3', invalid: false },
        ],
        dropLocations: [
            { id: 1, invalid: false },
            { id: 2, invalid: false },
        ],
    };
    const shuffledAnswers = [
        {
            explanation: 'Explanation for why this is correct',
            hint: 'A hint',
            invalid: false,
            isCorrect: true,
            text: 'Correct answer 2',
        },
        {
            explanation: 'Explanation for why this is wrong',
            hint: 'A hint',
            invalid: false,
            isCorrect: false,
            text: 'Wrong Answer 1',
        },
        {
            explanation: 'Explanation for why this is wrong',
            hint: 'A hint',
            invalid: false,
            isCorrect: false,
            text: 'Wrong Answer 2',
        },
        {
            explanation: 'Explanation for why this is correct',
            hint: 'A hint',
            invalid: false,
            isCorrect: true,
            text: 'Correct answer 1',
        },
    ];
    beforeEach(() => {
        service = TestBed.inject(ArtemisQuizService);
        vi.spyOn(globalThis.Math, 'random').mockReturnValue(0.2);
    });

    afterEach(() => {
        vi.spyOn(globalThis.Math, 'random').mockRestore();
    });

    it('shuffles order of Answer options', () => {
        const quizExercise: any = Object.assign({}, quiz, { quizQuestions: [multipleChoice] });
        service.randomizeOrder(quizExercise.quizQuestions, quizExercise.randomizeQuestionOrder);
        expect(quizExercise.quizQuestions[0].answerOptions).toStrictEqual(shuffledAnswers);
    });

    it('switches order of Quiz Questions', () => {
        const quizExercise: any = Object.assign({}, quiz, { quizQuestions: [shortAnswer, multipleChoice] });
        const expected = Object.assign({}, quiz, { quizQuestions: [multipleChoice, shortAnswer] });
        service.randomizeOrder(quizExercise.quizQuestions, quizExercise.randomizeQuestionOrder);
        expect(quizExercise).toStrictEqual(expected);
    });

    it('should not randomize when randomizeQuestionOrder is false', () => {
        const quizExercise: any = Object.assign({}, quiz, {
            quizQuestions: [shortAnswer, multipleChoice],
            randomizeQuestionOrder: false,
        });
        const originalOrder = [...quizExercise.quizQuestions];
        service.randomizeOrder(quizExercise.quizQuestions, false);
        expect(quizExercise.quizQuestions).toEqual(originalOrder);
    });

    it('should handle undefined quizQuestions', () => {
        expect(() => service.randomizeOrder(undefined, true)).not.toThrow();
    });

    it('should shuffle drag items for drag and drop questions', () => {
        const dndQuestion = { ...dragAndDrop };
        const quizExercise: any = Object.assign({}, quiz, { quizQuestions: [dndQuestion] });
        service.randomizeOrder(quizExercise.quizQuestions, true);
        // Should have shuffled drag items
        expect(quizExercise.quizQuestions[0].dragItems).toBeDefined();
    });

    it('should not shuffle answer options when randomizeOrder is false on question', () => {
        const mcQuestionNoShuffle: MultipleChoiceQuestion = {
            ...multipleChoice,
            randomizeOrder: false,
        };
        const quizExercise: any = Object.assign({}, quiz, { quizQuestions: [mcQuestionNoShuffle] });
        const originalAnswers = [...mcQuestionNoShuffle.answerOptions!];
        service.randomizeOrder(quizExercise.quizQuestions, true);
        expect(quizExercise.quizQuestions[0].answerOptions).toEqual(originalAnswers);
    });

    it('should handle short answer questions without shuffling', () => {
        const saQuestion: ShortAnswerQuestion = {
            ...shortAnswer,
            randomizeOrder: true,
        };
        const quizExercise: any = Object.assign({}, quiz, { quizQuestions: [saQuestion] });
        // Short answer questions should not throw during randomization
        expect(() => service.randomizeOrder(quizExercise.quizQuestions, true)).not.toThrow();
    });
});

describe('Quiz Service - Static Methods', () => {
    setupTestBed({ zoneless: true });

    describe('isUninitialized', () => {
        it('should return true when quiz batch started but participation not initialized', () => {
            const quizExercise: QuizExercise = {
                quizEnded: false,
                quizBatches: [{ started: true }],
                studentParticipations: [{ initializationState: undefined }],
            } as QuizExercise;

            expect(ArtemisQuizService.isUninitialized(quizExercise)).toBeTrue();
        });

        it('should return false when quiz has ended', () => {
            const quizExercise: QuizExercise = {
                quizEnded: true,
                quizBatches: [{ started: true }],
                studentParticipations: [{ initializationState: undefined }],
            } as QuizExercise;

            expect(ArtemisQuizService.isUninitialized(quizExercise)).toBeFalse();
        });

        it('should return false when participation is initialized', () => {
            const participation: StudentParticipation = {
                initializationState: InitializationState.INITIALIZED,
            } as StudentParticipation;
            const quizExercise: QuizExercise = {
                quizEnded: false,
                quizBatches: [{ started: true }],
                studentParticipations: [participation],
            } as QuizExercise;

            expect(ArtemisQuizService.isUninitialized(quizExercise)).toBeFalse();
        });

        it('should return false when participation is finished', () => {
            const participation: StudentParticipation = {
                initializationState: InitializationState.FINISHED,
            } as StudentParticipation;
            const quizExercise: QuizExercise = {
                quizEnded: false,
                quizBatches: [{ started: true }],
                studentParticipations: [participation],
            } as QuizExercise;

            expect(ArtemisQuizService.isUninitialized(quizExercise)).toBeFalse();
        });

        it('should return false when no quiz batch has started', () => {
            const quizExercise: QuizExercise = {
                quizEnded: false,
                quizBatches: [{ started: false }],
                studentParticipations: [{ initializationState: undefined }],
            } as QuizExercise;

            expect(ArtemisQuizService.isUninitialized(quizExercise)).toBeFalse();
        });
    });

    describe('notStarted', () => {
        it('should return true when quiz batch not started and not ended', () => {
            const quizExercise: QuizExercise = {
                quizEnded: false,
                quizBatches: [{ started: false }],
                studentParticipations: [{ initializationState: undefined }],
            } as QuizExercise;

            expect(ArtemisQuizService.notStarted(quizExercise)).toBeTrue();
        });

        it('should return true when no quiz batches exist', () => {
            const quizExercise: QuizExercise = {
                quizEnded: false,
                quizBatches: undefined,
                studentParticipations: undefined,
            } as QuizExercise;

            expect(ArtemisQuizService.notStarted(quizExercise)).toBeTrue();
        });

        it('should return false when quiz batch has started', () => {
            const quizExercise: QuizExercise = {
                quizEnded: false,
                quizBatches: [{ started: true }],
                studentParticipations: [{ initializationState: undefined }],
            } as QuizExercise;

            expect(ArtemisQuizService.notStarted(quizExercise)).toBeFalse();
        });

        it('should return false when quiz has ended', () => {
            const quizExercise: QuizExercise = {
                quizEnded: true,
                quizBatches: [{ started: false }],
                studentParticipations: [{ initializationState: undefined }],
            } as QuizExercise;

            expect(ArtemisQuizService.notStarted(quizExercise)).toBeFalse();
        });

        it('should return false when participation is initialized', () => {
            const participation: StudentParticipation = {
                initializationState: InitializationState.INITIALIZED,
            } as StudentParticipation;
            const quizExercise: QuizExercise = {
                quizEnded: false,
                quizBatches: [{ started: false }],
                studentParticipations: [participation],
            } as QuizExercise;

            expect(ArtemisQuizService.notStarted(quizExercise)).toBeFalse();
        });
    });
});
