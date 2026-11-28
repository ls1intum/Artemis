import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { THEME_LOCAL_STORAGE_KEY, Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { readFileSync } from 'node:fs';
import { runInThisContext } from 'node:vm';
import { TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';

describe('ThemeService', () => {
    let service: ThemeService;
    let localStorageService: LocalStorageService;

    let storeSpy: ReturnType<typeof vi.spyOn>;
    let windowMatchMediaSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [{ provide: ThemeService, useClass: ThemeService }],
        });
        await TestBed.compileComponents();

        service = TestBed.inject(ThemeService);
        localStorageService = TestBed.inject(LocalStorageService);
        storeSpy = vi.spyOn(localStorageService, 'store');

        windowMatchMediaSpy = vi.spyOn(window, 'matchMedia').mockImplementation((query: string) => {
            if (query === '(prefers-color-scheme)') {
                return { media: '(prefers-color-scheme)', addEventListener: vi.fn() } as unknown as MediaQueryList;
            }
            if (query === '(prefers-color-scheme: dark)') {
                return { media: '(prefers-color-scheme: dark)', matches: false, addEventListener: vi.fn() } as unknown as MediaQueryList;
            }
            throw new Error('Should not happen');
        });
        // the service has read the colour scheme of the system when it was created above
        windowMatchMediaSpy.mockClear();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        document.documentElement.removeAttribute('data-theme');
        localStorage.removeItem(THEME_LOCAL_STORAGE_KEY);
        windowMatchMediaSpy.mockRestore();
    });

    it('applies theme changes correctly', () => {
        TestBed.tick();
        expect(document.documentElement.getAttribute('data-theme')).toBe('light');

        service.applyThemePreference(Theme.DARK);
        TestBed.tick();

        expect(service.currentTheme()).toBe(Theme.DARK);
        expect(storeSpy).toHaveBeenCalledWith(THEME_LOCAL_STORAGE_KEY, 'DARK');
        expect(document.documentElement.getAttribute('data-theme')).toBe('dark');

        service.applyThemePreference(Theme.LIGHT);
        TestBed.tick();

        expect(service.currentTheme()).toBe(Theme.LIGHT);
        expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    });

    it('restores stored theme correctly', () => {
        const retrieveSpy = vi.spyOn(localStorageService, 'retrieve').mockReturnValue('LIGHT');

        service.initialize();
        TestBed.tick();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(service.currentTheme()).toBe(Theme.LIGHT);
    });

    it('follows a theme change of another tab', () => {
        service.initialize();
        TestBed.tick();
        expect(service.currentTheme()).toBe(Theme.LIGHT);

        // The other tab has stored the preference; this tab only receives the storage event.
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue('DARK');
        window.dispatchEvent(new StorageEvent('storage', { key: THEME_LOCAL_STORAGE_KEY }));
        TestBed.tick();

        expect(service.currentTheme()).toBe(Theme.DARK);
        expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    });

    it('keeps the theme that the script of index.html has set before the application started', () => {
        // The user has chosen the dark theme; the script has set the attribute and the service is created afterwards.
        localStorage.setItem(THEME_LOCAL_STORAGE_KEY, JSON.stringify('DARK'));
        document.documentElement.setAttribute('data-theme', 'dark');
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({});

        const freshService = TestBed.inject(ThemeService);
        TestBed.tick();

        expect(freshService.currentTheme()).toBe(Theme.DARK);
        expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    });

    it('reads the key and the values in index.html that the service stores', () => {
        const html = readFileSync('src/main/webapp/index.html', 'utf8');

        expect(html).toContain(`localStorage.getItem('${THEME_LOCAL_STORAGE_KEY}')`);
        for (const theme of Theme.all) {
            expect(html).toContain(`stored === '${theme.identifier}') theme = '${theme.attributeValue}'`);
        }
    });

    it('starts with the dark theme of the system when the user has made no choice', () => {
        windowMatchMediaSpy.mockImplementation(() => ({ matches: true, media: '(prefers-color-scheme: dark)', addEventListener: vi.fn() }) as unknown as MediaQueryList);
        document.documentElement.setAttribute('data-theme', 'dark');
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({});

        const freshService = TestBed.inject(ThemeService);
        TestBed.tick();

        expect(freshService.currentTheme()).toBe(Theme.DARK);
        expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    });

    it('follows the system when the stored preference cannot be read', () => {
        localStorage.setItem(THEME_LOCAL_STORAGE_KEY, '{not json');
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({});

        expect(() => TestBed.inject(ThemeService)).not.toThrow();
        expect(TestBed.inject(ThemeService).currentTheme()).toBe(Theme.LIGHT);
    });

    describe('the script of index.html', () => {
        /** The source of the script that sets the theme before the application starts. */
        function bootScript(): string {
            const html = readFileSync('src/main/webapp/index.html', 'utf8');
            const script = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((match) => match[1]).find((source) => source.includes(THEME_LOCAL_STORAGE_KEY));
            if (!script) {
                throw new Error('index.html has no script that reads the theme preference');
            }
            return script;
        }

        afterEach(() => {
            localStorage.removeItem(THEME_LOCAL_STORAGE_KEY);
            vi.unstubAllGlobals();
        });

        it.each([
            { stored: JSON.stringify('DARK'), systemDark: false, expected: 'dark' },
            { stored: JSON.stringify('LIGHT'), systemDark: true, expected: 'light' },
            { stored: undefined, systemDark: true, expected: 'dark' },
            { stored: undefined, systemDark: false, expected: 'light' },
            { stored: '{not json', systemDark: true, expected: 'dark' },
            { stored: JSON.stringify('UNKNOWN'), systemDark: false, expected: 'light' },
        ])('sets $expected for the stored value $stored and a dark system scheme of $systemDark', ({ stored, systemDark, expected }) => {
            if (stored === undefined) {
                localStorage.removeItem(THEME_LOCAL_STORAGE_KEY);
            } else {
                localStorage.setItem(THEME_LOCAL_STORAGE_KEY, stored);
            }
            windowMatchMediaSpy.mockImplementation(() => ({ matches: systemDark }) as MediaQueryList);
            document.documentElement.removeAttribute('data-theme');

            runInThisContext(bootScript());

            expect(document.documentElement.getAttribute('data-theme')).toBe(expected);
        });

        it('falls back to the light theme where the browser has no matchMedia', () => {
            localStorage.removeItem(THEME_LOCAL_STORAGE_KEY);
            vi.stubGlobal('matchMedia', undefined);
            document.documentElement.removeAttribute('data-theme');

            runInThisContext(bootScript());

            expect(document.documentElement.getAttribute('data-theme')).toBe('light');
        });
    });

    it('applies dark OS preferences', () => {
        const retrieveSpy = vi.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);
        windowMatchMediaSpy.mockRestore();
        windowMatchMediaSpy = vi.spyOn(window, 'matchMedia').mockImplementation((query: string) => {
            if (query === '(prefers-color-scheme)') {
                return { media: '(prefers-color-scheme)', addEventListener: vi.fn() } as unknown as MediaQueryList;
            }
            if (query === '(prefers-color-scheme: dark)') {
                return { media: '(prefers-color-scheme: dark)', matches: true, addEventListener: vi.fn() } as unknown as MediaQueryList;
            }
            throw new Error('Should not happen');
        });

        service.initialize();
        TestBed.tick();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(windowMatchMediaSpy).toHaveBeenCalledOnce();
        expect(windowMatchMediaSpy).toHaveBeenNthCalledWith(1, '(prefers-color-scheme: dark)');
        expect(service.currentTheme()).toBe(Theme.DARK);
    });

    it('applies light OS preferences', () => {
        const retrieveSpy = vi.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);

        service.initialize();
        TestBed.tick();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(windowMatchMediaSpy).toHaveBeenCalledOnce();
        expect(windowMatchMediaSpy).toHaveBeenNthCalledWith(1, '(prefers-color-scheme: dark)');
        expect(service.currentTheme()).toBe(Theme.LIGHT);
    });

    it('prints in the light theme and restores the theme afterwards', async () => {
        vi.useFakeTimers();
        const initialDisplayClass = 'someDisplayClass';
        service.applyThemePreference(Theme.DARK);
        TestBed.tick();

        let themeWhilePrinting: string | null = null;
        const winSpy = vi.spyOn(window, 'print').mockImplementation(() => {
            themeWhilePrinting = document.documentElement.getAttribute('data-theme');
        });
        const returnedElement = { style: { display: initialDisplayClass } };
        const docSpy = vi.spyOn(document, 'getElementById').mockReturnValue(returnedElement as unknown as HTMLElement);

        service.print();

        expect(document.documentElement.getAttribute('data-theme')).toBe('light');
        await vi.advanceTimersByTimeAsync(250);
        expect(docSpy).toHaveBeenCalledWith('notification-sidebar');
        expect(docSpy).toHaveBeenCalledTimes(2); // changing the style of the notification sidebar to display: none and back to the initial value
        expect(winSpy).toHaveBeenCalledOnce();
        expect(themeWhilePrinting).toBe('light');
        await vi.advanceTimersByTimeAsync(250);
        expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
        expect(returnedElement.style.display).toBe(initialDisplayClass);
        vi.useRealTimers();
    });
});
