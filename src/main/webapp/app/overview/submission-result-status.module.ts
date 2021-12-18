import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ResultComponent } from 'app/exercises/shared/result/result.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseActionsModule],
    declarations: [SubmissionResultStatusComponent, UpdatingResultComponent, ResultComponent],
    exports: [SubmissionResultStatusComponent, UpdatingResultComponent, ResultComponent],
})
export class SubmissionResultStatusModule {}
