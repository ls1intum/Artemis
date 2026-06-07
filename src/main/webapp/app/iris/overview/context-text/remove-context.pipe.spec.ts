import { RemoveContextPipe } from './remove-context.pipe';

describe('RemoveContextPipe', () => {
    let pipe: RemoveContextPipe;

    beforeEach(() => {
        pipe = new RemoveContextPipe();
    });

    it('should remove context block from message', () => {
        const result = pipe.transform('[context:123:7:125.5]How does this work?');
        expect(result).toBe('How does this work?');
    });

    it('should handle message without context block', () => {
        const result = pipe.transform('How does this work?');
        expect(result).toBe('How does this work?');
    });

    it('should remove multiple context blocks', () => {
        const result = pipe.transform('[context:123:7:125.5]First [context:456:8:200.0]Second');
        expect(result).toBe('First Second');
    });

    it('should handle null', () => {
        const result = pipe.transform(null);
        expect(result).toBe('');
    });

    it('should handle undefined', () => {
        const result = pipe.transform(undefined);
        expect(result).toBe('');
    });

    it('should handle empty string', () => {
        const result = pipe.transform('');
        expect(result).toBe('');
    });

    it('should trim whitespace after removal', () => {
        const result = pipe.transform('[context:123:7:125.5]  Message with spaces  ');
        expect(result).toBe('Message with spaces');
    });
});
