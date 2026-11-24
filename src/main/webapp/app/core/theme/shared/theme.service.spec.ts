import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { THEME_LOCAL_STORAGE_KEY, Theme, ThemeService } from 'app/core/theme/shared/theme.service';
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
    });

    afterEach(() => {
        vi.restoreAllMocks();
        document.documentElement.removeAttribute('data-theme');
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
