import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ValidationReason } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard',
    templateUrl: './programming-exercise-update-wizard.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardComponent implements OnInit {
    programmingExercise: ProgrammingExercise;

    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();

    @Input()
    get exercise() {
        return this.programmingExercise;
    }

    set exercise(exercise: ProgrammingExercise) {
        this.programmingExercise = exercise;
        this.exerciseChange.emit(this.programmingExercise);
    }

    @Input() toggleMode: () => void;
    @Input() isSaving: boolean;
    @Input() currentStep: number;
    @Output() onNextStep: EventEmitter<any> = new EventEmitter();
    @Input() getInvalidReasons: () => ValidationReason[];
    @Input() isImport: boolean;

    constructor(protected activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
    }

    nextStep() {
        this.onNextStep.emit();
    }
}
