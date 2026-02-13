import { Directive, effect, inject, input, signal } from '@angular/core';
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
    disabled = signal(false);

    constructor() {
        effect((onCleanup) => {
            const featureInput = this.feature();
            const skip = this.skipFeatureToggle();
            const overwrite = this.overwriteDisabled();

            // default when no feature is set
            if (!featureInput) {
                this.featureActive.set(true);
                this.disabled.set(overwrite === true || false);
                return;
            }
            const sub = this.featureToggleService.getFeatureToggleActive(featureInput).subscribe((active) => {
                const effectiveActive = skip || active;
                this.featureActive.set(effectiveActive);
                this.disabled.set(overwrite === true || !effectiveActive);
            });
            onCleanup(() => sub.unsubscribe());
        });
    }
}
