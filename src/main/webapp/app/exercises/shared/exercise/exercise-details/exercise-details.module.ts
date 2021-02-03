import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ExerciseDetailsComponent } from 'app/exercises/shared/exercise/exercise-details/exercise-details.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisModePickerModule } from 'app/exercises/shared/mode-picker/mode-picker.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisExerciseModule } from 'app/exercises/shared/exercise/exercise.module';

import { exercisePopupRoute } from 'app/exercises/shared/exercise/exercise.route';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

const ENTITY_STATES = [...exercisePopupRoute];

@NgModule({
    imports: [
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisExerciseModule,
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisModePickerModule,
        AssessmentInstructionsModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisMarkdownModule,
    ],
    declarations: [ExerciseDetailsComponent],
    exports: [ExerciseDetailsComponent],
})
export class ExerciseDetailsModule {}
