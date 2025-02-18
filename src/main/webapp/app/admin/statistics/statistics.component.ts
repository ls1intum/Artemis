import { Component } from '@angular/core';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
    imports: [TranslateDirective, StatisticsGraphComponent, ArtemisTranslatePipe],
})
export class StatisticsComponent {
    // html properties
    SpanType = SpanType;
    graphTypes = [
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
    currentSpan: SpanType = SpanType.WEEK;
    statisticsView: StatisticsView = StatisticsView.ARTEMIS;

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
