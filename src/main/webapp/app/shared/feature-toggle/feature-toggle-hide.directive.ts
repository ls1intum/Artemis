import { Directive, computed, effect, inject, input, signal } from '@angular/core';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

@Directive({
    selector: '[jhiFeatureToggleHide]',
    host: {
        '[class.d-none]': 'hidden()',
    },
})
export class FeatureToggleHideDirective {
    private featureToggleService = inject(FeatureToggleService);

    feature = input<FeatureToggle | undefined>(undefined, { alias: 'jhiFeatureToggleHide' });

    private featureActive = signal(true);
    hidden = computed(() => !this.featureActive());

    constructor() {
        effect((onCleanup) => {
            const featureInput = this.feature();

            // if no feature, default to "visible"
            if (!featureInput) {
                this.featureActive.set(true);
                return;
            }

            const sub = this.featureToggleService.getFeatureToggleActive(featureInput).subscribe((active) => this.featureActive.set(active));

            onCleanup(() => sub.unsubscribe());
        });
    }
}
