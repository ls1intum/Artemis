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

interface SettingsForm {
    firstName: FormControl<string | undefined>;
    lastName: FormControl<string | undefined>;
    email: FormControl<string | undefined>;
    langKey: FormControl<string | undefined>;
}

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

    readonly success = signal(false);
    readonly account = signal<User | undefined>(undefined);
    readonly languages = LANGUAGES;
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

    ngOnInit() {
        this.accountService.identity().then((user) => {
            if (user) {
                this.settingsForm.patchValue({
                    firstName: user.firstName,
                    lastName: user.lastName,
                    email: user.email,
                    langKey: user.langKey,
                });
                this.account.set(user);
            }
        });
    }

    /**
     * Saves the current user account, writing all changes made to the database.
     */
    save() {
        this.success.set(false);
        const currentAccount = this.account();
        if (!currentAccount) {
            return;
        }

        // Note: changing the email is currently not supported, because we would need to send another activation link
        currentAccount.firstName = this.settingsForm.controls.firstName.value || undefined;
        currentAccount.lastName = this.settingsForm.controls.lastName.value || undefined;
        currentAccount.langKey = this.settingsForm.controls.langKey.value || undefined;

        this.accountService.save(currentAccount).subscribe({
            next: () => {
                this.success.set(true);
                this.accountService.authenticate(currentAccount);
                if (currentAccount.langKey !== this.translateService.getCurrentLang()) {
                    this.translateService.use(currentAccount.langKey!);
                }
            },
            error: () => this.success.set(false),
        });
    }
}
