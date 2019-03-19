import { Moment } from 'moment';
import { BaseEntity } from 'app/shared';
import { User } from 'app/core';

export class Notification implements BaseEntity {
    public id: number;
    public title: string;
    public text: string;
    public notificationDate: Moment;
    public target: string;
    public author: User;

    constructor() {}
}
