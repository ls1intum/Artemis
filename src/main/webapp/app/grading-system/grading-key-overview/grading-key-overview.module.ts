import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { NgModule } from '@angular/core';

import { GradingKeyTableComponent } from 'app/grading-system/grading-key-overview/grading-key/grading-key-table.component';

@NgModule({
    imports: [GradingKeyOverviewComponent, GradingKeyTableComponent],
    exports: [GradingKeyOverviewComponent, GradingKeyTableComponent],
})
export class GradingKeyOverviewModule {}
