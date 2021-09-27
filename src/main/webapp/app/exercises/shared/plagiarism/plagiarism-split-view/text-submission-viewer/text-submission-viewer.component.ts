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

    /**
     * Name of the currently selected file.
     */
    public currentFile: string;

    /**
     * Content of the currently selected file.
     */
    public fileContent: string;

    /**
     * List of files the given submission consists of.
     * `null` for text exercises.
     */
    public files: string[];

    /**
     * True, if the given exercise is of type 'programming'.
     */
    public isProgrammingExercise: boolean;

    /**
     * True, if the file content for the given submission is being loaded.
     */
    public loading: boolean;

    /**
     * Token that marks the beginning of a highlighted match.
     */
    public tokenStart = '<span class="plagiarism-match">';

    /**
     * Token that marks the end of a highlighted match.
     */
    public tokenEnd = '</span>';

    /**
     * True if currently selected file is not a text file.
     */
    binaryFile?: boolean;

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
                        this.fileContent = this.insertMatchTokens(submission.text || '');
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

        this.repositoryService.setDomain([DomainType.PARTICIPATION, { id: this.plagiarismSubmission.submissionId }]);

        this.repositoryService.getFileHeaders(file).subscribe((response) => {
            const contentType = response.headers.get('content-type');
            if (contentType && !contentType.startsWith('text')) {
                this.binaryFile = true;
                this.loading = false;
            } else {
                this.binaryFile = false;
                this.repositoryService.getFile(file).subscribe(
                    ({ fileContent }) => {
                        this.loading = false;
                        this.fileContent = this.insertMatchTokens(fileContent);
                    },
                    () => {
                        this.loading = false;
                    },
                );
            }
        });
    }

    /**
     * Downloads the currently selected file with a friendly name consisting of the exercises short name, the student login and the filename.
     */
    downloadCurrentFile() {
        this.repositoryService.downloadFile(this.currentFile, this.exercise.shortName + '_' + this.plagiarismSubmission.studentLogin + '_' + this.currentFile);
    }

    getMatchesForCurrentFile() {
        return this.matches.get(this.currentFile || 'none') || [];
    }

    insertToken(text: string, token: string, position: number) {
        return [text.slice(0, position), token, text.slice(position)].join('');
    }

    insertMatchTokens(fileContent: string) {
        const rows = fileContent.split('\n');
        const matches = this.getMatchesForCurrentFile();
        const offsets = new Array(rows.length).fill(0);

        matches.forEach(({ from, to }) => {
            const idxLineFrom = from.line - 1;
            const idxLineTo = to.line - 1;

            const idxColumnFrom = from.column - 1 + offsets[idxLineFrom];

            if (rows[idxLineFrom]) {
                rows[idxLineFrom] = this.insertToken(rows[idxLineFrom], this.tokenStart, idxColumnFrom);
                offsets[idxLineFrom] += this.tokenStart.length;
            }

            const idxColumnTo = to.column + to.length - 1 + offsets[idxLineTo];

            if (rows[idxLineTo]) {
                rows[idxLineTo] = this.insertToken(rows[idxLineTo], this.tokenEnd, idxColumnTo);
                offsets[idxLineTo] += this.tokenEnd.length;
            }
        });

        return rows.join('\n');
    }
}
