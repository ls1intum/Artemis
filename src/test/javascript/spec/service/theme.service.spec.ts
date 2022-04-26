import { Theme, THEME_LOCAL_STORAGE_KEY, THEME_OVERRIDE_ID, ThemeService } from 'app/core/theme/theme.service';
import { MockLocalStorageService } from '../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { TestBed } from '@angular/core/testing';
import { MockThemeService } from '../helpers/mocks/service/mock-theme.service';

describe('ThemeService', () => {
    let service: ThemeService;
    let localStorageService: LocalStorageService;

    let linkElement: HTMLElement;
    let documentGetElementMock: jest.SpyInstance;
    let headElement: HTMLElement;
    let documentgetElementsByTagNameMock: jest.SpyInstance;
    let newElement: HTMLLinkElement;
    let documentCreateElementMock: jest.SpyInstance;
    let storeSpy: jest.SpyInstance;

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
                documentgetElementsByTagNameMock = jest.spyOn(document, 'getElementsByTagName').mockReturnValue([headElement] as unknown as HTMLCollectionOf<HTMLElement>);

                newElement = {} as HTMLLinkElement;
                documentCreateElementMock = jest.spyOn(document, 'createElement').mockReturnValue(newElement);

                storeSpy = jest.spyOn(localStorageService, 'store');
            });
    });

    afterEach(() => jest.restoreAllMocks());

    it('doesnt change anything if current theme is reapplied', () => {
        service.applyTheme(Theme.LIGHT);
        expect(storeSpy).toHaveBeenCalledOnce();
        expect(storeSpy).toHaveBeenCalledWith(THEME_LOCAL_STORAGE_KEY, 'LIGHT');
    });

    it('applies theme changes correctly', () => {
        service.applyTheme(Theme.DARK);

        expect(documentGetElementMock).toHaveBeenCalledOnce();
        expect(documentGetElementMock).toHaveBeenCalledWith(THEME_OVERRIDE_ID);

        expect(documentgetElementsByTagNameMock).toHaveBeenCalledOnce();
        expect(documentgetElementsByTagNameMock).toHaveBeenCalledWith('head');
        expect(documentCreateElementMock).toHaveBeenCalledOnce();
        expect(documentCreateElementMock).toHaveBeenCalledWith('link');

        expect(newElement.id).toBe(THEME_OVERRIDE_ID);
        expect(newElement.rel).toBe('stylesheet');
        expect(newElement.href).toBe('theme-dark.css');
        expect(newElement.onload).toEqual(expect.any(Function));
        expect(headElement.getElementsByTagName).toHaveBeenCalledOnce();
        expect(headElement.getElementsByTagName).toHaveBeenCalledWith('link');
        expect(headElement.insertBefore).toHaveBeenCalledOnce();
        expect(headElement.insertBefore).toHaveBeenCalledWith(newElement, undefined);

        expect(service.getCurrentTheme()).toBe(Theme.LIGHT);
        expect(storeSpy).toHaveBeenCalledWith(THEME_LOCAL_STORAGE_KEY, 'DARK');

        // @ts-ignore
        newElement.onload();

        expect(linkElement.remove).toHaveBeenCalledOnce();
        expect(service.getCurrentTheme()).toBe(Theme.DARK);

        service.applyTheme(Theme.LIGHT);

        expect(documentGetElementMock).toHaveBeenCalledTimes(2);
        expect(documentGetElementMock).toHaveBeenNthCalledWith(2, THEME_OVERRIDE_ID);
        expect(linkElement.remove).toHaveBeenCalledTimes(2);
        expect(service.getCurrentTheme()).toBe(Theme.LIGHT);
    });

    it('restores stored theme correctly', () => {
        const retrieveSpy = jest.spyOn(localStorageService, 'retrieve').mockReturnValue('LIGHT');
        const applySpy = jest.spyOn(service, 'applyTheme').mockImplementation(jest.fn());
        const windowMatchMediaSpy = jest.spyOn(window, 'matchMedia');

        service.restoreTheme();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(applySpy).toHaveBeenCalledWith(Theme.LIGHT);
        expect(windowMatchMediaSpy).not.toHaveBeenCalled();
    });

    it('applies dark OS preferences', () => {
        const retrieveSpy = jest.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);
        const applySpy = jest.spyOn(service as any as MockThemeService, 'applyThemeInternal').mockImplementation(jest.fn());
        const windowMatchMediaSpy = jest.spyOn(window, 'matchMedia').mockImplementation((query) => {
            if (query === '(prefers-color-scheme)') {
                return { media: 'all' } as MediaQueryList;
            }
            if (query === '(prefers-color-scheme: dark)') {
                return { matches: true } as MediaQueryList;
            }
            throw new Error('Shouldnt happen');
        });

        service.restoreTheme();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(windowMatchMediaSpy).toHaveBeenCalledTimes(2);
        expect(windowMatchMediaSpy).toHaveBeenNthCalledWith(1, '(prefers-color-scheme)');
        expect(windowMatchMediaSpy).toHaveBeenNthCalledWith(2, '(prefers-color-scheme: dark)');
        expect(applySpy).toHaveBeenCalledOnce();
        expect(applySpy).toHaveBeenCalledWith(Theme.DARK, true);

        windowMatchMediaSpy.mockRestore();
    });

    it('applies light OS preferences', () => {
        const retrieveSpy = jest.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);
        const applySpy = jest.spyOn(service as any as MockThemeService, 'applyThemeInternal').mockImplementation(jest.fn());
        const windowMatchMediaSpy = jest.spyOn(window, 'matchMedia').mockImplementation((query) => {
            if (query === '(prefers-color-scheme)') {
                return { media: 'all' } as MediaQueryList;
            }
            if (query === '(prefers-color-scheme: dark)') {
                return { matches: false } as MediaQueryList;
            }
            throw new Error('Shouldnt happen');
        });

        service.restoreTheme();

        expect(retrieveSpy).toHaveBeenCalledOnce();
        expect(windowMatchMediaSpy).toHaveBeenCalledTimes(2);
        expect(windowMatchMediaSpy).toHaveBeenNthCalledWith(1, '(prefers-color-scheme)');
        expect(windowMatchMediaSpy).toHaveBeenNthCalledWith(2, '(prefers-color-scheme: dark)');
        expect(applySpy).not.toHaveBeenCalled();
        expect(service.isAutoDetected).toBe(true);

        windowMatchMediaSpy.mockRestore();
    });
});
