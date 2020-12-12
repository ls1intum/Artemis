import { Component } from '@angular/core';
import { Graphs, SpanType } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
})
export class StatisticsComponent {
    // html properties
    SpanType = SpanType;
    Graphs = Graphs;
    graphTypes = [Graphs.SUBMISSIONS, Graphs.ACTIVE_USERS, Graphs.RELEASED_EXERCISES];
    currentSpan: SpanType = SpanType.WEEK;

    constructor() {}

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
