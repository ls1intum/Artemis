import { NgModule } from '@angular/core';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';

@NgModule({
    exports: [NonProgrammingExerciseDetailCommonActionsComponent],
    imports: [
        ArtemisSharedCommonModule,
        ArtemisExerciseScoresModule,
        ArtemisSharedModule,
        RouterModule,
        ArtemisAssessmentSharedModule,
        NonProgrammingExerciseDetailCommonActionsComponent,
    ],
})
export class NonProgrammingExerciseDetailCommonActionsModule {}
