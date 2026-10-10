import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

export enum ChatServiceMode {
    TEXT_EXERCISE = 'TEXT_EXERCISE_CHAT',
    PROGRAMMING_EXERCISE = 'PROGRAMMING_EXERCISE_CHAT',
    COURSE = 'COURSE_CHAT',
    LECTURE = 'LECTURE_CHAT',
    TUTOR_SUGGESTION = 'TUTOR_SUGGESTION',
}

/** The exercise types that have an Iris chat, and the chat mode each of them opens. To add Iris support for a new exercise type, add a single entry here. */
export const EXERCISE_TYPE_TO_CHAT_MODE: Record<string, ChatServiceMode> = {
    [ExerciseType.TEXT]: ChatServiceMode.TEXT_EXERCISE,
    [ExerciseType.PROGRAMMING]: ChatServiceMode.PROGRAMMING_EXERCISE,
};

export interface SessionContext {
    mode: ChatServiceMode;
    entityId: number;
    entityName?: string;
}

/**
 * Structural equality for session contexts. Ignores `entityName` so a route-derived context
 * (no name) and a dropdown-picked context (with name) compare equal when they target the
 * same `mode + entityId`.
 */
export function sameSessionContext(a: SessionContext | undefined, b: SessionContext | undefined): boolean {
    if (a === b) return true;
    if (!a || !b) return false;
    return a.mode === b.mode && a.entityId === b.entityId;
}
