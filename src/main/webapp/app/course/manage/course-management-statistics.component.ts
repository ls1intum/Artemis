import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { CourseManagementStatisticsDTO } from './course-management-statistics-dto';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { Course } from 'app/entities/course.model';

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
    course: Course;

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
        this.route.data.subscribe(({ course }) => {
            if (course) {
                this.course = course;
            }
            if (this.course.id) {
                this.service.getCourseStatistics(this.course.id).subscribe((res: CourseManagementStatisticsDTO) => {
                    this.courseStatistics = res;
                });
            }
        });
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }
}
