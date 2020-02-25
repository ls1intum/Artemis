import { NgModule } from '@angular/core';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';

@NgModule({
    declarations: [FeatureToggleDirective],
    exports: [FeatureToggleDirective],
})
export class FeatureToggleModule {
    static forRoot() {
        return {
            ngModule: FeatureToggleModule,
        };
    }
}
