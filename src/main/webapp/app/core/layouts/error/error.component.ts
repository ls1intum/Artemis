import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-error',
    templateUrl: './error.component.html',
    imports: [TranslateDirective],
})
export class ErrorComponent implements OnInit {
    private route = inject(ActivatedRoute);

    readonly errorMessage = signal<string | undefined>(undefined);
    readonly error403 = signal<boolean | undefined>(undefined);
    readonly error404 = signal<boolean | undefined>(undefined);

    ngOnInit() {
        this.route.data.subscribe((routeData) => {
            if (routeData.error403) {
                this.error403.set(routeData.error403);
            }
            if (routeData.error404) {
                this.error404.set(routeData.error404);
            }
            if (routeData.errorMessage) {
                this.errorMessage.set(routeData.errorMessage);
            }
        });
    }
}
