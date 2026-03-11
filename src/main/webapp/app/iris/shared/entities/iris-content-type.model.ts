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

/** A single option in a multiple-choice question. */
export interface McqOption {
    text: string;
    correct: boolean;
}

/** Structured data representing a multiple-choice question sent by the LLM. */
export interface McqData {
    type: 'mcq';
    question: string;
    options: McqOption[];
    explanation: string;
}

/** Message content carrying an arbitrary JSON payload, used for structured responses like MCQs. */
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

/**
 * Type guard that checks whether the given message content is JSON content.
 * @param content the message content to check
 * @returns true if the content is an IrisJsonMessageContent
 */
export function isJsonContent(content: IrisMessageContent): content is IrisJsonMessageContent {
    return content.type === IrisMessageContentType.JSON;
}

/**
 * Validates that a plain object has the full McqOption shape: a non-empty text string and a boolean correct field.
 * @param obj the value to validate
 * @returns true if obj is a valid McqOption
 */
function isValidMcqOption(obj: unknown): obj is McqOption {
    if (typeof obj !== 'object' || obj == null) {
        return false;
    }
    const rec = obj as Record<string, unknown>;
    return typeof rec['text'] === 'string' && rec['text'].length > 0 && typeof rec['correct'] === 'boolean';
}

/**
 * Runtime type guard that checks whether the given message content is a fully valid MCQ.
 * Validates type, question (non-empty string), options (array with at least 2 valid entries),
 * and explanation (non-empty string).
 * @param content the message content to check
 * @returns true if the content is JSON content containing a valid McqData payload
 */
export function isMcqContent(content: IrisMessageContent): content is IrisJsonMessageContent & { attributes: McqData } {
    if (!isJsonContent(content)) {
        return false;
    }
    const attrs = content.attributes;
    if (attrs?.['type'] !== 'mcq') {
        return false;
    }
    if (typeof attrs['question'] !== 'string' || attrs['question'].length === 0) {
        return false;
    }
    if (typeof attrs['explanation'] !== 'string' || attrs['explanation'].length === 0) {
        return false;
    }
    const options = attrs['options'];
    if (!Array.isArray(options) || options.length < 2) {
        return false;
    }
    return options.every(isValidMcqOption);
}

/**
 * Extracts typed McqData from a message content if it represents a valid MCQ.
 * Uses the isMcqContent type guard for full shape validation instead of an unchecked cast.
 * @param content the message content to extract from
 * @returns the McqData if the content is a valid MCQ, undefined otherwise
 */
export function getMcqData(content: IrisMessageContent): McqData | undefined {
    if (isMcqContent(content)) {
        return content.attributes;
    }
    return undefined;
}
