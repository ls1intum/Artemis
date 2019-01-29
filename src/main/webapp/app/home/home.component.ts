import { Component, OnInit } from '@angular/core';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';
import { Router } from '@angular/router';

import { LoginModalService, AccountService, User } from '../core';

@Component({
    selector: 'jhi-home',
    templateUrl: './home.component.html',
    styleUrls: ['home.scss']
})
export class HomeComponent implements OnInit {
    account: User;
    modalRef: NgbModalRef;

    constructor(
        private router: Router,
        private accountService: AccountService,
        private loginModalService: LoginModalService,
        private eventManager: JhiEventManager
    ) {}

    ngOnInit() {
        this.accountService.identity().then(user => {
            this.currentUserCallback(user);
        });
        this.registerAuthenticationSuccess();
    }

    registerAuthenticationSuccess() {
        this.eventManager.subscribe('authenticationSuccess', (message: string) => {
            this.accountService.identity().then(user => {
                this.currentUserCallback(user);
            });
        });
    }

    currentUserCallback(account: User) {
        this.account = account;
        if (account) {
            this.router.navigate(['courses']);
        }
    }

    isAuthenticated() {
        return this.accountService.isAuthenticated();
    }

    login() {
        this.modalRef = this.loginModalService.open();
    }
}
