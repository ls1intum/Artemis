import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MomentModule } from 'ngx-moment';
import { ResultHistoryComponent } from 'app/overview/result-history/result-history.component';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ResultDetailComponent } from 'app/exercises/shared/result/result-detail.component';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ArtemisProgrammingExerciseActionsModule],
    declarations: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent, SubmissionResultStatusComponent],
    exports: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent, SubmissionResultStatusComponent],
})
export class ArtemisResultModule {}
