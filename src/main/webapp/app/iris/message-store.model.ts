import { IrisClientMessageDescriptor, IrisMessageDescriptor, IrisServerMessageDescriptor } from 'app/entities/iris/iris.model';

export enum ActionType {
    HISTORY_MESSAGE_LOADED = 'history-message-loaded',
    ACTIVE_CONVERSATION_MESSAGE_LOADED = 'active-conversation-message-loaded',
    STUDENT_MESSAGE_SENT = 'student-message-sent',
}

export interface HistoryMessageLoadedAction {
    type: ActionType.HISTORY_MESSAGE_LOADED;
    message: IrisServerMessageDescriptor;
}

export interface ActiveConversationMessageLoadedAction {
    type: ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED;
    message: IrisServerMessageDescriptor;
}

export interface StudentMessageSentAction {
    type: ActionType.STUDENT_MESSAGE_SENT;
    message: IrisClientMessageDescriptor;
}

export type MessageStoreAction = HistoryMessageLoadedAction | ActiveConversationMessageLoadedAction | StudentMessageSentAction;

export interface MessageStoreState {
    messages: IrisMessageDescriptor[];
}
