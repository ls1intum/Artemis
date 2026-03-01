import { Directive, computed, effect, inject, input, signal } from '@angular/core';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

@Directive({
    selector: '[jhiFeatureToggleLink]',
    host: { '[class.disabled]': 'disabled()' },
})
export class FeatureToggleLinkDirective {
    private featureToggleService = inject(FeatureToggleService);

    feature = input<FeatureToggle | undefined>(undefined, { alias: 'jhiFeatureToggleLink' });
    overwriteDisabled = input<boolean | null>(null);
    skipFeatureToggle = input<boolean>(false);

    private featureActive = signal(true);
    disabled = computed(() => this.overwriteDisabled() === true || !this.featureActive());
    constructor() {
        effect((onCleanup) => {
            const featureInput = this.feature();
            const skip = this.skipFeatureToggle();

            // default when no feature is set
            if (!featureInput) {
                this.featureActive.set(true);
                return;
            }
            const sub = this.featureToggleService.getFeatureToggleActive(featureInput).subscribe((active) => {
                const effectiveActive = skip || active;
                this.featureActive.set(effectiveActive);
            });
            onCleanup(() => sub.unsubscribe());
        });
    }
}
