import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ParticipantScoresAverageTableComponent } from './participant-scores-average-table/participant-scores-average-table.component';
import { ParticipantScoresTableComponent } from './participant-scores-table/participant-scores-table.component';
import { ParticipantScoresTablesContainerComponent } from './participant-scores-tables-container/participant-scores-tables-container.component';
import { ParticipantScoresDistributionComponent } from 'app/shared/participant-scores/participant-scores-distribution/participant-scores-distribution.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { NgxChartsModule } from '@swimlane/ngx-charts';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisDataTableModule, NgxDatatableModule, ArtemisSharedComponentModule, NgxChartsModule],
    declarations: [ParticipantScoresTableComponent, ParticipantScoresAverageTableComponent, ParticipantScoresTablesContainerComponent, ParticipantScoresDistributionComponent],
    exports: [ParticipantScoresTablesContainerComponent, ParticipantScoresDistributionComponent],
})
export class ArtemisParticipantScoresModule {}
