import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TumAetUiBarChartComponent, TumAetUiBarChartConfig } from '@tumaet/ui-angular';
import { TumAetUiChartData } from 'app/shared-ui/chart/tum-aet-ui-chart-adapters';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheckCircle, faSync, faTimesCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-question-statistics-chart',
    templateUrl: './question-statistics-chart.component.html',
    styleUrls: ['../quiz-point-statistic/quiz-point-statistic.component.scss'],
    imports: [TumAetUiBarChartComponent, TranslateDirective, ArtemisTranslatePipe, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { class: 'contents' },
})
export class QuestionStatisticsChartComponent {
    readonly rated = input(true);
    readonly participants = input(0);
    readonly showSolution = input(false);
    readonly chartData = input.required<TumAetUiChartData>();
    readonly chartConfig = input.required<TumAetUiBarChartConfig>();
    readonly toggleSolution = output<void>();
    readonly toggleRated = output<void>();
    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faTimesCircle = faTimesCircle;
    protected readonly faSync = faSync;
}
