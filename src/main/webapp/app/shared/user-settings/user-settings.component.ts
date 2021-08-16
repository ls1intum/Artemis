import { Component, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subscription, tap } from 'rxjs';

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
