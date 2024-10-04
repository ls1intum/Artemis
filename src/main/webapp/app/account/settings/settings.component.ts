import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { LANGUAGES } from 'app/core/language/language.constants';
import { User } from 'app/core/user/user.model';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-settings',
    templateUrl: './settings.component.html',
    standalone: true,
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, ArtemisSharedCommonModule, ArtemisSharedModule],
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
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.isRegistrationEnabled = profileInfo.registrationEnabled || false;
            }
        });
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
                if (this.account.langKey !== this.translateService.currentLang) {
                    this.translateService.use(this.account.langKey!);
                }
            },
            error: () => (this.success = false),
        });
    }
}
