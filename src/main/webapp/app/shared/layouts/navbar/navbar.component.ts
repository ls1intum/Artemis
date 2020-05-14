import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { tap } from 'rxjs/operators';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageService } from 'ng-jhipster';
import { SessionStorageService } from 'ngx-webstorage';

import { User } from 'app/core/user/user.model';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';

import { SERVER_API_URL, VERSION } from 'app/app.constants';
import * as moment from 'moment';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { LoginService } from 'app/core/login/login.service';

@Component({
    selector: 'jhi-navbar',
    templateUrl: './navbar.component.html',
    styleUrls: ['navbar.scss'],
})
export class NavbarComponent implements OnInit, OnDestroy {
    readonly SERVER_API_URL = SERVER_API_URL;

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
        public guidedTourService: GuidedTourService,
    ) {
        this.version = VERSION ? VERSION : '';
        this.isNavbarCollapsed = true;
    }

    /**
     * Lifecycle function which is called on initialisation. Sets the {@link inProduction} flag using {@link profileService~getProfileInfo}.
     * Triggers {@link subscribeForGuidedTourAvailability} to check if a guided tour is available. Finally it sets the {@link currAccount}..
     * The current user account is needed to hide menu items for users who are not logged in.
     */
    ngOnInit() {
        this.languages = this.languageHelper.getAll();

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.inProduction = profileInfo.inProduction;
            }
        });

        this.subscribeForGuidedTourAvailability();

        // The current user is needed to hide menu items for not logged in users.
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(tap((user: User) => (this.currAccount = user)))
            .subscribe();
    }

    /**
     * Lifecycle function that performs cleanup.
     * Unsubscribes from {@link authStateSubscription} from {@link accountService~getAuthenticationState} used to retrieve the current user.
     */
    ngOnDestroy(): void {
        if (this.authStateSubscription) {
            this.authStateSubscription.unsubscribe();
        }
    }

    /** Check if a guided tour is available for the current route to display the start tour button in the account menu.
     * Sets the {@link isTourAvailable} flag.
     * @method
     */
    subscribeForGuidedTourAvailability(): void {
        // Check availability after first subscribe call since the router event been triggered already
        this.guidedTourService.getGuidedTourAvailabilityStream().subscribe((isAvailable) => {
            this.isTourAvailable = isAvailable;
        });
    }

    /** Wrapper method to change the language to {@param languageKey}.
     * Triggers {@link sessionStorage~store}, {@link languageService~changeLanguage} and {@link moment~locale}.
     * @method
     * @param languageKey {string}
     */
    changeLanguage(languageKey: string) {
        this.sessionStorage.store('locale', languageKey);
        this.languageService.changeLanguage(languageKey);
        moment.locale(languageKey);
    }

    /** Collapse the navigation bar by setting the {@link isNavbarCollapsed} flag.
     * @method
     */
    collapseNavbar() {
        this.isNavbarCollapsed = true;
    }

    /** Returns true if this account is authenticated. Wrapper function for {@link accountService~isAuthenticated}
     * @method
     * @returns boolean
     */
    isAuthenticated() {
        return this.accountService.isAuthenticated();
    }

    /** Performs a safe logout. It resets the local cache {@link participationWebsocketService~resetLocalCache}, collapses the navigation bar {@link collapseNavbar} and
     * performs the logout {@link loginService~logout}.
     * @method
     */
    logout() {
        this.participationWebsocketService.resetLocalCache();
        this.collapseNavbar();
        this.loginService.logout();
    }

    /** Toggles the state of the navigation bar from collapsed to open and vice versa by setting the {@link isNavbarCollapsed} flag.
     * @method
     */
    toggleNavbar() {
        this.isNavbarCollapsed = !this.isNavbarCollapsed;
    }

    /** Wrapper function for {@link accountService~getImageUrl}. Returns the image url of the account.
     * @method
     * @returns {string|null}
     */
    getImageUrl(): string | null {
        return this.accountService.getImageUrl();
    }

    /**
     * Determine the label for initiating the guided tour based on the last seen tour step
     */
    guidedTourInitLabel(): string {
        switch (this.guidedTourService.getLastSeenTourStepForInit()) {
            case -1: {
                return 'global.menu.restartTutorial';
            }
            case 0: {
                return 'global.menu.startTutorial';
            }
            default: {
                return 'global.menu.continueTutorial';
            }
        }
    }
}
