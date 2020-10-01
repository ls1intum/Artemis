import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription, of } from 'rxjs';
import { tap, map, switchMap, filter } from 'rxjs/operators';
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
import { Router, NavigationEnd, ActivatedRoute, RouterEvent } from '@angular/router';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { Exam } from 'app/entities/exam.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';

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
    currAccount?: User;
    isRegistrationEnabled = false;

    private authStateSubscription: Subscription;
    private routerEventSubscription: Subscription;
    private exam?: Exam;
    private examId?: number;

    constructor(
        private loginService: LoginService,
        private languageService: JhiLanguageService,
        private languageHelper: JhiLanguageHelper,
        private localeConversionService: LocaleConversionService,
        private sessionStorage: SessionStorageService,
        private accountService: AccountService,
        private profileService: ProfileService,
        private participationWebsocketService: ParticipationWebsocketService,
        public guidedTourService: GuidedTourService,
        private router: Router,
        private route: ActivatedRoute,
        private examParticipationService: ExamParticipationService,
        private serverDateService: ArtemisServerDateService,
    ) {
        this.version = VERSION ? VERSION : '';
        this.isNavbarCollapsed = true;
        this.getExamId();
    }

    ngOnInit() {
        this.languages = this.languageHelper.getAll();

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.inProduction = profileInfo.inProduction;
                this.isRegistrationEnabled = profileInfo.registrationEnabled;
            }
        });

        this.subscribeForGuidedTourAvailability();

        // The current user is needed to hide menu items for not logged in users.
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(tap((user: User) => (this.currAccount = user)))
            .subscribe();

        this.examParticipationService.currentlyLoadedStudentExam.subscribe((studentExam) => {
            this.exam = studentExam.exam;
        });
    }

    ngOnDestroy(): void {
        if (this.authStateSubscription) {
            this.authStateSubscription.unsubscribe();
        }
        if (this.routerEventSubscription) {
            this.routerEventSubscription.unsubscribe();
        }
    }

    /**
     * Check if a guided tour is available for the current route to display the start tour button in the account menu
     */
    subscribeForGuidedTourAvailability(): void {
        // Check availability after first subscribe call since the router event been triggered already
        this.guidedTourService.getGuidedTourAvailabilityStream().subscribe((isAvailable) => {
            this.isTourAvailable = isAvailable;
        });
    }

    changeLanguage(languageKey: string) {
        this.sessionStorage.store('locale', languageKey);
        this.languageService.changeLanguage(languageKey);
        moment.locale(languageKey);
        this.localeConversionService.locale = languageKey;
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

    getImageUrl() {
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

    /**
     * get exam id from current route
     */
    getExamId() {
        this.routerEventSubscription = this.router.events.pipe(filter((event: RouterEvent) => event instanceof NavigationEnd)).subscribe((event) => {
            const examId = of(event).pipe(
                map(() => this.route.root),
                map((root) => root.firstChild),
                switchMap((firstChild) => {
                    if (firstChild) {
                        return firstChild?.paramMap.pipe(map((paramMap) => paramMap.get('examId')));
                    } else {
                        return of(null);
                    }
                }),
            );
            examId.subscribe((id) => {
                if (id !== null && !event.url.includes('management')) {
                    this.examId = +id;
                } else {
                    this.examId = undefined;
                }
            });
        });
    }

    /**
     * check if exam mode is active
     */
    examModeActive(): boolean {
        if (this.exam && this.exam.id === this.examId && this.exam.startDate && this.exam.endDate) {
            return this.serverDateService.now().isBetween(this.exam.startDate, this.exam.endDate);
        }
        return false;
    }
}
