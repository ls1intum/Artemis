import { NgModule } from '@angular/core';
import { ExamScoresComponent } from './exam-scores.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MomentModule } from 'ngx-moment';
import { ArtemisExamScoresRoutingModule } from 'app/exam/exam-scores/exam-scores.route';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';

@NgModule({
    declarations: [ExamScoresComponent],
    imports: [ArtemisSharedModule, MomentModule, ArtemisExamScoresRoutingModule, ArtemisDataTableModule, NgxDatatableModule, ArtemisResultModule],
})
export class ArtemisExamScoresModule {}
