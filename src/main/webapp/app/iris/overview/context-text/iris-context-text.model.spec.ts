import { parseContext, removeContextBlock } from './iris-context-text.model';

describe('IrisContextText', () => {
    describe('parseContext', () => {
        it('should parse context with all parameters', () => {
            const result = parseContext('[context:123:7:125.5]How does this work?');

            expect(result).toBeDefined();
            expect(result?.lectureUnitId).toBe('123');
            expect(result?.page).toBe('7');
            expect(result?.timestamp).toBe('125.5');
        });

        it('should parse context with PDF only', () => {
            const result = parseContext('[context:123:7:]What does this slide mean?');

            expect(result).toBeDefined();
            expect(result?.lectureUnitId).toBe('123');
            expect(result?.page).toBe('7');
            expect(result?.timestamp).toBe('');
        });

        it('should parse context with video only', () => {
            const result = parseContext('[context:123::95.2]What does the speaker say?');

            expect(result).toBeDefined();
            expect(result?.lectureUnitId).toBe('123');
            expect(result?.page).toBe('');
            expect(result?.timestamp).toBe('95.2');
        });

        it('should parse context without page or timestamp', () => {
            const result = parseContext('[context:123::]Can you explain this?');

            expect(result).toBeDefined();
            expect(result?.lectureUnitId).toBe('123');
            expect(result?.page).toBe('');
            expect(result?.timestamp).toBe('');
        });

        it('should return undefined for text without context', () => {
            const result = parseContext('What is this about?');

            expect(result).toBeUndefined();
        });

        it('should return undefined for empty string', () => {
            const result = parseContext('');

            expect(result).toBeUndefined();
        });
    });

    describe('removeContextBlock', () => {
        it('should remove context block from text', () => {
            const result = removeContextBlock('[context:123:7:125.5]How does this work?');

            expect(result).toBe('How does this work?');
        });

        it('should handle text without context block', () => {
            const result = removeContextBlock('How does this work?');

            expect(result).toBe('How does this work?');
        });

        it('should remove multiple context blocks', () => {
            const result = removeContextBlock('[context:123:7:125.5]First [context:456:8:200.0]Second');

            expect(result).toBe('First Second');
        });

        it('should handle empty string', () => {
            const result = removeContextBlock('');

            expect(result).toBe('');
        });

        it('should trim whitespace after removal', () => {
            const result = removeContextBlock('[context:123:7:125.5]  Message with spaces  ');

            expect(result).toBe('Message with spaces');
        });
    });
});
