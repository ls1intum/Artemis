import {JhiAlertService} from 'ng-jhipster';
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {HttpResponse, HttpErrorResponse} from '@angular/common/http';
import {Exercise, ExerciseService} from 'app/entities/exercise';
import {Participation, ParticipationService} from 'app/entities/participation';
import {Result, ResultService} from 'app/entities/result';
import {TutorLeaderboardData} from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.component';

@Component({
    selector: 'jhi-instructor-exercise-dashboard',
    templateUrl: './instructor-exercise-dashboard.component.html',
    providers: [JhiAlertService]
})
export class InstructorExerciseDashboardComponent implements OnInit {
    exercise: Exercise;
    courseId: number;
    numberOfAssessments: number;

    dataNumbersForPieChart: number[];
    tutorLeaderboardData: TutorLeaderboardData = {};

    constructor(
        private exerciseService: ExerciseService,
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        private participationService: ParticipationService,
        private resultService: ResultService
    ) {}

    ngOnInit(): void {
        this.courseId = +this.route.snapshot.paramMap.get('courseId');
        this.loadExercise(Number(this.route.snapshot.paramMap.get('exerciseId')));
    }

    private loadExercise(exerciseId: number) {
        this.exerciseService.find(exerciseId).subscribe(
            (res: HttpResponse<Exercise>) => {
                this.exercise = res.body;

                this.participationService.findAllParticipationsByExercise(this.exercise.id, {withEagerResults: true}).subscribe((participationRes: HttpResponse<Participation[]>) => {
                    this.exercise.participations = participationRes.body;
                    this.numberOfAssessments = this.exercise.participations.filter(participation =>
                        participation.results.filter(result => result.rated).length > 0
                    ).length;

                    this.dataNumbersForPieChart = [
                        this.exercise.participations.length - this.numberOfAssessments,
                        this.numberOfAssessments,
                    ];
                });
            },
            (response: HttpErrorResponse) => this.onError(response.message)
        );

        this.resultService.getResultsForExercise(this.courseId, exerciseId, {withAssessors: true}).subscribe(
            (res: HttpResponse<Result[]>) => {
                const results = res.body;

                for (const result of results) {
                    const tutorId = result.assessor.id;
                    if (!this.tutorLeaderboardData[tutorId]) {
                        this.tutorLeaderboardData[tutorId] = {
                            tutor: result.assessor,
                            nrOfAssessments: 0
                        };
                    }

                    this.tutorLeaderboardData[tutorId].nrOfAssessments++;
                }
            }
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }
}
