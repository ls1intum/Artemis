import { NgModule } from '@angular/core';
import { ArtemisAppRoutingModule } from 'app/app-routing.module';
import { ExerciseDetailsComponent } from 'app/exercises/shared/exercise/exercise-details/exercise-details.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisModePickerModule } from 'app/exercises/shared/mode-picker/mode-picker.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisModePickerModule, AssessmentInstructionsModule, ArtemisAppRoutingModule],
    declarations: [ExerciseDetailsComponent],
    exports: [ExerciseDetailsComponent],
})
export class ExerciseDetailsModule {}
