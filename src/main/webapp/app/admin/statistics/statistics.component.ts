import { Component } from '@angular/core';
import { Graphs, SpanType } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
})
export class StatisticsComponent {
    // html properties
    SpanType = SpanType;
    graphTypes = [Graphs.SUBMISSIONS, Graphs.ACTIVE_USERS, Graphs.LOGGED_IN_USERS, Graphs.RELEASED_EXERCISES, Graphs.EXERCISES_DUE];
    currentSpan: SpanType = SpanType.WEEK;

    constructor() {}

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
