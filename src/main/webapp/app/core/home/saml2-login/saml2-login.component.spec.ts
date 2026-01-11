import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Saml2LoginComponent } from './saml2-login.component';
import { LoginService } from 'app/core/login/login.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProvider } from 'ng-mocks';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ComponentRef } from '@angular/core';
import { Saml2Config } from './saml2.config';

describe('Saml2LoginComponent', () => {
    let component: Saml2LoginComponent;
    let componentRef: ComponentRef<Saml2LoginComponent>;
    let fixture: ComponentFixture<Saml2LoginComponent>;
    let loginService: LoginService;
    let eventManager: EventManager;
    let alertService: AlertService;

    const mockSaml2Config: Saml2Config = {
        identityProviderName: 'Test IDP',
        buttonLabel: 'Login with SAML2',
    };

    beforeEach(async () => {
        // Clear cookies before each test
        document.cookie = 'SAML2flow=; expires=Thu, 01 Jan 1970 00:00:00 UTC; SameSite=Lax;';

        await TestBed.configureTestingModule({
            imports: [Saml2LoginComponent],
            providers: [MockProvider(LoginService), MockProvider(EventManager), MockProvider(AlertService)],
        }).compileComponents();

        fixture = TestBed.createComponent(Saml2LoginComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        loginService = TestBed.inject(LoginService);
        eventManager = TestBed.inject(EventManager);
        alertService = TestBed.inject(AlertService);

        // Set required input
        componentRef.setInput('saml2Profile', mockSaml2Config);
    });

    afterEach(() => {
        // Clear cookies after each test
        document.cookie = 'SAML2flow=; expires=Thu, 01 Jan 1970 00:00:00 UTC; SameSite=Lax;';
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should have default input values', () => {
        expect(component.rememberMe()).toBeTrue();
        expect(component.acceptedTerms()).toBeFalse();
        expect(component.saml2Profile()).toEqual(mockSaml2Config);
    });

    it('should accept custom input values', () => {
        componentRef.setInput('rememberMe', false);
        componentRef.setInput('acceptedTerms', true);
        fixture.detectChanges();

        expect(component.rememberMe()).toBeFalse();
        expect(component.acceptedTerms()).toBeTrue();
    });

    describe('ngOnInit', () => {
        it('should not call loginSAML2 when SAML2flow cookie is not present', () => {
            const loginSpy = jest.spyOn(component, 'loginSAML2');
            fixture.detectChanges();

            expect(loginSpy).not.toHaveBeenCalled();
        });

        it('should call loginSAML2 and remove cookie when SAML2flow cookie is present', () => {
            document.cookie = 'SAML2flow=true; SameSite=Lax;';
            const loginSpy = jest.spyOn(component, 'loginSAML2').mockImplementation();

            fixture.detectChanges();

            expect(loginSpy).toHaveBeenCalledOnce();
            expect(document.cookie.indexOf('SAML2flow=')).toBe(-1);
        });
    });

    describe('loginSAML2', () => {
        it('should broadcast authenticationSuccess on successful login', fakeAsync(() => {
            jest.spyOn(loginService, 'loginSAML2').mockResolvedValue(undefined);
            const broadcastSpy = jest.spyOn(eventManager, 'broadcast');

            component.loginSAML2();
            tick();

            expect(loginService.loginSAML2).toHaveBeenCalledWith(true);
            expect(broadcastSpy).toHaveBeenCalledWith({
                name: 'authenticationSuccess',
                content: 'Sending Authentication Success',
            });
        }));

        it('should use rememberMe input value when calling loginService', fakeAsync(() => {
            componentRef.setInput('rememberMe', false);
            fixture.detectChanges();

            jest.spyOn(loginService, 'loginSAML2').mockResolvedValue(undefined);

            component.loginSAML2();
            tick();

            expect(loginService.loginSAML2).toHaveBeenCalledWith(false);
        }));

        it('should set cookie on 401 error', fakeAsync(() => {
            const error401 = new HttpErrorResponse({ status: 401 });
            jest.spyOn(loginService, 'loginSAML2').mockRejectedValue(error401);

            // Suppress JSDOM's "Not implemented: navigation" console.error
            const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation();

            component.loginSAML2();
            tick();

            expect(document.cookie).toContain('SAML2flow=true');

            consoleErrorSpy.mockRestore();
        }));

        it('should show warning with default message on 403 error without details', fakeAsync(() => {
            const headers = new HttpHeaders();
            const error403 = new HttpErrorResponse({ status: 403, headers });
            jest.spyOn(loginService, 'loginSAML2').mockRejectedValue(error403);
            const warningSpy = jest.spyOn(alertService, 'warning');

            component.loginSAML2();
            tick();

            expect(warningSpy).toHaveBeenCalledWith('Forbidden');
        }));

        it('should show warning with details on 403 error with X-artemisApp-error header', fakeAsync(() => {
            const headers = new HttpHeaders().set('X-artemisApp-error', 'User is disabled');
            const error403 = new HttpErrorResponse({ status: 403, headers });
            jest.spyOn(loginService, 'loginSAML2').mockRejectedValue(error403);
            const warningSpy = jest.spyOn(alertService, 'warning');

            component.loginSAML2();
            tick();

            expect(warningSpy).toHaveBeenCalledWith('Forbidden: User is disabled');
        }));

        it('should not show warning on other error status codes', fakeAsync(() => {
            const error500 = new HttpErrorResponse({ status: 500 });
            jest.spyOn(loginService, 'loginSAML2').mockRejectedValue(error500);
            const warningSpy = jest.spyOn(alertService, 'warning');

            component.loginSAML2();
            tick();

            expect(warningSpy).not.toHaveBeenCalled();
            // Cookie should not be set for non-401 errors
            expect(document.cookie).not.toContain('SAML2flow=true');
        }));
    });
});
