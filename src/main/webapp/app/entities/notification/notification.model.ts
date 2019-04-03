import { Moment } from 'moment';
import { BaseEntity } from 'app/shared';
import { User } from 'app/core';
import { ExerciseType } from 'app/entities/exercise';

export enum NotificationType {
    SYSTEM = 'system',
    GROUP = 'group',
    SINGLE = 'single'
}

export class Notification implements BaseEntity {
    public id: number;
    public notificationType: NotificationType;
    public title: string;
    public text: string;
    public notificationDate: Moment;
    public target: string;
    public author: User;

    protected constructor(notificationType: NotificationType) {
        this.notificationType = notificationType;
    }
}
