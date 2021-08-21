import { Component, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subscription, tap } from 'rxjs';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
//used only in the client
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
    optionCore: OptionCore;
}

export interface OptionCore {
    id?: number;
    optionSpecifier: string;
    webapp: boolean;
    email?: boolean;
    changed?: boolean;
}

@Component({
    selector: 'jhi-user-settings',
    templateUrl: 'user-settings.component.html',
    styleUrls: ['user-settings.component.scss'],
})
export class UserSettingsComponent {
    currentUser?: User;

    private authStateSubscription: Subscription;

    constructor(private accountService: AccountService, private userSettingsService: UserSettingsService) {}

    ngOnInit() {
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(tap((user: User) => (this.currentUser = user)))
            .subscribe();
    }
}
