import { Component, Input } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ProgrammingAuxiliaryRepositoryButtonsDetail } from 'app/detail-overview-list/detail.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-repo-download.component';

@Component({
    selector: 'jhi-programming-auxiliary-repository-buttons-detail',
    templateUrl: 'programming-auxiliary-repository-buttons-detail.component.html',
    imports: [RouterModule, ArtemisSharedComponentModule, ArtemisSharedModule, ProgrammingExerciseInstructorRepoDownloadComponent],
})
export class ProgrammingAuxiliaryRepositoryButtonsDetailComponent {
    @Input() detail: ProgrammingAuxiliaryRepositoryButtonsDetail;

    readonly faExclamationTriangle = faExclamationTriangle;
}
