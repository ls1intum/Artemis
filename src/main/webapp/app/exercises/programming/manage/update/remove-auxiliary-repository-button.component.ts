import { Component, Input } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';

@Component({
    selector: 'jhi-remove-auxiliary-repository-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.ERROR"
            [btnSize]="ButtonSize.SMALL"
            [icon]="'minus'"
            [title]="'entity.action.remove'"
            (onClick)="removeAuxiliaryRepository($event)"
        ></jhi-button>
    `,
})
export class RemoveAuxiliaryRepositoryButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() programmingExercise: ProgrammingExercise;

    removeAuxiliaryRepository(event: MouseEvent) {
        if (this.programmingExercise.auxiliaryRepositories === undefined) {
            this.programmingExercise.auxiliaryRepositories = [];
        }
        this.programmingExercise.auxiliaryRepositories?.push(new AuxiliaryRepository());
        this.programmingExercise.auxiliaryRepositories = [...this.programmingExercise.auxiliaryRepositories];

        // TODO AUXREPOS
    }
}
