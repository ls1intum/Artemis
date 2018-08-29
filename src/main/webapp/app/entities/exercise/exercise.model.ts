import { BaseEntity } from './../../shared';
import { Course } from '../course';
import { Participation } from '../participation';

export const enum DifficultyLevel {
    'EASY',
    'MEDIUM',
    'HARD'
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
    public releaseDate: any;
    public dueDate: any;
    public maxScore: number;
    public difficulty: DifficultyLevel;
    public categories: string[];
    public participations: Participation[];
    public course: Course;
    public openForSubmission: boolean;
    public participationStatus: string;
    public loading: boolean;
    public isAtLeastTutor: boolean;
    public type: ExerciseType;

    constructor(type: ExerciseType) {
        this.type = type;
    }
}
