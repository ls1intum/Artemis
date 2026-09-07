import { TestBed } from '@angular/core/testing';
import { SafeUrl } from '@angular/platform-browser';
import { beforeEach, describe, expect, it } from 'vitest';
import { SafeUrlPipe } from 'app/foundation/pipes/safe-url.pipe';

describe('SafeUrlPipe', () => {
    let pipe: SafeUrlPipe;

    beforeEach(() => {
        pipe = TestBed.runInInjectionContext(() => new SafeUrlPipe());
    });

    // The pipe returns a trusted SafeUrl wrapper; read the underlying string it decided to trust.
    function unwrap(safeUrl: SafeUrl): string {
        return (safeUrl as { changingThisBreaksApplicationSecurity?: string })?.changingThisBreaksApplicationSecurity ?? '';
    }

    it.each(['javascript:alert(1)', 'JavaScript:alert(1)', '  javascript:alert(1)', 'java\nscript:alert(1)'])('neutralises the script-bearing URL %s', (payload) => {
        expect(unwrap(pipe.transform(payload))).not.toMatch(/^\s*javascript:/i);
    });

    // These are the schemes the code button's IDE deep links use; blocking them would break cloning.
    it.each([
        'vscode://vscode.git/clone?url=https%3A%2F%2Fexample.org%2Frepo.git',
        'sourcetree://cloneRepo?type=stash&cloneUrl=https://example.org/repo.git&baseWebUrl=https://example.org',
        'https://example.org/repo.git',
        'http://example.org/repo.git',
    ])('passes the application URL %s through unchanged', (url) => {
        expect(unwrap(pipe.transform(url))).toBe(url);
    });

    it('returns an empty URL for null/undefined input', () => {
        expect(unwrap(pipe.transform(undefined))).toBe('');
        expect(unwrap(pipe.transform(null))).toBe('');
    });
});
