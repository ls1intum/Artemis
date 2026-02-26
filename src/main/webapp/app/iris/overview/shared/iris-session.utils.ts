import { faChalkboardUser, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';

/**
 * Known "new chat" titles from all languages (server-side: messages*.properties, client-side: iris.json).
 * Must match the values in src/main/resources/i18n/messages*.properties (iris.chat.session.newChatTitle)
 * and src/main/webapp/i18n/&#42;/iris.json (artemisApp.iris.chatHistory.newChat).
 */
export const NEW_CHAT_TITLES = new Set(['new chat', 'neuer chat']);

export function chatModeIcon(mode: ChatServiceMode | undefined): IconProp | undefined {
    switch (mode) {
        case ChatServiceMode.PROGRAMMING_EXERCISE:
            return faKeyboard;
        case ChatServiceMode.LECTURE:
            return faChalkboardUser;
        default:
            return undefined;
    }
}

export function chatModeTooltipKey(mode: ChatServiceMode | undefined): string | undefined {
    switch (mode) {
        case ChatServiceMode.PROGRAMMING_EXERCISE:
            return 'artemisApp.iris.chatHistory.relatedEntityTooltip.programmingExercise';
        case ChatServiceMode.LECTURE:
            return 'artemisApp.iris.chatHistory.relatedEntityTooltip.lecture';
        default:
            return undefined;
    }
}
