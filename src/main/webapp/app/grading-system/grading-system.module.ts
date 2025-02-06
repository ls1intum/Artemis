import { NgModule } from '@angular/core';

import { DetailedGradingSystemComponent } from 'app/grading-system/detailed-grading-system/detailed-grading-system.component';
import { GradingSystemInfoModalComponent } from 'app/grading-system/grading-system-info-modal/grading-system-info-modal.component';
import { GradingSystemComponent } from 'app/grading-system/grading-system.component';
import { gradingSystemState } from 'app/grading-system/grading-system.route';
import { RouterModule } from '@angular/router';
import { IntervalGradingSystemComponent } from 'app/grading-system/interval-grading-system/interval-grading-system.component';

import { GradingSystemPresentationsComponent } from 'app/grading-system/grading-system-presentations/grading-system-presentations.component';

@NgModule({
    imports: [
        RouterModule.forChild(gradingSystemState),

        GradingSystemComponent,
        DetailedGradingSystemComponent,
        IntervalGradingSystemComponent,
        GradingSystemInfoModalComponent,
        GradingSystemPresentationsComponent,
    ],
    exports: [GradingSystemComponent, GradingSystemInfoModalComponent],
})
export class GradingSystemModule {}
