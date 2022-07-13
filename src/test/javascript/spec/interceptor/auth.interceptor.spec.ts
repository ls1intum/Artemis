import { HttpRequest } from '@angular/common/http';
import { AuthInterceptor } from 'app/core/interceptor/auth.interceptor';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../helpers/mocks/service/mock-local-storage.service';

describe(`AuthInterceptor`, () => {
    let authInterceptor: AuthInterceptor;

    let localStorageMock: LocalStorageService;
    let sessionStorageMock: SessionStorageService;

    const token = 'my-token-123';

    beforeEach(() => {
        localStorageMock = new MockLocalStorageService() as any as LocalStorageService;
        sessionStorageMock = new MockLocalStorageService() as any as SessionStorageService;
        authInterceptor = new AuthInterceptor(localStorageMock, sessionStorageMock);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should add a token to a request if it is a request to artemis and token is present in local or session storage', () => {
        [localStorageMock, sessionStorageMock].forEach((service) => {
            const storageSpy = jest.spyOn(service, 'retrieve').mockReturnValue(token);
            const requestMock = new HttpRequest('GET', `${SERVER_API_URL}test`);
            const cloneSpy = jest.spyOn(requestMock, 'clone');
            const mockHandler = {
                handle: jest.fn(),
            };

            authInterceptor.intercept(requestMock, mockHandler);

            expect(storageSpy).toHaveBeenCalledWith('authenticationToken');
            expect(cloneSpy).toHaveBeenCalledOnce();
            expect(cloneSpy).toHaveBeenCalledWith({
                setHeaders: {
                    Authorization: 'Bearer ' + token,
                },
            });
            expect(mockHandler.handle).toHaveBeenCalledOnce();

            jest.restoreAllMocks();
        });
    });

    it('should not add token to a request if it is a request to artemis but token is unavailable', () => {
        const localStorageSpy = jest.spyOn(localStorageMock, 'retrieve');
        const sessionStorageSpy = jest.spyOn(localStorageMock, 'retrieve');

        const requestMock = new HttpRequest('GET', `${SERVER_API_URL}test`);
        const cloneSpy = jest.spyOn(requestMock, 'clone');
        const mockHandler = {
            handle: jest.fn(),
        };

        authInterceptor.intercept(requestMock, mockHandler);

        expect(localStorageSpy).toHaveBeenCalledWith('authenticationToken');
        expect(sessionStorageSpy).toHaveBeenCalledWith('authenticationToken');
        expect(cloneSpy).toHaveBeenCalledTimes(0);
        expect(mockHandler.handle).toHaveBeenCalledOnce();
    });

    it('should not add token to a request if it is not a request to artemis', () => {
        const localStorageSpy = jest.spyOn(localStorageMock, 'retrieve').mockReturnValue(token);

        const requestMock = new HttpRequest('GET', 'https://example.com/test');
        const cloneSpy = jest.spyOn(requestMock, 'clone');
        const mockHandler = {
            handle: jest.fn(),
        };

        authInterceptor.intercept(requestMock, mockHandler);

        expect(localStorageSpy).toHaveBeenCalledTimes(0);
        expect(cloneSpy).toHaveBeenCalledTimes(0);
        expect(mockHandler.handle).toHaveBeenCalledOnce();
    });
});
