import { Component, Input } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ProgrammingAuxiliaryRepositoryButtonsDetail } from 'app/detail-overview-list/detail.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CodeButtonComponent } from 'app/shared/components/code-button/code-button.component';

@Component({
    selector: 'jhi-programming-auxiliary-repository-buttons-detail',
    templateUrl: 'programming-auxiliary-repository-buttons-detail.component.html',
    imports: [RouterModule, ArtemisProgrammingExerciseActionsModule, NgbTooltipModule, FontAwesomeModule, CodeButtonComponent],
})
export class ProgrammingAuxiliaryRepositoryButtonsDetailComponent {
    @Input() detail: ProgrammingAuxiliaryRepositoryButtonsDetail;

    readonly faExclamationTriangle = faExclamationTriangle;
}
