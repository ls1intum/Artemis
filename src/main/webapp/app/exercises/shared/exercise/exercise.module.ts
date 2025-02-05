import { NgModule } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ExerciseDetailStatisticsComponent } from 'app/exercises/shared/statistics/exercise-detail-statistics.component';
import { ExerciseStatisticsComponent } from 'app/exercises/shared/statistics/exercise-statistics.component';
import { QuizExerciseLifecycleButtonsComponent } from 'app/exercises/quiz/manage/quiz-exercise-lifecycle-buttons.component';

@NgModule({
    imports: [ArtemisSharedComponentModule, FormsModule, ExerciseDetailStatisticsComponent, ExerciseStatisticsComponent, QuizExerciseLifecycleButtonsComponent],
    exports: [ExerciseDetailStatisticsComponent, ExerciseStatisticsComponent, QuizExerciseLifecycleButtonsComponent],
})
export class ArtemisExerciseModule {}
