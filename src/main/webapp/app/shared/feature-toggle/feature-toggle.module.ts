import { NgModule } from '@angular/core';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';

@NgModule({
    declarations: [FeatureToggleDirective, FeatureToggleLinkDirective, FeatureToggleHideDirective],
    exports: [FeatureToggleDirective, FeatureToggleLinkDirective, FeatureToggleHideDirective],
})
export class FeatureToggleModule {}
