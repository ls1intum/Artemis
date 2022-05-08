import { Theme, THEME_LOCAL_STORAGE_KEY, THEME_OVERRIDE_ID, ThemeService } from 'app/core/theme/theme.service';
import { MockLocalStorageService } from '../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';

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
                documentgetElementsByTagNameMock = jest.spyOn(document, 'getElementsByTagName').mockReturnValue([headElement] as unknown as HTMLCollectionOf<HTMLElement>);

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
                    throw new Error('Shouldnt happen');
                });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        windowMatchMediaSpy.mockRestore();
    });

    it('applies theme changes correctly', () => {
        service.applyThemeExplicitly(Theme.DARK);

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

        service.applyThemeExplicitly(Theme.LIGHT);

        expect(documentGetElementMock).toHaveBeenCalledTimes(2);
        expect(documentGetElementMock).toHaveBeenNthCalledWith(2, THEME_OVERRIDE_ID);
        expect(linkElement.remove).toHaveBeenCalledTimes(2);
        expect(service.getCurrentTheme()).toBe(Theme.LIGHT);
    });

    it('restores stored theme correctly', () => {
        const retrieveSpy = jest.spyOn(localStorageService, 'retrieve').mockReturnValue('LIGHT');

        service.initialize();

        expect(retrieveSpy).toHaveBeenCalledTimes(2);
        expect(service.getCurrentTheme()).toBe(Theme.LIGHT);
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
        // @ts-ignore
        newElement?.onload();

        expect(retrieveSpy).toHaveBeenCalledTimes(2);
        expect(windowMatchMediaSpy).toHaveBeenCalledOnce();
        expect(windowMatchMediaSpy).toHaveBeenNthCalledWith(1, '(prefers-color-scheme: dark)');
        expect(service.getCurrentTheme()).toBe(Theme.DARK);
    });

    it('applies light OS preferences', () => {
        const retrieveSpy = jest.spyOn(localStorageService, 'retrieve').mockReturnValue(undefined);

        service.initialize();

        expect(retrieveSpy).toHaveBeenCalledTimes(2);
        expect(windowMatchMediaSpy).toHaveBeenCalledOnce();
        expect(windowMatchMediaSpy).toHaveBeenNthCalledWith(1, '(prefers-color-scheme: dark)');
        expect(service.getCurrentTheme()).toBe(Theme.LIGHT);
    });

    it('does print correctly', fakeAsync(() => {
        const winSpy = jest.spyOn(window, 'print').mockImplementation();
        const returnedElement = { rel: 'stylesheet' };
        const docSpy = jest.spyOn(document, 'getElementById').mockReturnValue(returnedElement as any as HTMLElement);

        service.print();

        expect(docSpy).toHaveBeenCalledOnce();
        expect(docSpy).toHaveBeenCalledWith(THEME_OVERRIDE_ID);
        expect(returnedElement.rel).toBe('none-tmp');
        tick(250);
        expect(winSpy).toHaveBeenCalledOnce();
        tick(250);
        expect(returnedElement.rel).toBe('stylesheet');
    }));
});
