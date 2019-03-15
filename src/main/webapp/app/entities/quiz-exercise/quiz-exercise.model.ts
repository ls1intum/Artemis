import { QuizQuestion } from '../quiz-question';
import { QuizPointStatistic } from '../quiz-point-statistic';
import { Exercise, ExerciseType } from '../exercise';
import { Moment } from 'moment';
import { Course } from '../course';

export class QuizExercise extends Exercise {

    public id: number;
    public randomizeQuestionOrder = true;   // default value
    public isVisibleBeforeStart = false;    // default value
    public isOpenForPractice = false;       // default value
    public isPlannedToStart = false;        // default value
    public duration: number;
    public quizPointStatistic: QuizPointStatistic;
    public questions: QuizQuestion[];
    public status: string;
    public isActiveQuiz = false;            // default value
    public isPracticeModeAvailable = true;  // default value

    // helper attributes
    public adjustedDueDate: Moment;
    public adjustedReleaseDate: Moment;
    public ended: boolean;
    public started: boolean;
    public remainingTime: number;
    public timeUntilPlannedStart: number;
    public visibleToStudents: boolean;

    constructor(course?: Course) {
        super(ExerciseType.QUIZ);
        this.course = course;
    }
}
