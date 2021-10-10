import { AlertService } from 'app/core/util/alert.service';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { SortService } from 'app/shared/service/sort.service';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-instructor-exercise-dashboard',
    templateUrl: './instructor-exercise-dashboard.component.html',
    providers: [],
})
export class InstructorExerciseDashboardComponent implements OnInit {
    exercise: Exercise;
    courseId: number;

    stats = new StatsForDashboard();

    dataForAssessmentPieChart: number[];
    totalManualAssessmentPercentage = new DueDateStat();
    totalAutomaticAssessmentPercentage = new DueDateStat();

    constructor(
        private exerciseService: ExerciseService,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private resultService: ResultService,
        private router: Router,
        private sortService: SortService,
    ) {}

    /**
     * Extracts the course and exercise ids from the route params and fetches the exercise from the server
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.loadExercise(exerciseId);
    }

    /**
     * Navigates back to the instructor dashboard where the user came from
     */
    back() {
        this.router.navigate([`/course-management/${this.courseId}/instructor-dashboard`]);
    }

    /**
     * Computes the stats for the assessment charts.
     * Percentages are rounded towards zero.
     */
    public setStatistics() {
        if (this.stats.numberOfSubmissions.inTime > 0) {
            this.totalManualAssessmentPercentage.inTime = Math.floor(
                ((this.stats.totalNumberOfAssessments.inTime - this.stats.numberOfAutomaticAssistedAssessments.inTime) / this.stats.numberOfSubmissions.inTime) * 100,
            );
            this.totalAutomaticAssessmentPercentage.inTime = Math.floor((this.stats.numberOfAutomaticAssistedAssessments.inTime / this.stats.numberOfSubmissions.inTime) * 100);
        } else {
            this.totalManualAssessmentPercentage.inTime = 100;
        }
        if (this.stats.numberOfSubmissions.late > 0) {
            this.totalManualAssessmentPercentage.late = Math.floor(
                ((this.stats.totalNumberOfAssessments.late - this.stats.numberOfAutomaticAssistedAssessments.late) / this.stats.numberOfSubmissions.late) * 100,
            );
            this.totalAutomaticAssessmentPercentage.late = Math.floor((this.stats.numberOfAutomaticAssistedAssessments.late / this.stats.numberOfSubmissions.late) * 100);
        } else {
            this.totalManualAssessmentPercentage.late = 100;
        }

        this.dataForAssessmentPieChart = [
            this.stats.numberOfSubmissions.total - this.stats.totalNumberOfAssessments.total,
            this.stats.totalNumberOfAssessments.total - this.stats.numberOfAutomaticAssistedAssessments.total,
            this.stats.numberOfAutomaticAssistedAssessments.total,
        ];
    }

    private loadExercise(exerciseId: number) {
        this.exerciseService.find(exerciseId).subscribe(
            (response: HttpResponse<Exercise>) => (this.exercise = response.body!),
            (error: HttpErrorResponse) => onError(this.alertService, error),
        );

        this.exerciseService.getStatsForInstructors(exerciseId).subscribe(
            (response: HttpResponse<StatsForDashboard>) => {
                this.stats = StatsForDashboard.from(Object.assign({}, this.stats, response.body));
                this.sortService.sortByProperty(this.stats.tutorLeaderboardEntries, 'points', false);
                this.setStatistics();
            },
            (errorMessage: string) => this.onError(errorMessage),
        );
    }

    private onError(error: string) {
        this.alertService.error(error);
    }
}
