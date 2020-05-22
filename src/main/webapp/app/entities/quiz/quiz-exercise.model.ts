import { Moment } from 'moment';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { QuizPointStatistic } from 'app/entities/quiz/quiz-point-statistic.model';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { Course } from 'app/entities/course.model';

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
    public status: string;

    // helper attributes
    public adjustedDueDate: Moment;
    public adjustedReleaseDate: Moment;
    public ended: boolean;
    public started: boolean;
    public statusAsNumber: number;

    public isActiveQuiz = false; // default value (set by client, might need to be computed before evaluated)
    public isPracticeModeAvailable = true; // default value (set by client, might need to be computed before evaluated)

    constructor(course?: Course) {
        super(ExerciseType.QUIZ);
        this.course = course || null;
    }
}
