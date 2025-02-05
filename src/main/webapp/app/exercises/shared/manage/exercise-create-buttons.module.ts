import { NgModule } from '@angular/core';
import { ExerciseCreateButtonsComponent } from 'app/exercises/shared/manage/exercise-create-buttons.component';

import { ArtemisFileUploadExerciseManagementRoutingModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.route';

@NgModule({
    imports: [ArtemisFileUploadExerciseManagementRoutingModule, ExerciseCreateButtonsComponent],
    exports: [ExerciseCreateButtonsComponent],
})
export class ArtemisExerciseCreateButtonsModule {}
