import { BaseEntity } from 'app/shared';
import { Course } from '../course';
import { InitializationState, Participation } from '../participation';
import { Moment } from 'moment';

export const enum DifficultyLevel {
    EASY = 'EASY',
    MEDIUM = 'MEDIUM',
    HARD = 'HARD'
}

// IMPORTANT NOTICE: The following strings have to be consistent with the ones defined in Exercise.java
export const enum ExerciseType {
    PROGRAMMING = 'programming',
    MODELING = 'modeling',
    QUIZ = 'quiz',
    TEXT = 'text',
    FILE_UPLOAD = 'file-upload'
}

export abstract class Exercise implements BaseEntity {
    public id: number;
    public problemStatement: string;
    public gradingInstructions: string;
    public title: string;
    public shortName: string;
    public releaseDate: Moment;
    public dueDate: Moment;
    public maxScore: number;
    public difficulty: DifficultyLevel;
    public categories: string[];
    public participations: Participation[];
    public course: Course;
    public initialisationStatus: InitializationState;
    public type: ExerciseType;

    // helper attributes
    public isAtLeastTutor: boolean;
    public loading: boolean;

    protected constructor(type: ExerciseType) {
        this.type = type;
    }
}
