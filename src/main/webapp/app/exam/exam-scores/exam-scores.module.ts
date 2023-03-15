import { NgModule } from '@angular/core';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { BarChartModule } from '@swimlane/ngx-charts';

import { ExamScoresComponent } from './exam-scores.component';
import { ExamScoresAverageScoresGraphComponent } from 'app/exam/exam-scores/exam-scores-average-scores-graph.component';
import { ArtemisExamScoresRoutingModule } from 'app/exam/exam-scores/exam-scores.route';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { ExportModule } from 'app/shared/export/export.module';
import { ArtemisParticipantScoresModule } from 'app/shared/participant-scores/participant-scores.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    declarations: [ExamScoresComponent, ExamScoresAverageScoresGraphComponent],
    imports: [
        ArtemisSharedModule,
        ArtemisExamScoresRoutingModule,
        ArtemisDataTableModule,
        NgxDatatableModule,
        ArtemisResultModule,
        ArtemisSharedComponentModule,
        BarChartModule,
        ArtemisParticipantScoresModule,
        ExportModule,
    ],
})
export class ArtemisExamScoresModule {}
