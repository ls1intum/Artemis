import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { FileUploadExerciseComponent } from 'app/exercises/file-upload/shared/file-upload-exercise.component';

@NgModule({
    imports: [ArtemisSharedModule, SortByModule, FeatureToggleModule, RouterModule],
    declarations: [FileUploadExerciseComponent],
    exports: [FileUploadExerciseComponent],
})
export class ArtemisFileUploadExerciseModule {}
