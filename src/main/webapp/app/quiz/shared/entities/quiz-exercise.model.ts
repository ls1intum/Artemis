import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, resetForImport } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizPointStatistic } from 'app/quiz/shared/entities/quiz-point-statistic.model';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { QuizConfiguration } from 'app/quiz/shared/entities/quiz-configuration.model';
import { QuizParticipation } from 'app/quiz/shared/entities/quiz-participation.model';

export enum QuizStatus {
    CLOSED,
    OPEN_FOR_PRACTICE,
    ACTIVE,
    VISIBLE,
    INVISIBLE,
}

export enum QuizMode {
    SYNCHRONIZED = 'SYNCHRONIZED',
    BATCHED = 'BATCHED',
    INDIVIDUAL = 'INDIVIDUAL',
}

export class QuizBatch {
    id?: number;
    startTime?: dayjs.Dayjs;
    started?: boolean;
    ended?: boolean;
    submissionAllowed?: boolean;
    password?: string;

    // local helpers
    startTimeError?: boolean;
}

export class QuizExercise extends Exercise implements QuizConfiguration, QuizParticipation {
    public visibleToStudents?: boolean; // (computed by server)
    public allowedNumberOfAttempts?: number;
    public remainingNumberOfAttempts?: number;
    public randomizeQuestionOrder?: boolean;
    public duration?: number;
    public quizPointStatistic?: QuizPointStatistic;
    public quizQuestions?: QuizQuestion[];
    public status?: QuizStatus;
    public quizMode?: QuizMode = QuizMode.INDIVIDUAL; // default value
    public quizBatches?: QuizBatch[];

    // helper attributes
    public quizEnded?: boolean;
    public quizStarted?: boolean;

    public isActiveQuiz?: boolean;
    public isPracticeModeAvailable?: boolean;
    public isEditable?: boolean;

    constructor(course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(ExerciseType.QUIZ);
        this.course = course;
        this.exerciseGroup = exerciseGroup;
        this.randomizeQuestionOrder = true; // default value (set by server)
        this.isActiveQuiz = false; // default value (set by client, might need to be computed before evaluated)
        this.isPracticeModeAvailable = true; // default value (set by client, might need to be computed before evaluated)
        this.isEditable = false; // default value (set by client, might need to be computed before evaluated)
    }
}

export function resetQuizForImport(exercise: QuizExercise) {
    resetForImport(exercise);

    exercise.quizBatches = [];
    exercise.isEditable = true;
}
