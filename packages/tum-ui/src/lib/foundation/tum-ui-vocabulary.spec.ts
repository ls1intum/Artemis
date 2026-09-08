import { vi } from 'vitest';
import { TumUiSeverity, resetDeprecationWarningsForTesting, resolveSeverity, resolveSize, warnDeprecatedInput } from './tum-ui-vocabulary';

describe('tum-ui vocabulary', () => {
    let warn: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        resetDeprecationWarningsForTesting();
        warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('passes a canonical severity through untouched and silently', () => {
        expect(resolveSeverity<TumUiSeverity>('danger', 'tum-ui-tag')).toBe('danger');
        expect(warn).not.toHaveBeenCalled();
    });

    it('maps the two spellings that predate the union', () => {
        expect(resolveSeverity<TumUiSeverity>('warn', 'tum-ui-tag')).toBe('warning');
        expect(resolveSeverity<TumUiSeverity>('error', 'tum-ui-message')).toBe('danger');
    });

    it('maps every spelling of the middle size onto one name', () => {
        expect(resolveSize('medium', 'tum-ui-tag')).toBe('medium');
        expect(resolveSize('default', 'tum-ui-button')).toBe('medium');
        expect(resolveSize('normal', 'tum-ui-table')).toBe('medium');
    });

    it('falls back for an absent size, and lets a component choose its own default', () => {
        expect(resolveSize(undefined, 'tum-ui-item')).toBe('medium');
        expect(resolveSize(undefined, 'tum-ui-progress-spinner', 'large')).toBe('large');
        expect(warn).not.toHaveBeenCalled();
    });

    it('reports each deprecated spelling once, not once per instance', () => {
        // A table of fifty tags must not print fifty identical lines and bury everything else in the console.
        for (let index = 0; index < 50; index++) {
            resolveSeverity<TumUiSeverity>('warn', 'tum-ui-tag');
        }
        expect(warn).toHaveBeenCalledTimes(1);

        resolveSeverity<TumUiSeverity>('warn', 'tum-ui-message');
        expect(warn).toHaveBeenCalledTimes(2);
    });

    it('reports a deprecated input once, whatever value it carries', () => {
        warnDeprecatedInput('tum-ui-card', 'header', 'a projected title');
        warnDeprecatedInput('tum-ui-card', 'header', 'a projected title');
        expect(warn).toHaveBeenCalledTimes(1);
    });
});
