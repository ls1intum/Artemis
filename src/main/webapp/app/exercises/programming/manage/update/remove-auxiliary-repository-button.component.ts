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

    @Input() row: AuxiliaryRepository;

    removeAuxiliaryRepository(event: MouseEvent) {
        let auxRepoIndex = this.programmingExercise.auxiliaryRepositories?.indexOf(this.row)!;
        let tmp = [...this.programmingExercise.auxiliaryRepositories!];
        tmp?.splice(auxRepoIndex, 1)!;
        this.programmingExercise.auxiliaryRepositories = [...tmp];
    }
}
