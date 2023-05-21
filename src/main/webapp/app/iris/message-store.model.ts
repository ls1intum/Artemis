import { IrisClientMessage, IrisMessage, IrisServerMessage } from 'app/entities/iris/iris.model';

export enum ActionType {
    HISTORY_MESSAGE_LOADED = 'history-message-loaded',
    ACTIVE_CONVERSATION_MESSAGE_LOADED = 'active-conversation-message-loaded',
    STUDENT_MESSAGE_SENT = 'student-message-sent',
    SESSION_ID_CHANGED = 'session-id-changed',
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

export class StudentMessageSentAction {
    readonly type: ActionType;

    public constructor(public readonly message: IrisClientMessage, public readonly callbacks = {}) {
        this.type = ActionType.STUDENT_MESSAGE_SENT;
        this.callbacks = callbacks;
    }
}

export class SessionIdReceivedAction {
    readonly type: ActionType;

    public constructor(public readonly sessionId: number | null) {
        this.type = ActionType.SESSION_ID_CHANGED;
    }
}

export type MessageStoreAction = HistoryMessageLoadedAction | ActiveConversationMessageLoadedAction | StudentMessageSentAction | SessionIdReceivedAction;

export function isHistoryMessageLoadedAction(action: MessageStoreAction): action is HistoryMessageLoadedAction {
    return action.type === ActionType.HISTORY_MESSAGE_LOADED;
}

export function isActiveConversationMessageLoadedAction(action: MessageStoreAction): action is ActiveConversationMessageLoadedAction {
    return action.type === ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED;
}

export function isStudentMessageSentAction(action: MessageStoreAction): action is StudentMessageSentAction {
    return action.type === ActionType.STUDENT_MESSAGE_SENT;
}

export function isSessionIdReceivedAction(action: MessageStoreAction): action is SessionIdReceivedAction {
    return action.type === ActionType.SESSION_ID_CHANGED;
}

export class MessageStoreState {
    public constructor(public messages: ReadonlyArray<IrisMessage>, public sessionId: number | null) {}
}
