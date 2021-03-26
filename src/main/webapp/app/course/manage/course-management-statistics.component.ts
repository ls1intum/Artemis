import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { Subscription } from 'rxjs';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { CourseManagementStatisticsDTO } from './course-management-statistics-dto';

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

    defaultTitle = 'Course';
    currentAverageScore = 0;
    currentAbsolutePoints = 0;
    currentMaxPoints = 1;
    courseStatistics: CourseManagementStatisticsDTO;

    selectedValue: string;
    exerciseTitles: string[];

    constructor(private service: StatisticsService, private route: ActivatedRoute) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseId = params['courseId'];
        });
        this.service.getCourseStatistics(this.courseId).subscribe((res: CourseManagementStatisticsDTO) => {
            this.courseStatistics = res;
            this.selectedValue = this.defaultTitle;
            this.exerciseTitles = Object.keys(res.exerciseNameToMaxPointsMap);
            this.currentAbsolutePoints = this.courseStatistics.averagePointsOfCourse;
            this.currentMaxPoints = this.courseStatistics.maxPointsOfCourse;
            this.currentAverageScore = Math.round((this.courseStatistics.averagePointsOfCourse / this.courseStatistics.maxPointsOfCourse) * 100);
        });
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }

    onSelect(): void {
        this.currentAbsolutePoints =
            this.selectedValue === this.defaultTitle ? this.courseStatistics.averagePointsOfCourse : this.courseStatistics.exerciseNameToAveragePointsMap[this.selectedValue];
        this.currentMaxPoints =
            this.selectedValue === this.defaultTitle ? this.courseStatistics.maxPointsOfCourse : this.courseStatistics.exerciseNameToMaxPointsMap[this.selectedValue];
        this.currentAverageScore = Math.round((this.currentAbsolutePoints / this.currentMaxPoints) * 100);
    }
}
