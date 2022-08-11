import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { AuthServerProvider, Credentials } from 'app/core/auth/auth-jwt.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockLocalStorageService } from '../helpers/mocks/service/mock-local-storage.service';

describe('AuthServerProvider', () => {
    let service: AuthServerProvider;
    let localStorageService: LocalStorageService;
    let sessionStorageService: SessionStorageService;
    let httpMock: HttpTestingController;
    let localStorageStoreSpy: jest.SpyInstance;
    let sessionStorageStoreSpy: jest.SpyInstance;
    let sessionStorageClearSpy: jest.SpyInstance;
    let localStorageClearSpy: jest.SpyInstance;

    const tokenKey = 'authenticationToken';
    const storedToken = 'test token with some length';
    const respPayload = { id_token: storedToken };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(AuthServerProvider);
                localStorageService = TestBed.inject(LocalStorageService);
                sessionStorageService = TestBed.inject(SessionStorageService);
                httpMock = TestBed.inject(HttpTestingController);

                localStorageStoreSpy = jest.spyOn(localStorageService, 'store');
                sessionStorageStoreSpy = jest.spyOn(sessionStorageService, 'store');
                sessionStorageClearSpy = jest.spyOn(sessionStorageService, 'clear');
                localStorageClearSpy = jest.spyOn(localStorageService, 'clear');
            });
    });

    afterEach(() => jest.restoreAllMocks());

    describe('test token retrieval', () => {
        it('should return the authentication token out of the local storage', () => {
            localStorageService.store(tokenKey, storedToken);
            const localStorageRetrieveSpy = jest.spyOn(localStorageService, 'retrieve');

            const returnedToken = service.getToken();

            expect(returnedToken).toEqual(storedToken);
            expect(localStorageRetrieveSpy).toHaveBeenCalledOnce();
            expect(localStorageRetrieveSpy).toHaveBeenCalledWith(tokenKey);
        });

        it('should return the authentication token out of the session storage if nothing is stored in the local storage', () => {
            sessionStorageService.store(tokenKey, storedToken);
            const retrieveMock = jest.spyOn(sessionStorageService, 'retrieve').mockReturnValue(storedToken);

            const returnedToken = service.getToken();

            expect(returnedToken).toEqual(storedToken);
            expect(retrieveMock).toHaveBeenCalledOnce();
            expect(retrieveMock).toHaveBeenCalledWith(tokenKey);
        });
    });

    describe('test login', () => {
        let credentials: Credentials;
        it('should login with credentials if they should be remembered', fakeAsync(() => {
            credentials = new Credentials('Test user', 'password1234', true);

            service.login(credentials).subscribe(() => {
                expect(localStorageStoreSpy).toHaveBeenCalledOnce();
                expect(localStorageStoreSpy).toHaveBeenCalledWith(tokenKey, storedToken);
            });

            const req = httpMock.expectOne({ method: 'POST', url: `api/authenticate` });
            req.flush(respPayload);
            tick();
        }));

        it('should login with credentials if they should not be remembered', fakeAsync(() => {
            credentials = new Credentials('Test user', 'password1234', false);

            service.login(credentials).subscribe(() => {
                expect(sessionStorageStoreSpy).toHaveBeenCalledOnce();
                expect(sessionStorageStoreSpy).toHaveBeenCalledWith(tokenKey, storedToken);
            });

            const req = httpMock.expectOne({ method: 'POST', url: `api/authenticate` });
            req.flush(respPayload);
            tick();
        }));
    });

    describe('test login with SAML2', () => {
        it('should login with SAML2 if login should be remembered', fakeAsync(() => {
            service.loginSAML2(true).subscribe(() => {
                expect(localStorageStoreSpy).toHaveBeenCalledOnce();
                expect(localStorageStoreSpy).toHaveBeenCalledWith(tokenKey, storedToken);
            });

            const req = httpMock.expectOne({ method: 'POST', url: `api/saml2` });
            req.flush(respPayload);
            tick();
        }));

        it('should login with SAML2 if login should not be remembered', fakeAsync(() => {
            service.loginSAML2(false).subscribe(() => {
                expect(sessionStorageStoreSpy).toHaveBeenCalledOnce();
                expect(sessionStorageStoreSpy).toHaveBeenCalledWith(tokenKey, storedToken);
            });

            const req = httpMock.expectOne({ method: 'POST', url: `api/saml2` });
            req.flush(respPayload);
            tick();
        }));
    });

    describe('test login with token', () => {
        it('should login with token if token is present', async () => {
            await expect(service.loginWithToken(storedToken, true)).resolves.toBe(storedToken);
            expect(localStorageStoreSpy).toHaveBeenCalledOnce();
            expect(localStorageStoreSpy).toHaveBeenCalledWith(tokenKey, storedToken);
        });

        it('should return error message promise if no token is present', async () => {
            await expect(service.loginWithToken('', true)).rejects.toBe('auth-jwt-service Promise reject');
            expect(localStorageStoreSpy).not.toHaveBeenCalled();
        });
    });

    describe('test authentication token removal', () => {
        it('should clear only the authentication token', fakeAsync(() => {
            localStorageService.store(tokenKey, storedToken);
            sessionStorageService.store(tokenKey, storedToken);

            service.removeAuthTokenFromCaches().subscribe((resp) => {
                expect(resp).toBeUndefined();
                expect(sessionStorageClearSpy).toHaveBeenCalledOnce();
                expect(sessionStorageClearSpy).toHaveBeenCalledWith(tokenKey);
                expect(localStorageClearSpy).toHaveBeenCalledOnce();
                expect(localStorageClearSpy).toHaveBeenCalledWith(tokenKey);
            });
            tick();
        }));

        it('should clear the whole storage', fakeAsync(() => {
            localStorageService.store(tokenKey, storedToken);
            sessionStorageService.store(tokenKey, storedToken);

            service.clearCaches().subscribe((resp) => {
                expect(resp).toBeUndefined();
                expect(sessionStorageClearSpy).toHaveBeenCalledOnce();
                expect(sessionStorageClearSpy).toHaveBeenCalledWith();
                expect(localStorageClearSpy).toHaveBeenCalledOnce();
                expect(localStorageClearSpy).toHaveBeenCalledWith();
            });
            tick();
        }));
    });
});
