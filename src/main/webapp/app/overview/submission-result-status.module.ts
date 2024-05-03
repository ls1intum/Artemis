import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { SelfLearningFeedbackRequestComponent } from 'app/exercises/shared/self-learning-feedback-request/self-learning-feedback-request.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseActionsModule],
    declarations: [SubmissionResultStatusComponent, UpdatingResultComponent, ResultComponent, SelfLearningFeedbackRequestComponent],
    exports: [SubmissionResultStatusComponent, UpdatingResultComponent, ResultComponent, SelfLearningFeedbackRequestComponent],
})
export class SubmissionResultStatusModule {}
