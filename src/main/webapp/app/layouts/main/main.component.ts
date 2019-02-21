import { Component, OnInit } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, NavigationError, Router } from '@angular/router';
import { Angulartics2 } from 'angulartics2';
import { Angulartics2Piwik } from 'angulartics2/piwik';

import { JhiLanguageHelper } from 'app/core';
import { ProfileService } from '../profiles/profile.service';

@Component({
    selector: 'jhi-main',
    templateUrl: './main.component.html'
})
export class JhiMainComponent implements OnInit {
    constructor(
        private jhiLanguageHelper: JhiLanguageHelper,
        private router: Router,
        private profileService: ProfileService,
        private angulartics: Angulartics2,
        private angularticsPiwik: Angulartics2Piwik
    ) {
        this.setupAnalytics().then(null);
    }

    private async setupAnalytics() {
        const profileInfo = await this.profileService.getProfileInfo();

        if (profileInfo.inProduction && window.location.host === 'artemis.ase.in.tum.de') {
            // only Track in Production Environment
            this.angularticsPiwik.startTracking();
        } else {
            // Enable Developer Mode in all other environments
            this.angulartics.settings.developerMode = true;
        }
    }

    private getPageTitle(routeSnapshot: ActivatedRouteSnapshot) {
        let title: string = routeSnapshot.data && routeSnapshot.data['pageTitle'] ? routeSnapshot.data['pageTitle'] : 'arTeMiSApp';
        if (routeSnapshot.firstChild) {
            title = this.getPageTitle(routeSnapshot.firstChild) || title;
        }
        return title;
    }

    ngOnInit() {
        this.router.events.subscribe(event => {
            if (event instanceof NavigationEnd) {
                this.jhiLanguageHelper.updateTitle(this.getPageTitle(this.router.routerState.snapshot.root));
            }
            if (event instanceof NavigationError && event.error.status === 404) {
                this.router.navigate(['/404']);
            }
        });
    }
}
