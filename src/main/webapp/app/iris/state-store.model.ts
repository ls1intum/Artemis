import { IrisClientMessage, IrisMessage, IrisServerMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey, IrisErrorType, errorMessages } from 'app/entities/iris/iris-errors.model';

export enum ActionType {
    NUM_NEW_MESSAGES_RESET = 'num-new-messages-reset',
    HISTORY_MESSAGE_LOADED = 'history-message-loaded',
    ACTIVE_CONVERSATION_MESSAGE_LOADED = 'active-conversation-message-loaded',
    CONVERSATION_ERROR_OCCURRED = 'conversation-error-occurred',
    STUDENT_MESSAGE_SENT = 'student-message-sent',
    SESSION_CHANGED = 'session-changed',
    RATE_MESSAGE_SUCCESS = 'rate-message-success',
}

export interface MessageStoreAction {
    type: ActionType;
}

export class NumNewMessagesResetAction implements MessageStoreAction {
    readonly type: ActionType;

    public constructor() {
        this.type = ActionType.NUM_NEW_MESSAGES_RESET;
    }
}

export class HistoryMessageLoadedAction implements MessageStoreAction {
    readonly type: ActionType;

    public constructor(public readonly message: IrisServerMessage) {
        this.type = ActionType.HISTORY_MESSAGE_LOADED;
    }
}

export class ActiveConversationMessageLoadedAction implements MessageStoreAction {
    readonly type: ActionType;

    public constructor(public readonly message: IrisMessage) {
        this.type = ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED;
    }
}

export class ConversationErrorOccurredAction implements MessageStoreAction {
    readonly type: ActionType;
    readonly errorObject: IrisErrorType | null;

    constructor(errorType: IrisErrorMessageKey | null, paramsMap: Map<string, any> | undefined = undefined) {
        this.type = ActionType.CONVERSATION_ERROR_OCCURRED;
        this.errorObject = ConversationErrorOccurredAction.buildErrorObject(errorType, paramsMap);
    }

    private static buildErrorObject(errorType: IrisErrorMessageKey | null, paramsMap?: Map<string, any>): IrisErrorType | null {
        if (!errorType) return null;
        const errorObject = errorMessages[errorType];
        errorObject.paramsMap = paramsMap;
        return errorObject;
    }
}

export class StudentMessageSentAction implements MessageStoreAction {
    readonly type: ActionType;

    public constructor(
        public readonly message: IrisClientMessage,
        public readonly timeoutId: ReturnType<typeof setTimeout> | null = null,
    ) {
        this.type = ActionType.STUDENT_MESSAGE_SENT;
    }
}

export class SessionReceivedAction implements MessageStoreAction {
    readonly type: ActionType;

    public constructor(
        public readonly sessionId: number,
        public readonly messages: ReadonlyArray<IrisMessage>,
    ) {
        this.type = ActionType.SESSION_CHANGED;
    }
}

export class RateMessageSuccessAction implements MessageStoreAction {
    readonly type: ActionType;

    public constructor(
        public readonly index: number,
        public readonly helpful: boolean,
    ) {
        this.type = ActionType.RATE_MESSAGE_SUCCESS;
    }
}

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

export function isRateMessageSuccessAction(action: MessageStoreAction): action is RateMessageSuccessAction {
    return action.type === ActionType.RATE_MESSAGE_SUCCESS;
}

export class MessageStoreState {
    public constructor(
        public messages: ReadonlyArray<IrisMessage>,
        public sessionId: number | null,
        public isLoading: boolean,
        public numNewMessages: number,
        public error: IrisErrorType | null,
        public serverResponseTimeout: ReturnType<typeof setTimeout> | null,
    ) {}
}
