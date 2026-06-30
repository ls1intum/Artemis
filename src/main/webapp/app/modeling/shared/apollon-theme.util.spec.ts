import { artemisApollonTheme } from 'app/modeling/shared/apollon-theme.util';

describe('artemisApollonTheme', () => {
    const theme = artemisApollonTheme();

    it('maps Apollon brand/surface tokens onto Artemis PrimeNG design tokens', () => {
        expect(theme['--apollon-primary']).toBe('var(--p-primary-color)');
        expect(theme['--apollon-primary-contrast']).toBe('var(--p-text-color)');
        expect(theme['--apollon-background']).toBe('var(--p-content-background)');
        expect(theme['--apollon-border']).toBe('var(--p-content-border-color)');
    });

    it('uses live var(--p-*) references for every token so the editor re-colours on theme toggle', () => {
        const entries = Object.entries(theme);
        expect(entries.length).toBeGreaterThan(0);
        for (const [token, value] of entries) {
            expect(token).toMatch(/^--apollon-/);
            // No literal colors — only references to Artemis's theme-aware PrimeNG tokens.
            expect(value).toMatch(/^var\(--p-[a-z-]+\)$/);
        }
    });
});
