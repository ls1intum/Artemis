import { Component, OnInit } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { Subscription, tap } from 'rxjs';

@Component({
    selector: 'jhi-chatbot-popup',
    templateUrl: './chatbot-popup.component.html',
    styleUrls: ['./chatbot-popup.component.scss'],
})
export class ChatbotPopupComponent implements OnInit {
    public firstName: string | undefined;

    private authStateSubscription: Subscription;

    constructor(private accountService: AccountService) {}

    ngOnInit() {
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(tap((user: User) => (this.firstName = user.firstName?.toString().split(' ')[0])))
            .subscribe();
    }
}
