import { Moment } from 'moment';
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
    public id: number;
    public remainingTime: number; // (computed by server)
    public timeUntilPlannedStart: number; // (computed by server)
    public visibleToStudents: boolean; // (computed by server)
    public randomizeQuestionOrder = true; // default value (set by server)
    public isVisibleBeforeStart = false; // default value (set by server)
    public isOpenForPractice = false; // default value (set by server)
    public isPlannedToStart = false; // default value (set by server)
    public duration: number;
    public quizPointStatistic: QuizPointStatistic;
    public quizQuestions: QuizQuestion[];
    public status: QuizStatus;

    // helper attributes
    public adjustedDueDate: Moment;
    public adjustedReleaseDate: Moment;
    public ended: boolean;
    public started: boolean;

    public isActiveQuiz = false; // default value (set by client, might need to be computed before evaluated)
    public isPracticeModeAvailable = true; // default value (set by client, might need to be computed before evaluated)

    constructor(course?: Course, exerciseGroup?: ExerciseGroup) {
        super(ExerciseType.QUIZ);
        this.course = course || null;
        this.exerciseGroup = exerciseGroup || null;
    }
}
