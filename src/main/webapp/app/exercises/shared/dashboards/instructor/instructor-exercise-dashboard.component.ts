import { AlertService } from 'app/core/alert/alert.service';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ResultService } from 'app/exercises/shared/result/result.service';

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
    totalManualAssessmentPercentage: number;
    totalAutomaticAssessmentPercentage: number;

    constructor(
        private exerciseService: ExerciseService,
        private route: ActivatedRoute,
        private jhiAlertService: AlertService,
        private resultService: ResultService,
        private router: Router,
    ) {}

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.loadExercise(exerciseId);
    }

    back() {
        this.router.navigate([`/course-management/${this.courseId}/instructor-dashboard`]);
    }
    public setStatistics() {
        if (this.stats.numberOfSubmissions > 0) {
            this.totalManualAssessmentPercentage = Math.round(
                ((this.stats.numberOfAssessments - this.stats.numberOfAutomaticAssistedAssessments) / this.stats.numberOfSubmissions) * 100,
            );
            this.totalAutomaticAssessmentPercentage = Math.round((this.stats.numberOfAutomaticAssistedAssessments / this.stats.numberOfSubmissions) * 100);
        }

        this.dataForAssessmentPieChart = [
            this.stats.numberOfSubmissions - this.stats.numberOfAssessments,
            this.stats.numberOfAssessments - this.stats.numberOfAutomaticAssistedAssessments,
            this.stats.numberOfAutomaticAssistedAssessments,
        ];
    }
    private loadExercise(exerciseId: number) {
        this.exerciseService.find(exerciseId).subscribe(
            (res: HttpResponse<Exercise>) => (this.exercise = res.body!),
            (response: HttpErrorResponse) => this.onError(response.message),
        );

        this.exerciseService.getStatsForInstructors(exerciseId).subscribe(
            (res: HttpResponse<StatsForDashboard>) => {
                this.stats = Object.assign({}, this.stats, res.body);
                this.setStatistics();
            },
            (response: string) => this.onError(response),
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, undefined);
    }
}
