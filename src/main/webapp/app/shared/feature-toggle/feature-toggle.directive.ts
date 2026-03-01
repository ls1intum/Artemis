import { Directive, computed, effect, inject, input, signal } from '@angular/core';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

@Directive({
    selector: '[jhiFeatureToggle]',
    host: {
        '[attr.disabled]': 'disabled() ? "" : null',
        '[class.disabled]': 'disabled()',
        '[attr.aria-disabled]': 'disabled()',
    },
})
export class FeatureToggleDirective {
    private featureToggleService = inject(FeatureToggleService);

    features = input<FeatureToggle | FeatureToggle[] | undefined>(undefined, { alias: 'jhiFeatureToggle' });
    overwriteDisabled = input<boolean | null>(null);
    skipFeatureToggle = input<boolean>(false);
    private featureActive = signal(true);
    disabled = computed(() => this.overwriteDisabled() === true || !this.featureActive());

    constructor() {
        effect((onCleanup) => {
            const featureInput = this.features();
            const skip = this.skipFeatureToggle();

            // default when no feature is set
            if (!featureInput) {
                this.featureActive.set(true);
                return;
            }

            const featureArray = Array.isArray(featureInput) ? featureInput : [featureInput];
            if (featureArray.length === 0) {
                this.featureActive.set(true);
                return;
            }

            const sub = this.featureToggleService.getFeatureTogglesActive(featureArray).subscribe((active) => {
                this.featureActive.set(skip || active);
            });

            onCleanup(() => sub.unsubscribe());
        });
    }
}
