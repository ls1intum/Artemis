export interface IrisConversation {
    id: number;
    exerciseId: number;
    studentId: number;
    latestMessageId?: string;
    messageDescriptors: IrisMessageDescriptor[];
}

export enum IrisSender {
    SERVER = 'server',
    USER = 'user',
}

export interface IrisServerMessageDescriptor {
    messageId: number;
    sender: IrisSender.SERVER;
    messageContent?: IrisMessageContent;
    sentDatetime: Date; // dayjs?
}

export interface IrisClientMessageDescriptor {
    sender: IrisSender.USER;
    messageContent: IrisMessageContent;
    sentDatetime: Date; // dayjs?
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
