import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { NgModule } from '@angular/core';

@NgModule({
    declarations: [GradingKeyOverviewComponent],
    imports: [ArtemisSharedModule],
    exports: [GradingKeyOverviewComponent],
})
export class GradingKeyOverviewModule {}
