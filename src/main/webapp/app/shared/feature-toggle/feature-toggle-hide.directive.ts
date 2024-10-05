import { Directive, HostBinding, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { Subscription } from 'rxjs/internal/Subscription';

@Directive({
    selector: '[jhiFeatureToggleHide]',
})
export class FeatureToggleHideDirective implements OnInit, OnDestroy {
    private featureToggleService = inject(FeatureToggleService);

    @Input('jhiFeatureToggleHide') feature?: FeatureToggle;

    private featureActive = true;

    private featureToggleActiveSubscription: Subscription;

    ngOnInit() {
        if (this.feature) {
            this.featureToggleActiveSubscription = this.featureToggleService.getFeatureToggleActive(this.feature).subscribe((active) => {
                this.featureActive = active;
            });
        }
    }

    ngOnDestroy(): void {
        this.featureToggleActiveSubscription?.unsubscribe();
    }

    /**
     * This will hide the element if the feature is inactive.
     */
    @HostBinding('class.d-none')
    get hidden(): boolean {
        return !this.featureActive;
    }
}
