import { NgModule } from '@angular/core';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisExampleTextSubmissionModule } from 'app/exercises/text/manage/example-text-submission/example-text-submission.module';
import { TextExerciseImportComponent } from 'app/exercises/text/manage/text-exercise-import.component';
import { ArtemisSharedLibsModule } from 'app/shared/shared-libs.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisTextExerciseModule, ArtemisExampleTextSubmissionModule, ArtemisSharedLibsModule, ArtemisSharedComponentModule, ArtemisSharedModule],
    declarations: [TextExerciseImportComponent],
})
export class ArtemisTextExerciseManagementModule {}
