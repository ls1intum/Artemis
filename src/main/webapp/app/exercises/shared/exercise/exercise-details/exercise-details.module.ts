import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ExerciseDetailsComponent } from 'app/exercises/shared/exercise/exercise-details/exercise-details.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisModePickerModule } from 'app/exercises/shared/mode-picker/mode-picker.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { exercisePopupRoute } from 'app/exercises/shared/exercise/exercise.route';

const ENTITY_STATES = [...exercisePopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisModePickerModule, AssessmentInstructionsModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [ExerciseDetailsComponent],
    exports: [ExerciseDetailsComponent],
})
export class ExerciseDetailsModule {}
