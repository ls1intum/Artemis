import { Component, input } from '@angular/core';
import { Submission, getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { isManualResult } from 'app/exercise/result/result.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DecimalPipe, NgClass } from '@angular/common';

@Component({
    selector: 'jhi-assessment-progress-label',
    templateUrl: './assessment-progress-label.component.html',
    imports: [TranslateDirective, NgClass, DecimalPipe],
})
export class AssessmentProgressLabelComponent {
    readonly submissions = input<Submission[]>([]);

    get numberAssessedSubmissions(): number {
        return this.submissions().filter((submission) => {
            const result = getLatestSubmissionResult(submission);
            return result?.rated && isManualResult(result) && result?.completionDate;
        }).length;
    }
}
