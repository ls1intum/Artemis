import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ParticipantScoresDistributionComponent } from 'app/shared/participant-scores/participant-scores-distribution/participant-scores-distribution.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { BarChartModule } from '@swimlane/ngx-charts';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisDataTableModule, NgxDatatableModule, ArtemisSharedComponentModule, BarChartModule],
    declarations: [ParticipantScoresDistributionComponent],
    exports: [ParticipantScoresDistributionComponent],
})
export class ArtemisParticipantScoresModule {}
