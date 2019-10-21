import { Component, OnInit } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { Account, AccountService, JhiLanguageHelper } from '../../core';

@Component({
    selector: 'jhi-settings',
    templateUrl: './settings.component.html',
})
export class SettingsComponent implements OnInit {
    error: string | null;
    success: string | null;
    settingsAccount: Account;
    languages: string[];

    constructor(private account: AccountService, private accountService: AccountService, private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {}

    ngOnInit() {
        this.accountService.identity().then(user => {
            this.settingsAccount = this.copyAccount(user!);
        });
        this.languageHelper.getAll().then(languages => {
            this.languages = languages;
        });
    }

    /**
     * Saves the current user account, writing all changes made to the database.
     */
    save() {
        this.account.save(this.settingsAccount).subscribe(
            () => {
                this.error = null;
                this.success = 'OK';
                this.accountService.identity(true).then(user => {
                    this.settingsAccount = this.copyAccount(user!);
                });
                this.languageService.getCurrent().then(current => {
                    if (this.settingsAccount.langKey !== null && this.settingsAccount.langKey !== current) {
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

    /**
     * Creates a copy of the given account. Note, that this copy is not written to the database. It rather just
     * creates a copy of the JS object for convenience purposes.
     *
     * @param account The account, for which to create an identical copy (object)
     */
    copyAccount(account: Account): Account {
        return new Account(
            account.activated || undefined,
            account.authorities || undefined,
            account.email || undefined,
            account.firstName || undefined,
            account.langKey || undefined,
            account.lastName || undefined,
            account.login || undefined,
            account.imageUrl || undefined,
        );
    }
}
