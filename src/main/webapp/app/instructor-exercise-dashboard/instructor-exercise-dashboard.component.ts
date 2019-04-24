import { JhiAlertService } from 'ng-jhipster';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { Result, ResultService } from 'app/entities/result';
import { TutorLeaderboardData } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.component';
import { StatsForInstructorDashboard } from 'app/entities/course';

@Component({
    selector: 'jhi-instructor-exercise-dashboard',
    templateUrl: './instructor-exercise-dashboard.component.html',
    providers: [JhiAlertService],
})
export class InstructorExerciseDashboardComponent implements OnInit {
    exercise: Exercise;
    courseId: number;

    stats: StatsForInstructorDashboard = {
        numberOfStudents: 0,
        numberOfSubmissions: 0,
        numberOfTutors: 0,
        numberOfAssessments: 0,
        numberOfComplaints: 0,
        numberOfOpenComplaints: 0,
    };

    dataForAssessmentPieChart: number[];
    tutorLeaderboardData: TutorLeaderboardData = {};
    totalAssessmentPercentage: number;

    constructor(
        private exerciseService: ExerciseService,
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        private resultService: ResultService,
        private router: Router,
    ) {}

    ngOnInit(): void {
        this.courseId = +this.route.snapshot.paramMap.get('courseId');
        this.loadExercise(+this.route.snapshot.paramMap.get('exerciseId'));
    }

    back() {
        this.router.navigate([`/course/${this.courseId}/instructor-dashboard`]);
    }

    private loadExercise(exerciseId: number) {
        this.exerciseService
            .find(exerciseId)
            .subscribe((res: HttpResponse<Exercise>) => (this.exercise = res.body), (response: HttpErrorResponse) => this.onError(response.message));

        this.resultService.getResultsForExercise(this.courseId, exerciseId, { withAssessors: true }).subscribe((res: HttpResponse<Result[]>) => {
            const results = res.body;

            for (const result of results) {
                if (result.rated && result.assessor) {
                    const tutorId = result.assessor.id;
                    if (!this.tutorLeaderboardData[tutorId]) {
                        this.tutorLeaderboardData[tutorId] = {
                            tutor: result.assessor,
                            numberOfAssessments: 0,
                            numberOfComplaints: 0,
                        };
                    }

                    this.tutorLeaderboardData[tutorId].numberOfAssessments++;

                    if (result.hasComplaint) {
                        this.tutorLeaderboardData[tutorId].numberOfComplaints++;
                    }
                }
            }
        });

        this.exerciseService.getStatsForInstructors(exerciseId).subscribe(
            (res: HttpResponse<StatsForInstructorDashboard>) => {
                this.stats = Object.assign({}, this.stats, res.body);

                if (this.stats.numberOfSubmissions > 0) {
                    this.totalAssessmentPercentage = Math.round((this.stats.numberOfAssessments / this.stats.numberOfSubmissions) * 100);
                }

                this.dataForAssessmentPieChart = [this.stats.numberOfSubmissions - this.stats.numberOfAssessments, this.stats.numberOfAssessments];
            },
            (response: string) => this.onError(response),
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }
}
