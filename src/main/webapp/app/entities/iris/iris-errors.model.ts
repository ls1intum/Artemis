export enum IrisErrorMessageKey {
    SESSION_LOAD_FAILED = 'sessionLoadFailed',
    SEND_MESSAGE_FAILED = 'sendMessageFailed',
    HISTORY_LOAD_FAILED = 'historyLoadFailed',
    INVALID_SESSION_STATE = 'invalidSessionState',
    SESSION_CREATION_FAILED = 'sessionCreationFailed',
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
];

export const errorMessages: Readonly<{ [key in IrisErrorMessageKey]: IrisErrorType }> = Object.freeze(
    IrisErrors.reduce((map, obj) => {
        map[obj.key] = obj;
        return map;
    }, {} as { [key in IrisErrorMessageKey]: IrisErrorType }),
);
