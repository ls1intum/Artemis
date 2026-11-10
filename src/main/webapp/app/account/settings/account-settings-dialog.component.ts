import { ChangeDetectionStrategy, Component, computed, inject, model, signal } from '@angular/core';
import { FormField, disabled, email, form, maxLength, minLength, pattern, required } from '@angular/forms/signals';
import { MODULE_FEATURE_SAML2 } from 'app/app.constants';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { TumAetUiButtonComponent, TumAetUiDialogComponent, TumAetUiFormFieldComponent, TumAetUiInputDirective, TumAetUiMessageComponent } from '@tumaet/ui-angular';

interface AccountSettingsFormModel {
    firstName: string;
    lastName: string;
    email: string;
}

@Component({
    selector: 'jhi-account-settings-dialog',
    templateUrl: './account-settings-dialog.component.html',
    imports: [
        FormField,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiButtonComponent,
        TumAetUiDialogComponent,
        TumAetUiFormFieldComponent,
        TumAetUiInputDirective,
        TumAetUiMessageComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountSettingsDialogComponent {
    private readonly accountService = inject(AccountService);
    private readonly profileService = inject(ProfileService);
    private readonly profileInfo = this.profileService.getProfileInfo();

    readonly visible = model(false);
    readonly isSaml2Active = signal(this.profileService.isModuleFeatureActive(MODULE_FEATURE_SAML2));
    readonly allowedEmailPatternReadable = this.profileInfo.allowedEmailPatternReadable ?? this.profileInfo.allowedEmailPattern;

    readonly settingsModel = signal<AccountSettingsFormModel>({ firstName: '', lastName: '', email: '' });
    readonly settingsChanged = computed(() => this.haveSettingsChanged());
    readonly settingsForm = form(this.settingsModel, (path) => {
        required(path.firstName);
        minLength(path.firstName, 2);
        maxLength(path.firstName, 50);
        required(path.lastName);
        minLength(path.lastName, 2);
        maxLength(path.lastName, 50);
        required(path.email);
        minLength(path.email, 5);
        maxLength(path.email, 100);
        const allowedEmailPattern = this.profileInfo.allowedEmailPattern;
        if (allowedEmailPattern) {
            pattern(path.email, new RegExp(allowedEmailPattern));
        } else {
            email(path.email);
        }
        const fieldsDisabled = () => !this.accountService.userIdentity()?.internal || this.isSaml2Active();
        disabled(path.firstName, { when: fieldsDisabled });
        disabled(path.lastName, { when: fieldsDisabled });
        disabled(path.email, { when: fieldsDisabled });
    });

    initializeForm(): void {
        const user = this.accountService.userIdentity();
        if (!user) {
            return;
        }

        this.settingsForm().reset({
            firstName: user.firstName ?? '',
            lastName: user.lastName ?? '',
            email: user.email ?? '',
        });
    }

    saveSettings(): void {
        const user = this.accountService.userIdentity();
        if (!user || this.settingsForm().invalid() || !this.settingsChanged()) {
            return;
        }

        const userToSave = deepClone(user);
        if (!this.isSaml2Active()) {
            const settings = this.settingsModel();
            userToSave.firstName = settings.firstName;
            userToSave.lastName = settings.lastName;
            userToSave.name = `${settings.firstName} ${settings.lastName}`;
            userToSave.email = settings.email;
        }

        this.accountService.save(userToSave).subscribe({
            next: () => {
                this.accountService.authenticate(userToSave);
                this.visible.set(false);
            },
            // All possible failures are already handled through automatic alerts.
            error: () => {},
        });
    }

    private haveSettingsChanged(): boolean {
        const user = this.accountService.userIdentity();
        if (!user) {
            return false;
        }

        const settings = this.settingsModel();
        return settings.firstName !== (user.firstName ?? '') || settings.lastName !== (user.lastName ?? '') || settings.email !== (user.email ?? '');
    }
}
