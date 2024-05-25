import { NgModule } from '@angular/core';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { GradingKeyOverviewModule } from 'app/grading-system/grading-key-overview/grading-key-overview.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ExerciseCategoriesModule } from 'app/shared/exercise-categories/exercise-categories.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisSharedPipesModule,
        ArtemisExerciseButtonsModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        OrionModule,
        GradingKeyOverviewModule,
        SubmissionResultStatusModule,
        ExerciseCategoriesModule,
    ],
    declarations: [CourseExerciseRowComponent],
    exports: [CourseExerciseRowComponent],
})
export class ArtemisCourseExerciseRowModule {}
