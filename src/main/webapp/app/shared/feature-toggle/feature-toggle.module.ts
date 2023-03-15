import { NgModule } from '@angular/core';

import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';

@NgModule({
    declarations: [FeatureToggleDirective, FeatureToggleLinkDirective, FeatureToggleHideDirective],
    exports: [FeatureToggleDirective, FeatureToggleLinkDirective, FeatureToggleHideDirective],
})
export class FeatureToggleModule {}
