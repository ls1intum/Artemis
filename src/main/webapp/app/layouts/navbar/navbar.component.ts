import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, Event, NavigationEnd } from '@angular/router';
import { Subscription } from 'rxjs';
import { distinctUntilChanged, tap } from 'rxjs/operators';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageService } from 'ng-jhipster';
import { SessionStorageService } from 'ngx-webstorage';

import { ProfileService } from '../profiles/profile.service';
import { AccountService, JhiLanguageHelper, LoginService, User } from 'app/core';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';

import { VERSION } from 'app/app.constants';
import * as moment from 'moment';
import { ParticipationWebsocketService } from 'app/entities/participation';

@Component({
    selector: 'jhi-navbar',
    templateUrl: './navbar.component.html',
    styleUrls: ['navbar.scss'],
})
export class NavbarComponent implements OnInit, OnDestroy {
    inProduction: boolean;
    isNavbarCollapsed: boolean;
    isTourAvailable: boolean;
    languages: string[];
    modalRef: NgbModalRef;
    version: string;
    currAccount: User | null;

    private authStateSubscription: Subscription;

    constructor(
        private loginService: LoginService,
        private languageService: JhiLanguageService,
        private languageHelper: JhiLanguageHelper,
        private sessionStorage: SessionStorageService,
        private accountService: AccountService,
        private profileService: ProfileService,
        private participationWebsocketService: ParticipationWebsocketService,
        private router: Router,
        private guidedTourService: GuidedTourService,
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

        // The current user is needed to hide menu items for not logged in users.
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(
                distinctUntilChanged(),
                tap((user: User) => (this.currAccount = user)),
            )
            .subscribe();
    }

    ngOnDestroy(): void {
        if (this.authStateSubscription) {
            this.authStateSubscription.unsubscribe();
        }
    }

    changeLanguage(languageKey: string) {
        this.sessionStorage.store('locale', languageKey);
        this.languageService.changeLanguage(languageKey);
        moment.locale(languageKey);
    }

    collapseNavbar() {
        this.isNavbarCollapsed = true;
    }

    isAuthenticated() {
        return this.accountService.isAuthenticated();
    }

    logout() {
        this.participationWebsocketService.resetLocalCache();
        this.collapseNavbar();
        this.loginService.logout();
    }

    toggleNavbar() {
        this.isNavbarCollapsed = !this.isNavbarCollapsed;
    }

    getImageUrl(): string | null {
        return this.accountService.getImageUrl();
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
            this.guidedTourService.startTourForComponent('overview');
        }
    }
}
