import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LANGUAGES } from 'app/core/language/shared/language.constants';
import { User } from 'app/core/user/user.model';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';

@Component({
    selector: 'jhi-settings',
    templateUrl: './settings.component.html',
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, ArtemisTranslatePipe, FindLanguageFromKeyPipe],
})
export class SettingsComponent implements OnInit {
    private accountService = inject(AccountService);
    private fb = inject(FormBuilder);
    private translateService = inject(TranslateService);
    private profileService = inject(ProfileService);

    success = false;
    account: User;
    languages = LANGUAGES;
    settingsForm: FormGroup;
    isRegistrationEnabled = false;

    ngOnInit() {
        this.isRegistrationEnabled = this.profileService.getProfileInfo().registrationEnabled || false;
        this.accountService.identity().then((user) => {
            if (user) {
                this.settingsForm.patchValue({
                    firstName: user.firstName,
                    lastName: user.lastName,
                    email: user.email,
                    langKey: user.langKey,
                });
                this.account = user;
            }
        });
        this.initializeForm();
    }

    private initializeForm() {
        if (this.settingsForm) {
            return;
        }
        this.settingsForm = this.fb.group({
            firstName: [undefined as string | undefined, [Validators.required, Validators.minLength(2), Validators.maxLength(50)]],
            lastName: [undefined as string | undefined, [Validators.required, Validators.minLength(2), Validators.maxLength(50)]],
            email: [undefined as string | undefined, [Validators.required, Validators.minLength(5), Validators.maxLength(100), Validators.email]],
            langKey: [undefined as string | undefined],
        });
    }

    /**
     * Saves the current user account, writing all changes made to the database.
     */
    save() {
        this.success = false;
        // Note: changing the email is currently not supported, because we would need to send another activation link
        this.account.firstName = this.settingsForm.get('firstName')!.value || undefined;
        this.account.lastName = this.settingsForm.get('lastName')!.value || undefined;
        this.account.langKey = this.settingsForm.get('langKey')!.value || undefined;

        this.accountService.save(this.account).subscribe({
            next: () => {
                this.success = true;
                this.accountService.authenticate(this.account);
                if (this.account.langKey !== this.translateService.getCurrentLang()) {
                    this.translateService.use(this.account.langKey!);
                }
            },
            error: () => (this.success = false),
        });
    }
}
