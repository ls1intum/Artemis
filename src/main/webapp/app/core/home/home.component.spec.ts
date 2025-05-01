import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HomeComponent } from './home.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { LoginService } from 'app/core/login/login.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { MockProvider } from 'ng-mocks';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { SetupPasskeyModalComponent } from 'app/core/course/overview/setup-passkey-modal/setup-passkey-modal.component';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';

describe('HomeComponent', () => {
    let component: HomeComponent;
    let fixture: ComponentFixture<HomeComponent>;
    let accountService: AccountService;
    let modalService: NgbModal;

    let router: MockRouter;

    const route = {
        data: of({}),
        children: [],
        queryParams: of({}),
    } as any as ActivatedRoute;

    beforeEach(async () => {
        router = new MockRouter();
        router.setUrl('');

        await TestBed.configureTestingModule({
            imports: [MockRouterLinkDirective, RouterTestingModule, SetupPasskeyModalComponent],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: router },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                MockProvider(LoginService),
                MockProvider(StateStorageService),
                MockProvider(EventManager),
                MockProvider(AlertService),
                MockProvider(WebauthnService),
                MockProvider(WebauthnApiService),
                MockProvider(NgbModal),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(HomeComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        modalService = TestBed.inject(NgbModal);
        fixture.detectChanges();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with profile info and prefilled username', () => {
        expect(component.username).toBe('testUser');
        expect(component.isPasskeyEnabled).toBeTrue();
    });

    it('should open the setup passkey modal if conditions are met', () => {
        const openSpy = jest.spyOn(modalService, 'open');
        component.openSetupPasskeyModal();
        expect(openSpy).toHaveBeenCalled();
    });

    it('should not open the setup passkey modal if passkey feature is disabled', () => {
        component.isPasskeyEnabled = false;
        const openSpy = jest.spyOn(modalService, 'open');
        component.openSetupPasskeyModal();
        expect(openSpy).not.toHaveBeenCalled();
    });

    it('should handle login success and navigate to courses', async () => {
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        jest.spyOn(accountService, 'identity').mockResolvedValue({} as any);

        await component.login();
        expect(navigateSpy).toHaveBeenCalledWith(['courses']);
    });

    it('should validate form correctly', () => {
        component.username = 'testUser';
        component.password = 'password123';
        component.checkFormValidity();
        expect(component.isFormValid).toBeTrue();

        component.password = '';
        component.checkFormValidity();
        expect(component.isFormValid).toBeFalse();
    });

    describe('openSetupPasskeyModal', () => {
        it('should not open the modal if passkey feature is disabled', () => {
            component.isPasskeyEnabled = false;
            const openModalSpy = jest.spyOn(modalService, 'open');

            component.openSetupPasskeyModal();

            expect(openModalSpy).not.toHaveBeenCalled();
        });

        it('should not open the modal if the user is on the login screen', () => {
            component.isPasskeyEnabled = true;
            const openModalSpy = jest.spyOn(modalService, 'open');
            jest.spyOn(accountService, 'isAuthenticatedSignal').mockReturnValue(false);

            component.openSetupPasskeyModal();

            expect(openModalSpy).not.toHaveBeenCalled();
        });

        it('should not open the modal if the user has already registered a passkey', () => {
            component.isPasskeyEnabled = true;
            const openModalSpy = jest.spyOn(modalService, 'open');
            jest.spyOn(accountService, 'isAuthenticatedSignal').mockReturnValue(true);
            accountService.userIdentity = { hasRegisteredAPasskey: true } as any;

            component.openSetupPasskeyModal();

            expect(openModalSpy).not.toHaveBeenCalled();
        });

        it('should open the modal if the passkey feature is enabled, the user is authenticated, and no passkey is registered', () => {
            component.isPasskeyEnabled = true;
            const openModalSpy = jest.spyOn(modalService, 'open');
            jest.spyOn(accountService, 'isAuthenticatedSignal').mockReturnValue(true);

            accountService.userIdentity = { hasRegisteredAPasskey: false } as any;

            component.openSetupPasskeyModal();

            expect(openModalSpy).toHaveBeenCalledWith(SetupPasskeyModalComponent, { size: 'lg', backdrop: 'static' });
        });
    });
});
