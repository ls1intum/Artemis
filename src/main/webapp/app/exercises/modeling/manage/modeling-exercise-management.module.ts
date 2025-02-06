import { NgModule } from '@angular/core';
import { ArtemisExampleModelingSubmissionModule } from 'app/exercises/modeling/manage/example-modeling/example-modeling-submission.module';
import { ArtemisModelingExerciseModule } from 'app/exercises/modeling/manage/modeling-exercise.module';

import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@NgModule({
    imports: [ArtemisExampleModelingSubmissionModule, ArtemisModelingExerciseModule, ArtemisSharedCommonModule],
})
export class ArtemisModelingExerciseManagementModule {}
