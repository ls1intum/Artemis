import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ExerciseType } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-text-submission-viewer',
    styleUrls: ['./text-submission-viewer.component.html'],
    templateUrl: './text-submission-viewer.component.html',
})
export class TextSubmissionViewerComponent implements OnChanges {
    @Input() exercise: ModelingExercise;
    @Input() plagiarismSubmission: PlagiarismSubmission<ModelingSubmissionElement>;

    public loading: boolean;
    public submission: ProgrammingSubmission | TextSubmission;

    constructor(private programmingSubmissionService: ProgrammingSubmissionService, private textSubmissionService: TextSubmissionService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.plagiarismSubmission) {
            this.loading = true;

            const currentPlagiarismSubmission: PlagiarismSubmission<TextSubmissionElement> = changes.plagiarismSubmission.currentValue;

            if (this.isProgrammingExercise()) {
                // TODO
                this.loading = false;
            } else {
                this.textSubmissionService.getTextSubmissionForExerciseWithoutAssessment(currentPlagiarismSubmission.submissionId).subscribe(
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

    isProgrammingExercise() {
        return this.exercise.type === ExerciseType.PROGRAMMING;
    }
}
