import { NgModule } from '@angular/core';
import { ArtemisExampleModelingSubmissionModule } from 'app/exercises/modeling/manage/example-modeling/example-modeling-submission.module';
import { ArtemisModelingExerciseModule } from 'app/exercises/modeling/manage/modeling-exercise.module';
import { ModelingExerciseImportComponent } from 'app/exercises/modeling/manage/modeling-exercise-import.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [
        ArtemisExampleModelingSubmissionModule,
        ArtemisModelingExerciseModule,
        ArtemisSharedCommonModule,
        ArtemisSharedComponentModule,
        ArtemisSharedPipesModule,
        ArtemisMarkdownModule,
    ],
    declarations: [ModelingExerciseImportComponent],
})
export class ArtemisModelingExerciseManagementModule {}
