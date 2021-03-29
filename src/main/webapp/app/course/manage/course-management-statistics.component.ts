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
        Graphs.ACTIVE_TUTORS,
        Graphs.CREATED_RESULTS,
        Graphs.CREATED_FEEDBACKS,
        Graphs.QUESTIONS_ASKED,
        Graphs.QUESTIONS_ANSWERED,
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
            this.selectedValueAverageScore = this.defaultTitle;
            this.exerciseTitles = Object.keys(res.exerciseNameToMaxPointsMap);
            this.currentAbsolutePoints = this.courseStatistics.averagePointsOfCourse;
            this.currentMaxPoints = this.courseStatistics.maxPointsOfCourse;
            this.currentAverageScore = Math.round((this.courseStatistics.averagePointsOfCourse / this.courseStatistics.maxPointsOfCourse) * 100);

            this.currentAverageRating = this.courseStatistics.averageRatingInCourse;
            this.selectedValueAverageRating = this.defaultTitle;
            this.tutorNames = Object.keys(res.tutorToAverageRatingMap);
            this.currentAverageRatingInPercent = (this.currentAverageRating * 100) / 5;
        });
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
    }

    /**
     * Callback function for when an user selected a Exercise or the course from the dropdown list below the average score doughnut chart
     */
    onAverageScoreSelect(): void {
        this.currentAbsolutePoints =
            this.selectedValueAverageScore === this.defaultTitle
                ? this.courseStatistics.averagePointsOfCourse
                : this.courseStatistics.exerciseNameToAveragePointsMap[this.selectedValueAverageScore];
        this.currentMaxPoints =
            this.selectedValueAverageScore === this.defaultTitle
                ? this.courseStatistics.maxPointsOfCourse
                : this.courseStatistics.exerciseNameToMaxPointsMap[this.selectedValueAverageScore];
        this.currentAverageScore = Math.round((this.currentAbsolutePoints / this.currentMaxPoints) * 100);
    }

    /**
     * Callback function for when an user selected a tutor or the course from the dropdown list below the tutor rating doughnut chart
     */
    onAverageRatingSelect(): void {
        this.currentAverageRating =
            this.selectedValueAverageRating === this.defaultTitle
                ? this.courseStatistics.averageRatingInCourse
                : this.courseStatistics.tutorToAverageRatingMap[this.selectedValueAverageRating];
        this.currentAverageRatingInPercent = (this.currentAverageRating * 100) / 5;
    }
}
