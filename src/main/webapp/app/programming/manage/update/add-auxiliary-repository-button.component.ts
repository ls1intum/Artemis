import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

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
    imports: [ButtonComponent],
})
export class AddAuxiliaryRepositoryButtonComponent {
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly faPlus = faPlus;

    @Input() programmingExercise: ProgrammingExercise;

    @Output() onRefresh: EventEmitter<void> = new EventEmitter<void>();

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
        // TODO: The 'emit' function requires a mandatory void argument
        this.onRefresh.emit();
        this.programmingExercise.auxiliaryRepositories = [...this.programmingExercise.auxiliaryRepositories];
    }
}
