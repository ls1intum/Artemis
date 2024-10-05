import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-error',
    templateUrl: './error.component.html',
    standalone: true,
})
export class ErrorComponent implements OnInit {
    private route = inject(ActivatedRoute);

    errorMessage: string;
    error403: boolean;
    error404: boolean;

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
