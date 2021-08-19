import { Component, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subscription, tap } from 'rxjs';

export interface SettingsCategory {
    name: string;
    groups: OptionGroup[];
}

export interface OptionGroup {
    name: string;
    // todo securityLevel : STUDENT (new enum), maybe add this to settings category and option as well
    options: Option[];
}

export interface Option {
    name: string;
    description: string;
    webapp: boolean;
}

@Component({
    selector: 'jhi-user-settings',
    templateUrl: 'user-settings.component.html',
    styleUrls: ['user-settings.component.scss'],
})
export class UserSettingsComponent implements OnInit {
    currAccount?: User;

    private authStateSubscription: Subscription;

    constructor(private accountService: AccountService) {}

    ngOnInit() {
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(tap((user: User) => (this.currAccount = user)))
            .subscribe();
    }
}
