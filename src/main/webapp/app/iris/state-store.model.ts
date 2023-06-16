import { IrisClientMessage, IrisMessage, IrisServerMessage } from 'app/entities/iris/iris-message.model';

export enum ActionType {
    NUM_NEW_MESSAGES_RESET = 'num-new-messages-reset',
    HISTORY_MESSAGE_LOADED = 'history-message-loaded',
    ACTIVE_CONVERSATION_MESSAGE_LOADED = 'active-conversation-message-loaded',
    CONVERSATION_ERROR_OCCURRED = 'conversation-error-occurred',
    STUDENT_MESSAGE_SENT = 'student-message-sent',
    SESSION_CHANGED = 'session-changed',
}

export class NumNewMessagesResetAction {
    readonly type: ActionType;

    public constructor() {
        this.type = ActionType.NUM_NEW_MESSAGES_RESET;
    }
}

export class HistoryMessageLoadedAction {
    readonly type: ActionType;

    public constructor(public readonly message: IrisServerMessage) {
        this.type = ActionType.HISTORY_MESSAGE_LOADED;
    }
}

export class ActiveConversationMessageLoadedAction {
    readonly type: ActionType;

    public constructor(public readonly message: IrisServerMessage) {
        this.type = ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED;
    }
}

export class ConversationErrorOccurredAction {
    readonly type: ActionType;

    public constructor(public readonly errorMessage: string) {
        this.type = ActionType.CONVERSATION_ERROR_OCCURRED;
    }
}

export class StudentMessageSentAction {
    readonly type: ActionType;

    public constructor(public readonly message: IrisClientMessage) {
        this.type = ActionType.STUDENT_MESSAGE_SENT;
    }
}

export class SessionReceivedAction {
    readonly type: ActionType;

    public constructor(public readonly sessionId: number, public readonly messages: ReadonlyArray<IrisMessage>) {
        this.type = ActionType.SESSION_CHANGED;
    }
}

export type MessageStoreAction =
    | NumNewMessagesResetAction
    | HistoryMessageLoadedAction
    | ActiveConversationMessageLoadedAction
    | ConversationErrorOccurredAction
    | StudentMessageSentAction
    | SessionReceivedAction;

export function isNumNewMessagesResetAction(action: MessageStoreAction): action is NumNewMessagesResetAction {
    return action.type === ActionType.NUM_NEW_MESSAGES_RESET;
}

export function isHistoryMessageLoadedAction(action: MessageStoreAction): action is HistoryMessageLoadedAction {
    return action.type === ActionType.HISTORY_MESSAGE_LOADED;
}

export function isActiveConversationMessageLoadedAction(action: MessageStoreAction): action is ActiveConversationMessageLoadedAction {
    return action.type === ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED;
}

export function isConversationErrorOccurredAction(action: MessageStoreAction): action is ConversationErrorOccurredAction {
    return action.type === ActionType.CONVERSATION_ERROR_OCCURRED;
}

export function isStudentMessageSentAction(action: MessageStoreAction): action is StudentMessageSentAction {
    return action.type === ActionType.STUDENT_MESSAGE_SENT;
}

export function isSessionReceivedAction(action: MessageStoreAction): action is SessionReceivedAction {
    return action.type === ActionType.SESSION_CHANGED;
}

export class MessageStoreState {
    public constructor(
        public messages: ReadonlyArray<IrisMessage>,
        public sessionId: number | null,
        public isLoading: boolean,
        public numNewMessages: number,
        public error: string,
    ) {}
}
