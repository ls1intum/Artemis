import { describe, expect, it } from 'vitest';
import { splitterPassThrough } from 'app/assessment/manage/assessment-workspace/splitter-pass-through';

const gutterBackground = (pt: ReturnType<typeof splitterPassThrough>) => ((pt.gutter as { style: Record<string, string> }).style as Record<string, string>)['background'];

describe('splitterPassThrough', () => {
    it('should draw the gutters in the grey of the shell by default, as on the pages of a student', () => {
        expect(gutterBackground(splitterPassThrough('horizontal'))).toBe('var(--bs-body-bg)');
    });

    it('should draw the gutters in the colour of the panels when asked to, as on the assessment pages', () => {
        expect(gutterBackground(splitterPassThrough('vertical', {}, 'panel'))).toBe('var(--module-bg)');
    });
});
