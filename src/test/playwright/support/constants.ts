import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ProgrammingExerciseSubmission } from './pageobjects/exercises/programming/OnlineEditorPage';

// Constants which are used in the test specs

// Requests
export const BASE_API = 'api';

export const COURSE_ADMIN_BASE = `${BASE_API}/core/admin/courses`;

export const PROGRAMMING_EXERCISE_BASE = `${BASE_API}/programming/programming-exercises`;
export const QUIZ_EXERCISE_BASE = `${BASE_API}/quiz/quiz-exercises`;
export const QUIZ_EXERCISE_BASE_CREATION = new RegExp(`${BASE_API}/quiz/(courses|exercise-groups)/\\d+/quiz-exercises$`);
export const TEXT_EXERCISE_BASE = `${BASE_API}/text/text-exercises`;
export const MODELING_EXERCISE_BASE = `${BASE_API}/modeling/modeling-exercises`;
export const UPLOAD_EXERCISE_BASE = `${BASE_API}/fileupload/file-upload-exercises`;

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

// CourseWideContext
// Copied from src\main\webapp\app\shared\metis\metis.util.ts
export enum CourseWideContext {
    TECH_SUPPORT = 'TECH_SUPPORT',
    ORGANIZATION = 'ORGANIZATION',
    RANDOM = 'RANDOM',
    ANNOUNCEMENT = 'ANNOUNCEMENT',
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
// Copied from src\main\webapp\app\entities\exercise.model.ts
export type Exercise = {
    title?: string;
    type?: ExerciseType;
    id?: number;
    additionalData?: AdditionalData;
    exerciseGroup?: ExerciseGroup;
};

// ExerciseMode
// Copied from src\main\webapp\app\entities\exercise.model.ts
export enum ExerciseMode {
    INDIVIDUAL = 'INDIVIDUAL',
    TEAM = 'TEAM',
}

// Copied from src\main\webapp\app\entities\quiz\quiz-exercise.model.ts
export enum QuizMode {
    SYNCHRONIZED = 'SYNCHRONIZED',
    BATCHED = 'BATCHED',
    INDIVIDUAL = 'INDIVIDUAL',
}

// Exercise commit entity displayed in commit history
export type ExerciseCommit = {
    message: string;
    result?: string;
};
