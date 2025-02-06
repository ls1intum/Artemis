import { NgModule } from '@angular/core';

import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';

@NgModule({
    imports: [ArtemisCourseExerciseRowModule, ExerciseUnitComponent],
    exports: [ExerciseUnitComponent],
})
export class ArtemisLectureUnitsModule {}
