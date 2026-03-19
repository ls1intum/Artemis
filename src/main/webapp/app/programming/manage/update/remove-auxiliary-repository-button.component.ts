import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

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

    @Input() programmingExercise: ProgrammingExercise;

    @Input() row: AuxiliaryRepository;

    @Output() onRefresh: EventEmitter<void> = new EventEmitter<void>();

    // Icons
    faTrash = faTrash;

    /**
     * Removes the auxiliary repository of the selected row from the respective programming exercise.
     */
    removeAuxiliaryRepository() {
        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
        const auxRepoIndex = this.programmingExercise.auxiliaryRepositories?.indexOf(this.row)!;
        this.programmingExercise.auxiliaryRepositories?.splice(auxRepoIndex, 1); // Note: splice changes the array auxiliaryRepositories in place
        // TODO: The 'emit' function requires a mandatory void argument
        this.onRefresh.emit();
        // This activates the angular change detection
        this.programmingExercise.auxiliaryRepositories = [...this.programmingExercise.auxiliaryRepositories!];
    }
}
