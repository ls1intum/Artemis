import { Component, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subscription, tap } from 'rxjs';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';

/**
 * UserSettingsComponent serves as the common ground for different settings
 */
@Component({
    selector: 'jhi-user-settings',
    templateUrl: 'user-settings.component.html',
    styleUrls: ['user-settings.component.scss'],
})
export class UserSettingsComponent {
    currentUser?: User;

    private authStateSubscription: Subscription;

    constructor(private accountService: AccountService) {}

    ngOnInit() {
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(tap((user: User) => (this.currentUser = user)))
            .subscribe();
    }
}
