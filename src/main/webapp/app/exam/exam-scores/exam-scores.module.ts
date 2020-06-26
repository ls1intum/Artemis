import { NgModule } from '@angular/core';
import { ExamScoresComponent } from './exam-scores.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MomentModule } from 'ngx-moment';
import { ArtemisExamScoresRoutingModule } from 'app/exam/exam-scores/exam-scores-routing.module';

@NgModule({
    declarations: [ExamScoresComponent],
    imports: [ArtemisSharedModule, MomentModule, ArtemisExamScoresRoutingModule],
})
export class ArtemisExamScoresModule {}
