import dayjs from 'dayjs';
import { Exercise } from 'app/entities/exercise.model';
import { User } from 'app/core/user/user.model';

export interface IrisConversation {
    id: number;
    programmingExercise?: Exercise;
    user?: User;
    latestMessageId?: string;
    messageDescriptors: IrisMessageDescriptor[];
    irisEnabled: boolean;
}

export enum IrisSender {
    SERVER = 'server',
    USER = 'user',
}

export interface IrisServerMessageDescriptor {
    messageId: number;
    sender: IrisSender.SERVER;
    messageContent?: IrisMessageContent;
    sentAt: dayjs.Dayjs;
    helpful?: boolean;
}

export class IrisClientMessageDescriptor {
    sender: IrisSender.USER;
    messageContent: IrisMessageContent;
    messageId?: number;
    sentAt?: dayjs.Dayjs;
}

export type IrisMessageDescriptor = IrisClientMessageDescriptor | IrisServerMessageDescriptor;

export enum IrisMessageContentType {
    SOURCE_CODE = 'source-code',
    TEXT = 'text',
    IMAGE = 'image',
}

export interface IrisMessageContent {
    type: IrisMessageContentType;
    content: string;
}
