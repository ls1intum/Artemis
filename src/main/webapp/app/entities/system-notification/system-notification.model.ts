import { Moment } from 'moment';

export const enum SystemNotificationType {
    WARNING = 'WARNING',
    INFO = 'INFO'
}

export interface ISystemNotification {
    id?: number;
    expireDate?: Moment;
    type?: SystemNotificationType;
}

export class SystemNotification implements ISystemNotification {
    constructor(public id?: number, public expireDate?: Moment, public type?: SystemNotificationType) {}
}
