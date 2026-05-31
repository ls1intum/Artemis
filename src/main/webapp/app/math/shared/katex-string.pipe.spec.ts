import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { KatexStringPipe } from 'app/math/shared/katex-string.pipe';

describe('KatexStringPipe', () => {
    setupTestBed({ zoneless: true });

    let pipe: KatexStringPipe;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [KatexStringPipe] });
        pipe = TestBed.inject(KatexStringPipe);
    });

    it('returns empty string for undefined input', () => {
        expect(pipe.transform(undefined)).toBe('');
    });

    it('returns SafeHtml for a valid LaTeX expression', () => {
        const result = pipe.transform('x + 1') as { changingThisBreaksApplicationSecurity?: string };
        expect(result).toBeTruthy();
        if (typeof result === 'object') {
            expect(result.changingThisBreaksApplicationSecurity).toMatch(/katex/);
        }
    });
});
