import { Component, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subscription, tap } from 'rxjs';
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
/*
export interface UserOption {
    id?: number;
    changed?: boolean;
    category?: string;
    group?: string;
    name: string;
    description: string;
    webapp: boolean;
    email?: boolean;
    user?: User;
}
 */
/*
export interface Option {
    name: string;
    description: string;
    webapp: boolean;
}

//used for communication between client and server
export interface UserOption {
    id: number;
    category: string;
    group: string;
    name: string;
    description: string;
    webapp: boolean;
    email: boolean;
    user: User;
}
 */

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
