import { TestBed } from '@angular/core/testing';
import { ExerciseMode, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizQuestionType, ScoringType } from 'app/entities/quiz/quiz-question.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ArtemisQuizService } from 'app/shared/quiz/quiz.service';

describe('Quiz Service', () => {
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
        isOpenForPractice: false,
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
        jest.spyOn(global.Math, 'random').mockReturnValue(0.2);
    });

    afterEach(() => {
        jest.spyOn(global.Math, 'random').mockRestore();
    });

    it('shuffles order of Answer options', () => {
        const quizExercise: any = Object.assign({}, quiz, { quizQuestions: [multipleChoice] });
        service.randomizeOrder(quizExercise);
        expect(quizExercise.quizQuestions[0].answerOptions).toStrictEqual(shuffledAnswers);
    });

    it('switches order of Quiz Questions', () => {
        const quizExercise = Object.assign({}, quiz, { quizQuestions: [shortAnswer, multipleChoice] });
        const expected = Object.assign({}, quiz, { quizQuestions: [multipleChoice, shortAnswer] });
        service.randomizeOrder(quizExercise);
        expect(quizExercise).toStrictEqual(expected);
    });
});
