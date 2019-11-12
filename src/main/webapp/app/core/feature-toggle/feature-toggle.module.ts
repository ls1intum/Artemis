import { NgModule } from '@angular/core';
import { FeatureToggleDirective } from 'app/core/feature-toggle/feature-toggle.directive';
import { FeatureToggleService } from 'app/core/feature-toggle/feature-toggle.service';
import { ArtemisSharedModule } from '../../shared';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [FeatureToggleDirective],
    providers: [FeatureToggleService],
    exports: [FeatureToggleDirective],
})
export class FeatureToggleModule {
    static forRoot() {
        return {
            ngModule: FeatureToggleModule,
            providers: [FeatureToggleService],
        };
    }
}
