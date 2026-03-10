export enum IrisMessageContentType {
    TEXT = 'text',
    JSON = 'json',
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

export interface McqOption {
    text: string;
    correct: boolean;
}

export interface McqQuestionData {
    question: string;
    options: McqOption[];
    explanation: string;
}

export interface McqData extends McqQuestionData {
    type: 'mcq';
    response?: McqResponseData;
}

export interface McqSetData {
    type: 'mcq-set';
    questions: McqQuestionData[];
    responses?: McqResponseData[];
}

export interface McqResponseData {
    selectedIndex: number;
    submitted: boolean;
    questionIndex?: number;
}

export class IrisJsonMessageContent extends IrisMessageContent {
    constructor(public attributes: Record<string, unknown>) {
        super(IrisMessageContentType.JSON);
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

export function isJsonContent(content: IrisMessageContent): content is IrisJsonMessageContent {
    return content.type === IrisMessageContentType.JSON;
}

export function isMcqContent(content: IrisMessageContent): boolean {
    if (!isJsonContent(content)) {
        return false;
    }
    return content.attributes?.['type'] === 'mcq';
}

export function getMcqData(content: IrisMessageContent): McqData | undefined {
    if (!isJsonContent(content)) {
        return undefined;
    }
    const attrs = content.attributes;
    if (attrs?.['type'] === 'mcq') {
        return attrs as unknown as McqData;
    }
    return undefined;
}

export function isMcqSetContent(content: IrisMessageContent): boolean {
    if (!isJsonContent(content)) {
        return false;
    }
    return content.attributes?.['type'] === 'mcq-set';
}

export function getMcqSetData(content: IrisMessageContent): McqSetData | undefined {
    if (!isJsonContent(content)) {
        return undefined;
    }
    const attrs = content.attributes;
    if (attrs?.['type'] === 'mcq-set') {
        return attrs as unknown as McqSetData;
    }
    return undefined;
}
