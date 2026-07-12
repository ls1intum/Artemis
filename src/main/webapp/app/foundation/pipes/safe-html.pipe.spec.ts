import { TestBed } from '@angular/core/testing';
import { SafeHtml } from '@angular/platform-browser';
import { beforeEach, describe, expect, it } from 'vitest';
import { SafeHtmlPipe } from 'app/foundation/pipes/safe-html.pipe';

describe('SafeHtmlPipe', () => {
    let pipe: SafeHtmlPipe;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        pipe = TestBed.runInInjectionContext(() => new SafeHtmlPipe());
    });

    // The pipe returns a trusted SafeHtml wrapper; read the underlying string that DOMPurify produced.
    function unwrap(safeHtml: SafeHtml): string {
        return (safeHtml && typeof safeHtml === 'object' && 'changingThisBreaksApplicationSecurity' in safeHtml
            ? (safeHtml as { changingThisBreaksApplicationSecurity: string }).changingThisBreaksApplicationSecurity
            : '') as string;
    }

    it('strips script tags and event-handler attributes (XSS payload)', () => {
        const result = unwrap(pipe.transform('<img src="x" onerror="alert(1)">before<script>alert(2)</script>after'));
        expect(result).not.toContain('onerror');
        expect(result).not.toContain('<script');
        expect(result).not.toContain('alert(2)');
        // benign surrounding text is preserved
        expect(result).toContain('before');
        expect(result).toContain('after');
    });

    it('strips javascript: URLs from links', () => {
        const result = unwrap(pipe.transform('<a href="javascript:alert(1)">click</a>'));
        expect(result).not.toContain('javascript:');
        expect(result).toContain('click');
    });

    it('preserves benign inline markup and entities that callers rely on', () => {
        const result = unwrap(pipe.transform('<strong>bold</strong> <sub>x</sub> [95 - &infin;)'));
        expect(result).toContain('<strong>bold</strong>');
        expect(result).toContain('<sub>x</sub>');
        // the infinity entity survives (kept as entity or decoded to the glyph)
        expect(result).toMatch(/&infin;|∞/);
    });

    it('returns an empty result for null/undefined input', () => {
        expect(unwrap(pipe.transform(undefined))).toBe('');
        expect(unwrap(pipe.transform(null))).toBe('');
    });
});
