import { describe, expect, it } from 'vitest';
import hljs from 'app/foundation/util/highlight-languages.util';

/**
 * Guards the curated language list against CVE-2026-133937 (ReDoS in the livescript grammar of highlight.js).
 * The grammar must stay unregistered until highlight.js ships a release that fixes it.
 */
describe('highlight-languages.util', () => {
    const liveScriptSnippet = ['add = (a, b) -> a + b', 'class Greeter', '  greet: (name) -> "Hello, #{name}"', 'console.log add 1, 2', '/[a-z]+/g.test "abc"'].join('\n');

    it('should register the curated languages on the shared core instance', () => {
        expect(hljs.listLanguages()).toEqual(expect.arrayContaining(['java', 'python', 'typescript']));
        expect(hljs.getLanguage('java')).toBeDefined();
    });

    it('should not register the livescript grammar', () => {
        expect(hljs.listLanguages()).not.toContain('livescript');
        expect(hljs.getLanguage('livescript')).toBeUndefined();
    });

    it('should not register livescript under its alias', () => {
        expect(hljs.getLanguage('ls')).toBeUndefined();
    });

    it('should never report livescript when auto-detecting the language', () => {
        const result = hljs.highlightAuto(liveScriptSnippet);

        expect(result.language).not.toBe('livescript');
        expect(result.secondBest?.language).not.toBe('livescript');
    });
});
