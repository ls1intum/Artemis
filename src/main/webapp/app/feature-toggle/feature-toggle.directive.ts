import { Directive, Input, ElementRef, OnInit, OnChanges, SimpleChanges, Renderer2, HostBinding } from '@angular/core';
import { ActiveFeatureToggles, FeatureToggle, FeatureToggleService } from 'app/feature-toggle/feature-toggle.service';
import { tap } from 'rxjs/operators';

@Directive({
    selector: '[jhiFeatureToggle]',
})
export class FeatureToggleDirective implements OnInit {
    @Input('jhiFeatureToggle') feature: FeatureToggle;
    @HostBinding('disabled') disabled = false;

    constructor(private featureToggleService: FeatureToggleService) {}

    ngOnInit() {
        if (this.feature) {
            this.featureToggleService
                .getFeatureToggleActive(this.feature)
                .pipe(
                    // Disable the element if the feature is inactive.
                    tap(active => {
                        this.disabled = !active;
                    }),
                )
                .subscribe();
        }
    }
}
