import { Component, OnInit } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, NavigationError, Router } from '@angular/router';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ThemeService } from 'app/core/theme/theme.service';

@Component({
    selector: 'jhi-main',
    templateUrl: './main.component.html',
})
export class JhiMainComponent implements OnInit {
    constructor(private jhiLanguageHelper: JhiLanguageHelper, private router: Router, private profileService: ProfileService, private themeService: ThemeService) {
        this.setupErrorHandling().then(null);
    }

    private async setupErrorHandling() {
        this.profileService.getProfileInfo().subscribe((profileInfo: ProfileInfo) => {
            // sentry is only activated if it was specified in the application.yml file
            // this.sentryErrorHandler.initSentry(profileInfo);
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
        });

        this.themeService.initialize();
    }
}
