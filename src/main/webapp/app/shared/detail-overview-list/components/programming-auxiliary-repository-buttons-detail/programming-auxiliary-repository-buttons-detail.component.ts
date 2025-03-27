import { Component, Input } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ProgrammingAuxiliaryRepositoryButtonsDetail } from 'app/shared/detail-overview-list/detail.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CodeButtonComponent } from 'app/shared/components/code-button/code-button.component';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/programming/shared/actions/programming-exercise-instructor-repo-download.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-programming-auxiliary-repository-buttons-detail',
    templateUrl: 'programming-auxiliary-repository-buttons-detail.component.html',
    imports: [RouterModule, NgbTooltipModule, FontAwesomeModule, CodeButtonComponent, ProgrammingExerciseInstructorRepoDownloadComponent, TranslateDirective],
})
export class ProgrammingAuxiliaryRepositoryButtonsDetailComponent {
    @Input() detail: ProgrammingAuxiliaryRepositoryButtonsDetail;

    readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly RepositoryType = RepositoryType;
}
