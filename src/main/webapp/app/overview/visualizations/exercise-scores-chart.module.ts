import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ExerciseScoresChartComponent } from 'app/overview/visualizations/exercise-scores-chart/exercise-scores-chart.component';
import { ChartsModule } from 'ng2-charts';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgxChartsModule } from '@swimlane/ngx-charts';

@NgModule({
    imports: [ChartsModule, TranslateModule, CommonModule, ArtemisSharedModule, NgxChartsModule],
    declarations: [ExerciseScoresChartComponent],
    exports: [ExerciseScoresChartComponent],
})
export class ArtemisExerciseScoresChartModule {}
