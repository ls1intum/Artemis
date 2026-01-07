import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RemoveSecondsPipe } from 'app/tutorialgroup/shared/pipe/remove-seconds.pipe';

describe('RemoveSecondsPipe', () => {
    let pipe: RemoveSecondsPipe;

    beforeEach(() => {
        pipe = new RemoveSecondsPipe();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should remove seconds from a time string', () => {
        const time = '14:00:00';
        const timeWithoutSeconds = pipe.transform(time);
        expect(timeWithoutSeconds).toBe('14:00');
    });

    it('should convert undefined to an empty string', () => {
        const time = undefined;
        const timeWithoutSeconds = pipe.transform(time);
        expect(timeWithoutSeconds).toBe('');
    });

    it('should not change a time string without seconds', () => {
        const time = '14:00';
        const timeWithoutSeconds = pipe.transform(time);
        expect(timeWithoutSeconds).toBe('14:00');
    });
});
