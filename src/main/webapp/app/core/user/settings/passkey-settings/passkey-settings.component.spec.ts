import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PasskeySettingsComponent } from 'app/core/user/settings/passkey-settings/passkey-settings.component';
import { PasskeySettingsApiService } from 'app/core/user/settings/passkey-settings/passkey-settings-api.service';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { of } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { DisplayedPasskey } from 'app/core/user/settings/passkey-settings/passkey-settings.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ButtonComponent } from 'app/shared/components/button/button.component';
import { CustomMaxLengthDirective } from 'app/shared/validators/custom-max-length-validator/custom-max-length-validator.directive';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockJhiTranslateDirective } from 'test/helpers/mocks/directive/mock-jhi-translate-directive.directive';
import { MockTranslateValuesDirective } from 'test/helpers/mocks/directive/mock-translate-values.directive';
import { TranslateStore } from '@ngx-translate/core'; // Import TranslateStore
import { MockComponent, MockInstance, MockPipe, MockProvider } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { TranslateService } from '@ngx-translate/core';

describe('PasskeySettingsComponent', () => {
    let component: PasskeySettingsComponent;
    let fixture: ComponentFixture<PasskeySettingsComponent>;
    let passkeySettingsApiService: PasskeySettingsApiService;
    let webauthnApiService: WebauthnApiService;
    let accountService: AccountService;
    let alertService: AlertService;

    const mockUser: User = { id: 1, email: 'test@example.com' } as User;
    const mockPasskeys: DisplayedPasskey[] = [{ credentialId: '123', label: 'Test Passkey', created: new Date().toISOString(), lastUsed: new Date().toISOString() }];

    beforeEach(async () => {
        Object.defineProperty(navigator, 'credentials', {
            value: {
                create: jest.fn().mockResolvedValue({} as any),
            },
            writable: true,
        });

        await TestBed.configureTestingModule({
            imports: [],
            declarations: [],
            providers: [
                // MockJhiTranslateDirective, MockTranslateValuesDirective,
                WebauthnApiService,
                // MockProvider(TranslateStore), // Mock TranslateStore
                // MockAccountService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                // works
                // { provide: PasskeySettingsApiService, useValue: { getRegisteredPasskeys: jest.fn(), deletePasskey: jest.fn(), updatePasskeyLabel: jest.fn() } },
                // { provide: WebauthnApiService, useValue: { getRegistrationOptions: jest.fn() } },
                // { provide: AccountService, useValue: { getAuthenticationState: jest.fn(() => of(mockUser)) } }, // Mocking the observable
                // { provide: AlertService, useValue: { addErrorAlert: jest.fn() } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PasskeySettingsComponent);
        component = fixture.componentInstance;
        passkeySettingsApiService = TestBed.inject(PasskeySettingsApiService);
        webauthnApiService = TestBed.inject(WebauthnApiService);
        accountService = TestBed.inject(AccountService);
        alertService = TestBed.inject(AlertService);
    });

    it('should load the current user on initialization', () => {
        jest.spyOn(accountService, 'getAuthenticationState');
        expect(component.currentUser()).toEqual({ id: 99 });
    });

    it('should update registered passkeys', async () => {
        jest.spyOn(passkeySettingsApiService, 'getRegisteredPasskeys').mockResolvedValue(mockPasskeys);
        await component.updateRegisteredPasskeys();
        expect(component.registeredPasskeys()).toEqual(mockPasskeys);
    });

    it('should handle adding a new passkey', async () => {
        jest.spyOn(webauthnApiService, 'getRegistrationOptions').mockResolvedValue({});
        jest.spyOn(passkeySettingsApiService, 'getRegisteredPasskeys').mockResolvedValue(mockPasskeys);
        jest.spyOn(navigator.credentials, 'create').mockResolvedValue({} as any);

        await component.addNewPasskey();
        expect(passkeySettingsApiService.getRegisteredPasskeys).toHaveBeenCalled();
    });

    it('should handle errors when adding a new passkey', async () => {
        jest.spyOn(webauthnApiService, 'getRegistrationOptions').mockRejectedValue(new Error('Test Error'));
        await component.addNewPasskey();
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.registration');
    });

    it('should edit a passkey label', async () => {
        const passkey = { ...mockPasskeys[0], isEditingLabel: false };
        component.editPasskeyLabel(passkey);
        expect(passkey.isEditingLabel).toBeTrue();
        expect(passkey.labelBeforeEdit).toEqual(passkey.label);
    });

    it('should save a passkey label', async () => {
        const passkey = { ...mockPasskeys[0], isEditingLabel: true };
        jest.spyOn(passkeySettingsApiService, 'updatePasskeyLabel').mockResolvedValue(passkey);

        await component.savePasskeyLabel(passkey);
        expect(passkey.isEditingLabel).toBeFalse();
        expect(passkeySettingsApiService.updatePasskeyLabel).toHaveBeenCalledWith(passkey.credentialId, passkey);
    });

    it('should handle errors when saving a passkey label', async () => {
        const passkey = { ...mockPasskeys[0], isEditingLabel: true };
        jest.spyOn(passkeySettingsApiService, 'updatePasskeyLabel').mockRejectedValue(new Error('Test Error'));

        await component.savePasskeyLabel(passkey);
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('Unable to update passkey label');
        expect(passkey.label).toEqual(passkey.labelBeforeEdit);
    });

    it('should delete a passkey', async () => {
        jest.spyOn(passkeySettingsApiService, 'deletePasskey').mockResolvedValue(undefined);
        jest.spyOn(passkeySettingsApiService, 'getRegisteredPasskeys').mockResolvedValue([]);

        await component.deletePasskey(mockPasskeys[0]);
        expect(passkeySettingsApiService.deletePasskey).toHaveBeenCalledWith(mockPasskeys[0].credentialId);
        expect(component.registeredPasskeys()).toEqual([]);
    });

    it('should handle errors when deleting a passkey', async () => {
        jest.spyOn(passkeySettingsApiService, 'deletePasskey').mockRejectedValue(new Error('Test Error'));

        await component.deletePasskey(mockPasskeys[0]);
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('Unable to delete passkey');
    });
});
