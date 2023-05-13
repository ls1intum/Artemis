import { IrisClientMessageDescriptor, IrisMessageDescriptor, IrisServerMessageDescriptor } from 'app/entities/iris/iris.model';

export enum ActionType {
    HISTORY_MESSAGE_LOADED = 'history-message-loaded',
    ACTIVE_CONVERSATION_MESSAGE_LOADED = 'active-conversation-message-loaded',
    STUDENT_MESSAGE_SENT = 'student-message-sent',
}

export class HistoryMessageLoadedAction {
    type: ActionType;

    public constructor(public readonly message: IrisServerMessageDescriptor) {
        this.type = ActionType.HISTORY_MESSAGE_LOADED;
    }
}

export class ActiveConversationMessageLoadedAction {
    type: ActionType;

    public constructor(public readonly message: IrisServerMessageDescriptor) {
        this.type = ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED;
    }
}

export class StudentMessageSentAction {
    type: ActionType;

    public constructor(public readonly message: IrisClientMessageDescriptor) {
        this.type = ActionType.STUDENT_MESSAGE_SENT;
    }
}

export type MessageStoreAction = HistoryMessageLoadedAction | ActiveConversationMessageLoadedAction | StudentMessageSentAction;

export class MessageStoreState {
    public constructor(public messages: ReadonlyArray<IrisMessageDescriptor>) {}
}
