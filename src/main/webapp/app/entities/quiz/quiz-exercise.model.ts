import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { QuizPointStatistic } from 'app/entities/quiz/quiz-point-statistic.model';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

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

export class QuizExercise extends Exercise {
    public id?: number;
    public visibleToStudents?: boolean; // (computed by server)
    public allowedNumberOfAttempts?: number;
    public remainingNumberOfAttempts?: number;
    public randomizeQuestionOrder?: boolean;
    public isOpenForPractice?: boolean;
    public duration?: number;
    public quizPointStatistic?: QuizPointStatistic;
    public quizQuestions?: QuizQuestion[];
    public status?: QuizStatus;
    public quizMode?: QuizMode;
    public quizBatches?: QuizBatch[];

    // helper attributes
    public quizEnded?: boolean;
    public quizStarted?: boolean;

    public isActiveQuiz?: boolean;
    public isPracticeModeAvailable?: boolean;

    constructor(course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(ExerciseType.QUIZ);
        this.course = course;
        this.exerciseGroup = exerciseGroup;
        this.randomizeQuestionOrder = true; // default value (set by server)
        this.isOpenForPractice = false; // default value (set by server)
        this.isActiveQuiz = false; // default value (set by client, might need to be computed before evaluated)
        this.isPracticeModeAvailable = true; // default value (set by client, might need to be computed before evaluated)
    }
}
