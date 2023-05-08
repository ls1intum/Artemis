import { IrisClientMessageDescriptor, IrisMessageDescriptor, IrisServerMessageDescriptor } from 'app/entities/iris/iris.model';

export enum ActionType {
    HISTORY_MESSAGE_LOADED = 'history-message-loaded',
    ACTIVE_CONVERSATION_MESSAGE_LOADED = 'active-conversation-message-loaded',
    STUDENT_MESSAGE_SENT = 'student-message-sent',
}

export class HistoryMessageLoadedAction {
    type: ActionType.HISTORY_MESSAGE_LOADED;
    readonly message: IrisServerMessageDescriptor;
}

export class ActiveConversationMessageLoadedAction {
    type: ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED;
    readonly message: IrisServerMessageDescriptor;
}

export class StudentMessageSentAction {
    type: ActionType.STUDENT_MESSAGE_SENT;
    readonly message: IrisClientMessageDescriptor;
}

export type MessageStoreAction = HistoryMessageLoadedAction | ActiveConversationMessageLoadedAction | StudentMessageSentAction;

export class MessageStoreState {
    messages: ReadonlyArray<IrisMessageDescriptor>;
}
