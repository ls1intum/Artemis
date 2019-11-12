import { Directive, Input, ElementRef, OnInit } from '@angular/core';
import { ActiveFeatures, FeatureToggle, FeatureToggleService } from 'app/core/feature-toggle/feature-toggle.service';
import { tap } from 'rxjs/operators';

@Directive({
    selector: '[jhiFeatureToggle]',
})
export class FeatureToggleDirective implements OnInit {
    @Input('jhiFeatureToggle') feature: FeatureToggle;

    constructor(private el: ElementRef, private featureToggleService: FeatureToggleService) {}

    ngOnInit() {
        this.featureToggleService
            .getFeatureToggleActive(this.feature)
            .pipe(
                // Disable the element if the feature is inactive.
                tap(active => (this.el.nativeElement.disabled = !active)),
            )
            .subscribe();
    }
}
