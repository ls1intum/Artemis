import { Component } from '@angular/core';
import { Graphs, SpanType } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-course-detail-statistics',
    templateUrl: './course-detail-statistics.component.html',
})
export class CourseDetailStatisticsComponent {
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

    constructor() {}

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
