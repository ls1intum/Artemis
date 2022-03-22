import { Injectable } from '@angular/core';
import { Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class ChartRoutingService {
    constructor(private router: Router) {}

    /**
     * Opens the target page in a new tab
     * Used for routing via charts
     * @param route the target route
     */
    routeInNewTab(route: any[]): void {
        const url = this.router.serializeUrl(this.router.createUrlTree(route));
        window.open(url, '_blank');
    }
}
