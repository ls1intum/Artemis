import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { TextExercise } from 'app/entities/text-exercise.model';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-text-submission-viewer',
    styleUrls: ['./text-submission-viewer.component.scss'],
    templateUrl: './text-submission-viewer.component.html',
})
export class TextSubmissionViewerComponent implements OnChanges {
    @Input() exercise: TextExercise;
    @Input() plagiarismSubmission: PlagiarismSubmission<TextSubmissionElement>;

    public loading: boolean;
    public submission: TextSubmission;

    constructor(private repositoryService: CodeEditorRepositoryFileService, private textSubmissionService: TextSubmissionService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.plagiarismSubmission) {
            this.loading = true;

            const currentPlagiarismSubmission: PlagiarismSubmission<TextSubmissionElement> = changes.plagiarismSubmission.currentValue;

            this.repositoryService.setDomain([DomainType.PARTICIPATION, { id: currentPlagiarismSubmission.submissionId }]);

            this.repositoryService.getRepositoryContent().subscribe((files) => {
                console.log(files);
            });

            this.textSubmissionService.getTextSubmission(currentPlagiarismSubmission.submissionId).subscribe(
                (submission: TextSubmission) => {
                    this.loading = false;

                    this.submission = submission;
                },
                () => {
                    this.loading = false;
                },
            );
        }
    }
}
