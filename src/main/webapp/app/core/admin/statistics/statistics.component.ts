import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { Graphs, SpanType, StatisticsView } from 'app/exercise/shared/entities/statistics.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * Component for displaying Artemis statistics with various graph types.
 */
@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
    imports: [TranslateDirective, StatisticsGraphComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatisticsComponent {
    /** Enum for template access */
    protected readonly SpanType = SpanType;

    /** Available graph types to display */
    protected readonly graphTypes = [
        Graphs.SUBMISSIONS,
        Graphs.ACTIVE_USERS,
        Graphs.LOGGED_IN_USERS,
        Graphs.RELEASED_EXERCISES,
        Graphs.EXERCISES_DUE,
        Graphs.CONDUCTED_EXAMS,
        Graphs.EXAM_PARTICIPATIONS,
        Graphs.EXAM_REGISTRATIONS,
        Graphs.ACTIVE_TUTORS,
        Graphs.CREATED_RESULTS,
        Graphs.CREATED_FEEDBACKS,
    ];

    /** Current time span for statistics */
    readonly currentSpan = signal<SpanType>(SpanType.WEEK);

    /** Statistics view type */
    readonly statisticsView = signal<StatisticsView>(StatisticsView.ARTEMIS);

    onTabChanged(span: SpanType): void {
        this.currentSpan.set(span);
    }
}
