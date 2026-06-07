import { Component, input } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ProgrammingAuxiliaryRepositoryButtonsDetail } from 'app/shared-ui/detail-overview-list/detail.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/programming/shared/actions/instructor-repo-download/programming-exercise-instructor-repo-download.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-programming-auxiliary-repository-buttons-detail',
    templateUrl: 'programming-auxiliary-repository-buttons-detail.component.html',
    imports: [RouterModule, NgbTooltipModule, FontAwesomeModule, CodeButtonComponent, ProgrammingExerciseInstructorRepoDownloadComponent, TranslateDirective],
})
export class ProgrammingAuxiliaryRepositoryButtonsDetailComponent {
    detail = input.required<ProgrammingAuxiliaryRepositoryButtonsDetail>();

    readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly RepositoryType = RepositoryType;
}
