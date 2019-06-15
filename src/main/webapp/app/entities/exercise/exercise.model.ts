import { BaseEntity } from 'app/shared';
import { Course } from '../course';
import { Participation } from '../participation';
import { Moment } from 'moment';
import { ExampleSubmission } from '../example-submission';
import { TutorParticipation } from 'app/entities/tutor-participation';
import { Attachment } from 'app/entities/attachment';
import { StudentQuestion } from 'app/entities/student-question';

export const enum DifficultyLevel {
    EASY = 'EASY',
    MEDIUM = 'MEDIUM',
    HARD = 'HARD',
}

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in Exercise.java
export enum ExerciseType {
    PROGRAMMING = 'programming',
    MODELING = 'modeling',
    QUIZ = 'quiz',
    TEXT = 'text',
    FILE_UPLOAD = 'file-upload',
}

export const enum ParticipationStatus {
    QUIZ_UNINITIALIZED = 'quiz-uninitialized',
    QUIZ_ACTIVE = 'quiz-active',
    QUIZ_SUBMITTED = 'quiz-submitted',
    QUIZ_NOT_STARTED = 'quiz-not-started',
    QUIZ_NOT_PARTICIPATED = 'quiz-not-participated',
    QUIZ_FINISHED = 'quiz-finished',
    MODELING_EXERCISE = 'modeling-exercise',
    TEXT_EXERCISE = 'text-exercise',
    UNINITIALIZED = 'uninitialized',
    INITIALIZED = 'initialized',
    INACTIVE = 'inactive',
}

export interface ExerciseCategory {
    exerciseId: number;
    category: string;
    color: string;
}

export abstract class Exercise implements BaseEntity {
    public id: number;
    public problemStatement: string;
    public gradingInstructions: string;
    public title: string;
    public shortName: string;
    public releaseDate: Moment;
    public dueDate: Moment;
    public assessmentDueDate: Moment;
    public maxScore: number;
    public difficulty: DifficultyLevel;
    public categories: string[];
    public type: ExerciseType;

    public participations: Participation[];
    public tutorParticipations: TutorParticipation[];
    public course: Course;
    public participationStatus: ParticipationStatus;
    public exampleSubmissions: ExampleSubmission[];
    public attachments: Attachment[];
    public studentQuestions: StudentQuestion[];

    public numberOfParticipations?: number;
    public numberOfAssessments?: number;
    public numberOfComplaints?: number;

    // helper attributes
    public isAtLeastTutor = false; // default value
    public isAtLeastInstructor = false; // default value
    public loading: boolean;
    public numberOfParticipationsWithRatedResult: number;
    public numberOfSuccessfulParticipations: number;
    public averagePoints: number;

    protected constructor(type: ExerciseType) {
        this.type = type;
    }
}

export function getIcon(exerciseType: ExerciseType): string {
    const icons = {
        [ExerciseType.PROGRAMMING]: 'keyboard',
        [ExerciseType.MODELING]: 'project-diagram',
        [ExerciseType.QUIZ]: 'check-double',
        [ExerciseType.TEXT]: 'font',
        [ExerciseType.FILE_UPLOAD]: 'file-upload',
    };

    return icons[exerciseType];
}

export function getIconTooltip(exerciseType: ExerciseType): string {
    const tooltips = {
        [ExerciseType.PROGRAMMING]: 'artemisApp.exercise.isProgramming',
        [ExerciseType.MODELING]: 'artemisApp.exercise.isModeling',
        [ExerciseType.QUIZ]: 'artemisApp.exercise.isQuiz',
        [ExerciseType.TEXT]: 'artemisApp.exercise.isText',
        [ExerciseType.FILE_UPLOAD]: 'artemisApp.exercise.isFileUpload',
    };

    return tooltips[exerciseType];
}
