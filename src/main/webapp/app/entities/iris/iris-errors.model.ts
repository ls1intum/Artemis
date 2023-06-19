export enum IrisErrorMessageKey {
    SESSION_LOAD_FAILED = 'artemisApp.exerciseChatbot.errors.sessionLoadFailed',
    SEND_MESSAGE_FAILED = 'artemisApp.exerciseChatbot.errors.sendMessageFailed',
    HISTORY_LOAD_FAILED = 'artemisApp.exerciseChatbot.errors.historyLoadFailed',
    INVALID_SESSION_STATE = 'artemisApp.exerciseChatbot.errors.invalidSessionState',
    SESSION_CREATION_FAILED = 'artemisApp.exerciseChatbot.errors.sessionCreationFailed',
    RATE_MESSAGE_FAILED = 'artemisApp.exerciseChatbot.errors.rateMessageFailed',
    IRIS_DISABLED = 'artemisApp.exerciseChatbot.errors.irisDisabled',
}

export interface IrisErrorType {
    key: IrisErrorMessageKey;
    fatal: boolean;
}

const IrisErrors: IrisErrorType[] = [
    { key: IrisErrorMessageKey.SESSION_LOAD_FAILED, fatal: true },
    { key: IrisErrorMessageKey.SEND_MESSAGE_FAILED, fatal: false },
    { key: IrisErrorMessageKey.HISTORY_LOAD_FAILED, fatal: true },
    { key: IrisErrorMessageKey.INVALID_SESSION_STATE, fatal: true },
    { key: IrisErrorMessageKey.SESSION_CREATION_FAILED, fatal: true },
    { key: IrisErrorMessageKey.RATE_MESSAGE_FAILED, fatal: false },
    { key: IrisErrorMessageKey.IRIS_DISABLED, fatal: true },
];

export const errorMessages: Readonly<{ [key in IrisErrorMessageKey]: IrisErrorType }> = Object.freeze(
    IrisErrors.reduce((map, obj) => {
        map[obj.key] = obj;
        return map;
    }, {} as { [key in IrisErrorMessageKey]: IrisErrorType }),
);
