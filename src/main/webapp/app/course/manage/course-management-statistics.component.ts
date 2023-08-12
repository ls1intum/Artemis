import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { Subscription } from 'rxjs';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { CourseManagementStatisticsDTO } from './course-management-statistics-dto';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-course-management-statistics',
    templateUrl: './course-management-statistics.component.html',
    styleUrls: ['./course-management-statistics.component.scss'],
})
export class CourseManagementStatisticsComponent implements OnInit {
    documentationType = DocumentationType.Statistics;
    // html properties
    SpanType = SpanType;
    graph = Graphs;
    graphTypes = [
        Graphs.SUBMISSIONS,
        Graphs.ACTIVE_USERS,
        Graphs.RELEASED_EXERCISES,
        Graphs.EXERCISES_DUE,
        Graphs.ACTIVE_TUTORS,
        Graphs.CREATED_RESULTS,
        Graphs.CREATED_FEEDBACKS,
        Graphs.POSTS,
        Graphs.RESOLVED_POSTS,
        Graphs.CONDUCTED_EXAMS,
        Graphs.EXAM_PARTICIPATIONS,
        Graphs.EXAM_REGISTRATIONS,
    ];
    currentSpan: SpanType = SpanType.WEEK;
    statisticsView: StatisticsView = StatisticsView.COURSE;
    paramSub: Subscription;
    courseId: number;

    defaultTitle = 'Course';
    // Average Score
    selectedValueAverageScore: string;
    currentAverageScore = 0;
    currentAbsolutePoints = 0;
    currentMaxPoints = 1;
    exerciseTitles: string[];

    // Average Rating
    selectedValueAverageRating: string;
    currentAverageRating = 0;
    currentAverageRatingInPercent = 0;
    tutorNames: string[];

    courseStatistics: CourseManagementStatisticsDTO;

    constructor(private service: StatisticsService, private route: ActivatedRoute) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseId = params['courseId'];
        });
        this.service.getCourseStatistics(this.courseId).subscribe((res: CourseManagementStatisticsDTO) => {
            this.courseStatistics = res;
        });
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
