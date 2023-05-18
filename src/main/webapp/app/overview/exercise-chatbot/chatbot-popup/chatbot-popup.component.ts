import { Component, OnInit } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-chatbot-popup',
    templateUrl: './chatbot-popup.component.html',
    styleUrls: ['./chatbot-popup.component.scss'],
})
export class ChatbotPopupComponent implements OnInit {
    public firstName: string | undefined;

    constructor(private accountService: AccountService) {}

    ngOnInit() {
        this.accountService.identity().then((user: User) => {
            this.firstName = user!.firstName?.split(' ')[0];
        });
    }
}
