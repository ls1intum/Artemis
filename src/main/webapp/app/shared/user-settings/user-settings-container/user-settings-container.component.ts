import { Component, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subscription, tap } from 'rxjs';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';

/**
 * UserSettingsContainerComponent serves as the common ground for different settings
 */
@Component({
    selector: 'jhi-user-settings',
    templateUrl: 'user-settings-container.component.html',
    styleUrls: ['user-settings-container.component.scss'],
})
export class UserSettingsContainerComponent {
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
