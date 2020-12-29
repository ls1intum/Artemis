import { Component, Input, OnChanges, SimpleChanges, ViewEncapsulation } from '@angular/core';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { DomainType, FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';

@Component({
    selector: 'jhi-text-submission-viewer',
    styleUrls: ['./text-submission-viewer.component.scss'],
    templateUrl: './text-submission-viewer.component.html',
    encapsulation: ViewEncapsulation.None,
})
export class TextSubmissionViewerComponent implements OnChanges {
    @Input() exercise: ProgrammingExercise | TextExercise;
    @Input() matches: Map<string, { from: TextSubmissionElement; to: TextSubmissionElement }[]>;
    @Input() plagiarismSubmission: PlagiarismSubmission<TextSubmissionElement>;

    public currentFile: string;
    public fileContent: string;
    public files: string[];
    public isProgrammingExercise: boolean;
    public loading: boolean;

    constructor(private repositoryService: CodeEditorRepositoryFileService, private textSubmissionService: TextSubmissionService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.plagiarismSubmission) {
            const currentPlagiarismSubmission: PlagiarismSubmission<TextSubmissionElement> = changes.plagiarismSubmission.currentValue;
            this.loading = true;

            if (this.exercise.type === ExerciseType.PROGRAMMING) {
                this.isProgrammingExercise = true;

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
            } else {
                this.isProgrammingExercise = false;

                this.textSubmissionService.getTextSubmission(currentPlagiarismSubmission.submissionId).subscribe(
                    (submission: TextSubmission) => {
                        this.loading = false;
                        this.fileContent = this.parseFileRows(submission.text || '');
                    },
                    () => {
                        this.loading = false;
                    },
                );
            }
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
        return this.matches.get(this.currentFile || 'none') || [];
    }

    insertToken(text: string, token: string, position: number) {
        return [text.slice(0, position), token, text.slice(position)].join('');
    }

    parseFileRows(fileContent: string) {
        const rows = fileContent.split('\n');
        const matches = this.getMatchesForCurrentFile();

        matches.forEach(({ from, to }) => {
            rows[from.line - 1] = this.insertToken(rows[from.line - 1], '<span class="plagiarism-match">', from.column - 1);
            rows[to.line - 1] = this.insertToken(rows[to.line - 1], '</span>', to.column + to.length - 1);
        });

        return rows.join('\n');
    }
}
