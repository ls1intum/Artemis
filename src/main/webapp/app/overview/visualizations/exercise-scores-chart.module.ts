import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { LineChartModule } from '@swimlane/ngx-charts';

import { ExerciseScoresChartComponent } from 'app/overview/visualizations/exercise-scores-chart/exercise-scores-chart.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [TranslateModule, CommonModule, ArtemisSharedModule, LineChartModule],
    declarations: [ExerciseScoresChartComponent],
    exports: [ExerciseScoresChartComponent],
})
export class ArtemisExerciseScoresChartModule {}
