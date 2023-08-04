// Constants which are used in the test specs

// Requests
export const DELETE = 'DELETE';
export const POST = 'POST';
export const GET = 'GET';
export const PUT = 'PUT';
export const PATCH = 'PATCH';
export const BASE_API = 'api/';
export const EXERCISE_BASE = BASE_API + 'exercises/';

// Constants
export const USER_ID_SELECTOR = 'USERID';

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

// CourseWideContext
// Copied from src\main\webapp\app\shared\metis\metis.util.ts
export enum CourseWideContext {
    TECH_SUPPORT = 'TECH_SUPPORT',
    ORGANIZATION = 'ORGANIZATION',
    RANDOM = 'RANDOM',
    ANNOUNCEMENT = 'ANNOUNCEMENT',
}
