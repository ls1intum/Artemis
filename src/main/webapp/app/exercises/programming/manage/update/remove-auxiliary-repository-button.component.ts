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
            (onClick)="removeAuxiliaryRepository()"
        ></jhi-button>
    `,
})
export class RemoveAuxiliaryRepositoryButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() programmingExercise: ProgrammingExercise;

    @Input() row: AuxiliaryRepository;

    /**
     * Removes the auxiliary repository of the selected row from the respective programming exercise and activates the angular change detection.
     */
    removeAuxiliaryRepository() {
        // TODO Niclas Schümann: fix this implementation or remove unused variables
        // const auxRepoIndex = this.programmingExercise.auxiliaryRepositories?.indexOf(this.row)!;
        // const removedRepository = this.programmingExercise.auxiliaryRepositories?.splice(auxRepoIndex, 1)!;
        this.programmingExercise.auxiliaryRepositories = [...this.programmingExercise.auxiliaryRepositories!];
    }
}
