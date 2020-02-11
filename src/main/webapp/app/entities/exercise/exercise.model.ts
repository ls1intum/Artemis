import { BaseEntity } from 'app/shared';
import { Course } from '../course';
import { Moment } from 'moment';
import { ExampleSubmission } from '../example-submission/example-submission.model';
import { TutorParticipation } from 'app/entities/tutor-participation';
import { Attachment } from 'app/entities/attachment';
import { StudentQuestion } from 'app/entities/student-question';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { AssessmentType } from 'app/entities/assessment-type';

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

export enum ParticipationStatus {
    QUIZ_UNINITIALIZED = 'quiz-uninitialized',
    QUIZ_ACTIVE = 'quiz-active',
    QUIZ_SUBMITTED = 'quiz-submitted',
    QUIZ_NOT_STARTED = 'quiz-not-started',
    QUIZ_NOT_PARTICIPATED = 'quiz-not-participated',
    QUIZ_FINISHED = 'quiz-finished',
    UNINITIALIZED = 'uninitialized',
    INITIALIZED = 'initialized',
    INACTIVE = 'inactive',
    EXERCISE_ACTIVE = 'exercise-active',
    EXERCISE_SUBMITTED = 'exercise-submitted',
    EXERCISE_MISSED = 'exercise-missed',
}

export interface ExerciseCategory {
    exerciseId: number;
    category: string;
    color: string;
}

export abstract class Exercise implements BaseEntity {
    public id: number;
    public problemStatement: string | null;
    public gradingInstructions: string;
    public title: string;
    public shortName: string;
    public releaseDate: Moment | null;
    public dueDate: Moment | null;
    public assessmentDueDate: Moment | null;
    public maxScore: number;
    public assessmentType: AssessmentType;
    public difficulty: DifficultyLevel | null;
    public categories: string[];
    public type: ExerciseType;

    public studentParticipations: StudentParticipation[];
    public tutorParticipations: TutorParticipation[];
    public course: Course | null;
    public participationStatus: ParticipationStatus;
    public exampleSubmissions: ExampleSubmission[];
    public attachments: Attachment[];
    public studentQuestions: StudentQuestion[];

    public numberOfParticipations?: number;
    public numberOfAssessments?: number;
    public numberOfComplaints?: number;
    public numberOfOpenComplaints?: number;
    public numberOfMoreFeedbackRequests?: number;
    public numberOfOpenMoreFeedbackRequests?: number;

    // helper attributes
    public isAtLeastTutor = false; // default value
    public isAtLeastInstructor = false; // default value
    public assessmentDueDateError = false;
    public dueDateError = false;
    public loading: boolean;
    public numberOfParticipationsWithRatedResult: number;
    public numberOfSuccessfulParticipations: number;
    public averagePoints: number;
    public presentationScoreEnabled = false; // default value;

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
