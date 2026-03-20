import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthServerProvider, Credentials } from 'app/core/auth/auth-jwt.service';
import { provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { firstValueFrom } from 'rxjs';

describe('AuthServerProvider', () => {
    setupTestBed({ zoneless: true });

    let service: AuthServerProvider;
    let localStorageService: LocalStorageService;
    let sessionStorageService: SessionStorageService;
    let httpMock: HttpTestingController;
    let sessionStorageClearSpy: ReturnType<typeof vi.spyOn>;
    let localStorageClearSpy: ReturnType<typeof vi.spyOn>;

    const tokenKey = 'authenticationToken';
    const storedToken = 'test token with some length';
    const respPayload = { id_token: storedToken };

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        await TestBed.compileComponents();
        service = TestBed.inject(AuthServerProvider);
        localStorageService = TestBed.inject(LocalStorageService);
        sessionStorageService = TestBed.inject(SessionStorageService);
        httpMock = TestBed.inject(HttpTestingController);

        sessionStorageClearSpy = vi.spyOn(sessionStorageService, 'clear');
        localStorageClearSpy = vi.spyOn(localStorageService, 'clear');
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('test login', () => {
        let credentials: Credentials;

        it('should login with credentials if they should be remembered', async () => {
            credentials = new Credentials('Test user', 'password1234', true);

            const loginPromise = firstValueFrom(service.login(credentials));

            const req = httpMock.expectOne({ method: 'POST', url: `api/core/public/authenticate` });
            req.flush(respPayload);

            await loginPromise;
        });

        it('should login with credentials if they should not be remembered', async () => {
            credentials = new Credentials('Test user', 'password1234', false);

            const loginPromise = firstValueFrom(service.login(credentials));

            const req = httpMock.expectOne({ method: 'POST', url: `api/core/public/authenticate` });
            req.flush(respPayload);

            await loginPromise;
        });
    });

    it('should logout', async () => {
        const logoutPromise = firstValueFrom(service.logout());

        const req = httpMock.expectOne({ method: 'POST', url: `api/core/public/logout` });
        req.flush(respPayload);

        await logoutPromise;
    });

    describe('test login with SAML2', () => {
        it('should login with SAML2 if login should be remembered', async () => {
            const loginPromise = firstValueFrom(service.loginSAML2(true));

            const req = httpMock.expectOne({ method: 'POST', url: `api/core/public/saml2` });
            req.flush(respPayload);

            await loginPromise;
        });

        it('should login with SAML2 if login should not be remembered', async () => {
            const loginPromise = firstValueFrom(service.loginSAML2(false));

            const req = httpMock.expectOne({ method: 'POST', url: `api/core/public/saml2` });
            req.flush(respPayload);

            await loginPromise;
        });
    });

    it('should clear caches', async () => {
        localStorageService.store(tokenKey, storedToken);
        sessionStorageService.store(tokenKey, storedToken);

        const resp = await firstValueFrom(service.clearCaches());

        expect(resp).toBeUndefined();
        expect(sessionStorageClearSpy).toHaveBeenCalledOnce();
        expect(sessionStorageClearSpy).toHaveBeenCalledWith();
        expect(localStorageClearSpy).toHaveBeenCalledOnce();
        expect(localStorageClearSpy).toHaveBeenCalledWith();
    });
});
