import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ExerciseScoresChartComponent } from 'app/overview/visualizations/exercise-scores-chart/exercise-scores-chart.component';
import { ChartsModule } from 'ng2-charts';

@NgModule({
    imports: [ChartsModule, TranslateModule, CommonModule],
    declarations: [ExerciseScoresChartComponent],
    exports: [ExerciseScoresChartComponent],
})
export class ArtemisExerciseScoresChartModule {}
