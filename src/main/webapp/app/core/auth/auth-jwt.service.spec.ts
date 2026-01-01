import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AuthServerProvider, Credentials } from 'app/core/auth/auth-jwt.service';
import { provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';

describe('AuthServerProvider', () => {
    let service: AuthServerProvider;
    let localStorageService: LocalStorageService;
    let sessionStorageService: SessionStorageService;
    let httpMock: HttpTestingController;
    let sessionStorageClearSpy: jest.SpyInstance;
    let localStorageClearSpy: jest.SpyInstance;

    const tokenKey = 'authenticationToken';
    const storedToken = 'test token with some length';
    const respPayload = { id_token: storedToken };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();
        service = TestBed.inject(AuthServerProvider);
        localStorageService = TestBed.inject(LocalStorageService);
        sessionStorageService = TestBed.inject(SessionStorageService);
        httpMock = TestBed.inject(HttpTestingController);

        sessionStorageClearSpy = jest.spyOn(sessionStorageService, 'clear');
        localStorageClearSpy = jest.spyOn(localStorageService, 'clear');
    });

    afterEach(() => jest.restoreAllMocks());

    describe('test login', () => {
        let credentials: Credentials;
        it('should login with credentials if they should be remembered', fakeAsync(() => {
            credentials = new Credentials('Test user', 'password1234', true);

            service.login(credentials).subscribe();

            const req = httpMock.expectOne({ method: 'POST', url: `api/core/public/authenticate` });
            req.flush(respPayload);
            tick();
        }));

        it('should login with credentials if they should not be remembered', fakeAsync(() => {
            credentials = new Credentials('Test user', 'password1234', false);

            service.login(credentials).subscribe();

            const req = httpMock.expectOne({ method: 'POST', url: `api/core/public/authenticate` });
            req.flush(respPayload);
            tick();
        }));
    });

    it('should logout', fakeAsync(() => {
        service.logout().subscribe();

        const req = httpMock.expectOne({ method: 'POST', url: `api/core/public/logout` });
        req.flush(respPayload);
        tick();
    }));

    describe('test login with SAML2', () => {
        it('should login with SAML2 if login should be remembered', fakeAsync(() => {
            service.loginSAML2(true).subscribe();

            const req = httpMock.expectOne({ method: 'POST', url: `api/core/public/saml2` });
            req.flush(respPayload);
            tick();
        }));

        it('should login with SAML2 if login should not be remembered', fakeAsync(() => {
            service.loginSAML2(false).subscribe();

            const req = httpMock.expectOne({ method: 'POST', url: `api/core/public/saml2` });
            req.flush(respPayload);
            tick();
        }));
    });

    it('should clear caches', fakeAsync(() => {
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
