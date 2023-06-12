import { NgModule } from '@angular/core';
import { ExerciseCreateButtonsComponent } from 'app/exercises/shared/manage/exercise-create-buttons.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisFileUploadExerciseManagementRoutingModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.route';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisFileUploadExerciseManagementRoutingModule],
    declarations: [ExerciseCreateButtonsComponent],
    exports: [ExerciseCreateButtonsComponent],
})
export class ArtemisExerciseCreateButtonsModule {}
