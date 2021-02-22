import { NgModule } from '@angular/core';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';

@NgModule({
    declarations: [FeatureToggleDirective, FeatureToggleLinkDirective],
    exports: [FeatureToggleDirective, FeatureToggleLinkDirective],
})
export class FeatureToggleModule {}
