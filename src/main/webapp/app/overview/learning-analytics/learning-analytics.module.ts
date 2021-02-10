import { NgModule } from '@angular/core';
import { ExerciseScoresChartComponent } from 'app/overview/learning-analytics/exercise-scores-chart/exercise-scores-chart.component';
import { ChartsModule } from 'ng2-charts';

@NgModule({
    imports: [ChartsModule],
    declarations: [ExerciseScoresChartComponent],
    exports: [ExerciseScoresChartComponent],
})
export class ArtemisLearningAnalyticsModule {}
