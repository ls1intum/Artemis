import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ParticipantScoresComponent } from './participant-scores.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisDataTableModule, NgxDatatableModule],
    declarations: [ParticipantScoresComponent],
    exports: [ParticipantScoresComponent],
})
export class ArtemisParticipantScoresModule {}
