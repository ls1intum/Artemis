import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { JhiLanguageService } from 'ng-jhipster';

import { AccountService } from 'app/core/auth/account.service';
import { Account } from 'app/core/user/account.model';
import { JhiLanguageHelper } from 'app/core/language/language.helper';

@Component({
    selector: 'jhi-settings',
    templateUrl: './settings.component.html',
})
export class SettingsComponent implements OnInit {
    error: string | null;
    success: string | null;
    settingsAccount: Account;
    languages: string[];
    settingsForm = this.fb.group({
        firstName: [undefined, [Validators.required, Validators.minLength(1), Validators.maxLength(50)]],
        lastName: [undefined, [Validators.required, Validators.minLength(1), Validators.maxLength(50)]],
        email: [undefined, [Validators.required, Validators.minLength(5), Validators.maxLength(254), Validators.email]],
        activated: [false],
        authorities: [[]],
        langKey: ['en'],
        login: [],
        imageUrl: [],
    });

    constructor(private accountService: AccountService, private fb: FormBuilder, private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {}

    ngOnInit() {
        this.accountService.identity().then((user) => {
            this.updateForm(user!);
        });
        this.languages = this.languageHelper.getAll();
    }

    /**
     * Saves the current user account, writing all changes made to the database.
     */
    save() {
        const settingsAccount = this.accountFromForm();
        this.accountService.save(settingsAccount).subscribe(
            () => {
                this.error = null;
                this.success = 'OK';
                this.accountService.identity(true).then((user) => {
                    this.updateForm(user!);
                });
                this.languageService.getCurrent().then((current) => {
                    if (this.settingsAccount.langKey && this.settingsAccount.langKey !== current) {
                        this.languageService.changeLanguage(this.settingsAccount.langKey);
                    }
                });
            },
            () => {
                this.success = null;
                this.error = 'ERROR';
            },
        );
    }

    private accountFromForm(): any {
        const account = {};
        return {
            ...account,
            firstName: this.settingsForm.get('firstName')!.value,
            lastName: this.settingsForm.get('lastName')!.value,
            email: this.settingsForm.get('email')!.value,
            activated: this.settingsForm.get('activated')!.value,
            authorities: this.settingsForm.get('authorities')!.value,
            langKey: this.settingsForm.get('langKey')!.value,
            login: this.settingsForm.get('login')!.value,
            imageUrl: this.settingsForm.get('imageUrl')!.value,
        };
    }

    updateForm(account: Account): void {
        this.settingsForm.patchValue({
            firstName: account.firstName,
            lastName: account.lastName,
            email: account.email,
            activated: account.activated,
            authorities: account.authorities,
            langKey: account.langKey,
            login: account.login,
            imageUrl: account.imageUrl,
        });
    }
}
