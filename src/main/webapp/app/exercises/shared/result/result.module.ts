import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ResultDetailComponent } from 'app/exercises/shared/result/detail/result-detail.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { BarChartModule } from '@swimlane/ngx-charts';
import { ArtemisFeedbackModule } from 'app/exercises/shared/feedback/feedback.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseActionsModule, ArtemisFeedbackModule, ArtemisSharedComponentModule, SubmissionResultStatusModule, BarChartModule],
    declarations: [ResultDetailComponent, ResultHistoryComponent],
    exports: [ResultDetailComponent, ResultHistoryComponent],
})
export class ArtemisResultModule {}
