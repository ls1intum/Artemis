import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { THEME_LOCAL_STORAGE_KEY, THEME_OVERRIDE_ID, Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

describe('ThemeService', () => {
    setupTestBed({ zoneless: true });

    let service: ThemeService;
    let localStorageService: LocalStorageService;

    let linkElement: HTMLElement;
    let documentGetElementMock: ReturnType<typeof vi.spyOn>;
    let headElement: HTMLElement;
    let documentGetElementsByTagNameMock: ReturnType<typeof vi.spyOn>;
    let headElementGetElementsByTagNameMock: ReturnType<typeof vi.spyOn>;
    let newElement: HTMLLinkElement;
    let documentCreateElementMock: ReturnType<typeof vi.spyOn>;
    let storeSpy: ReturnType<typeof vi.spyOn>;
    let windowMatchMediaSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [{ provide: ThemeService, useClass: ThemeService }],
        });
        await TestBed.compileComponents();

        service = TestBed.inject(ThemeService);
        localStorageService = TestBed.inject(LocalStorageService);
        linkElement = {
            remove: vi.fn(),
        } as unknown as HTMLElement;
        documentGetElementMock = vi.spyOn(document, 'getElementById').mockReturnValue(linkElement);

        headElement = {
            getElementsByTagName: vi.fn().mockReturnValue([{}, {}]),
            insertBefore: vi.fn(),
        } as unknown as HTMLElement;
        documentGetElementsByTagNameMock = vi.spyOn(document, 'getElementsByTagName').mockReturnValue([headElement] as unknown as HTMLCollectionOf<HTMLElement>);
        headElementGetElementsByTagNameMock = vi.spyOn(headElement, 'getElementsByTagName');

        newElement = {} as HTMLLinkElement;
        documentCreateElementMock = vi.spyOn(document, 'createElement').mockReturnValue(newElement);

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
        documentGetElementMock.mockRestore();
        windowMatchMediaSpy.mockRestore();
    });

    it('applies theme changes correctly', () => {
        TestBed.tick();
        expect(documentGetElementMock).toHaveBeenCalledOnce();

        service.applyThemePreference(Theme.DARK);
        TestBed.tick();

        expect(documentGetElementMock).toHaveBeenCalledTimes(2);
        expect(documentGetElementMock).toHaveBeenCalledWith(THEME_OVERRIDE_ID);

        expect(documentGetElementsByTagNameMock).toHaveBeenCalledOnce();
        expect(documentGetElementsByTagNameMock).toHaveBeenCalledWith('head');
        expect(documentCreateElementMock).toHaveBeenCalledOnce();
        expect(documentCreateElementMock).toHaveBeenCalledWith('link');

        expect(newElement.id).toBe(THEME_OVERRIDE_ID);
        expect(newElement.rel).toBe('stylesheet');
        expect(newElement.href).toMatch(new RegExp(`^${String('theme-dark.css')}`));
        expect(newElement.onload).toEqual(expect.any(Function));
        expect(headElementGetElementsByTagNameMock).toHaveBeenCalledOnce();
        expect(headElementGetElementsByTagNameMock).toHaveBeenCalledWith('link');
        expect(headElement.insertBefore).toHaveBeenCalledOnce();
        expect(headElement.insertBefore).toHaveBeenCalledWith(newElement, undefined);

        expect(service.currentTheme()).toBe(Theme.DARK);
        expect(storeSpy).toHaveBeenCalledWith(THEME_LOCAL_STORAGE_KEY, 'DARK');

        expect(linkElement.remove).toHaveBeenCalledOnce();
        expect(service.currentTheme()).toBe(Theme.DARK);

        service.applyThemePreference(Theme.LIGHT);
        TestBed.tick();

        expect(documentGetElementMock).toHaveBeenCalledTimes(3);
        expect(documentGetElementMock).toHaveBeenNthCalledWith(3, THEME_OVERRIDE_ID);
        expect(linkElement.remove).toHaveBeenCalledTimes(2);
        expect(service.currentTheme()).toBe(Theme.LIGHT);
    });

    it('restores stored theme correctly', () => {
        const retrieveSpy = vi.spyOn(localStorageService, 'retrieve').mockReturnValue('LIGHT');

        service.initialize();
        TestBed.tick();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(service.currentTheme()).toBe(Theme.LIGHT);
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
        // @ts-ignore
        newElement?.onload();

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

    it('does print correctly', async () => {
        vi.useFakeTimers();
        const initialDisplayClass = 'someDisplayClass';

        const winSpy = vi.spyOn(window, 'print').mockImplementation(() => {});
        const returnedElement = { rel: 'stylesheet', style: { display: initialDisplayClass }, remove: vi.fn() };
        const docSpy = vi.spyOn(document, 'getElementById').mockReturnValue(returnedElement as unknown as HTMLElement);

        service.print();
        TestBed.tick();

        expect(docSpy).toHaveBeenCalledTimes(2);
        expect(docSpy).toHaveBeenCalledWith(THEME_OVERRIDE_ID);
        expect(returnedElement.rel).toBe('none-tmp');
        await vi.advanceTimersByTimeAsync(250);
        expect(docSpy).toHaveBeenCalledWith('notification-sidebar');
        expect(docSpy).toHaveBeenCalledTimes(4); // 1x for theme override, 2x for notification sidebar (changing style to display: none and back to initial value)
        expect(winSpy).toHaveBeenCalledOnce();
        await vi.advanceTimersByTimeAsync(250);
        expect(returnedElement.rel).toBe('stylesheet');
        expect(returnedElement.style.display).toBe(initialDisplayClass);
        vi.useRealTimers();
    });
});
