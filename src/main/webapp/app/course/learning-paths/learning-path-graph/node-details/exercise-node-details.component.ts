import { Component, Input, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-exercise-node-details',
    templateUrl: './exercise-node-details.component.html',
})
export class ExerciseNodeDetailsComponent implements OnInit {
    @Input() exerciseId: number;

    exercise: Exercise;

    isLoading = false;

    constructor(private exerciseService: ExerciseService, private alertService: AlertService) {}

    ngOnInit() {
        if (this.exerciseId) {
            this.loadData();
        }
    }
    private loadData() {
        this.isLoading = true;
        this.exerciseService.find(this.exerciseId).subscribe({
            next: (exerciseResponse) => {
                this.exercise = exerciseResponse.body!;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }
}
