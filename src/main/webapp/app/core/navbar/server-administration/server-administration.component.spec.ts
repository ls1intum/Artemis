import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ServerAdministrationComponent } from './server-administration.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { MockComponent } from 'ng-mocks';
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
            imports: [ServerAdministrationComponent, TranslateModule.forRoot(), MockComponent(FeatureOverlayComponent)],
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

    it('should emit collapseNavbarListener when dropdown item is clicked', () => {
        const collapseNavbarSpy = jest.spyOn(component.collapseNavbarListener, 'emit');
        fixture.detectChanges();

        const dropdownItem = fixture.debugElement.query(By.css('a[routerLink]'));
        expect(dropdownItem).toBeTruthy();

        const mockClickEvent = {
            button: 0, // Simulates a primary (left) mouse click
            preventDefault: () => {},
        };
        dropdownItem.triggerEventHandler('click', mockClickEvent);
        expect(collapseNavbarSpy).toHaveBeenCalled();
    });

    it('should handle input properties correctly', () => {
        fixture.componentRef.setInput('isExamActive', true);
        fixture.componentRef.setInput('isExamStarted', true);
        fixture.componentRef.setInput('localCIActive', true);
        fixture.componentRef.setInput('irisEnabled', true);
        fixture.componentRef.setInput('ltiEnabled', true);
        fixture.componentRef.setInput('standardizedCompetenciesEnabled', true);
        fixture.componentRef.setInput('atlasEnabled', true);
        fixture.componentRef.setInput('examEnabled', true);

        fixture.detectChanges();

        expect(component.isExamActive()).toBeTrue();
        expect(component.isExamStarted()).toBeTrue();
        expect(component.localCIActive()).toBeTrue();
        expect(component.irisEnabled()).toBeTrue();
        expect(component.ltiEnabled()).toBeTrue();
        expect(component.standardizedCompetenciesEnabled()).toBeTrue();
        expect(component.atlasEnabled()).toBeTrue();
        expect(component.examEnabled()).toBeTrue();
    });

    it('should have default input values as false', () => {
        fixture.detectChanges();

        expect(component.isExamActive()).toBeFalse();
        expect(component.isExamStarted()).toBeFalse();
        expect(component.localCIActive()).toBeFalse();
        expect(component.irisEnabled()).toBeFalse();
        expect(component.ltiEnabled()).toBeFalse();
        expect(component.standardizedCompetenciesEnabled()).toBeFalse();
        expect(component.atlasEnabled()).toBeFalse();
        expect(component.examEnabled()).toBeFalse();
    });

    describe('Passkey Authentication', () => {
        beforeEach(() => {
            fixture.detectChanges();
        });

        it('should not open dropdown when user is not logged in with passkey', () => {
            jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(false);
            component['justLoggedInWithPasskey'].set(true);

            const openSpy = jest.spyOn(component.adminMenuDropdown(), 'open');

            component['openDropdownIfUserLoggedInWithPasskey']();

            expect(openSpy).not.toHaveBeenCalled();
        });

        it('should not open dropdown when justLoggedInWithPasskey is false', () => {
            jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(true);
            component['justLoggedInWithPasskey'].set(false);

            const openSpy = jest.spyOn(component.adminMenuDropdown(), 'open');

            component['openDropdownIfUserLoggedInWithPasskey']();

            expect(openSpy).not.toHaveBeenCalled();
        });

        it('should open dropdown after timeout when user just logged in with passkey', async () => {
            jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(true);
            component['justLoggedInWithPasskey'].set(true);

            const openSpy = jest.spyOn(component.adminMenuDropdown(), 'open');

            component['openDropdownIfUserLoggedInWithPasskey']();

            // Check that justLoggedInWithPasskey was reset immediately
            expect(component['justLoggedInWithPasskey']()).toBeFalse();

            // Wait for setTimeout to execute
            await new Promise((resolve) => setTimeout(resolve, 10));
            expect(openSpy).toHaveBeenCalled();
        });

        it('should not show modal when passkey enforcement is disabled', () => {
            jest.spyOn(passkeyGuard, 'shouldEnforcePasskeyForAdminFeatures').mockReturnValue(false);
            const closeSpy = jest.spyOn(component.adminMenuDropdown(), 'close');

            component['showModalForPasskeyLogin']();

            expect(closeSpy).not.toHaveBeenCalled();
            expect(component.loginWithPasskeyModal().showModal).toBeFalse();
        });

        it('should not show modal when user is already logged in with approved passkey', () => {
            jest.spyOn(passkeyGuard, 'shouldEnforcePasskeyForAdminFeatures').mockReturnValue(true);
            jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);
            const closeSpy = jest.spyOn(component.adminMenuDropdown(), 'close');

            component['showModalForPasskeyLogin']();

            expect(closeSpy).not.toHaveBeenCalled();
            expect(component.loginWithPasskeyModal().showModal).toBeFalse();
        });

        it('should show modal when passkey enforcement is enabled and user not logged in with passkey', () => {
            jest.spyOn(passkeyGuard, 'shouldEnforcePasskeyForAdminFeatures').mockReturnValue(true);
            jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(false);
            const closeSpy = jest.spyOn(component.adminMenuDropdown(), 'close');

            component['showModalForPasskeyLogin']();

            expect(closeSpy).toHaveBeenCalled();
            expect(component.loginWithPasskeyModal().showModal).toBeTrue();
        });

        it('should set justLoggedInWithPasskey signal when onJustLoggedInWithPasskey is called', () => {
            component.onJustLoggedInWithPasskey(true);
            expect(component['justLoggedInWithPasskey']()).toBeTrue();

            component.onJustLoggedInWithPasskey(false);
            expect(component['justLoggedInWithPasskey']()).toBeFalse();
        });
    });
});
