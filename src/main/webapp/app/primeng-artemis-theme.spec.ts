import { Theme } from '@primeuix/themes';
import { AuraArtemis } from './primeng-artemis-theme';

describe('Artemis PrimeNG button palette', () => {
    const previousTheme = Theme.getTheme();
    let stylesheet: HTMLStyleElement;
    let rules: CSSStyleRule[];

    beforeAll(() => {
        Theme.setTheme({ preset: AuraArtemis, options: { prefix: 'p', darkModeSelector: '.theme-dark', cssLayer: false } });
        stylesheet = document.createElement('style');
        // Exercise PrimeNG's real token compiler and the browser CSS parser, rather than just
        // inspecting our preset object: incorrect nesting must not silently fall back to Aura.
        const css = Theme.getComponent('button', {}).css;
        if (!css) throw new Error('PrimeNG did not generate button CSS');
        stylesheet.textContent = css;
        document.head.append(stylesheet);
        rules = Array.from(stylesheet.sheet!.cssRules).filter((rule): rule is CSSStyleRule => 'selectorText' in rule);
    });

    afterAll(() => {
        stylesheet.remove();
        Theme.setTheme(previousTheme);
    });

    describe.each([':root,:host', '.theme-dark'])('compiled theme %s', (selector) => {
        it.each(['primary', 'secondary', 'success', 'info', 'warn', 'danger', 'contrast'])('keeps %s colors on the shared customizable palette', (severity) => {
            const style = rules.find((rule) => rule.selectorText === selector)?.style;
            expect(style).toBeDefined();
            const property = (name: string) => style!.getPropertyValue(`--p-button-${name}`).trim();
            for (const state of ['', '-hover', '-active']) {
                expect(property(`${severity}${state}-background`)).toBe(`var(--tumaet-ui-button-${severity}-background)`);
                expect(property(`${severity}${state}-color`)).toBe(`var(--tumaet-ui-button-${severity}-contrast)`);
            }
            for (const variant of ['outlined', 'text']) {
                expect(property(`${variant}-${severity}-color`)).toBe(`var(--tumaet-ui-button-${severity}-foreground)`);
                expect(property(`${variant}-${severity}-hover-background`)).toBe('var(--tumaet-ui-button-hover-overlay)');
                expect(property(`${variant}-${severity}-active-background`)).toBe('var(--tumaet-ui-button-active-overlay)');
            }
            const border = severity === 'secondary' ? 'var(--tumaet-ui-button-secondary-border)' : `var(--tumaet-ui-button-${severity}-background)`;
            expect(property(`outlined-${severity}-border-color`)).toBe(border);
            expect(property(`${severity}-focus-ring-color`)).toBe('var(--tumaet-ui-button-focus-color)');
        });
    });
});
