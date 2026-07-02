import { applyArtemisApollonThemeToDocument, artemisApollonTheme } from 'app/modeling/shared/apollon-theme.util';

describe('artemisApollonTheme', () => {
    it('maps the chrome-ramp base tokens and brand accent to the right PrimeNG tokens', () => {
        const theme = artemisApollonTheme();
        // --apollon-background + --apollon-primary-contrast feed Apollon's :root color-mix() chrome
        // ramp — a wrong mapping here silently breaks dark-mode cohesion, so pin them to literals.
        expect(theme['--apollon-background']).toBe('var(--p-content-background)');
        expect(theme['--apollon-primary-contrast']).toBe('var(--p-text-color)');
        expect(theme['--apollon-primary']).toBe('var(--p-primary-color)');
    });

    it('uses live var(--p-*) references for every token so the editor re-colours on theme toggle (no literal colours)', () => {
        const entries = Object.entries(artemisApollonTheme());
        expect(entries.length).toBeGreaterThan(0);
        for (const [token, value] of entries) {
            expect(token).toMatch(/^--apollon-/);
            expect(value).toMatch(/^var\(--p-[a-z-]+\)$/);
        }
    });
});

describe('applyArtemisApollonThemeToDocument', () => {
    // Spy on setProperty rather than reading values back: jsdom does not round-trip
    // custom-property var() values through getPropertyValue (real browsers do).
    it("stamps the theme onto the document root, where Apollon's :root chrome ramp resolves", () => {
        const setProperty = vi.spyOn(document.documentElement.style, 'setProperty');

        applyArtemisApollonThemeToDocument();

        // The two chrome-ramp base tokens must land on <html> for Apollon's :root ramp to follow.
        expect(setProperty).toHaveBeenCalledWith('--apollon-background', 'var(--p-content-background)');
        expect(setProperty).toHaveBeenCalledWith('--apollon-primary-contrast', 'var(--p-text-color)');
        setProperty.mockRestore();
    });
});
