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
    HIDDEN,
}

export class QuizExercise extends Exercise {
    public id?: number;
    public remainingTime?: number; // (computed by server)
    public timeUntilPlannedStart?: number; // (computed by server)
    public visibleToStudents?: boolean; // (computed by server)
    public randomizeQuestionOrder?: boolean;
    public isVisibleBeforeStart?: boolean;
    public isOpenForPractice?: boolean;
    public isPlannedToStart?: boolean;
    public duration?: number;
    public quizPointStatistic?: QuizPointStatistic;
    public quizQuestions?: QuizQuestion[];
    public status?: QuizStatus;

    // helper attributes
    public adjustedDueDate?: dayjs.Dayjs;
    public adjustedReleaseDate?: dayjs.Dayjs;
    public ended?: boolean;
    public started?: boolean;

    public isActiveQuiz?: boolean;
    public isPracticeModeAvailable?: boolean;

    constructor(course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(ExerciseType.QUIZ);
        this.course = course;
        this.exerciseGroup = exerciseGroup;
        this.randomizeQuestionOrder = true; // default value (set by server)
        this.isVisibleBeforeStart = false; // default value (set by server)
        this.isOpenForPractice = false; // default value (set by server)
        this.isPlannedToStart = false; // default value (set by server)
        this.isActiveQuiz = false; // default value (set by client, might need to be computed before evaluated)
        this.isPracticeModeAvailable = true; // default value (set by client, might need to be computed before evaluated)
    }
}
