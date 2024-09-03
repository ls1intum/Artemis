import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { AuxiliaryRepository } from 'app/entities/programming/programming-exercise-auxiliary-repository-model';
import { faPlus } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-add-auxiliary-repository-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.SUCCESS"
            [btnSize]="ButtonSize.SMALL"
            [icon]="faPlus"
            [title]="'entity.action.addAuxiliaryRepository'"
            (onClick)="addAuxiliaryRepositoryRow()"
        />
    `,
})
export class AddAuxiliaryRepositoryButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() programmingExercise: ProgrammingExercise;

    @Output() onRefresh: EventEmitter<any> = new EventEmitter<any>();

    // Icons
    faPlus = faPlus;

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
        this.onRefresh.emit();
        this.programmingExercise.auxiliaryRepositories = [...this.programmingExercise.auxiliaryRepositories];
    }
}
