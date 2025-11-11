import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { PasskeyRequiredComponent } from './passkey-required.component';
import { AccountService } from 'app/core/auth/account.service';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAccountService } from '../../../test/helpers/mocks/service/mock-account.service';
import { MockRouter } from '../../../test/helpers/mocks/mock-router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('PasskeyRequiredComponent', () => {
    let component: PasskeyRequiredComponent;
    let fixture: ComponentFixture<PasskeyRequiredComponent>;
    let accountService: AccountService;
    let webauthnService: WebauthnService;
    let alertService: AlertService;
    let router: Router;
    let activatedRoute: ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PasskeyRequiredComponent, TranslateDirective],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        queryParams: of({ returnUrl: '/admin/user-management' }),
                    },
                },
                {
                    provide: WebauthnService,
                    useValue: {
                        loginWithPasskey: jest.fn().mockResolvedValue(undefined),
                        addNewPasskey: jest.fn().mockResolvedValue(undefined),
                    },
                },
                {
                    provide: AlertService,
                    useValue: {
                        success: jest.fn(),
                        error: jest.fn(),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PasskeyRequiredComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        webauthnService = TestBed.inject(WebauthnService);
        alertService = TestBed.inject(AlertService);
        router = TestBed.inject(Router);
        activatedRoute = TestBed.inject(ActivatedRoute);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should retrieve returnUrl from query parameters on init', () => {
        fixture.detectChanges();

        expect(component.returnUrl).toBe('/admin/user-management');
    });

    it('should redirect to returnUrl if user is already logged in with passkey', () => {
        jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(true);
        const navigateByUrlSpy = jest.spyOn(router, 'navigateByUrl');

        fixture.detectChanges();

        expect(navigateByUrlSpy).toHaveBeenCalledWith('/admin/user-management');
    });

    it('should call webauthnService.loginWithPasskey when signInWithPasskey is called', async () => {
        component.returnUrl = '/admin/metrics';
        const loginSpy = jest.spyOn(webauthnService, 'loginWithPasskey');
        const navigateByUrlSpy = jest.spyOn(router, 'navigateByUrl');

        await component.signInWithPasskey();

        expect(loginSpy).toHaveBeenCalled();
        expect(navigateByUrlSpy).toHaveBeenCalledWith('/admin/metrics');
    });

    it('should call addNewPasskey and then loginWithPasskey when setupPasskeyAndLogin is called', async () => {
        component.returnUrl = '/admin/logs';
        const addPasskeySpy = jest.spyOn(webauthnService, 'addNewPasskey');
        const loginSpy = jest.spyOn(webauthnService, 'loginWithPasskey');
        const alertSpy = jest.spyOn(alertService, 'success');

        await component.setupPasskeyAndLogin();

        expect(addPasskeySpy).toHaveBeenCalled();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.success.registration');
        expect(loginSpy).toHaveBeenCalled();
    });

    it('should navigate to home when cancel is called', () => {
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.cancel();

        expect(navigateSpy).toHaveBeenCalledWith(['/']);
    });

    it('should handle login errors and show alert', async () => {
        const error = new Error('Login failed');
        jest.spyOn(webauthnService, 'loginWithPasskey').mockRejectedValue(error);
        const alertErrorSpy = jest.spyOn(alertService, 'error');

        await component.signInWithPasskey();

        expect(alertErrorSpy).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.login');
    });

    it('should update user identity after successful login', async () => {
        const mockUser = { id: 1, login: 'testuser', isLoggedInWithPasskey: false, internal: true };
        jest.spyOn(accountService, 'userIdentity').mockReturnValue(mockUser);
        const userIdentitySetSpy = jest.spyOn(accountService.userIdentity, 'set');
        component.returnUrl = '/admin';

        await component.signInWithPasskey();

        expect(userIdentitySetSpy).toHaveBeenCalledWith({
            ...mockUser,
            isLoggedInWithPasskey: true,
            internal: true,
        });
    });
});
