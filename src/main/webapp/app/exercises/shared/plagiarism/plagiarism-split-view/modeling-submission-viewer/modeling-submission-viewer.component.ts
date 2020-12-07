import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';

@Component({
    selector: 'jhi-modeling-submission-viewer',
    styleUrls: ['./modeling-submission-viewer.component.scss'],
    templateUrl: './modeling-submission-viewer.component.html',
})
export class ModelingSubmissionViewerComponent implements OnChanges {
    @Input() exercise: ModelingExercise;
    @Input() plagiarismSubmission: PlagiarismSubmission<ModelingSubmissionElement>;

    public loading: boolean;
    public submission: ModelingSubmission;

    constructor(private modelingSubmissionService: ModelingSubmissionService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.plagiarismSubmission) {
            this.loading = true;

            const currentPlagiarismSubmission: PlagiarismSubmission<ModelingSubmissionElement> = changes.plagiarismSubmission.currentValue;

            this.modelingSubmissionService.getSubmission(currentPlagiarismSubmission.submissionId).subscribe(
                (submission: ModelingSubmission) => {
                    this.loading = false;

                    submission.model = JSON.parse(submission.model!);
                    this.submission = submission;
                },
                () => {
                    this.loading = false;
                },
            );
        }
    }
}
