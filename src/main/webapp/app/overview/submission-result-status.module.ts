import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ResultProgressBarComponent } from 'app/exercises/shared/result/result-progress-bar/result-progress-bar.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisSharedComponentModule,
        ResultProgressBarComponent,
        SubmissionResultStatusComponent,
        UpdatingResultComponent,
        ResultComponent,
    ],
    exports: [SubmissionResultStatusComponent, UpdatingResultComponent, ResultComponent],
})
export class SubmissionResultStatusModule {}
