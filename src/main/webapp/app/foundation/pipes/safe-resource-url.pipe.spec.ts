import { TestBed } from '@angular/core/testing';
import { SafeResourceUrl } from '@angular/platform-browser';
import { beforeEach, describe, expect, it } from 'vitest';
import { SafeResourceUrlPipe } from 'app/foundation/pipes/safe-resource-url.pipe';

describe('SafeResourceUrlPipe', () => {
    let pipe: SafeResourceUrlPipe;

    beforeEach(() => {
        pipe = TestBed.runInInjectionContext(() => new SafeResourceUrlPipe());
    });

    // The pipe returns a trusted SafeResourceUrl wrapper; read the underlying string it decided to trust.
    function unwrap(safeResourceUrl: SafeResourceUrl): string {
        return (safeResourceUrl as { changingThisBreaksApplicationSecurity?: string })?.changingThisBreaksApplicationSecurity ?? '';
    }

    // An attachment video unit's videoSource reaches an <iframe src> through this pipe, so a scheme that
    // executes must not survive it.
    it.each([
        'javascript:alert(1)',
        'JAVASCRIPT:alert(1)',
        'data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==',
        'vbscript:msgbox(1)',
        'file:///etc/passwd',
        // A custom application scheme has no business in an embedding context either.
        'vscode://vscode.git/clone?url=x',
    ])('yields about:blank for the non-embeddable scheme %s', (payload) => {
        expect(unwrap(pipe.transform(payload))).toBe('about:blank');
    });

    it.each(['https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ', 'https://live.rbg.tum.de/w/course/1', 'http://example.org/video.mp4'])(
        'passes the embeddable URL %s through unchanged',
        (url) => {
            expect(unwrap(pipe.transform(url))).toBe(url);
        },
    );

    it('passes a relative URL through, since it inherits the page scheme', () => {
        expect(unwrap(pipe.transform('/api/core/files/attachments/1'))).toBe('/api/core/files/attachments/1');
    });

    it('yields about:blank for nullish and unparseable input', () => {
        expect(unwrap(pipe.transform(undefined))).toBe('about:blank');
        expect(unwrap(pipe.transform(null))).toBe('about:blank');
        expect(unwrap(pipe.transform(''))).toBe('about:blank');
        expect(unwrap(pipe.transform('http://'))).toBe('about:blank');
    });
});
