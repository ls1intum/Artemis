import { AlertService } from 'app/core/alert/alert.service';
import { Component, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from '../../manage/course-management.service';
import { catchError, map, tap } from 'rxjs/operators';
import { of, zip } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { getIcon, getIconTooltip } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-instructor-course-dashboard',
    templateUrl: './instructor-course-dashboard.component.html',
    providers: [],
})
export class InstructorCourseDashboardComponent implements OnInit {
    course: Course;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    isLoading = true;
    exercisesSortingPredicate = 'assessmentDueDate';
    exercisesReverseOrder = false;

    stats = new StatsForDashboard();
    dataForAssessmentPieChart: number[];

    readonly MIN_POINTS_GREEN = 100;
    readonly MIN_POINTS_ORANGE = 50;

    constructor(private courseService: CourseManagementService, private resultService: ResultService, private route: ActivatedRoute, private jhiAlertService: AlertService) {}

    /**
     * On init fetch the course from the server.
     */
    ngOnInit(): void {
        this.isLoading = true;
        this.loadCourse(Number(this.route.snapshot.paramMap.get('courseId')));
    }

    /**
     * Load the course and statistics relevant for instructors for a course.
     * @param courseId ID of the course to load.
     */
    private loadCourse(courseId: number) {
        // Load the course.
        const loadCourseObservable = this.courseService.findWithExercisesAndParticipations(courseId).pipe(
            map((res: HttpResponse<Course>) => res.body!),
            tap((course: Course) => (this.course = course)),
            catchError((response: HttpErrorResponse) => {
                this.onError(response.message);
                return of(null);
            }),
        );

        // Load course stats.
        const loadStatsObservable = this.courseService.getStatsForInstructors(courseId).pipe(
            map((res: HttpResponse<StatsForDashboard>) => Object.assign({}, this.stats, res.body)),
            tap((stats) => {
                this.stats = stats;
                this.dataForAssessmentPieChart = [this.stats.numberOfSubmissions - this.stats.numberOfAssessments, this.stats.numberOfAssessments];
            }),
            catchError((response: string) => {
                this.onError(response);
                return of(null);
            }),
        );

        // After both calls are done, the loading flag is removed.
        zip(loadCourseObservable, loadStatsObservable).subscribe(() => {
            this.isLoading = false;
        });
    }

    /**
     * Calculate percentage for given numerator an denominator.
     * @param numerator
     * @param denominator
     */
    calculatePercentage(numerator: number, denominator: number): number {
        if (denominator === 0) {
            return 0;
        }

        return Math.round((numerator / denominator) * 100);
    }

    /**
     * Return class of assessment progress.
     * @param numberOfAssessments Number of assessed submissions.
     * @param length Total number of participations.
     * @return {string} 'bg-danger', 'bg-warning' or 'bg-success' depending on percentage of assessed submissions.
     */
    calculateClass(numberOfAssessments: number, length: number): string {
        const percentage = this.calculatePercentage(numberOfAssessments, length);

        if (percentage < this.MIN_POINTS_ORANGE) {
            return 'bg-danger';
        } else if (percentage < this.MIN_POINTS_GREEN) {
            return 'bg-warning';
        }

        return 'bg-success';
    }

    /**
     * Empty callback function.
     */
    callback() {}

    /**
     * Pass an error to the jhiAlertService.
     * @param error
     */
    private onError(error: string) {
        this.jhiAlertService.error(error, null, undefined);
    }
}
