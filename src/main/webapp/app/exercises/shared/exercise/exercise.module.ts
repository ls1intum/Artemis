import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormsModule } from '@angular/forms';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ExerciseDetailStatisticsComponent } from 'app/exercises/shared/statistics/exercise-detail-statistics.component';
import { ExerciseStatisticsComponent } from 'app/exercises/shared/statistics/exercise-statistics.component';
import { ArtemisChartsModule } from 'app/shared/chart/artemis-charts.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, FormsModule, ArtemisChartsModule],
    declarations: [ExerciseDetailStatisticsComponent, ExerciseStatisticsComponent],
    exports: [ExerciseDetailStatisticsComponent, ExerciseStatisticsComponent],
})
export class ArtemisExerciseModule {}
