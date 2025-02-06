import { NgModule } from '@angular/core';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';

import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { GradingKeyOverviewModule } from 'app/grading-system/grading-key-overview/grading-key-overview.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';

@NgModule({
    imports: [ArtemisExerciseButtonsModule, ArtemisHeaderExercisePageWithDetailsModule, GradingKeyOverviewModule, SubmissionResultStatusModule, CourseExerciseRowComponent],
    exports: [CourseExerciseRowComponent],
})
export class ArtemisCourseExerciseRowModule {}
