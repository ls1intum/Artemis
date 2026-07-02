import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Graphs, SpanType, StatisticsView } from 'app/exercise/shared/entities/statistics.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { StatisticsGraphComponent } from 'app/exercise/statistics-graph/statistics-graph.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { SelectButtonModule } from 'primeng/selectbutton';

interface SpanOption {
    label: string;
    value: SpanType;
}

/**
 * Component for displaying Artemis statistics with various graph types.
 */
@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, StatisticsGraphComponent, ArtemisTranslatePipe, AdminTitleBarTitleDirective, AdminTitleBarActionsDirective, SelectButtonModule, FormsModule],
})
export class StatisticsComponent {
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

    protected readonly spanOptions: SpanOption[] = [
        { label: 'statistics.span.day', value: SpanType.DAY },
        { label: 'statistics.span.week', value: SpanType.WEEK },
        { label: 'statistics.span.month', value: SpanType.MONTH },
        { label: 'statistics.span.quarter', value: SpanType.QUARTER },
        { label: 'statistics.span.year', value: SpanType.YEAR },
    ];

    /** Current time span for statistics */
    readonly currentSpan = signal<SpanType>(SpanType.WEEK);

    /** Statistics view type */
    readonly statisticsView = signal<StatisticsView>(StatisticsView.ARTEMIS);

    onTabChanged(span: SpanType): void {
        this.currentSpan.set(span);
    }
}
