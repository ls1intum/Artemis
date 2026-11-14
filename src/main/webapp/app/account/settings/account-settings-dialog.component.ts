import { ChangeDetectionStrategy, Component, computed, inject, input, model, output, signal } from '@angular/core';
import { FormField, disabled, email, form, maxLength, minLength, pattern, required } from '@angular/forms/signals';
import { MODULE_FEATURE_SAML2 } from 'app/app.constants';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';
import { PendingEmailChangeDTO } from 'app/account/user/shared/dto/pending-email-change.dto';
import { TumAetUiButtonDirective, TumAetUiDialogComponent, TumAetUiFormFieldComponent, TumAetUiInputDirective, TumAetUiMessageComponent } from '@tumaet/ui-angular';

/** Error keys of the server that are shown as their own message. All other failures show a generic message. */
const EMAIL_CHANGE_ERROR_KEYS = ['emailInvalid', 'emailChangeUnavailable', 'emailChangeRequestedTooRecently', 'noPendingEmailChange'];
const GENERIC_ERROR_MESSAGE_KEY = 'settings.messages.error.fail';

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
        TumAetUiButtonDirective,
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
    /** The request to change the email address that waits for confirmation, used to tell a new request from an unchanged one */
    readonly pendingEmailChange = input<PendingEmailChangeDTO | undefined>(undefined);
    /** Emits the request that a save created or replaced: the confirmation mails were sent and the email address stays the current one until both are confirmed */
    readonly emailChangeRequested = output<PendingEmailChangeDTO>();
    /** Indicates the email already exists error */
    readonly errorEmailExists = signal(false);
    /** Translation key of the error to show for any other failure, undefined when there is none */
    readonly errorMessageKey = signal<string | undefined>(undefined);
    /** Whether the server can send emails. Without it an email change cannot be confirmed, so the email cannot be changed. */
    readonly mailConfigured = this.profileInfo.mailConfigured ?? true;
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
            pattern(path.email, new RegExp(`^(?:${allowedEmailPattern})$`));
        } else {
            email(path.email);
        }
        const fieldsDisabled = () => !this.accountService.userIdentity()?.internal || this.isSaml2Active();
        disabled(path.firstName, { when: fieldsDisabled });
        disabled(path.lastName, { when: fieldsDisabled });
        disabled(path.email, { when: () => fieldsDisabled() || !this.mailConfigured });
    });

    initializeForm(): void {
        this.errorEmailExists.set(false);
        this.errorMessageKey.set(undefined);
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

    onSubmit(event: SubmitEvent): void {
        event.preventDefault();
        this.saveSettings();
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
            userToSave.email = settings.email;
        }

        this.errorEmailExists.set(false);
        this.errorMessageKey.set(undefined);
        const previousRequestTime = this.pendingEmailChange()?.requestedAt;
        this.accountService.save(userToSave).subscribe({
            next: (response) => {
                const pendingEmailChange = response.pendingEmailChange ?? undefined;
                // Database reads round the initial Instant to milliseconds; distinct requests are at least a minute apart.
                if (
                    pendingEmailChange !== undefined &&
                    userToSave.email !== user.email &&
                    (previousRequestTime === undefined || Math.abs(Date.parse(pendingEmailChange.requestedAt) - Date.parse(previousRequestTime)) > 2)
                ) {
                    this.emailChangeRequested.emit(pendingEmailChange);
                }
                // The email address of the account stays the current one until the change is confirmed from both addresses.
                const updatedUser = cloneWith(userToSave, { email: user.email, name: `${userToSave.firstName} ${userToSave.lastName}` });
                this.accountService.authenticate(updatedUser);
                this.visible.set(false);
            },
            error: (response) => this.handleError(response),
        });
    }

    /**
     * Maps account-save errors to localized feedback in the dialog, which stays open so the input can be corrected.
     * @param response the server error returned by the save request
     */
    private handleError(response: { status?: number; error?: { type?: string; errorKey?: string } }): void {
        const errorKey = response.error?.errorKey;
        if (response.status === 400 && (response.error?.type === 'https://www.jhipster.tech/problem/email-already-used' || errorKey === 'emailAlreadyUsed')) {
            this.errorEmailExists.set(true);
        } else if (response.status === 400 && errorKey && EMAIL_CHANGE_ERROR_KEYS.includes(errorKey)) {
            this.errorMessageKey.set(`settings.messages.error.${errorKey}`);
        } else {
            this.errorMessageKey.set(GENERIC_ERROR_MESSAGE_KEY);
        }
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
