export enum IrisMessageContentType {
    TEXT = 'text',
}

export abstract class IrisMessageContent {
    id?: number;
    messageId?: number;

    protected constructor(public type: IrisMessageContentType) {}
}

export class IrisTextMessageContent extends IrisMessageContent {
    constructor(public textContent: string) {
        super(IrisMessageContentType.TEXT);
    }
}

export function isTextContent(content: IrisMessageContent): content is IrisTextMessageContent {
    return content.type === IrisMessageContentType.TEXT;
}

export function getTextContent(content: IrisMessageContent) {
    if (isTextContent(content)) {
        const irisMessageTextContent = content as IrisTextMessageContent;
        return irisMessageTextContent.textContent;
    }
}
