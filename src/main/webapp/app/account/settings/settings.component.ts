import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LANGUAGES } from 'app/core/language/shared/language.constants';
import { User } from 'app/account/user/user.model';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FindLanguageFromKeyPipe } from 'app/foundation/language/find-language-from-key.pipe';
import { TumAetUiButtonComponent, TumAetUiFormFieldComponent, TumAetUiInputDirective, TumAetUiMessageComponent, TumAetUiSelectComponent } from '@tumaet/ui-angular';
import { MODULE_FEATURE_SAML2 } from 'app/app.constants';
import { PendingEmailChangeDTO } from 'app/account/user/shared/dto/pending-email-change.dto';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';

/** Error keys of the server that are shown as their own message. All other failures show a generic message. */
const EMAIL_CHANGE_ERROR_KEYS = ['emailInvalid', 'emailChangeUnavailable', 'emailChangeRequestedTooRecently', 'noPendingEmailChange'];
const GENERIC_ERROR_MESSAGE_KEY = 'settings.messages.error.fail';

/**
 * Type definition for the user settings form controls.
 */
interface SettingsForm {
    firstName: FormControl<string>;
    lastName: FormControl<string>;
    email: FormControl<string>;
    langKey: FormControl<string>;
}

/**
 * Component for managing user account settings.
 * Allows users to update their personal information (name, email) and language preference.
 */
@Component({
    selector: 'jhi-settings',
    templateUrl: './settings.component.html',
    imports: [
        TranslateDirective,
        FormsModule,
        ReactiveFormsModule,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        TumAetUiButtonComponent,
        TumAetUiFormFieldComponent,
        TumAetUiInputDirective,
        TumAetUiMessageComponent,
        TumAetUiSelectComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent implements OnInit {
    private readonly accountService = inject(AccountService);
    private readonly translateService = inject(TranslateService);
    private readonly profileService = inject(ProfileService);

    /** Indicates the settings were successfully saved */
    readonly success = signal(false);
    /** Indicates email already exists error */
    readonly errorEmailExists = signal(false);
    /** Translation key of the error to show for any other failure, undefined when there is none */
    readonly errorMessageKey = signal<string | undefined>(undefined);
    /** The request to change the email address that waits for confirmation, undefined when there is none */
    readonly pendingEmailChange = signal<PendingEmailChangeDTO | undefined>(undefined);
    /** Indicates the last save created or replaced an email change request, so confirmation mails were sent */
    readonly emailChangeRequested = signal(false);
    /** Indicates the confirmation mails of the pending email change were sent again */
    readonly confirmationMailsResent = signal(false);
    /** Indicates the pending email change was cancelled */
    readonly emailChangeCancelled = signal(false);
    /** Indicates a resend or cancel request of the pending email change is running */
    readonly pendingChangeRequestRunning = signal(false);
    /** Whether the server can send emails. Without it an email change cannot be confirmed, so the email cannot be changed. */
    readonly mailConfigured = signal(true);
    /** The current user's account data */
    readonly currentUser = signal<User | undefined>(undefined);
    /** List of available languages for the language selector */
    readonly languages = LANGUAGES;
    /** Language options for the selector, labelled with each language's own name. Constant: LANGUAGES is static. */
    protected readonly languageOptions = LANGUAGES.map((language) => ({ label: new FindLanguageFromKeyPipe().transform(language), value: language }));
    /** Whether self-registration is enabled (affects UI display) */
    readonly isRegistrationEnabled = signal(false);
    /** Whether the current user is an internal user (can edit their name and email) */
    readonly isInternalUser = signal(false);
    /** Whether the SAML2 profile is active (names/email are synced from IdP and cannot be changed) */
    readonly isSaml2Active = signal(false);
    /** Optional regex pattern restricting allowed email domains (e.g., university emails only) */
    readonly allowedEmailPattern?: string;
    /** Human-readable description of allowed email pattern for display in UI */
    readonly allowedEmailPatternReadable = signal<string | undefined>(undefined);

    readonly settingsForm: FormGroup<SettingsForm>;

    constructor() {
        const profileInfo = this.profileService.getProfileInfo();
        this.isRegistrationEnabled.set(profileInfo.registrationEnabled || false);
        this.isSaml2Active.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_SAML2));
        this.allowedEmailPattern = profileInfo.allowedEmailPattern;
        this.allowedEmailPatternReadable.set(profileInfo.allowedEmailPatternReadable);
        this.mailConfigured.set(profileInfo.mailConfigured);

        this.settingsForm = new FormGroup<SettingsForm>({
            firstName: new FormControl<string>('', {
                nonNullable: true,
                validators: [Validators.required, Validators.minLength(2), Validators.maxLength(50)],
            }),
            lastName: new FormControl<string>('', {
                nonNullable: true,
                validators: [Validators.required, Validators.minLength(2), Validators.maxLength(50)],
            }),
            email: new FormControl<string>('', {
                nonNullable: true,
                // Use pattern validation if email domain is restricted, otherwise standard email validation
                validators: this.allowedEmailPattern
                    ? [Validators.required, Validators.minLength(5), Validators.maxLength(100), Validators.pattern(this.allowedEmailPattern)]
                    : [Validators.required, Validators.minLength(5), Validators.maxLength(100), Validators.email],
            }),
            langKey: new FormControl<string>('', { nonNullable: true }),
        });
    }

    /**
     * Loads the current user's account data and populates the form.
     */
    ngOnInit() {
        void this.accountService.identity().then((user) => {
            if (user) {
                this.settingsForm.patchValue({
                    firstName: user.firstName,
                    lastName: user.lastName,
                    email: user.email,
                    langKey: user.langKey,
                });
                this.currentUser.set(user);
                this.isInternalUser.set(user.internal || false);
                this.updateNameAndEmailControlState(user.internal || false);
                if (user.internal && !this.isSaml2Active()) {
                    this.loadPendingEmailChange();
                }
            }
        });
    }

    private loadPendingEmailChange(): void {
        this.accountService.getPendingEmailChange().subscribe((pendingEmailChange) => this.pendingEmailChange.set(pendingEmailChange));
    }

    /**
     * Enables or disables the firstName, lastName, and email form controls based on
     * whether the user is internal and whether SAML2 is active. The email is also disabled when the server cannot send mails.
     * Disabled controls are excluded from validation, preventing spurious errors.
     */
    private updateNameAndEmailControlState(isInternal: boolean): void {
        const shouldDisable = !isInternal || this.isSaml2Active();
        const action = shouldDisable ? 'disable' : 'enable';
        this.settingsForm.controls.firstName[action]();
        this.settingsForm.controls.lastName[action]();
        this.settingsForm.controls.email[shouldDisable || !this.mailConfigured() ? 'disable' : 'enable']();
    }

    /**
     * Saves the user's settings to the server.
     * Updates first name, last name and language preference. A changed email address is not applied: the server
     * creates a pending request and mails a confirmation link to the current and the new address. The account
     * keeps its current email address until both links are confirmed, so the form shows the current address again.
     * If the language changed, updates the application's display language.
     */
    saveSettings() {
        this.resetMessages();

        const currentUser = this.currentUser();
        if (!currentUser) {
            return;
        }

        const firstName = this.settingsForm.controls.firstName.value;
        const lastName = this.settingsForm.controls.lastName.value;
        const email = this.settingsForm.controls.email.value;
        const langKey = this.settingsForm.controls.langKey.value;

        const userToSave = deepClone(currentUser);
        // When SAML2 is active, name and email are managed by the IdP — only langKey can be changed
        if (!this.isSaml2Active()) {
            userToSave.firstName = firstName || undefined;
            userToSave.lastName = lastName || undefined;
            userToSave.email = email || undefined;
        }
        userToSave.langKey = langKey || undefined;

        this.accountService.save(userToSave).subscribe({
            next: (response) => {
                this.success.set(true);
                const pendingEmailChange = response.pendingEmailChange ?? undefined;
                this.pendingEmailChange.set(pendingEmailChange);
                this.emailChangeRequested.set(pendingEmailChange !== undefined && userToSave.email !== currentUser.email);

                // The email address of the account stays the current one until the change is confirmed from both addresses.
                const updatedUser = cloneWith(userToSave, { email: currentUser.email });
                this.currentUser.set(updatedUser);
                this.accountService.authenticate(updatedUser);
                this.settingsForm.controls.email.setValue(currentUser.email ?? '');

                // Update UI language if the user changed their language preference
                if (langKey && langKey !== this.translateService.getCurrentLang()) {
                    this.translateService.use(langKey);
                }
            },
            error: (response) => this.handleError(response),
        });
    }

    /**
     * Sends the confirmation links of the pending email change to both addresses again.
     * Earlier confirmations are reset and the validity of the request restarts.
     */
    resendConfirmationMails() {
        this.resetMessages();
        this.pendingChangeRequestRunning.set(true);
        this.accountService.resendPendingEmailChange().subscribe({
            next: (pendingEmailChange) => {
                this.pendingChangeRequestRunning.set(false);
                this.pendingEmailChange.set(pendingEmailChange);
                this.confirmationMailsResent.set(true);
            },
            error: (response) => {
                this.pendingChangeRequestRunning.set(false);
                this.handleError(response);
            },
        });
    }

    /**
     * Cancels the pending email change. The email address of the account was never changed, so nothing else is reverted.
     */
    cancelEmailChange() {
        this.resetMessages();
        this.pendingChangeRequestRunning.set(true);
        this.accountService.cancelPendingEmailChange().subscribe({
            next: () => {
                this.pendingChangeRequestRunning.set(false);
                this.pendingEmailChange.set(undefined);
                this.emailChangeCancelled.set(true);
            },
            error: () => {
                this.pendingChangeRequestRunning.set(false);
                this.errorMessageKey.set(GENERIC_ERROR_MESSAGE_KEY);
            },
        });
    }

    private resetMessages(): void {
        this.success.set(false);
        this.errorEmailExists.set(false);
        this.errorMessageKey.set(undefined);
        this.emailChangeRequested.set(false);
        this.confirmationMailsResent.set(false);
        this.emailChangeCancelled.set(false);
    }

    private handleError(response: { status?: number; error?: { type?: string; errorKey?: string } }): void {
        this.success.set(false);
        const errorKey = response.error?.errorKey;
        if (response.status === 400 && (response.error?.type === 'https://www.jhipster.tech/problem/email-already-used' || errorKey === 'emailAlreadyUsed')) {
            this.errorEmailExists.set(true);
        } else if (response.status === 400 && errorKey && EMAIL_CHANGE_ERROR_KEYS.includes(errorKey)) {
            this.errorMessageKey.set(`settings.messages.error.${errorKey}`);
        } else {
            this.errorMessageKey.set(GENERIC_ERROR_MESSAGE_KEY);
        }
    }
}
