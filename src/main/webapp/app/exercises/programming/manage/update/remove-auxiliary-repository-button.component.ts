import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { faMinus } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-remove-auxiliary-repository-button',
    template: `
        <jhi-button
            [btnType]="ButtonType.ERROR"
            [btnSize]="ButtonSize.SMALL"
            [icon]="faMinus"
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

    @Output() onRefresh: EventEmitter<any> = new EventEmitter<any>();

    // Icons
    faMinus = faMinus;

    /**
     * Removes the auxiliary repository of the selected row from the respective programming exercise.
     */
    removeAuxiliaryRepository() {
        const auxRepoIndex = this.programmingExercise.auxiliaryRepositories?.indexOf(this.row)!;
        this.programmingExercise.auxiliaryRepositories?.splice(auxRepoIndex, 1); // Note: splice changes the array auxiliaryRepositories in place
        this.onRefresh.emit();
        // This activates the angular change detection
        this.programmingExercise.auxiliaryRepositories = [...this.programmingExercise.auxiliaryRepositories!];
    }
}
