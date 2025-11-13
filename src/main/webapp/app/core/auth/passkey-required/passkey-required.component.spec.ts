import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { PasskeyRequiredComponent } from './passkey-required.component';
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

describe('PasskeyRequiredComponent', () => {
    let component: PasskeyRequiredComponent;
    let fixture: ComponentFixture<PasskeyRequiredComponent>;
    let accountService: AccountService;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [PasskeyRequiredComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
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

        fixture = TestBed.createComponent(PasskeyRequiredComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        router = TestBed.inject(Router);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should retrieve returnUrl from query parameters on init', () => {
        fixture.detectChanges();

        expect(component.returnUrl).toBe('/admin/user-management');
    });

    it('should redirect to returnUrl if user is already logged in with passkey', async () => {
        jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);
        const navigateByUrlSpy = jest.spyOn(router, 'navigateByUrl');
        component.returnUrl = '/admin/user-management';

        await component['initializeUserIdentity']();

        expect(navigateByUrlSpy).toHaveBeenCalledWith('/admin/user-management');
    });

    it('should navigate to returnUrl when redirectToOriginalUrlOrHome is called with returnUrl', () => {
        component.returnUrl = '/admin/metrics';
        const navigateByUrlSpy = jest.spyOn(router, 'navigateByUrl');

        component.redirectToOriginalUrlOrHome();

        expect(navigateByUrlSpy).toHaveBeenCalledWith('/admin/metrics');
    });

    it('should navigate to home when redirectToOriginalUrlOrHome is called without returnUrl', () => {
        component.returnUrl = undefined;
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.redirectToOriginalUrlOrHome();

        expect(navigateSpy).toHaveBeenCalledWith(['/']);
    });

    it('should set userHasRegisteredAPasskey based on askToSetupPasskey', async () => {
        const mockUser = { id: 1, login: 'testuser', askToSetupPasskey: false, internal: true };
        jest.spyOn(accountService, 'userIdentity').mockReturnValue(mockUser);

        await component['initializeUserIdentity']();

        expect(component.userHasRegisteredAPasskey).toBeTrue();
    });
});
