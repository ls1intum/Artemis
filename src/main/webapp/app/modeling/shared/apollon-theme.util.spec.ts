import { applyArtemisApollonThemeToDocument, artemisApollonTheme } from 'app/modeling/shared/apollon-theme.util';

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

describe('applyArtemisApollonThemeToDocument', () => {
    // Assert on the setProperty calls rather than reading values back: the jsdom test
    // DOM does not round-trip custom-property `var()` values through getPropertyValue,
    // whereas real browsers do (verified against the live editor).
    it('stamps every mapped token as an inline custom property on the document root', () => {
        const setProperty = vi.spyOn(document.documentElement.style, 'setProperty');

        applyArtemisApollonThemeToDocument();

        const theme = artemisApollonTheme();
        expect(setProperty).toHaveBeenCalledTimes(Object.keys(theme).length);
        for (const [token, value] of Object.entries(theme)) {
            expect(setProperty).toHaveBeenCalledWith(token, value);
        }
        setProperty.mockRestore();
    });

    it("stamps onto <html> — where Apollon's :root-scoped chrome ramp resolves", () => {
        // The chrome derivation reads these two base tokens at :root; stamping them on
        // the mount alone would leave the chrome on Apollon's defaults.
        const setProperty = vi.spyOn(document.documentElement.style, 'setProperty');

        applyArtemisApollonThemeToDocument();

        expect(setProperty).toHaveBeenCalledWith('--apollon-background', 'var(--p-content-background)');
        expect(setProperty).toHaveBeenCalledWith('--apollon-primary-contrast', 'var(--p-text-color)');
        setProperty.mockRestore();
    });

    it('targets the documentElement of the passed document', () => {
        const setProperty = vi.fn();
        const fakeDoc = { documentElement: { style: { setProperty } } } as unknown as Document;

        applyArtemisApollonThemeToDocument(fakeDoc);

        expect(setProperty).toHaveBeenCalledWith('--apollon-primary', 'var(--p-primary-color)');
        expect(setProperty).toHaveBeenCalledTimes(Object.keys(artemisApollonTheme()).length);
    });
});
