import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@Component({
    selector: 'jhi-remove-auxiliary-repository-button',
    template: `
        <jhi-button [btnType]="ButtonType.ERROR" [btnSize]="ButtonSize.SMALL" [icon]="faTrash" [title]="'entity.action.remove'" (onClick)="removeAuxiliaryRepository()" />
    `,
    standalone: true,
    imports: [ArtemisSharedComponentModule],
})
export class RemoveAuxiliaryRepositoryButtonComponent {
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    protected readonly faTrash = faTrash;

    @Input({ required: true }) programmingExercise: ProgrammingExercise;

    @Input({ required: true }) row: AuxiliaryRepository;

    @Output() onRefresh: EventEmitter<any> = new EventEmitter<any>();

    /**
     * Removes the auxiliary repository of the selected row from the respective programming exercise.
     */
    removeAuxiliaryRepository() {
        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
        const auxRepoIndex = this.programmingExercise.auxiliaryRepositories?.indexOf(this.row)!;
        this.programmingExercise.auxiliaryRepositories?.splice(auxRepoIndex, 1); // Note: splice changes the array auxiliaryRepositories in place
        this.onRefresh.emit();
        // This activates the angular change detection
        this.programmingExercise.auxiliaryRepositories = [...this.programmingExercise.auxiliaryRepositories!];
    }
}
