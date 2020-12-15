import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { TextExercise } from 'app/entities/text-exercise.model';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { DomainType, FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-programming-submission-viewer',
    styleUrls: ['./programming-submission-viewer.component.scss'],
    templateUrl: './programming-submission-viewer.component.html',
})
export class ProgrammingSubmissionViewerComponent implements OnChanges {
    @Input() exercise: TextExercise;
    @Input() plagiarismSubmission: PlagiarismSubmission<TextSubmissionElement>;

    public fileContent: string;
    public files: string[];
    public loading: boolean;

    constructor(private repositoryService: CodeEditorRepositoryFileService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.plagiarismSubmission) {
            this.loading = true;

            const currentPlagiarismSubmission: PlagiarismSubmission<TextSubmissionElement> = changes.plagiarismSubmission.currentValue;

            this.repositoryService.setDomain([DomainType.PARTICIPATION, { id: currentPlagiarismSubmission.submissionId }]);
            this.repositoryService.getRepositoryContent().subscribe(
                (files) => {
                    this.loading = false;
                    this.files = this.filterFiles(files);
                },
                () => {
                    this.loading = false;
                },
            );
        }
    }

    filterFiles(files: { [p: string]: FileType }) {
        return Object.keys(files).filter((fileName) => files[fileName] === FileType.FILE);
    }

    handleFileSelect(file: string) {
        this.loading = true;

        this.repositoryService.getFile(file).subscribe(
            ({ fileContent }) => {
                this.loading = false;
                this.fileContent = fileContent;
            },
            () => {
                this.loading = false;
            },
        );
    }
}
