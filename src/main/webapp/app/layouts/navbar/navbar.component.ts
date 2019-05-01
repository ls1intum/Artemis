import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageService } from 'ng-jhipster';
import { SessionStorageService } from 'ngx-webstorage';

import { ProfileService } from 'app/layouts';
import { AccountService, JhiLanguageHelper, LoginService, User } from 'app/core';

import { VERSION } from 'app/app.constants';
import * as moment from 'moment';

@Component({
    selector: 'jhi-navbar',
    templateUrl: './navbar.component.html',
    styleUrls: ['navbar.scss'],
})
export class NavbarComponent implements OnInit {
    inProduction: boolean;
    isNavbarCollapsed: boolean;
    languages: string[];
    modalRef: NgbModalRef;
    version: string;
    currAccount: User | null;

    constructor(
        private loginService: LoginService,
        private languageService: JhiLanguageService,
        private languageHelper: JhiLanguageHelper,
        private sessionStorage: SessionStorageService,
        private accountService: AccountService,
        private profileService: ProfileService,
        private router: Router,
    ) {
        this.version = VERSION ? VERSION : '';
        this.isNavbarCollapsed = true;
    }

    ngOnInit() {
        this.languageHelper.getAll().then(languages => {
            this.languages = languages;
        });

        this.profileService.getProfileInfo().subscribe(
            profileInfo => {
                if (profileInfo) {
                    this.inProduction = profileInfo.inProduction;
                }
            },
            reason => {},
        );
        this.getCurrentAccount();
    }

    changeLanguage(languageKey: string) {
        this.sessionStorage.store('locale', languageKey);
        this.languageService.changeLanguage(languageKey);
        moment.locale(languageKey);
    }

    collapseNavbar() {
        this.isNavbarCollapsed = true;
    }

    getCurrentAccount() {
        if (!this.currAccount && this.accountService.isAuthenticated()) {
            this.accountService.identity().then(acc => {
                this.currAccount = acc;
            });
        }
        return true;
    }

    isAuthenticated() {
        return this.accountService.isAuthenticated();
    }

    logout() {
        this.currAccount = null;
        this.collapseNavbar();
        this.loginService.logout();
        // noinspection JSIgnoredPromiseFromCall
        this.router.navigate(['']);
    }

    toggleNavbar() {
        this.isNavbarCollapsed = !this.isNavbarCollapsed;
    }

    getImageUrl() {
        return this.isAuthenticated() ? this.accountService.getImageUrl() : null;
    }
}
