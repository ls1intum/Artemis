import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisCourseExerciseRowModule, ExerciseUnitComponent],
    exports: [ExerciseUnitComponent],
})
export class ArtemisLectureUnitsModule {}
