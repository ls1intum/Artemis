import { ExerciseGroup } from 'app/entities/exercise-group.model';

import { ProgrammingExerciseSubmission } from './pageobjects/exercises/programming/OnlineEditorPage';

// Constants which are used in the test specs

// Requests
export const DELETE = 'DELETE';
export const POST = 'POST';
export const GET = 'GET';
export const PUT = 'PUT';
export const PATCH = 'PATCH';
export const BASE_API = 'api';
export const EXERCISE_BASE = `${BASE_API}/exercises`;

export const COURSE_BASE = `${BASE_API}/courses`;
export const COURSE_ADMIN_BASE = `${BASE_API}/admin/courses`;

export const PROGRAMMING_EXERCISE_BASE = `${BASE_API}/programming-exercises`;
export const QUIZ_EXERCISE_BASE = `${BASE_API}/quiz-exercises`;
export const TEXT_EXERCISE_BASE = `${BASE_API}/text-exercises`;
export const MODELING_EXERCISE_BASE = `${BASE_API}/modeling-exercises`;
export const UPLOAD_EXERCISE_BASE = `${BASE_API}/file-upload-exercises`;

// Constants
export const USER_ID_SELECTOR = 'USERID';
export const MODELING_EDITOR_CANVAS = '#modeling-editor-canvas';

// Timeformat
export const TIME_FORMAT = 'YYYY-MM-DDTHH:mm:ss.SSS';

// ExerciseType
// Copied from app/entities/exercise.model
export enum ExerciseType {
    PROGRAMMING = 'programming',
    MODELING = 'modeling',
    QUIZ = 'quiz',
    TEXT = 'text',
    FILE_UPLOAD = 'file-upload',
}

// ProgrammingLanguage
// Copied from app/entities/programming-exercise.model
export enum ProgrammingLanguage {
    JAVA = 'JAVA',
    PYTHON = 'PYTHON',
    C = 'C',
    HASKELL = 'HASKELL',
    KOTLIN = 'KOTLIN',
    VHDL = 'VHDL',
    ASSEMBLER = 'ASSEMBLER',
    SWIFT = 'SWIFT',
    OCAML = 'OCAML',
    EMPTY = 'EMPTY',
}

// ProgrammingExerciseAssessmentType
export enum ProgrammingExerciseAssessmentType {
    AUTOMATIC,
    SEMI_AUTOMATIC,
    MANUAL,
}

// AdditionalData
export class AdditionalData {
    quizExerciseID?: number;
    submission?: ProgrammingExerciseSubmission;
    expectedScore?: number;
    textFixture?: string;
    practiceMode?: boolean;
    progExerciseAssessmentType?: ProgrammingExerciseAssessmentType;
}

// Exercise
export type Exercise = {
    title: string;
    type: ExerciseType;
    id: number;
    additionalData?: AdditionalData;
    exerciseGroup?: ExerciseGroup;
};
