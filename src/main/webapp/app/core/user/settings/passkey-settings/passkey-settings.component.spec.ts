import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PasskeySettingsComponent } from 'app/core/user/settings/passkey-settings/passkey-settings.component';
import { PasskeySettingsApiService } from 'app/core/user/settings/passkey-settings/passkey-settings-api.service';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { DisplayedPasskey } from 'app/core/user/settings/passkey-settings/passkey-settings.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { PasskeyDTO } from 'app/core/user/settings/passkey-settings/dto/passkey.dto';

import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { Authority } from 'app/shared/constants/authority.constants';
import { User } from 'app/core/user/user.model';

describe('PasskeySettingsComponent', () => {
    let component: PasskeySettingsComponent;
    let fixture: ComponentFixture<PasskeySettingsComponent>;
    let passkeySettingsApiService: PasskeySettingsApiService;
    let webauthnApiService: WebauthnApiService;
    let accountService: AccountService;
    let alertService: AlertService;

    const mockPasskeys: DisplayedPasskey[] = [
        {
            credentialId: '123',
            label: 'Test Passkey',
            created: new Date().toISOString(),
            lastUsed: new Date().toISOString(),
            isSuperAdminApproved: true,
        },
    ];

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
                { provide: AccountService, useClass: MockAccountService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                WebauthnApiService,
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

    it('should handle errors when adding a new passkey', async () => {
        jest.spyOn(alertService, 'addErrorAlert');
        jest.spyOn(webauthnApiService, 'getRegistrationOptions').mockRejectedValue(new Error('Test Error'));
        await expect(component.addPasskey()).rejects.toThrow('Test Error');
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
        passkey.labelBeforeEdit = 'Initial Label - before save';
        jest.spyOn(alertService, 'addErrorAlert');
        jest.spyOn(passkeySettingsApiService, 'updatePasskeyLabel').mockRejectedValue(new Error('Test Error'));

        await component.savePasskeyLabel(passkey);
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.save');
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
        jest.spyOn(alertService, 'addErrorAlert');
        jest.spyOn(passkeySettingsApiService, 'deletePasskey').mockRejectedValue(new Error('Test Error'));

        await component.deletePasskey(mockPasskeys[0]);
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.delete');
    });

    it('should return undefined when no passkey is provided', () => {
        const result = component.getDeleteSummary(undefined);
        expect(result).toBeUndefined();
    });

    it('should return a valid EntitySummary for a given passkey', async () => {
        const passkey: PasskeyDTO = {
            credentialId: '123',
            label: 'Test Passkey',
            created: '2023-10-01T12:00:00Z',
            lastUsed: '2023-10-02T12:00:00Z',
            isSuperAdminApproved: true,
        };

        const result = component.getDeleteSummary(passkey);

        if (result) {
            const summary = await firstValueFrom(result);
            expect(summary).toEqual({
                'artemisApp.userSettings.passkeySettingsPage.label': 'Test Passkey',
                'artemisApp.userSettings.passkeySettingsPage.created': '2023-10-01T12:00:00Z',
                'artemisApp.userSettings.passkeySettingsPage.lastUsed': '2023-10-02T12:00:00Z',
            });
        }
    });

    it('should cancel editing a passkey label', () => {
        const passkey: DisplayedPasskey = {
            credentialId: '123',
            label: 'New Label',
            labelBeforeEdit: 'Original Label',
            isEditingLabel: true,
            created: new Date().toISOString(),
            lastUsed: new Date().toISOString(),
            isSuperAdminApproved: true,
        };

        component.cancelEditPasskeyLabel(passkey);

        expect(passkey.isEditingLabel).toBeFalse();
        expect(passkey.label).toBe('Original Label');
    });

    it('should display editing label input field when isEditingLabel is true', async () => {
        const passkey: DisplayedPasskey = {
            credentialId: '123',
            label: 'Test Passkey',
            isEditingLabel: false,
            created: new Date().toISOString(),
            lastUsed: new Date().toISOString(),
            isSuperAdminApproved: true,
        };

        component.registeredPasskeys.set([passkey]);

        // Test with user (editing disabled)
        const user: User = { id: 1, login: 'user', authorities: [Authority.USER] };
        component.currentUser.set(user);
        fixture.detectChanges();

        let editingInput = fixture.nativeElement.querySelector('input[type="text"]');
        expect(editingInput).toBeNull();

        // Enable editing mode
        passkey.isEditingLabel = true;
        component.registeredPasskeys.set([passkey]);
        fixture.detectChanges();

        editingInput = fixture.nativeElement.querySelector('input[type="text"]');
        expect(editingInput).not.toBeNull();
    });
});
