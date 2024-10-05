import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { Exercise, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-exercise-node-details',
    templateUrl: './exercise-node-details.component.html',
})
export class ExerciseNodeDetailsComponent implements OnInit {
    private exerciseService = inject(ExerciseService);
    private alertService = inject(AlertService);

    @Input() exerciseId: number;
    @Input() exercise?: Exercise;
    @Output() exerciseChange = new EventEmitter<Exercise>();

    isLoading = false;

    ngOnInit() {
        if (!this.exercise) {
            this.loadData();
        }
    }
    private loadData() {
        this.isLoading = true;
        this.exerciseService.find(this.exerciseId).subscribe({
            next: (exerciseResponse) => {
                this.exercise = exerciseResponse.body!;
                this.isLoading = false;
                this.exerciseChange.emit(this.exercise);
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    protected readonly getIcon = getIcon;
    protected readonly getIconTooltip = getIconTooltip;
}
