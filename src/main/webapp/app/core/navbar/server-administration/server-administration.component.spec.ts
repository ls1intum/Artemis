import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ServerAdministrationComponent } from './server-administration.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { By } from '@angular/platform-browser';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { AccountService } from 'app/core/auth/account.service';
import { PasskeyAuthenticationGuard } from 'app/core/auth/passkey-authentication-guard/passkey-authentication.guard';

@Component({ template: '' })
class MockEmptyComponent {}

describe('ServerAdministrationComponent', () => {
    let component: ServerAdministrationComponent;
    let fixture: ComponentFixture<ServerAdministrationComponent>;
    let accountService: AccountService;
    let passkeyGuard: PasskeyAuthenticationGuard;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ServerAdministrationComponent, TranslateModule.forRoot()],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: '**', component: MockEmptyComponent }])],
        })
            .overrideComponent(ServerAdministrationComponent, {
                remove: { imports: [HasAnyAuthorityDirective] },
                add: { imports: [MockHasAnyAuthorityDirective] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ServerAdministrationComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        passkeyGuard = TestBed.inject(PasskeyAuthenticationGuard);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should emit collapseNavbarListener when collapseNavbar is called', () => {
        const collapseNavbarSpy = jest.spyOn(component.collapseNavbarListener, 'emit');

        component.collapseNavbar();

        expect(collapseNavbarSpy).toHaveBeenCalledWith();
    });

    it('should have default input values as false', () => {
        fixture.detectChanges();

        expect(component.isExamActive()).toBeFalse();
        expect(component.isExamStarted()).toBeFalse();
    });

    it('should handle input properties correctly', () => {
        fixture.componentRef.setInput('isExamActive', true);
        fixture.componentRef.setInput('isExamStarted', true);

        fixture.detectChanges();

        expect(component.isExamActive()).toBeTrue();
        expect(component.isExamStarted()).toBeTrue();
    });

    it('should not show admin link when exam is active', () => {
        fixture.componentRef.setInput('isExamActive', true);
        fixture.detectChanges();

        const adminLink = fixture.debugElement.query(By.css('a[routerLink="/admin"]'));
        expect(adminLink).toBeFalsy();
    });

    it('should not show admin link when exam is started', () => {
        fixture.componentRef.setInput('isExamStarted', true);
        fixture.detectChanges();

        const adminLink = fixture.debugElement.query(By.css('a[routerLink="/admin"]'));
        expect(adminLink).toBeFalsy();
    });

    it('should show admin link when exam is not active and not started', () => {
        fixture.detectChanges();

        const adminLink = fixture.debugElement.query(By.css('a[routerLink="/admin"]'));
        expect(adminLink).toBeTruthy();
    });

    describe('Passkey Authentication', () => {
        beforeEach(() => {
            fixture.detectChanges();
        });

        it('should not show modal when passkey enforcement is disabled', () => {
            jest.spyOn(passkeyGuard, 'shouldEnforcePasskeyForAdminFeatures').mockReturnValue(false);

            const result = component['showModalForPasskeyLogin']();

            expect(result).toBeFalse();
            expect(component.loginWithPasskeyModal().showModal).toBeFalse();
        });

        it('should not show modal when user is already logged in with approved passkey', () => {
            jest.spyOn(passkeyGuard, 'shouldEnforcePasskeyForAdminFeatures').mockReturnValue(true);
            jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);

            const result = component['showModalForPasskeyLogin']();

            expect(result).toBeFalse();
            expect(component.loginWithPasskeyModal().showModal).toBeFalse();
        });

        it('should show modal when passkey enforcement is enabled and user not logged in with passkey', () => {
            jest.spyOn(passkeyGuard, 'shouldEnforcePasskeyForAdminFeatures').mockReturnValue(true);
            jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(false);

            const result = component['showModalForPasskeyLogin']();

            expect(result).toBeTrue();
            expect(component.loginWithPasskeyModal().showModal).toBeTrue();
        });

        it('should prevent link click when passkey modal needs to be shown', () => {
            jest.spyOn(passkeyGuard, 'shouldEnforcePasskeyForAdminFeatures').mockReturnValue(true);
            jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(false);

            const mockEvent = { preventDefault: jest.fn() } as unknown as Event;
            component.onLinkClick(mockEvent);

            expect(mockEvent.preventDefault).toHaveBeenCalled();
        });

        it('should collapse navbar when passkey modal does not need to be shown', () => {
            jest.spyOn(passkeyGuard, 'shouldEnforcePasskeyForAdminFeatures').mockReturnValue(false);
            const collapseNavbarSpy = jest.spyOn(component.collapseNavbarListener, 'emit');

            const mockEvent = { preventDefault: jest.fn() } as unknown as Event;
            component.onLinkClick(mockEvent);

            expect(mockEvent.preventDefault).not.toHaveBeenCalled();
            expect(collapseNavbarSpy).toHaveBeenCalled();
        });
    });
});
