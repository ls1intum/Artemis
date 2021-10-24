import { Component, Input } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';

@Component({
    selector: 'jhi-add-auxiliary-repository-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.SUCCESS"
            [btnSize]="ButtonSize.SMALL"
            [icon]="'plus'"
            [title]="'entity.action.addAuxiliaryRepository'"
            (onClick)="addAuxiliaryRepositoryRow()"
        ></jhi-button>
    `,
})
export class AddAuxiliaryRepositoryButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() programmingExercise: ProgrammingExercise;
    @Input() onRefresh: () => void;

    /**
     * Adds a new auxiliary repository, which is displayed as a new row, to the respective programming exercise and activates the angular change detection.
     */
    addAuxiliaryRepositoryRow() {
        if (this.programmingExercise.auxiliaryRepositories === undefined) {
            this.programmingExercise.auxiliaryRepositories = [];
        }
        const newAuxiliaryRepository = new AuxiliaryRepository();
        newAuxiliaryRepository.name = '';
        newAuxiliaryRepository.checkoutDirectory = '';
        this.programmingExercise.auxiliaryRepositories?.push(newAuxiliaryRepository);
        this.onRefresh();
        this.programmingExercise.auxiliaryRepositories = [...this.programmingExercise.auxiliaryRepositories];
    }
}
