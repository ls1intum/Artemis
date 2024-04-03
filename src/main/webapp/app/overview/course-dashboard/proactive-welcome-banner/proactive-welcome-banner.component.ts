import { Component, OnInit } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-proactive-welcome-banner',
    templateUrl: './proactive-welcome-banner.component.html',
    styleUrl: './proactive-welcome-banner.component.scss',
})
export class ProactiveWelcomeBannerComponent implements OnInit {
    account?: User;
    constructor(private accountService: AccountService) {}

    async ngOnInit(): Promise<void> {
        this.accountService.identity().then((user) => {
            if (user) {
                this.account = user;
            }
        });
    }
    get fullName(): string {
        return this.account?.firstName ?? 'Traveler';
    }
}
