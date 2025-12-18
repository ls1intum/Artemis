import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { PasskeyAuthenticationPageComponent } from './passkey-authentication-page.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { provideRouter } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { By } from '@angular/platform-browser';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

describe('PasskeyAuthenticationPageComponent', () => {
    let component: PasskeyAuthenticationPageComponent;
    let fixture: ComponentFixture<PasskeyAuthenticationPageComponent>;
    let accountService: AccountService;
    let webauthnService: WebauthnService;
    let alertService: AlertService;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [PasskeyAuthenticationPageComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                WebauthnService,
                {
                    provide: ActivatedRoute,
                    useValue: {
                        queryParams: of({ returnUrl: '/admin/user-management' }),
                    },
                },
            ],
        });

        fixture = TestBed.createComponent(PasskeyAuthenticationPageComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        webauthnService = TestBed.inject(WebauthnService);
        alertService = TestBed.inject(AlertService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('ngOnInit', () => {
        it('should initialize user identity on init', async () => {
            const identitySpy = jest.spyOn(accountService, 'identity').mockResolvedValue({} as User);

            fixture.detectChanges();
            await fixture.whenStable();

            expect(identitySpy).toHaveBeenCalledOnce();
        });

        it('should retrieve returnUrl from query parameters on init', async () => {
            jest.spyOn(accountService, 'identity').mockResolvedValue({} as User);
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.returnUrl).toBe('/admin/user-management');
        });

        it('should redirect to returnUrl if user is already logged in with passkey', async () => {
            jest.spyOn(accountService, 'identity').mockResolvedValue({} as User);
            jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);
            const navigateByUrlSpy = jest.spyOn(router, 'navigateByUrl');
            component.returnUrl = '/admin/user-management';

            component.ngOnInit();
            await fixture.whenStable();

            expect(navigateByUrlSpy).toHaveBeenCalledWith('/admin/user-management');
        });
    });

    describe('redirectToOriginalUrlOrHome', () => {
        it('should navigate to returnUrl when redirectToOriginalUrlOrHome is called with returnUrl', () => {
            component.returnUrl = '/admin/metrics';
            const navigateByUrlSpy = jest.spyOn(router, 'navigateByUrl');

            component.redirectToOriginalUrlOrHome();

            expect(navigateByUrlSpy).toHaveBeenCalledExactlyOnceWith('/admin/metrics');
        });

        it('should navigate to home when redirectToOriginalUrlOrHome is called without returnUrl', () => {
            component.returnUrl = undefined;
            const navigateSpy = jest.spyOn(router, 'navigate');

            component.redirectToOriginalUrlOrHome();

            expect(navigateSpy).toHaveBeenCalledExactlyOnceWith(['/']);
        });
    });

    describe('setupPasskey', () => {
        it('should call webauthnService.addNewPasskey with user identity', async () => {
            const mockUser = { id: 1, login: 'testuser' };
            jest.spyOn(accountService, 'userIdentity').mockReturnValue(mockUser as User);
            const addNewPasskeySpy = jest.spyOn(webauthnService, 'addNewPasskey').mockResolvedValue(undefined);
            const alertSuccessSpy = jest.spyOn(alertService, 'success');

            await component.setupPasskey();

            expect(addNewPasskeySpy).toHaveBeenCalledExactlyOnceWith(mockUser);
            expect(alertSuccessSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.userSettings.passkeySettingsPage.success.registration');
        });

        it('should show success alert after passkey setup', async () => {
            const mockUser = { id: 1, login: 'testuser' };
            jest.spyOn(accountService, 'userIdentity').mockReturnValue(mockUser as User);
            jest.spyOn(webauthnService, 'addNewPasskey').mockResolvedValue(undefined);
            const alertSuccessSpy = jest.spyOn(alertService, 'success');

            await component.setupPasskey();

            expect(alertSuccessSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.userSettings.passkeySettingsPage.success.registration');
        });
    });

    describe('signInWithPasskey', () => {
        it('should login with passkey and redirect when user is logged in with approved passkey', async () => {
            const loginSpy = jest.spyOn(webauthnService, 'loginWithPasskey').mockResolvedValue(undefined);
            const identitySpy = jest.spyOn(accountService, 'identity').mockResolvedValue({} as User);
            jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);
            const redirectSpy = jest.spyOn(component, 'redirectToOriginalUrlOrHome');

            await component.signInWithPasskey();

            expect(loginSpy).toHaveBeenCalledOnce();
            expect(identitySpy).toHaveBeenCalledExactlyOnceWith(true);
            expect(redirectSpy).toHaveBeenCalledOnce();
        });

        it('should show error when passkey is not super admin approved', async () => {
            jest.spyOn(webauthnService, 'loginWithPasskey').mockResolvedValue(undefined);
            jest.spyOn(accountService, 'identity').mockResolvedValue({} as User);
            jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(false);
            const alertErrorSpy = jest.spyOn(alertService, 'error');
            const redirectSpy = jest.spyOn(component, 'redirectToOriginalUrlOrHome');

            await component.signInWithPasskey();

            expect(alertErrorSpy).toHaveBeenCalledExactlyOnceWith('global.menu.admin.usedPasskeyIsNotSuperAdminApproved');
            expect(redirectSpy).not.toHaveBeenCalled();
        });

        it('should refresh identity after login', async () => {
            jest.spyOn(webauthnService, 'loginWithPasskey').mockResolvedValue(undefined);
            const identitySpy = jest.spyOn(accountService, 'identity').mockResolvedValue({} as User);
            jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);

            await component.signInWithPasskey();

            expect(identitySpy).toHaveBeenCalledExactlyOnceWith(true);
        });
    });

    it('should display setup passkey button when user should setup passkey', async () => {
        jest.spyOn(accountService, 'askToSetupPasskey').mockReturnValue(true);
        fixture.detectChanges();
        await fixture.whenStable();

        const buttonDebugElement = fixture.debugElement.query(By.directive(ButtonComponent));
        expect(buttonDebugElement).toBeTruthy();

        const buttonComponent = buttonDebugElement.componentInstance as ButtonComponent;
        expect(buttonComponent.title).toBe('global.menu.admin.setupPasskey');
    });

    it('should display sign in button when user has passkey registered', async () => {
        jest.spyOn(accountService, 'askToSetupPasskey').mockReturnValue(false);
        fixture.detectChanges();
        await fixture.whenStable();

        const buttonDebugElement = fixture.debugElement.query(By.directive(ButtonComponent));
        expect(buttonDebugElement).toBeTruthy();

        const buttonComponent = buttonDebugElement.componentInstance as ButtonComponent;
        expect(buttonComponent.title).toBe('global.menu.account.loginWithPasskey');
    });

    it('should display page heading', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const heading = fixture.nativeElement.querySelector('h3 span[jhiTranslate="global.menu.admin.loginWithPasskeyToUseAdminFeatures"]');
        expect(heading).toBeTruthy();
    });

    it('should display info text when user is not logged in with passkey', async () => {
        jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(false);
        fixture.detectChanges();
        await fixture.whenStable();

        const infoText = fixture.nativeElement.querySelector('p[jhiTranslate="artemisApp.userSettings.passkeySettingsPage.infoText"]');
        expect(infoText).toBeTruthy();
    });

    it('should display info alert when user is not logged in with passkey', async () => {
        jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(false);
        fixture.detectChanges();
        await fixture.whenStable();

        const infoAlert = fixture.nativeElement.querySelector('.alert-info');
        expect(infoAlert).toBeTruthy();

        const alertText = infoAlert.querySelector('p[jhiTranslate="global.menu.admin.passkeyRestrictionExplanation"]');
        expect(alertText).toBeTruthy();
    });

    it('should display approval instructions with correct translation keys when user is logged in with passkey but not approved', async () => {
        jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(true);
        fixture.detectChanges();
        await fixture.whenStable();

        const instructionSpans = fixture.nativeElement.querySelectorAll('p.text-wrap span[jhiTranslate]');
        expect(instructionSpans.length).toBeGreaterThanOrEqual(3);

        const startSpan = Array.from(instructionSpans).find((span: HTMLElement) => span.getAttribute('jhiTranslate') === 'global.menu.admin.passkeyApprovalInstructions.start') as
            | HTMLElement
            | undefined;
        expect(startSpan).toBeTruthy();

        const link = fixture.nativeElement.querySelector('a[routerLink="/user-settings/passkeys"]');
        expect(link).toBeTruthy();

        const linkSpan = link.querySelector('span[jhiTranslate="global.menu.admin.passkeyApprovalInstructions.link"]');
        expect(linkSpan).toBeTruthy();

        const endSpan = Array.from(instructionSpans).find((span: HTMLElement) => span.getAttribute('jhiTranslate') === 'global.menu.admin.passkeyApprovalInstructions.end') as
            | HTMLElement
            | undefined;
        expect(endSpan).toBeTruthy();
    });

    it('should display warning alert when user is logged in with passkey but not approved', async () => {
        jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(true);
        fixture.detectChanges();
        await fixture.whenStable();

        const warningAlert = fixture.nativeElement.querySelector('.alert-warning');
        expect(warningAlert).toBeTruthy();

        const alertText = warningAlert.querySelector('p[jhiTranslate="global.menu.admin.passkeyNotYetApproved"]');
        expect(alertText).toBeTruthy();
    });

    it('should set shouldSubmit to false on setup passkey button to prevent duplicate requests', async () => {
        jest.spyOn(accountService, 'askToSetupPasskey').mockReturnValue(true);
        fixture.detectChanges();
        await fixture.whenStable();

        const buttonDebugElement = fixture.debugElement.query(By.directive(ButtonComponent));
        const buttonElement = fixture.nativeElement.querySelector('jhi-button');
        const button = buttonElement.querySelector('button');

        const buttonComponent = buttonDebugElement.componentInstance as ButtonComponent;
        expect(buttonComponent.title).toBe('global.menu.admin.setupPasskey');
        expect(button.type).toBe('button'); // would be 'submit' if shouldSubmit was true
    });

    it('should set shouldSubmit to false on sign in with passkey button to prevent duplicate requests', async () => {
        jest.spyOn(accountService, 'askToSetupPasskey').mockReturnValue(false);
        fixture.detectChanges();
        await fixture.whenStable();

        const buttonDebugElement = fixture.debugElement.query(By.directive(ButtonComponent));
        const buttonElement = fixture.nativeElement.querySelector('jhi-button');
        const button = buttonElement.querySelector('button');

        const buttonComponent = buttonDebugElement.componentInstance as ButtonComponent;
        expect(buttonComponent.title).toBe('global.menu.account.loginWithPasskey');
        expect(button.type).toBe('button'); // would be 'submit' if shouldSubmit was true
    });
});
