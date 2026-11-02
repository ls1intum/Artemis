import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faUser } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { PlagiarismCaseVerdictComponent } from 'app/plagiarism/shared/verdict/plagiarism-case-verdict.component';
import { getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';

@Component({
    selector: 'div[jhi-plagiarism-case-header]',
    templateUrl: './plagiarism-case-header.component.html',
    imports: [FaIconComponent, TranslateDirective, PlagiarismCaseVerdictComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlagiarismCaseHeaderComponent {
    readonly plagiarismCase = input.required<PlagiarismCase>();
    protected readonly faUser = faUser;
    protected readonly getIcon = getIcon;
}
