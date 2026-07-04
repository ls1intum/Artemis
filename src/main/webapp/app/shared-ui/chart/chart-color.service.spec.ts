import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ChartColorService, resolveCssColor } from 'app/shared-ui/chart/chart-color.service';
import { ThemeService } from 'app/core/theme/shared/theme.service';

describe('ChartColorService', () => {
    setupTestBed({ zoneless: true });

    let service: ChartColorService;
    let getComputedStyleSpy: ReturnType<typeof vi.spyOn>;
    let cssVariables: Record<string, string>;

    beforeEach(() => {
        cssVariables = {};
        getComputedStyleSpy = vi.spyOn(window, 'getComputedStyle').mockReturnValue({
            getPropertyValue: (name: string) => cssVariables[name] ?? '',
        } as unknown as CSSStyleDeclaration);
        service = TestBed.inject(ChartColorService);
    });

    afterEach(() => {
        getComputedStyleSpy.mockRestore();
    });

    describe('resolveCssColor', () => {
        it('passes plain colors through unchanged', () => {
            expect(resolveCssColor('#5bc0de')).toBe('#5bc0de');
            expect(resolveCssColor('rgba(255, 255, 255, 0)')).toBe('rgba(255, 255, 255, 0)');
        });

        it('resolves var() references via computed style', () => {
            cssVariables['--graph-blue'] = 'rgba(93, 138, 201, 1)';

            expect(resolveCssColor('var(--graph-blue)')).toBe('rgba(93, 138, 201, 1)');
        });

        it('falls back when the variable is not defined', () => {
            expect(resolveCssColor('var(--graph-missing)', '#abcdef')).toBe('#abcdef');
        });
    });

    describe('resolvedColors', () => {
        it('re-resolves when the applied theme changes', () => {
            const themeService = TestBed.inject(ThemeService);
            cssVariables['--graph-green'] = 'green';
            const colors = service.resolvedColors(() => ['var(--graph-green)', '#123456']);

            expect(colors()).toEqual(['green', '#123456']);

            cssVariables['--graph-green'] = 'darkgreen';
            // without a theme change the computed stays cached
            expect(colors()).toEqual(['green', '#123456']);

            // simulate an applied theme change (private signal bump via the dark stylesheet onload path)
            (themeService as any)._appliedThemeRevision.update((revision: number) => revision + 1);
            expect(colors()).toEqual(['darkgreen', '#123456']);
        });
    });
});
