import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChalkboardUser, faFont, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { IrisMessageContent, isJsonContent } from 'app/iris/shared/entities/iris-content-type.model';
import { ChatServiceMode, SessionContext } from 'app/iris/shared/entities/iris-session-context.model';

/** Transition values are a contract shared with the server and Pyris. */
export type IrisContextSwitchTransition = 'added' | 'removed' | 'changed';

/** Server's CTXSWAP marker payload, read with a single cast rather than validated field-by-field. */
export interface ContextSwitchMarker {
    transition?: IrisContextSwitchTransition;
    entityMode?: ChatServiceMode;
    entityId?: number;
    name?: string;
}

export function parseContextSwitchMarker(contents: IrisMessageContent[]): ContextSwitchMarker {
    return contents.find(isJsonContent)?.attributes ?? {};
}

/**
 * Derives the session context a CTXSWAP marker switches to. A 'removed' transition returns to the
 * course chat, so the marker carries no entity and the context is built from the course id instead.
 * Returns undefined when the marker (or the course id for 'removed') is incomplete.
 */
export function contextFromSwitchMarker(marker: ContextSwitchMarker, courseId: number | undefined): SessionContext | undefined {
    if (marker.transition === 'removed') {
        return courseId ? { mode: ChatServiceMode.COURSE, entityId: courseId } : undefined;
    }
    if (marker.entityMode && marker.entityId) {
        return { mode: marker.entityMode, entityId: marker.entityId, entityName: marker.name };
    }
    return undefined;
}

export function iconForEntityMode(entityMode: ChatServiceMode | undefined): IconProp | undefined {
    switch (entityMode) {
        case ChatServiceMode.LECTURE:
            return faChalkboardUser;
        case ChatServiceMode.PROGRAMMING_EXERCISE:
            return faKeyboard;
        case ChatServiceMode.TEXT_EXERCISE:
            return faFont;
        default:
            return undefined;
    }
}

export function routeForContext(courseId: number | undefined, mode: ChatServiceMode | undefined, entityId: number | undefined): string | undefined {
    if (!courseId || !mode || !entityId) {
        return undefined;
    }
    switch (mode) {
        case ChatServiceMode.PROGRAMMING_EXERCISE:
        case ChatServiceMode.TEXT_EXERCISE:
            return `/courses/${courseId}/exercises/${entityId}`;
        case ChatServiceMode.LECTURE:
            return `/courses/${courseId}/lectures/${entityId}`;
        default:
            return undefined;
    }
}
