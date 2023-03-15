import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    declarations: [NonProgrammingExerciseDetailCommonActionsComponent],
    exports: [NonProgrammingExerciseDetailCommonActionsComponent],
    imports: [ArtemisSharedCommonModule, ArtemisExerciseScoresModule, ArtemisSharedModule, RouterModule, ArtemisAssessmentSharedModule],
})
export class NonProgrammingExerciseDetailCommonActionsModule {}
