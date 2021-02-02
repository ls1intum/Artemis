import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ParticipantScoresAverageTableComponent } from './participant-scores-average-table/participant-scores-average-table.component';
import { ParticipantScoresTableComponent } from './participant-scores-table/participant-scores-table.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisDataTableModule, NgxDatatableModule],
    declarations: [ParticipantScoresTableComponent, ParticipantScoresAverageTableComponent],
    exports: [ParticipantScoresTableComponent, ParticipantScoresAverageTableComponent],
})
export class ArtemisParticipantScoresModule {}
