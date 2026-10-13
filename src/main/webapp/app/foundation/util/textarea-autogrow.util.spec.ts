import { afterEach, describe, expect, it, vi } from 'vitest';
import { autogrowTextarea } from 'app/foundation/util/textarea-autogrow.util';

describe('autogrowTextarea', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set the height to the content height', () => {
        const textarea = document.createElement('textarea');
        vi.spyOn(textarea, 'scrollHeight', 'get').mockReturnValue(180);

        autogrowTextarea(textarea);

        expect(textarea.style.height).toBe('180px');
    });

    it('should add the border height of a border-box textarea', () => {
        const textarea = document.createElement('textarea');
        vi.spyOn(textarea, 'scrollHeight', 'get').mockReturnValue(180);
        vi.spyOn(textarea, 'offsetHeight', 'get').mockReturnValue(52);
        vi.spyOn(textarea, 'clientHeight', 'get').mockReturnValue(50);

        autogrowTextarea(textarea);

        expect(textarea.style.height).toBe('182px');
    });

    it('should reset the height before measuring so that the textarea can shrink', () => {
        const textarea = document.createElement('textarea');
        textarea.style.height = '500px';
        let heightWhileMeasuring: string | undefined;
        vi.spyOn(textarea, 'scrollHeight', 'get').mockImplementation(() => {
            heightWhileMeasuring = textarea.style.height;
            return 40;
        });

        autogrowTextarea(textarea);

        expect(heightWhileMeasuring).toBe('0px');
        expect(textarea.style.height).toBe('40px');
    });
});
