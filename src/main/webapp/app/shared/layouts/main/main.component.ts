import { Component, OnInit } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, NavigationError, Router } from '@angular/router';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { SentryErrorHandler } from 'app/core/sentry/sentry.error-handler';
import { ThemeService } from 'app/core/theme/theme.service';

@Component({
    selector: 'jhi-main',
    templateUrl: './main.component.html',
})
export class JhiMainComponent implements OnInit {
    public showSkeleton = true;

    constructor(
        private jhiLanguageHelper: JhiLanguageHelper,
        private router: Router,
        private profileService: ProfileService,
        private sentryErrorHandler: SentryErrorHandler,
        private themeService: ThemeService,
    ) {
        this.setupErrorHandling().then(null);
    }

    private async setupErrorHandling() {
        this.profileService.getProfileInfo().subscribe((profileInfo: ProfileInfo) => {
            // sentry is only activated if it was specified in the application.yml file
            this.sentryErrorHandler.initSentry(profileInfo);
        });
    }

    private getPageTitle(routeSnapshot: ActivatedRouteSnapshot): string {
        const title: string = routeSnapshot.data['pageTitle'] ?? 'artemisApp';
        if (routeSnapshot.firstChild) {
            return this.getPageTitle(routeSnapshot.firstChild) || title;
        }
        return title;
    }

    ngOnInit() {
        this.router.events.subscribe((event) => {
            if (event instanceof NavigationEnd) {
                this.jhiLanguageHelper.updateTitle(this.getPageTitle(this.router.routerState.snapshot.root));
            }
            if (event instanceof NavigationError && event.error.status === 404) {
                // noinspection JSIgnoredPromiseFromCall
                this.router.navigate(['/404']);
            }

            // Do now show skeleton when the url links to a problem statement which is displayed on the native clients
            this.showSkeleton = this.shouldShowSkeleton(this.router.url);
        });

        this.themeService.initialize();
    }

    private shouldShowSkeleton(url: string): boolean {
        const isStandaloneProblemStatement = url.match('\\/courses\\/\\d+\\/exercises\\/\\d+\\/problem-statement(\\/\\d*)?(\\/)?');
        const isStandaloneFeedback = url.match('\\/courses\\/\\d+\\/exercises\\/\\d+\\/feedback\\/\\d+\\/\\d+\\/(true|false)(\\/)?');
        return !isStandaloneProblemStatement && !isStandaloneFeedback;
    }
}
