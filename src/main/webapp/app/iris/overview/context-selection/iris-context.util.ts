import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChalkboardUser, faFont, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';

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
