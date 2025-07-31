import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AuthServerProvider, Credentials } from 'app/core/auth/auth-jwt.service';
import { provideHttpClient } from '@angular/common/http';

describe('AuthServerProvider', () => {
    let service: AuthServerProvider;
    let httpMock: HttpTestingController;

    const storedToken = 'test token with some length';
    const respPayload = { id_token: storedToken };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(AuthServerProvider);
                httpMock = TestBed.inject(HttpTestingController);
            });
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
});
