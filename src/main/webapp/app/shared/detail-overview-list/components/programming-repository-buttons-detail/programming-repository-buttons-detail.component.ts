import { Component, Input } from '@angular/core';
import type { ProgrammingRepositoryButtonsDetail } from 'app/shared/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/shared/components/no-data/no-data-component';
import { RouterModule } from '@angular/router';
import { CodeButtonComponent } from 'app/shared/components/code-button/code-button.component';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/programming/shared/actions/instructor-repo-download/programming-exercise-instructor-repo-download.component';

@Component({
    selector: 'jhi-programming-repository-buttons-detail',
    templateUrl: 'programming-repository-buttons-detail.component.html',
    imports: [NoDataComponent, RouterModule, CodeButtonComponent, ProgrammingExerciseInstructorRepoDownloadComponent],
})
export class ProgrammingRepositoryButtonsDetailComponent {
    @Input() detail: ProgrammingRepositoryButtonsDetail;
}
