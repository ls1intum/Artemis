import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared';
import { SubmissionResultStatusComponent } from './submission-result-status.component';
import { ArtemisResultModule } from 'app/entities/result';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';

@NgModule({
    declarations: [SubmissionResultStatusComponent],
    exports: [SubmissionResultStatusComponent],
    imports: [ArtemisSharedModule, ArtemisResultModule, ArtemisProgrammingExerciseActionsModule],
})
export class SubmissionResultStatusModule {}
