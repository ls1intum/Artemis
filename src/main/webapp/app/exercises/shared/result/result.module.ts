import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ResultDetailComponent } from 'app/exercises/shared/result/result-detail.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FeedbackCollapseComponent } from 'app/exercises/shared/result/feedback-collapse.component';
import { Ng2ChartsModule } from 'app/shared/chart/ng2-charts.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseActionsModule, ArtemisSharedComponentModule, Ng2ChartsModule],
    declarations: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent, SubmissionResultStatusComponent, FeedbackCollapseComponent],
    exports: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent, SubmissionResultStatusComponent],
})
export class ArtemisResultModule {}
