import { NgModule } from '@angular/core';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { BarChartModule } from '@swimlane/ngx-charts';

import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { ParticipantScoresDistributionComponent } from 'app/shared/participant-scores/participant-scores-distribution/participant-scores-distribution.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisDataTableModule, NgxDatatableModule, ArtemisSharedComponentModule, BarChartModule],
    declarations: [ParticipantScoresDistributionComponent],
    exports: [ParticipantScoresDistributionComponent],
})
export class ArtemisParticipantScoresModule {}
