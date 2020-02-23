import { Moment } from 'moment';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { QuizPointStatistic } from 'app/entities/quiz/quiz-point-statistic.model';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { Course } from 'app/entities/course.model';

export class QuizExercise extends Exercise {
    public id: number;
    public randomizeQuestionOrder = true; // default value
    public isVisibleBeforeStart = false; // default value
    public isOpenForPractice = false; // default value
    public isPlannedToStart = false; // default value
    public duration: number;
    public quizPointStatistic: QuizPointStatistic;
    public quizQuestions: QuizQuestion[];
    public status: string;
    public isActiveQuiz = false; // default value
    public isPracticeModeAvailable = true; // default value

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
        this.course = course || null;
    }
}
