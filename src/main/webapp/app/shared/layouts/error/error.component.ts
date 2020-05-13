import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-error',
    templateUrl: './error.component.html',
})
export class ErrorComponent implements OnInit {
    errorMessage: string;
    error403: boolean;
    error404: boolean;

    constructor(private route: ActivatedRoute) {}

    /**
     * Lifecycle function which is called on initialisation.
     * It subscribes to {@link route} and sets {@link errorMessage}, {@link error403} and {@link error404} when they are received.
     */
    ngOnInit() {
        this.route.data.subscribe((routeData) => {
            if (routeData.error403) {
                this.error403 = routeData.error403;
            }
            if (routeData.error404) {
                this.error404 = routeData.error404;
            }
            if (routeData.errorMessage) {
                this.errorMessage = routeData.errorMessage;
            }
        });
    }
}
