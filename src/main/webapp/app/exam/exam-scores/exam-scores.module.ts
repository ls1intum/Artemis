import { NgModule } from '@angular/core';
import { ExamScoresComponent } from './exam-scores.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisExamScoresRoutingModule } from 'app/exam/exam-scores/exam-scores.route';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ExamScoresAverageScoresGraphComponent } from 'app/exam/exam-scores/exam-scores-average-scores-graph.component';
import { BarChartModule } from '@swimlane/ngx-charts';
import { ArtemisParticipantScoresModule } from 'app/shared/participant-scores/participant-scores.module';
import { ExportModule } from 'app/shared/export/export.module';

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
