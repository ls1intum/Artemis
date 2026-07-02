export enum ChatServiceMode {
    TEXT_EXERCISE = 'TEXT_EXERCISE_CHAT',
    PROGRAMMING_EXERCISE = 'PROGRAMMING_EXERCISE_CHAT',
    COURSE = 'COURSE_CHAT',
    LECTURE = 'LECTURE_CHAT',
    TUTOR_SUGGESTION = 'TUTOR_SUGGESTION',
}

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
