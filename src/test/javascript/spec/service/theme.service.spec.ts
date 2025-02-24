import { THEME_LOCAL_STORAGE_KEY, THEME_OVERRIDE_ID, Theme, ThemeService } from 'app/core/theme/theme.service';
import { MockLocalStorageService } from '../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';

describe('ThemeService', () => {
    let service: ThemeService;
    let localStorageService: LocalStorageService;

    let linkElement: HTMLElement;
    let documentGetElementMock: jest.SpyInstance;
    let headElement: HTMLElement;
    let documentGetElementsByTagNameMock: jest.SpyInstance;
    let newElement: HTMLLinkElement;
    let documentCreateElementMock: jest.SpyInstance;
    let storeSpy: jest.SpyInstance;
    let windowMatchMediaSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: ThemeService, useClass: ThemeService },
            ],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ThemeService);
                localStorageService = TestBed.inject(LocalStorageService);
                linkElement = {
                    remove: jest.fn(),
                } as any as HTMLElement;
                documentGetElementMock = jest.spyOn(document, 'getElementById').mockReturnValue(linkElement);

                headElement = {
                    getElementsByTagName: jest.fn().mockReturnValue([{}, {}]),
                    insertBefore: jest.fn(),
                } as any as HTMLElement;
                documentGetElementsByTagNameMock = jest.spyOn(document, 'getElementsByTagName').mockReturnValue([headElement] as unknown as HTMLCollectionOf<HTMLElement>);

                newElement = {} as HTMLLinkElement;
                documentCreateElementMock = jest.spyOn(document, 'createElement').mockReturnValue(newElement);

                storeSpy = jest.spyOn(localStorageService, 'store');

                windowMatchMediaSpy = jest.spyOn(window, 'matchMedia').mockImplementation((query) => {
                    if (query === '(prefers-color-scheme)') {
                        return { media: '(prefers-color-scheme)', addEventListener: jest.fn() } as any as MediaQueryList;
                    }
                    if (query === '(prefers-color-scheme: dark)') {
                        return { media: '(prefers-color-scheme: dark)', matches: false, addEventListener: jest.fn() } as any as MediaQueryList;
                    }
                    throw new Error('Should not happen');
                });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        documentGetElementMock.mockRestore();
        windowMatchMediaSpy.mockRestore();
    });

    it('applies theme changes correctly', () => {
        TestBed.flushEffects();
        expect(documentGetElementMock).toHaveBeenCalledOnce();

        service.applyThemePreference(Theme.DARK);
        TestBed.flushEffects();

        expect(documentGetElementMock).toHaveBeenCalledTimes(2);
        expect(documentGetElementMock).toHaveBeenCalledWith(THEME_OVERRIDE_ID);

        expect(documentGetElementsByTagNameMock).toHaveBeenCalledOnce();
        expect(documentGetElementsByTagNameMock).toHaveBeenCalledWith('head');
        expect(documentCreateElementMock).toHaveBeenCalledOnce();
        expect(documentCreateElementMock).toHaveBeenCalledWith('link');

        expect(newElement.id).toBe(THEME_OVERRIDE_ID);
        expect(newElement.rel).toBe('stylesheet');
        expect(newElement.href).toStartWith('theme-dark.css');
        expect(newElement.onload).toEqual(expect.any(Function));
        expect(headElement.getElementsByTagName).toHaveBeenCalledOnce();
        expect(headElement.getElementsByTagName).toHaveBeenCalledWith('link');
        expect(headElement.insertBefore).toHaveBeenCalledOnce();
        expect(headElement.insertBefore).toHaveBeenCalledWith(newElement, undefined);

        expect(service.currentTheme()).toBe(Theme.DARK);
        expect(storeSpy).toHaveBeenCalledWith(THEME_LOCAL_STORAGE_KEY, 'DARK');

        expect(linkElement.remove).toHaveBeenCalledOnce();
        expect(service.currentTheme()).toBe(Theme.DARK);

        service.applyThemePreference(Theme.LIGHT);
        TestBed.flushEffects();

        expect(documentGetElementMock).toHaveBeenCalledTimes(3);
        expect(documentGetElementMock).toHaveBeenNthCalledWith(3, THEME_OVERRIDE_ID);
        expect(linkElement.remove).toHaveBeenCalledTimes(2);
        expect(service.currentTheme()).toBe(Theme.LIGHT);
    });

    it('restores stored theme correctly', () => {
        const retrieveSpy = jest.spyOn(localStorageService, 'retrieve').mockReturnValue('LIGHT');

        service.initialize();
        TestBed.flushEffects();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(service.currentTheme()).toBe(Theme.LIGHT);
    });

    it('applies dark OS preferences', () => {
        const retrieveSpy = jest.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);
        windowMatchMediaSpy.mockRestore();
        windowMatchMediaSpy = jest.spyOn(window, 'matchMedia').mockImplementation((query) => {
            if (query === '(prefers-color-scheme)') {
                return { media: '(prefers-color-scheme)', addEventListener: jest.fn() } as any as MediaQueryList;
            }
            if (query === '(prefers-color-scheme: dark)') {
                return { media: '(prefers-color-scheme: dark)', matches: true, addEventListener: jest.fn() } as any as MediaQueryList;
            }
            throw new Error('Shouldnt happen');
        });

        service.initialize();
        TestBed.flushEffects();
        // @ts-ignore
        newElement?.onload();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(windowMatchMediaSpy).toHaveBeenCalledOnce();
        expect(windowMatchMediaSpy).toHaveBeenNthCalledWith(1, '(prefers-color-scheme: dark)');
        expect(service.currentTheme()).toBe(Theme.DARK);
    });

    it('applies light OS preferences', () => {
        const retrieveSpy = jest.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);

        service.initialize();
        TestBed.flushEffects();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(windowMatchMediaSpy).toHaveBeenCalledOnce();
        expect(windowMatchMediaSpy).toHaveBeenNthCalledWith(1, '(prefers-color-scheme: dark)');
        expect(service.currentTheme()).toBe(Theme.LIGHT);
    });

    it('does print correctly', fakeAsync(() => {
        const initialDisplayClass = 'someDisplayClass';

        const winSpy = jest.spyOn(window, 'print').mockImplementation();
        const returnedElement = { rel: 'stylesheet', style: { display: initialDisplayClass }, remove: jest.fn() };
        const docSpy = jest.spyOn(document, 'getElementById').mockReturnValue(returnedElement as any as HTMLElement);

        service.print();
        TestBed.flushEffects();

        expect(docSpy).toHaveBeenCalledTimes(2);
        expect(docSpy).toHaveBeenCalledWith(THEME_OVERRIDE_ID);
        expect(returnedElement.rel).toBe('none-tmp');
        tick(250);
        expect(docSpy).toHaveBeenCalledWith('notification-sidebar');
        expect(docSpy).toHaveBeenCalledTimes(4); // 1x for theme override, 2x for notification sidebar (changing style to display: none and back to initial value)
        expect(winSpy).toHaveBeenCalledOnce();
        tick(250);
        expect(returnedElement.rel).toBe('stylesheet');
        expect(returnedElement.style.display).toBe(initialDisplayClass);
    }));
});
