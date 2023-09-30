import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { NgModule } from '@angular/core';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { GradingKeyTableComponent } from 'app/grading-system/grading-key-overview/grading-key/grading-key-table.component';

@NgModule({
    declarations: [GradingKeyOverviewComponent, GradingKeyTableComponent],
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule],
    exports: [GradingKeyOverviewComponent, GradingKeyTableComponent],
})
export class GradingKeyOverviewModule {}
