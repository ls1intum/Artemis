import { Component } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Subscription, tap } from 'rxjs';

@Component({
    selector: 'jhi-account-information',
    templateUrl: './account-information.component.html',
    //styleUrls: ['../user-settings-container/user-settings-container.component.scss'],
    styleUrls: ['../user-settings-prototype/user-settings-prototype.component.scss'],
})
export class AccountInformationComponent {
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
