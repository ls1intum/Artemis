import { NgModule } from '@angular/core';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisExampleTextSubmissionModule } from 'app/exercises/text/manage/example-text-submission/example-text-submission.module';

@NgModule({
    imports: [ArtemisTextExerciseModule, ArtemisExampleTextSubmissionModule],
})
export class ArtemisTextExerciseManagementModule {}
