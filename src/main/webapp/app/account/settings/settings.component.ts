import { Component, OnInit } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { Account, AccountService, JhiLanguageHelper } from '../../core';

@Component({
    selector: 'jhi-settings',
    templateUrl: './settings.component.html'
})
export class SettingsComponent implements OnInit {
    error: string;
    success: string;
    settingsAccount: Account;
    languages: string[];

    constructor(
        private account: AccountService,
        private accountService: AccountService,
        private languageService: JhiLanguageService,
        private languageHelper: JhiLanguageHelper
    ) {}

    ngOnInit() {
        this.accountService.identity().then(user => {
            this.settingsAccount = this.copyAccount(user);
        });
        this.languageHelper.getAll().then(languages => {
            this.languages = languages;
        });
    }

    save() {
        this.account.save(this.settingsAccount).subscribe(
            () => {
                this.error = null;
                this.success = 'OK';
                this.accountService.identity(true).then(user => {
                    this.settingsAccount = this.copyAccount(user);
                });
                this.languageService.getCurrent().then(current => {
                    if (this.settingsAccount.langKey !== current) {
                        this.languageService.changeLanguage(this.settingsAccount.langKey);
                    }
                });
            },
            () => {
                this.success = null;
                this.error = 'ERROR';
            }
        );
    }

    copyAccount(account: Account) {
        return new Account(
            account.activated,
            account.authorities,
            account.email,
            account.firstName,
            account.langKey,
            account.lastName,
            account.login,
            account.imageUrl
        );
    }
}
