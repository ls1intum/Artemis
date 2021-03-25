import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-course-management-statistics',
    templateUrl: './course-management-statistics.component.html',
})
export class CourseManagementStatisticsComponent implements OnInit {
    // html properties
    SpanType = SpanType;
    graphTypes = [
        Graphs.SUBMISSIONS,
        Graphs.ACTIVE_USERS,
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
    statisticsView: StatisticsView = StatisticsView.COURSE;
    paramSub: Subscription;
    courseId: number;

    constructor(private route: ActivatedRoute) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseId = params['courseId'];
        });
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
