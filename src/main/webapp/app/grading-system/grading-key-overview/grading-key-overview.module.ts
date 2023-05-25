import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { NgModule } from '@angular/core';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    declarations: [GradingKeyOverviewComponent],
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    exports: [GradingKeyOverviewComponent],
})
export class GradingKeyOverviewModule {}
