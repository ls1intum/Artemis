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
    firstName: FormControl<string | undefined>;
    lastName: FormControl<string | undefined>;
    email: FormControl<string | undefined>;
    langKey: FormControl<string | undefined>;
}

/**
 * Component for managing user account settings.
 * Allows users to update their personal information (name) and language preference.
 * Note: Email changes are currently not supported as they would require re-verification.
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
    /** The current user's account data */
    readonly currentUser = signal<User | undefined>(undefined);
    /** List of available languages for the language selector */
    readonly languages = LANGUAGES;
    /** Whether self-registration is enabled (affects UI display) */
    readonly isRegistrationEnabled: boolean;

    readonly settingsForm = new FormGroup<SettingsForm>({
        firstName: new FormControl<string | undefined>(undefined, {
            nonNullable: true,
            validators: [Validators.required, Validators.minLength(2), Validators.maxLength(50)],
        }),
        lastName: new FormControl<string | undefined>(undefined, {
            nonNullable: true,
            validators: [Validators.required, Validators.minLength(2), Validators.maxLength(50)],
        }),
        email: new FormControl<string | undefined>(undefined, {
            nonNullable: true,
            validators: [Validators.required, Validators.minLength(5), Validators.maxLength(100), Validators.email],
        }),
        langKey: new FormControl<string | undefined>(undefined, { nonNullable: true }),
    });

    constructor() {
        this.isRegistrationEnabled = this.profileService.getProfileInfo().registrationEnabled || false;
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
            }
        });
    }

    /**
     * Saves the user's settings to the server.
     * Updates first name, last name, and language preference.
     * If the language changed, updates the application's display language.
     * Note: Email changes are disabled as they would require sending a new activation link.
     */
    saveSettings() {
        this.success.set(false);

        const userToUpdate = this.currentUser();
        if (!userToUpdate) {
            return;
        }

        // Update user object with form values
        // Note: Email changes are not supported - would require re-verification
        userToUpdate.firstName = this.settingsForm.controls.firstName.value || undefined;
        userToUpdate.lastName = this.settingsForm.controls.lastName.value || undefined;
        userToUpdate.langKey = this.settingsForm.controls.langKey.value || undefined;

        this.accountService.save(userToUpdate).subscribe({
            next: () => {
                this.success.set(true);
                this.accountService.authenticate(userToUpdate);

                // Update UI language if the user changed their language preference
                if (userToUpdate.langKey !== this.translateService.getCurrentLang()) {
                    this.translateService.use(userToUpdate.langKey!);
                }
            },
            error: () => this.success.set(false),
        });
    }
}
