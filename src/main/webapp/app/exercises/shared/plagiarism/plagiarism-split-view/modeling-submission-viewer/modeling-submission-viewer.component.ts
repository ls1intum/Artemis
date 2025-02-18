import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { UMLModel } from '@ls1intum/apollon';
import { SplitPaneHeaderComponent } from '../split-pane-header/split-pane-header.component';

@Component({
    selector: 'jhi-modeling-submission-viewer',
    styleUrls: ['./modeling-submission-viewer.component.scss'],
    templateUrl: './modeling-submission-viewer.component.html',
    imports: [SplitPaneHeaderComponent, ModelingEditorComponent],
})
export class ModelingSubmissionViewerComponent implements OnChanges {
    private modelingSubmissionService = inject(ModelingSubmissionService);

    @Input() exercise: ModelingExercise;
    @Input() plagiarismSubmission: PlagiarismSubmission<ModelingSubmissionElement>;
    @Input() hideContent: boolean;

    public loading: boolean;
    public submissionModel: UMLModel;

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.plagiarismSubmission) {
            const currentPlagiarismSubmission: PlagiarismSubmission<ModelingSubmissionElement> = changes.plagiarismSubmission.currentValue;

            if (!this.hideContent) {
                this.loading = true;
                this.modelingSubmissionService.getSubmissionWithoutLock(currentPlagiarismSubmission.submissionId).subscribe({
                    next: (submission: ModelingSubmission) => {
                        this.loading = false;
                        this.submissionModel = JSON.parse(submission.model!) as UMLModel;
                    },
                    error: () => {
                        this.loading = false;
                    },
                });
            }
        }
    }
}
