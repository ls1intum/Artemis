import { Component, Input, OnChanges } from '@angular/core';
import { Submission, getLatestSubmissionResult } from 'app/entities/submission.model';
import { Result } from 'app/entities/result.model';

@Component({
    selector: 'jhi-assessment-progress-label',
    templateUrl: './assessment-progress-label.html',
})
export class AssessmentProgressLabelComponent implements OnChanges {
    @Input()
    submissions: Submission[] = [];
    numberAssessedSubmissions: number;

    ngOnChanges() {
        this.numberAssessedSubmissions = this.submissions.filter((submission) => {
            const result = getLatestSubmissionResult(submission);
            return result?.rated && Result.isManualResult(result) && result?.completionDate;
        }).length;
    }
}
