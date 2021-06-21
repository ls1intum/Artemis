import { GradingSystemComponent } from 'app/grading-system/grading-system.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { NgModule } from '@angular/core';

@NgModule({
    declarations: [GradingKeyOverviewComponent],
    imports: [ArtemisSharedModule],
    exports: [GradingKeyOverviewComponent],
    entryComponents: [GradingSystemComponent],
})
export class GradingKeyOverviewModule {}
