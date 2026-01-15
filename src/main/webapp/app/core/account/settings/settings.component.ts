import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LANGUAGES } from 'app/core/language/shared/language.constants';
import { User } from 'app/core/user/user.model';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';

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
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, ArtemisTranslatePipe, FindLanguageFromKeyPipe],
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
    /** The current user's account data */
    readonly currentUser = signal<User | undefined>(undefined);
    /** List of available languages for the language selector */
    readonly languages = LANGUAGES;
    /** Whether self-registration is enabled (affects UI display) */
    readonly isRegistrationEnabled: boolean;
    /** Whether the current user is an internal user (can edit their name and email) */
    readonly isInternalUser = signal(false);
    /** Optional regex pattern restricting allowed email domains (e.g., university emails only) */
    readonly allowedEmailPattern?: string;
    /** Human-readable description of allowed email pattern for display in UI */
    readonly allowedEmailPatternReadable?: string;

    readonly settingsForm: FormGroup<SettingsForm>;

    constructor() {
        const profileInfo = this.profileService.getProfileInfo();
        this.isRegistrationEnabled = profileInfo.registrationEnabled || false;
        this.allowedEmailPattern = profileInfo.allowedEmailPattern;
        this.allowedEmailPatternReadable = profileInfo.allowedEmailPatternReadable;

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
        this.accountService.identity().then((user) => {
            if (user) {
                this.settingsForm.patchValue({
                    firstName: user.firstName,
                    lastName: user.lastName,
                    email: user.email,
                    langKey: user.langKey,
                });
                this.currentUser.set(user);
                this.isInternalUser.set(user.internal || false);
            }
        });
    }

    /**
     * Saves the user's settings to the server.
     * Updates first name, last name, email, and language preference.
     * If the language changed, updates the application's display language.
     */
    saveSettings() {
        this.success.set(false);
        this.errorEmailExists.set(false);

        const userToUpdate = this.currentUser();
        if (!userToUpdate) {
            return;
        }

        // Update user object with form values
        const firstName = this.settingsForm.controls.firstName.value;
        const lastName = this.settingsForm.controls.lastName.value;
        const email = this.settingsForm.controls.email.value;
        const langKey = this.settingsForm.controls.langKey.value;

        userToUpdate.firstName = firstName || undefined;
        userToUpdate.lastName = lastName || undefined;
        userToUpdate.email = email || undefined;
        userToUpdate.langKey = langKey || undefined;

        this.accountService.save(userToUpdate).subscribe({
            next: () => {
                this.success.set(true);
                this.accountService.authenticate(userToUpdate);

                // Update UI language if the user changed their language preference
                if (langKey && langKey !== this.translateService.getCurrentLang()) {
                    this.translateService.use(langKey);
                }
            },
            error: (response) => {
                if (response.status === 400 && response.error?.type === 'https://www.jhipster.tech/problem/email-already-used') {
                    this.errorEmailExists.set(true);
                } else {
                    this.success.set(false);
                }
            },
        });
    }
}
