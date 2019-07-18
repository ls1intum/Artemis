import { Component, OnInit } from '@angular/core';
import { Router, Event, NavigationEnd } from '@angular/router';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageService } from 'ng-jhipster';
import { SessionStorageService } from 'ngx-webstorage';

import { ProfileService } from '../profiles/profile.service';
import { AccountService, JhiLanguageHelper, LoginService, User } from 'app/core';

import { VERSION } from 'app/app.constants';
import * as moment from 'moment';
import { ParticipationWebsocketService } from 'app/entities/participation';
import { OverviewComponent } from 'app/overview';

@Component({
    selector: 'jhi-navbar',
    templateUrl: './navbar.component.html',
    styleUrls: ['navbar.scss'],
})
export class NavbarComponent implements OnInit {
    inProduction: boolean;
    isNavbarCollapsed: boolean;
    isTourAvailable: boolean;
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
        private participationWebsocketService: ParticipationWebsocketService,
        private router: Router,
        private overviewComponent: OverviewComponent,
    ) {
        this.version = VERSION ? VERSION : '';
        this.isNavbarCollapsed = true;

        this.router.events.subscribe((event: Event) => {
            if (event instanceof NavigationEnd) {
                this.checkGuidedTourAvailability();
            }
        });
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
        this.participationWebsocketService.resetLocalCache();
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

    /**
     * Checks if the current component has a guided tour by comparing the current router url to manually defined urls
     * that provide tours.
     */
    checkGuidedTourAvailability() {
        if (this.router.url === '/overview') {
            this.isTourAvailable = true;
        } else {
            this.isTourAvailable = false;
        }
    }

    /**
     * Starts the guided tour of the current component
     * */
    startGuidedTour() {
        if (this.router.url === '/overview') {
            this.overviewComponent.startTour();
        }
    }
}
