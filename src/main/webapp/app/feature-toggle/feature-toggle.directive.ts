import { Directive, HostBinding, Input, OnInit } from '@angular/core';
import { FeatureToggle, FeatureToggleService } from 'app/feature-toggle/feature-toggle.service';
import { tap } from 'rxjs/operators';

@Directive({
    selector: '[jhiFeatureToggle]',
})
export class FeatureToggleDirective implements OnInit {
    @Input('jhiFeatureToggle') feature: FeatureToggle;
    @Input() overwriteDisabled: boolean | null;
    private featureActive = true;

    constructor(private featureToggleService: FeatureToggleService) {}

    ngOnInit() {
        if (this.feature) {
            this.featureToggleService
                .getFeatureToggleActive(this.feature)
                .pipe(
                    // Disable the element if the feature is inactive.
                    tap(active => {
                        this.featureActive = active;
                    }),
                )
                .subscribe();
        }
    }

    @HostBinding('disabled')
    get disabled(): boolean {
        return this.overwriteDisabled || !this.featureActive;
    }
}
