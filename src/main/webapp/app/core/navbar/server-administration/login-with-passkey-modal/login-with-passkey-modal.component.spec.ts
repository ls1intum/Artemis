import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { LoginWithPasskeyModalComponent } from './login-with-passkey-modal.component';
import { AccountService } from 'app/core/auth/account.service';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('LoginWithPasskeyModal', () => {
    let component: LoginWithPasskeyModalComponent;
    let fixture: ComponentFixture<LoginWithPasskeyModalComponent>;
    let router: Router;
    let eventManager: EventManager;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LoginWithPasskeyModalComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                { provide: AccountService, useClass: MockAccountService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                EventManager,
                WebauthnService,
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LoginWithPasskeyModalComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        eventManager = TestBed.inject(EventManager);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should hide modal when cancel is called', () => {
        component.showModal = true;
        component.cancel();
        expect(component.showModal).toBeFalse();
    });

    it('should emit justLoggedInWithPasskey and hide modal on login success', () => {
        const emitSpy = jest.spyOn(component.justLoggedInWithPasskey, 'emit');
        component.showModal = true;

        component.handleLoginSuccess();

        expect(emitSpy).toHaveBeenCalledWith(true);
        expect(component.showModal).toBeFalse();
    });

    it('should not navigate when on other pages', () => {
        Object.defineProperty(router, 'url', { value: '/admin/user-management', writable: true });
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.handleLoginSuccess();

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should not navigate when on home page', () => {
        Object.defineProperty(router, 'url', { value: '/', writable: true });
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.handleLoginSuccess();

        expect(navigateSpy).not.toHaveBeenCalled();
    });
});
