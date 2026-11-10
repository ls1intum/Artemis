import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogComponent } from '@tumaet/ui-angular';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AccountSettingsDialogComponent } from 'app/account/settings/account-settings-dialog.component';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('AccountSettingsDialogComponent', () => {
    let fixture: ComponentFixture<AccountSettingsDialogComponent>;
    let component: AccountSettingsDialogComponent;
    let userIdentity: ReturnType<typeof signal<User | undefined>>;
    let accountService: { userIdentity: typeof userIdentity; save: ReturnType<typeof vi.fn>; authenticate: ReturnType<typeof vi.fn> };
    let profileService: { getProfileInfo: ReturnType<typeof vi.fn>; isModuleFeatureActive: ReturnType<typeof vi.fn> };

    const user: User = {
        internal: true,
        name: 'Ada Lovelace',
        firstName: 'Ada',
        lastName: 'Lovelace',
        email: 'ada@example.com',
        langKey: 'en',
        login: 'ada',
        authorities: [],
    };

    beforeEach(async () => {
        userIdentity = signal<User | undefined>({ ...user });
        accountService = {
            userIdentity,
            save: vi.fn().mockReturnValue(of(new HttpResponse<User>({ body: userIdentity() }))),
            authenticate: vi.fn(),
        };
        profileService = {
            getProfileInfo: vi.fn().mockReturnValue({}),
            isModuleFeatureActive: vi.fn().mockReturnValue(false),
        };

        await TestBed.configureTestingModule({
            imports: [AccountSettingsDialogComponent],
            providers: [
                { provide: AccountService, useValue: accountService },
                { provide: ProfileService, useValue: profileService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
    });

    function createComponent(): void {
        fixture = TestBed.createComponent(AccountSettingsDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    }

    it('uses the AET dialog and does not offer a language control', () => {
        createComponent();

        expect(fixture.debugElement.query(By.directive(TumAetUiDialogComponent))).toBeTruthy();
        expect('langKey' in component.settingsModel()).toBe(false);
        expect(fixture.nativeElement.querySelector('[formControlName="langKey"]')).toBeNull();
    });

    it('loads the current account when opened', () => {
        createComponent();

        component.initializeForm();

        expect(component.settingsModel()).toEqual({ firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' });
    });

    it('only enables saving while valid account settings differ from the current account', () => {
        createComponent();
        component.initializeForm();
        component.visible.set(true);
        fixture.detectChanges();

        const saveButton = document.querySelector<HTMLButtonElement>('[data-testid="account-settings-save-button"]')!;
        expect(saveButton.disabled).toBe(true);

        component.settingsForm.firstName().value.set('Grace');
        fixture.detectChanges();
        expect(saveButton.disabled).toBe(false);

        component.settingsForm.firstName().value.set('Ada');
        fixture.detectChanges();
        expect(saveButton.disabled).toBe(true);
    });

    it('saves through native form submission and prevents navigation', () => {
        createComponent();
        component.initializeForm();
        component.visible.set(true);
        fixture.detectChanges();
        component.settingsForm.firstName().value.set('Grace');
        fixture.detectChanges();
        const form = document.querySelector<HTMLFormElement>('#account-settings-form')!;
        const submitEvent = new SubmitEvent('submit', { bubbles: true, cancelable: true });

        const notCancelled = form.dispatchEvent(submitEvent);

        expect(notCancelled).toBe(false);
        expect(accountService.save).toHaveBeenCalledWith(expect.objectContaining({ firstName: 'Grace' }));
    });

    it('saves name and email, updates the authenticated account, and closes the dialog', () => {
        createComponent();
        component.initializeForm();
        component.visible.set(true);
        component.settingsModel.set({ firstName: 'Grace', lastName: 'Hopper', email: 'grace@example.com' });

        component.saveSettings();

        expect(accountService.save).toHaveBeenCalledWith(expect.objectContaining({ firstName: 'Grace', lastName: 'Hopper', email: 'grace@example.com', langKey: 'en' }));
        expect(accountService.authenticate).toHaveBeenCalledWith(
            expect.objectContaining({ name: 'Grace Hopper', firstName: 'Grace', lastName: 'Hopper', email: 'grace@example.com' }),
        );
        expect(userIdentity()).toEqual(expect.objectContaining({ firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' }));
        expect(component.visible()).toBe(false);
    });

    it('only derives a combined name longer than 50 characters after saving', () => {
        const firstName = 'A'.repeat(30);
        const lastName = 'B'.repeat(30);
        let requestName: string | undefined;
        accountService.save.mockImplementation((userToSave: User) => {
            requestName = userToSave.name;
            return of(new HttpResponse<User>({ body: userToSave }));
        });
        createComponent();
        component.initializeForm();
        component.settingsModel.set({ firstName, lastName, email: 'ada@example.com' });

        component.saveSettings();

        expect(requestName).toBe('Ada Lovelace');
        expect(accountService.authenticate).toHaveBeenCalledWith(expect.objectContaining({ name: `${firstName} ${lastName}`, firstName, lastName }));
    });

    it('keeps the dialog open when saving fails', () => {
        accountService.save.mockReturnValue(throwError(() => new Error('Save failed')));
        createComponent();
        component.initializeForm();
        component.visible.set(true);
        component.settingsModel.set({ firstName: 'Grace', lastName: 'Hopper', email: 'grace@example.com' });

        component.saveSettings();

        expect(userIdentity()).toEqual(expect.objectContaining({ firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' }));
        expect(component.visible()).toBe(true);
    });

    it('uses the configured email pattern', () => {
        profileService.getProfileInfo.mockReturnValue({
            allowedEmailPattern: '[a-zA-Z0-9_\\-\\.\\+]+@(tum\\.de|in\\.tum\\.de|mytum\\.de)',
            allowedEmailPatternReadable: '@tum.de, @in.tum.de, @mytum.de',
        });
        createComponent();

        component.settingsForm.email().value.set('ada@tum.de.example.com');
        expect(component.settingsForm.email().valid()).toBe(false);
        component.settingsForm.email().value.set('ada@tum.de');
        expect(component.settingsForm.email().valid()).toBe(true);
        expect(component.allowedEmailPatternReadable).toBe('@tum.de, @in.tum.de, @mytum.de');
    });

    it('keeps identity-managed fields disabled for SAML2 users', () => {
        profileService.isModuleFeatureActive.mockReturnValue(true);
        createComponent();
        component.initializeForm();

        expect(component.settingsForm.firstName().disabled()).toBe(true);
        expect(component.settingsForm.lastName().disabled()).toBe(true);
        expect(component.settingsForm.email().disabled()).toBe(true);

        component.saveSettings();
        expect(accountService.save).not.toHaveBeenCalled();
    });
});
