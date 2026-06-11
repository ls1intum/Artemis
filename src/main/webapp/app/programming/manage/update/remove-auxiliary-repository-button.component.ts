import { Component, input, output } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-remove-auxiliary-repository-button',
    template: `
        <jhi-button [btnType]="ButtonType.ERROR" [btnSize]="ButtonSize.SMALL" [icon]="faTrash" [title]="'entity.action.remove'" (onClick)="removeAuxiliaryRepository()" />
    `,
    imports: [ButtonComponent],
})
export class RemoveAuxiliaryRepositoryButtonComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    readonly programmingExercise = input.required<ProgrammingExercise>();

    readonly row = input.required<AuxiliaryRepository>();

    readonly onRefresh = output<void>();

    // Icons
    faTrash = faTrash;

    /**
     * Removes the auxiliary repository of the selected row from the respective programming exercise.
     */
    removeAuxiliaryRepository() {
        const programmingExercise = this.programmingExercise();
        const auxiliaryRepositories = programmingExercise.auxiliaryRepositories;
        if (!auxiliaryRepositories) {
            return;
        }
        const row = this.row();
        // Locate the row either by reference or by id, so a -1 from indexOf cannot accidentally remove the last repository.
        let auxRepoIndex = auxiliaryRepositories.indexOf(row);
        if (auxRepoIndex === -1 && row.id !== undefined) {
            auxRepoIndex = auxiliaryRepositories.findIndex((repository) => repository.id === row.id);
        }
        if (auxRepoIndex === -1) {
            return;
        }
        auxiliaryRepositories.splice(auxRepoIndex, 1); // Note: splice changes the array auxiliaryRepositories in place
        this.onRefresh.emit(undefined);
        // This activates the angular change detection
        programmingExercise.auxiliaryRepositories = [...auxiliaryRepositories];
    }
}
