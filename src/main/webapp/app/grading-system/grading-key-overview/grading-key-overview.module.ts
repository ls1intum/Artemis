import { NgModule } from '@angular/core';

import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    declarations: [GradingKeyOverviewComponent],
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    exports: [GradingKeyOverviewComponent],
})
export class GradingKeyOverviewModule {}
