import { Directive, HostBinding, Input, OnInit, OnDestroy } from '@angular/core';
import { FeatureToggle, FeatureToggleService } from 'app/feature-toggle/feature-toggle.service';
import { tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';

@Directive({
    selector: '[jhiFeatureToggle]',
})
export class FeatureToggleDirective implements OnInit, OnDestroy {
    @Input('jhiFeatureToggle') feature: FeatureToggle;
    @Input() overwriteDisabled: boolean | null;
    private featureActive = true;

    private featureToggleActiveSubscription: Subscription;

    constructor(private featureToggleService: FeatureToggleService) {}

    ngOnInit() {
        if (this.feature) {
            this.featureToggleActiveSubscription = this.featureToggleService
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

    ngOnDestroy(): void {
        if (this.featureToggleActiveSubscription) {
            this.featureToggleActiveSubscription.unsubscribe();
        }
    }

    /**
     * This will disable the feature component (normally a button) if the specified feature flag is inactive OR
     * if there is some other condition given as an Input, which takes higher priority (input overwriteDisabled)
     */
    @HostBinding('disabled')
    get disabled(): boolean {
        return this.overwriteDisabled || !this.featureActive;
    }
}
