import { Component, Input } from '@angular/core';
import { NoDataComponent } from 'app/shared/no-data-component';
import { RouterModule } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ProgrammingAuxiliaryRepositoryButtonsDetail } from 'app/detail-overview-list/detail.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-programming-auxiliary-repository-buttons-detail',
    templateUrl: 'programming-auxiliary-repository-buttons-detail.component.html',
    standalone: true,
    imports: [NoDataComponent, RouterModule, ArtemisSharedComponentModule, ArtemisProgrammingExerciseActionsModule, ArtemisSharedModule],
})
export class ProgrammingAuxiliaryRepositoryButtonsDetailComponent {
    @Input() detail: ProgrammingAuxiliaryRepositoryButtonsDetail;

    readonly faExclamationTriangle = faExclamationTriangle;
}
