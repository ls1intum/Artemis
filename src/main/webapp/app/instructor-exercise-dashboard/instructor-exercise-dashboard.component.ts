import {JhiAlertService} from 'ng-jhipster';
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {HttpResponse, HttpErrorResponse} from '@angular/common/http';
import {Exercise, ExerciseService} from 'app/entities/exercise';
import {Participation, ParticipationService} from 'app/entities/participation';

@Component({
    selector: 'jhi-instructor-exercise-dashboard',
    templateUrl: './instructor-exercise-dashboard.component.html',
    providers: [JhiAlertService]
})
export class InstructorExerciseDashboardComponent implements OnInit {
    exercise: Exercise;
    courseId: number;

    dataForGraph: number[];

    constructor(
        private exerciseService: ExerciseService,
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        private participationService: ParticipationService
    ) {}

    ngOnInit(): void {
        this.courseId = +this.route.snapshot.paramMap.get('courseId');
        this.loadExercise(Number(this.route.snapshot.paramMap.get('exerciseId')));
    }

    private loadExercise(exerciseId: number) {
        this.exerciseService.find(exerciseId).subscribe(
            (res: HttpResponse<Exercise>) => {
                this.exercise = res.body;

                this.participationService.findAllParticipationsByExercise(this.exercise.id).subscribe((participationRes: HttpResponse<Participation[]>) => {
                    this.exercise.participations = participationRes.body;

                    this.dataForGraph = [
                        this.exercise.participations.length,
                        this.exercise.participations.filter(participation =>
                            participation.results.filter(result => result.rated).length > 0
                        ).length
                    ];
                });
            },
            (response: HttpErrorResponse) => this.onError(response.message)
        );
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }
}
