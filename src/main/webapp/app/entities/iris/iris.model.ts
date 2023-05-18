import dayjs from 'dayjs';
import { User } from 'app/core/user/user.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

export class IrisSession {
    id: number;
    exercise?: ProgrammingExercise;
    user?: User;
    messages: IrisMessage[];
}

export enum IrisSender {
    SERVER = 'server',
    USER = 'user',
    SYSTEM = 'system',
}

export class IrisServerMessage {
    id: number;
    sender: IrisSender.SERVER;
    content: IrisMessageContent[];
    sentAt: dayjs.Dayjs;
    helpful?: boolean;
}

export class IrisClientMessage {
    id?: number;
    sender: IrisSender.USER;
    content: IrisMessageContent;
    sentAt?: dayjs.Dayjs;
}

export type IrisMessage = IrisClientMessage | IrisServerMessage;

export enum IrisMessageContentType {
    TEXT = 'text',
}

export class IrisMessageContent {
    type: IrisMessageContentType.TEXT;
    textContent: string;
}
