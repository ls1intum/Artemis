import { Component, OnInit } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';

import { JhiLanguageHelper } from '../../core';
import { UpgradeModule } from '@angular/upgrade/static';

@Component({
    selector: 'jhi-main',
    templateUrl: './main.component.html'
})
export class JhiMainComponent implements OnInit {
    constructor(private jhiLanguageHelper: JhiLanguageHelper, private router: Router, private upgrade: UpgradeModule) {}

    /**
     * @function getPageTitle
     * @param {ActivatedRouteSnapshot} routeSnapshot
     * @returns {string}
     * @desc Return the title of the current route
     */
    private getPageTitle(routeSnapshot: ActivatedRouteSnapshot) {
        let title: string = routeSnapshot.data && routeSnapshot.data['pageTitle'] ? routeSnapshot.data['pageTitle'] : 'arTeMiSApp';
        if (routeSnapshot.firstChild) {
            title = this.getPageTitle(routeSnapshot.firstChild) || title;
        }
        return title;
    }

    /**
     * @function ngOnInit
     * @desc Angular framework function
     */
    ngOnInit() {
        /**
            Hybrid Application Setup
            We bootstrap the angularJS application when initializing the main component.
            If at some point we'd rather have the angularJS app to start later, we'd need to move this to
            another component and adjust the chained login procedure
            (save credentials in localStorage and do angularJs login once the app was bootstrapped.
            Further information regarding the hybrid setup:
            https://confluencebruegge.in.tum.de/display/ArTEMiS/Documentation+-+ArTEMiS+Hybrid+Application+Setup
         */
        this.upgrade.bootstrap(document.body, ['artemisApp'], { strictDi: true });

        /**
         * Update page title whenever route changes
         */
        this.router.events.subscribe(event => {
            if (event instanceof NavigationEnd) {
                this.jhiLanguageHelper.updateTitle(this.getPageTitle(this.router.routerState.snapshot.root));
            }
        });
    }
}
