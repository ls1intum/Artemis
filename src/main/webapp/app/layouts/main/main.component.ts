import { Component, OnInit } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';

import { JhiLanguageHelper } from '../../core';

@Component({
    selector: 'jhi-main',
    templateUrl: './main.component.html'
})
export class JhiMainComponent implements OnInit {
    constructor(private jhiLanguageHelper: JhiLanguageHelper, private router: Router) {}

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
         * Update page title whenever route changes
         */
        this.router.events.subscribe(event => {
            if (event instanceof NavigationEnd) {
                this.jhiLanguageHelper.updateTitle(this.getPageTitle(this.router.routerState.snapshot.root));
            }
        });
    }
}
