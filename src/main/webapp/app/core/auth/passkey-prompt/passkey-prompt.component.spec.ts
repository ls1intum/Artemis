import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PasskeyPromptComponent } from './passkey-prompt.component';
import { AccountService } from 'app/core/auth/account.service';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { provideRouter } from '@angular/router';

describe('PasskeyContentComponent', () => {
    let component: PasskeyPromptComponent;
    let fixture: ComponentFixture<PasskeyPromptComponent>;
    let accountService: AccountService;
    let webauthnService: WebauthnService;
    let alertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PasskeyPromptComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                { provide: AccountService, useClass: MockAccountService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        queryParams: of({}),
                    },
                },
                WebauthnService,
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PasskeyPromptComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        webauthnService = TestBed.inject(WebauthnService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('input properties', () => {
        it('should have default input values', () => {
            expect(component.showHeader()).toBeTrue();
            expect(component.showContent()).toBeTrue();
            expect(component.showFooter()).toBeTrue();
        });
    });

    describe('handleLinkToUserSettingsClick', () => {
        it('should emit linkToUserSettingsWasClicked event', () => {
            const emitSpy = jest.spyOn(component.linkToUserSettingsWasClicked, 'emit');

            component.handleLinkToUserSettingsClick();

            expect(emitSpy).toHaveBeenCalledOnce();
        });
    });

    describe('setupPasskey', () => {
        it('should call webauthnService.addNewPasskey with user identity', async () => {
            const mockUser = { id: 1, login: 'testuser' };
            jest.spyOn(accountService, 'userIdentity').mockReturnValue(mockUser as any);
            const addNewPasskeySpy = jest.spyOn(webauthnService, 'addNewPasskey').mockResolvedValue(undefined);
            const alertSuccessSpy = jest.spyOn(alertService, 'success');

            await component.setupPasskey();

            expect(addNewPasskeySpy).toHaveBeenCalledWith(mockUser);
            expect(alertSuccessSpy).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.success.registration');
        });

        it('should show success alert after passkey setup', async () => {
            const mockUser = { id: 1, login: 'testuser' };
            jest.spyOn(accountService, 'userIdentity').mockReturnValue(mockUser as any);
            jest.spyOn(webauthnService, 'addNewPasskey').mockResolvedValue(undefined);
            const alertSuccessSpy = jest.spyOn(alertService, 'success');

            await component.setupPasskey();

            expect(alertSuccessSpy).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.success.registration');
        });
    });

    describe('signInWithPasskey', () => {
        it('should login with passkey and trigger success handler when user is logged in with approved passkey', async () => {
            const loginSpy = jest.spyOn(webauthnService, 'loginWithPasskey').mockResolvedValue(undefined);
            const identitySpy = jest.spyOn(accountService, 'identity').mockResolvedValue({} as any);
            jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);
            const triggerSuccessSpy = jest.spyOn(component.triggerPasskeyLoginSuccessHandler, 'emit');

            await component.signInWithPasskey();

            expect(loginSpy).toHaveBeenCalledOnce();
            expect(identitySpy).toHaveBeenCalledWith(true);
            expect(triggerSuccessSpy).toHaveBeenCalledOnce();
        });

        it('should show error when passkey is not super admin approved', async () => {
            jest.spyOn(webauthnService, 'loginWithPasskey').mockResolvedValue(undefined);
            jest.spyOn(accountService, 'identity').mockResolvedValue({} as any);
            jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(false);
            const alertErrorSpy = jest.spyOn(alertService, 'error');
            const triggerSuccessSpy = jest.spyOn(component.triggerPasskeyLoginSuccessHandler, 'emit');

            await component.signInWithPasskey();

            expect(alertErrorSpy).toHaveBeenCalledWith('global.menu.admin.usedPasskeyIsNotSuperAdminApproved');
            expect(triggerSuccessSpy).not.toHaveBeenCalled();
        });

        it('should refresh identity after login', async () => {
            jest.spyOn(webauthnService, 'loginWithPasskey').mockResolvedValue(undefined);
            const identitySpy = jest.spyOn(accountService, 'identity').mockResolvedValue({} as any);
            jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);

            await component.signInWithPasskey();

            expect(identitySpy).toHaveBeenCalledWith(true);
        });
    });

    describe('template rendering', () => {
        it('should display header when showHeader is true', () => {
            fixture.componentRef.setInput('showHeader', true);
            fixture.detectChanges();

            const header = fixture.nativeElement.querySelector('h3');
            expect(header).toBeTruthy();
        });

        it('should not display header when showHeader is false', () => {
            fixture.componentRef.setInput('showHeader', false);
            fixture.detectChanges();

            const header = fixture.nativeElement.querySelector('h3');
            expect(header).toBeFalsy();
        });

        it('should display content section when showContent is true', () => {
            fixture.componentRef.setInput('showContent', true);
            fixture.detectChanges();

            const content = fixture.nativeElement.querySelector('p');
            expect(content).toBeTruthy();
        });

        it('should not display content section when showContent is false', () => {
            fixture.componentRef.setInput('showContent', false);
            fixture.detectChanges();

            const paragraphs = fixture.nativeElement.querySelectorAll('p');
            expect(paragraphs).toHaveLength(0);
        });

        it('should display footer with button when showFooter is true', () => {
            fixture.componentRef.setInput('showFooter', true);
            fixture.detectChanges();

            const button = fixture.nativeElement.querySelector('jhi-button');
            expect(button).toBeTruthy();
        });

        it('should not display footer when showFooter is false', () => {
            fixture.componentRef.setInput('showFooter', false);
            fixture.detectChanges();

            const button = fixture.nativeElement.querySelector('jhi-button');
            expect(button).toBeFalsy();
        });

        it('should display setup passkey button when user should setup passkey', () => {
            jest.spyOn(accountService, 'askToSetupPasskey').mockReturnValue(true);
            fixture.componentRef.setInput('showFooter', true);
            fixture.detectChanges();

            const button = fixture.nativeElement.querySelector('jhi-button');
            expect(button).toBeTruthy();
        });

        it('should display sign in button when user has passkey registered', () => {
            jest.spyOn(accountService, 'askToSetupPasskey').mockReturnValue(false);
            fixture.componentRef.setInput('showFooter', true);
            fixture.detectChanges();

            const button = fixture.nativeElement.querySelector('jhi-button');
            expect(button).toBeTruthy();
        });

        it('should display info alert when user is not logged in with passkey', () => {
            jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(false);
            fixture.componentRef.setInput('showContent', true);
            fixture.detectChanges();

            const infoAlert = fixture.nativeElement.querySelector('.alert-info');
            expect(infoAlert).toBeTruthy();
        });

        it('should display warning alert and link when user is logged in with passkey but not approved', () => {
            jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(true);
            fixture.componentRef.setInput('showContent', true);
            fixture.detectChanges();

            const warningAlert = fixture.nativeElement.querySelector('.alert-warning');
            const link = fixture.nativeElement.querySelector('a[routerLink="/user-settings/passkeys"]');
            expect(warningAlert).toBeTruthy();
            expect(link).toBeTruthy();
        });

        it('should call handleLinkToUserSettingsClick when link is clicked', () => {
            jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(true);
            fixture.componentRef.setInput('showContent', true);
            fixture.detectChanges();

            const handleLinkSpy = jest.spyOn(component, 'handleLinkToUserSettingsClick');
            const link = fixture.nativeElement.querySelector('a[routerLink="/user-settings/passkeys"]');

            // Prevent router navigation error by mocking console.error for this test
            const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation();
            link.click();
            consoleErrorSpy.mockRestore();

            expect(handleLinkSpy).toHaveBeenCalledOnce();
        });
    });
});
