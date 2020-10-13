import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExerciseResultComponent } from './exercise-result.component';
import { ExerciseResultDetailComponent } from './exercise-result-detail.component';
import { ExerciseResultUpdateComponent } from './exercise-result-update.component';
import { ExerciseResultDeleteDialogComponent } from './exercise-result-delete-dialog.component';
import { exerciseResultRoute } from './exercise-result.route';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(exerciseResultRoute)],
    declarations: [ExerciseResultComponent, ExerciseResultDetailComponent, ExerciseResultUpdateComponent, ExerciseResultDeleteDialogComponent],
    entryComponents: [ExerciseResultDeleteDialogComponent],
})
export class ArtemisExerciseResultModule {}
