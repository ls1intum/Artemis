import { Component, OnInit } from '@angular/core';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';
import { Router } from '@angular/router';

import { LoginModalService, Principal, User } from '../core';

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
        private principal: Principal,
        private loginModalService: LoginModalService,
        private eventManager: JhiEventManager
    ) {}

    ngOnInit() {
        this.principal.identity().then(account => {
            this.currentUserCallback(account);
        });
        this.registerAuthenticationSuccess();
    }

    registerAuthenticationSuccess() {
        this.eventManager.subscribe('authenticationSuccess', (message: string) => {
            this.principal.identity().then(account => {
                this.currentUserCallback(account);
            });
        });
    }

    currentUserCallback(account: User) {
        this.account = account;
        this.router.navigate(['courses']);
    }

    isAuthenticated() {
        return this.principal.isAuthenticated();
    }

    login() {
        this.modalRef = this.loginModalService.open();
    }
}
