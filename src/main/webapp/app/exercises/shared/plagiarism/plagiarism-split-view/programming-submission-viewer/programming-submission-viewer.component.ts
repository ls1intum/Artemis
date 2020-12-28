import { Component, Input, OnChanges, SimpleChanges, ViewEncapsulation } from '@angular/core';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { TextExercise } from 'app/entities/text-exercise.model';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { DomainType, FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-programming-submission-viewer',
    styleUrls: ['./programming-submission-viewer.component.scss'],
    templateUrl: './programming-submission-viewer.component.html',
    encapsulation: ViewEncapsulation.None,
})
export class ProgrammingSubmissionViewerComponent implements OnChanges {
    @Input() exercise: TextExercise;
    @Input() matches: Map<string, { from: TextSubmissionElement; to: TextSubmissionElement }[]>;
    @Input() plagiarismSubmission: PlagiarismSubmission<TextSubmissionElement>;

    public currentFile: string;
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
        this.currentFile = file;
        this.loading = true;

        this.repositoryService.getFile(file).subscribe(
            ({ fileContent }) => {
                this.loading = false;
                this.fileContent = this.parseFileRows(fileContent);
            },
            () => {
                this.loading = false;
            },
        );
    }

    getMatchesForCurrentFile() {
        return this.matches.get(this.currentFile) || [];
    }

    parseFileRows(fileContent: string) {
        const rows = fileContent.split('\n');
        const matches = this.getMatchesForCurrentFile();

        matches.forEach(({ from, to }) => {
            rows[from.line] = '<span class="plagiarism-match">';
            rows[to.line] = '</span>';
        });

        return rows.join('\n');
    }
}
