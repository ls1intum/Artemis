import { Component, Input } from '@angular/core';
import type { ProgrammingRepositoryButtonsDetail } from 'app/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/shared/no-data-component';
import { RouterModule } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';

@Component({
    selector: 'jhi-programming-repository-buttons-detail',
    templateUrl: 'programming-repository-buttons-detail.component.html',
    standalone: true,
    imports: [NoDataComponent, RouterModule, ArtemisSharedComponentModule, ArtemisProgrammingExerciseActionsModule],
})
export class ProgrammingRepositoryButtonsDetailComponent {
    @Input() detail: ProgrammingRepositoryButtonsDetail;
}
