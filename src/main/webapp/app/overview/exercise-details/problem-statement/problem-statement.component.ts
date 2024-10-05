import { HttpResponse } from '@angular/common/http';
import { Component, Input, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseDetailsType, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';

@Component({
    selector: 'jhi-problem-statement',
    templateUrl: './problem-statement.component.html',
    styleUrls: ['../../course-overview.scss'],
})
export class ProblemStatementComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private exerciseService = inject(ExerciseService);
    private participationService = inject(ParticipationService);

    @Input()
    public exercise?: Exercise;

    @Input()
    participation?: StudentParticipation;

    ngOnInit() {
        this.route.params.subscribe((params) => {
            const exerciseId = parseInt(params['exerciseId'], 10);
            let participationId: number | undefined = undefined;
            if (params['participationId']) {
                participationId = parseInt(params['participationId'], 10);
            }

            if (!this.exercise) {
                this.exerciseService.getExerciseDetails(exerciseId).subscribe((exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
                    this.exercise = exerciseResponse.body!.exercise;
                });
            }
            if (!this.participation && participationId) {
                this.participationService.find(participationId).subscribe((participationResponse) => {
                    this.participation = participationResponse.body!;
                });
            }
        });
    }

    get isProgrammingExercise(): boolean {
        return this.exercise?.type === ExerciseType.PROGRAMMING;
    }
}
