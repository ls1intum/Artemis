import { Component, Input } from '@angular/core';
import type { ProgrammingRepositoryButtonsDetail } from 'app/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/shared/no-data-component';
import { RouterModule } from '@angular/router';

import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { CodeButtonComponent } from 'app/shared/components/code-button/code-button.component';

@Component({
    selector: 'jhi-programming-repository-buttons-detail',
    templateUrl: 'programming-repository-buttons-detail.component.html',
    imports: [NoDataComponent, RouterModule, ArtemisProgrammingExerciseActionsModule, CodeButtonComponent],
})
export class ProgrammingRepositoryButtonsDetailComponent {
    @Input() detail: ProgrammingRepositoryButtonsDetail;
}
