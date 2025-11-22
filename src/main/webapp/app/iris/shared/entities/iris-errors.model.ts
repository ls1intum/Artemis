export enum IrisErrorMessageKey {
    SESSION_LOAD_FAILED = 'artemisApp.exerciseChatbot.errors.sessionLoadFailed',
    SEND_MESSAGE_FAILED = 'artemisApp.exerciseChatbot.errors.sendMessageFailed',
    HISTORY_LOAD_FAILED = 'artemisApp.exerciseChatbot.errors.historyLoadFailed',
    INVALID_SESSION_STATE = 'artemisApp.exerciseChatbot.errors.invalidSessionState',
    SESSION_CREATION_FAILED = 'artemisApp.exerciseChatbot.errors.sessionCreationFailed',
    RATE_MESSAGE_FAILED = 'artemisApp.exerciseChatbot.errors.rateMessageFailed',
    IRIS_DISABLED = 'artemisApp.exerciseChatbot.errors.irisDisabled',
    IRIS_SERVER_RESPONSE_TIMEOUT = 'artemisApp.exerciseChatbot.errors.timeout',
    EMPTY_MESSAGE = 'artemisApp.exerciseChatbot.errors.emptyMessage',
    FORBIDDEN = 'artemisApp.exerciseChatbot.errors.forbidden',
    INTERNAL_PYRIS_ERROR = 'artemisApp.exerciseChatbot.errors.internalPyrisError',
    INVALID_TEMPLATE = 'artemisApp.exerciseChatbot.errors.invalidTemplate',
    NO_MODEL_AVAILABLE = 'artemisApp.exerciseChatbot.errors.noModelAvailable',
    NO_RESPONSE = 'artemisApp.exerciseChatbot.errors.noResponse',
    PARSE_RESPONSE = 'artemisApp.exerciseChatbot.errors.parseResponse',
    TECHNICAL_ERROR_RESPONSE = 'artemisApp.exerciseChatbot.errors.technicalError',
    IRIS_NOT_AVAILABLE = 'artemisApp.exerciseChatbot.errors.irisNotAvailable',
    RATE_LIMIT_EXCEEDED = 'artemisApp.exerciseChatbot.errors.rateLimitExceeded',
    AI_USAGE_DECLINED = 'artemisApp.exerciseChatbot.errors.aiUsageDeclined',
}

export interface IrisErrorType {
    key: IrisErrorMessageKey;
    fatal: boolean;
    paramsMap?: Map<string, any>;
}

const IrisErrors: IrisErrorType[] = [
    { key: IrisErrorMessageKey.SESSION_LOAD_FAILED, fatal: true },
    { key: IrisErrorMessageKey.SEND_MESSAGE_FAILED, fatal: false },
    { key: IrisErrorMessageKey.HISTORY_LOAD_FAILED, fatal: true },
    { key: IrisErrorMessageKey.INVALID_SESSION_STATE, fatal: true },
    { key: IrisErrorMessageKey.SESSION_CREATION_FAILED, fatal: true },
    { key: IrisErrorMessageKey.RATE_MESSAGE_FAILED, fatal: false },
    { key: IrisErrorMessageKey.IRIS_DISABLED, fatal: true },
    { key: IrisErrorMessageKey.IRIS_SERVER_RESPONSE_TIMEOUT, fatal: false },
    { key: IrisErrorMessageKey.EMPTY_MESSAGE, fatal: false },
    { key: IrisErrorMessageKey.INTERNAL_PYRIS_ERROR, fatal: true },
    { key: IrisErrorMessageKey.INVALID_TEMPLATE, fatal: true },
    { key: IrisErrorMessageKey.NO_MODEL_AVAILABLE, fatal: true },
    { key: IrisErrorMessageKey.NO_RESPONSE, fatal: true },
    { key: IrisErrorMessageKey.PARSE_RESPONSE, fatal: true },
    { key: IrisErrorMessageKey.FORBIDDEN, fatal: true },
    { key: IrisErrorMessageKey.TECHNICAL_ERROR_RESPONSE, fatal: true },
    { key: IrisErrorMessageKey.IRIS_NOT_AVAILABLE, fatal: true },
    { key: IrisErrorMessageKey.RATE_LIMIT_EXCEEDED, fatal: true },
];

export const errorMessages: Readonly<{ [key in IrisErrorMessageKey]: IrisErrorType }> = Object.freeze(
    IrisErrors.reduce(
        (map, obj) => {
            map[obj.key] = obj;
            return map;
        },
        {} as { [key in IrisErrorMessageKey]: IrisErrorType },
    ),
);
