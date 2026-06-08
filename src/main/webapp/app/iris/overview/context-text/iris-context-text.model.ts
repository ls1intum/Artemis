export const CONTEXT_REGEX = /^\[context:(\d+):(\d*):(\d+(?:\.\d+)?)?]/;

export interface IrisContextParsed {
    lectureUnitId: string;
    page: string; // empty if not present
    timestamp: string; // empty if not present
}

export function parseContext(raw: string): IrisContextParsed | undefined {
    const match = raw.match(CONTEXT_REGEX);
    if (!match) {
        return undefined;
    }

    return {
        lectureUnitId: match[1],
        page: match[2] || '',
        timestamp: match[3] || '',
    };
}

export function removeContextBlock(text: string): string {
    return text.replace(CONTEXT_REGEX, '').trim();
}
