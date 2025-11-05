import { Component, OnDestroy, OnInit, Renderer2, inject } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, NavigationError, NavigationStart, Router, RouterOutlet } from '@angular/router';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { SentryErrorHandler } from 'app/core/sentry/sentry.error-handler';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { DOCUMENT, NgClass, NgStyle } from '@angular/common';
import { Subscription } from 'rxjs';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { LtiService } from 'app/shared/service/lti.service';
import { AlertOverlayComponent } from 'app/core/alert/alert-overlay.component';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { CourseNotificationPopupOverlayComponent } from 'app/communication/course-notification/course-notification-popup-overlay/course-notification-popup-overlay.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { PageRibbonComponent } from 'app/core/layouts/profiles/page-ribbon.component';
import { FooterComponent } from 'app/core/layouts/footer/footer.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

@Component({
    selector: 'jhi-app',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss'],
    imports: [AlertOverlayComponent, CdkScrollable, NgClass, NgStyle, PageRibbonComponent, RouterOutlet, FooterComponent, CourseNotificationPopupOverlayComponent],
})
export class AppComponent implements OnInit, OnDestroy {
    protected readonly FeatureToggle = FeatureToggle;

    private jhiLanguageHelper = inject(JhiLanguageHelper);
    private router = inject(Router);
    private profileService = inject(ProfileService);
    private examParticipationService = inject(ExamParticipationService);
    private sentryErrorHandler = inject(SentryErrorHandler);
    private themeService = inject(ThemeService);
    private document = inject<Document>(DOCUMENT);
    private renderer = inject(Renderer2);
    private ltiService = inject(LtiService);

    private examStartedSubscription: Subscription;
    private testRunSubscription: Subscription;
    private ltiSubscription: Subscription;
    /**
     * If the footer and header should be shown.
     * Only set to false on specific pages designed for the native Android and iOS applications where the footer and header are not wanted.
     * The decision on whether to show the skeleton or not for a specific route is defined in shouldShowSkeleton.
     */
    showSkeleton = true;
    isProduction = true;
    isTestServer = false;
    isExamStarted = false;
    isTestRunExam = false;
    isShownViaLti = false;
    usesModuleBackground = false;

    constructor() {
        this.setupErrorHandling().then(undefined);
    }

    private async setupErrorHandling() {
        const profileInfo = this.profileService.getProfileInfo();
        // sentry is only activated if it was specified in the application.yml file
        this.sentryErrorHandler.initSentry(profileInfo);
    }

    private getPageTitle(routeSnapshot: ActivatedRouteSnapshot): string {
        const title: string = routeSnapshot.data['pageTitle'] ?? 'artemisApp';
        if (routeSnapshot.firstChild) {
            return this.getPageTitle(routeSnapshot.firstChild) || title;
        }
        return title;
    }

    private getDeepestSnapshot(route: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
        return route.firstChild ? this.getDeepestSnapshot(route.firstChild) : route;
    }

    private getDeepestUsesModuleBackground(root: ActivatedRouteSnapshot): boolean {
        return this.getDeepestSnapshot(root).data?.['usesModuleBackground'] ?? false;
    }

    ngOnInit() {
        this.router.events.subscribe((event) => {
            if (event instanceof NavigationStart) {
                /*
                In the case where we do not want to show the skeleton, we also want to set the background to transparent
                such that the mobile native applications can display their background in the web view.

                However, as the default background attribute is defined in the body HTML tag, it is outside Angular's reach.
                We set the background ourselves by adding the transparent-background CSS class on the body element, thus
                overwriting the default background. We cannot do this in any other way, as Angular cannot modify the body
                itself.
                 */
                const shouldShowSkeletonNow = this.shouldShowSkeleton(event.url);
                if (!shouldShowSkeletonNow && this.showSkeleton) {
                    // If we already show the skeleton but do not want to show the skeleton anymore, we need to remove the background
                    this.renderer.addClass(this.document.body, 'transparent-background');
                } else if (shouldShowSkeletonNow && !this.showSkeleton) {
                    // If we want to show the skeleton but weren't showing it previously, we need to remove the class to show the skeleton again
                    this.renderer.removeClass(this.document.body, 'transparent-background');
                }
                // Do now show skeleton when the url links to a problem statement which is displayed on the native clients
                this.showSkeleton = shouldShowSkeletonNow;
            }
            if (event instanceof NavigationEnd) {
                this.jhiLanguageHelper.updateTitle(this.getPageTitle(this.router.routerState.snapshot.root));
                this.usesModuleBackground = this.getDeepestUsesModuleBackground(this.router.routerState.snapshot.root);
            }
            if (event instanceof NavigationError && event.error.status === 404) {
                // noinspection JSIgnoredPromiseFromCall
                this.router.navigate(['/404']);
            }
        });

        this.isTestServer = this.profileService.isTestServer();
        this.isProduction = this.profileService.isProduction();

        this.examStartedSubscription = this.examParticipationService.examIsStarted$.subscribe((isStarted) => {
            this.isExamStarted = isStarted;
        });

        this.testRunSubscription = this.examParticipationService.testRunStarted$.subscribe((isStarted) => {
            this.isTestRunExam = isStarted;
        });

        this.ltiSubscription = this.ltiService.isShownViaLti$.subscribe((isShownViaLti) => {
            this.isShownViaLti = isShownViaLti;
        });

        this.themeService.initialize();
    }

    /**
     * The skeleton should not be shown for the problem statement component if it is directly accessed and
     * for the standalone feedback component.
     */
    private shouldShowSkeleton(url: string): boolean {
        const isStandaloneProblemStatement = url.match('\\/courses\\/\\d+\\/exercises\\/\\d+\\/problem-statement(\\/\\d*)?(\\/)?');
        const isStandaloneFeedback = url.match('\\/courses\\/\\d+\\/exercises\\/\\d+\\/participations\\/\\d+\\/results\\/\\d+\\/feedback(\\/)?');
        return !isStandaloneProblemStatement && !isStandaloneFeedback;
    }

    ngOnDestroy(): void {
        this.examStartedSubscription?.unsubscribe();
        this.testRunSubscription?.unsubscribe();
        this.ltiSubscription?.unsubscribe();
    }
}
