export enum IrisMessageContentType {
    TEXT = 'text',
}

export class IrisMessageContent {
    type: IrisMessageContentType.TEXT;
    textContent: string;
}
