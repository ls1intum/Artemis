import { Component, Input, OnChanges, SimpleChanges, ViewEncapsulation } from '@angular/core';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { TextSubmission } from 'app/entities/text-submission.model';
import { FromToElement, TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { DomainChange, DomainType, FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { FileWithHasMatch } from 'app/exercises/shared/plagiarism/plagiarism-split-view/split-pane-header/split-pane-header.component';
import { escape } from 'lodash-es';

type FilesWithType = { [p: string]: FileType };

@Component({
    selector: 'jhi-text-submission-viewer',
    styleUrls: ['./text-submission-viewer.component.scss'],
    templateUrl: './text-submission-viewer.component.html',
    encapsulation: ViewEncapsulation.None,
})
export class TextSubmissionViewerComponent implements OnChanges {
    @Input() exercise: ProgrammingExercise | TextExercise;
    @Input() matches: Map<string, FromToElement[]>;
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
    public files: FileWithHasMatch[];

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
    private readonly tokenStart = '<span class="plagiarism-match">';

    /**
     * Token that marks the end of a highlighted match.
     */
    private readonly tokenEnd = '</span>';

    /**
     * True if currently selected file is not a text file.
     */
    binaryFile?: boolean;

    constructor(
        private repositoryService: CodeEditorRepositoryFileService,
        private textSubmissionService: TextSubmissionService,
    ) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.plagiarismSubmission) {
            const currentPlagiarismSubmission: PlagiarismSubmission<TextSubmissionElement> = changes.plagiarismSubmission.currentValue;
            this.loading = true;

            if (this.exercise.type === ExerciseType.PROGRAMMING) {
                this.loadProgrammingExercise(currentPlagiarismSubmission);
            } else {
                this.loadTextExercise(currentPlagiarismSubmission);
            }
        }
    }

    /**
     * Initializes this component with a programming exercise submission.
     *
     * @param currentPlagiarismSubmission The submission to load the plagiarism information for.
     */
    private loadProgrammingExercise(currentPlagiarismSubmission: PlagiarismSubmission<TextSubmissionElement>) {
        this.isProgrammingExercise = true;

        const domain: DomainChange = [DomainType.PARTICIPATION, { id: currentPlagiarismSubmission.submissionId }];
        this.repositoryService.getRepositoryContent(domain).subscribe({
            next: (files) => {
                this.loading = false;
                this.files = this.programmingExerciseFilesWithMatches(files);
            },
            error: () => {
                this.loading = false;
            },
        });
    }

    /**
     * Computes the list of all files that should be shown for the plagiarism view.
     *
     * @param files An unfiltered list of files.
     * @return A sorted list of files that should be shown for the plagiarism view.
     */
    private programmingExerciseFilesWithMatches(files: FilesWithType): Array<FileWithHasMatch> {
        return this.filterFiles(files)
            .map((file) => ({ file, hasMatch: this.hasMatch(file) }))
            .sort(TextSubmissionViewerComponent.compareFileWithHasMatch);
    }

    /**
     * Compares two files.
     *
     * Files which have got a match have got precedence over ones which don’t.
     * If two files either both or neither have a match, then they are sorted lexicographically.
     *
     * @param file1 Some file with information if it has a match.
     * @param file2 Some other file with information if it has a match.
     * @return `-1`, if `file1` is smaller according to the sorting order described above, `1` otherwise.
     */
    private static compareFileWithHasMatch(file1: FileWithHasMatch, file2: FileWithHasMatch): number {
        if (file1.hasMatch === file2.hasMatch) {
            return file1.file.localeCompare(file2.file);
        } else if (file1.hasMatch) {
            return -1;
        } else {
            return 1;
        }
    }

    /**
     * Initializes this component with a text exercise submission.
     *
     * @param currentPlagiarismSubmission The submission to load the plagiarism information for.
     */
    private loadTextExercise(currentPlagiarismSubmission: PlagiarismSubmission<TextSubmissionElement>) {
        this.isProgrammingExercise = false;

        this.textSubmissionService.getTextSubmission(currentPlagiarismSubmission.submissionId).subscribe({
            next: (submission: TextSubmission) => {
                this.loading = false;
                this.fileContent = this.insertMatchTokens(submission.text || '');
            },
            error: () => {
                this.loading = false;
            },
        });
    }

    filterFiles(files: FilesWithType) {
        return Object.keys(files).filter((fileName) => files[fileName] === FileType.FILE);
    }

    handleFileSelect(file: string) {
        this.currentFile = file;
        this.loading = true;

        const domain: DomainChange = [DomainType.PARTICIPATION, { id: this.plagiarismSubmission.submissionId }];

        this.repositoryService.getFileHeaders(file, domain).subscribe((response) => {
            const contentType = response.headers.get('content-type');
            if (contentType && !contentType.startsWith('text')) {
                this.binaryFile = true;
                this.loading = false;
            } else {
                this.binaryFile = false;
                this.repositoryService.getFile(file, domain).subscribe({
                    next: ({ fileContent }) => {
                        this.loading = false;
                        this.fileContent = this.insertMatchTokens(fileContent);
                    },
                    error: () => {
                        this.loading = false;
                    },
                });
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

    private hasMatch(file: string): boolean {
        return this.matches.has(file);
    }

    insertMatchTokens(fileContent: string): string {
        const matches = this.getMatchesForCurrentFile()
            .filter((match) => match.from && match.to)
            .sort((m1, m2) => {
                const lines = m1.from.line - m2.from.line;
                if (lines === 0) {
                    return m1.from.column - m2.from.column;
                }
                return lines;
            });

        if (!matches.length) {
            return escape(fileContent);
        }

        return this.buildFileContentWithHighlightedMatches(fileContent, matches);
    }

    /**
     * Builds the required HTML to highlight the matches in the file content.
     *
     * @param fileContent The text inside a file.
     * @param matches The found matching plagiarism fragments.
     * @return The file content as HTML with highlighted plagiarism matches.
     */
    private buildFileContentWithHighlightedMatches(fileContent: string, matches: FromToElement[]): string {
        const fileLines = fileContent.split('\n');
        let result = '';

        if (matches[0].from.line == 1 && matches[0].from.column != 0) {
            result += escape(fileLines[0].slice(0, matches[0].from.column - 1));
        } else if (matches[0].from.line == 1 && matches[0].from.column == 0) {
            result += '';
        } else {
            for (let i = 0; i < matches[0].from.line - 1; i++) {
                result += escape(fileLines[i]) + '\n';
            }
            result += escape(fileLines[matches[0].from.line - 1].slice(0, matches[0].from.column - 1));
        }

        for (let i = 0; i < matches.length; i++) {
            result += this.buildFileContentPartForMatch(matches, i, fileLines);
        }

        return result;
    }

    /**
     * Adds the highlights to the file content for a single match.
     *
     * @param matches All matches for the file.
     * @param match_index The index of the match for which the highlighting should be added.
     * @param fileLines The file content split by lines.
     * @return The part of the file content relevant to the match extended with the required HTML tags for the
     *         highlighting.
     */
    private buildFileContentPartForMatch(matches: FromToElement[], match_index: number, fileLines: string[]): string {
        const match = matches[match_index];
        const idxLineFrom = match.from.line - 1;
        const idxLineTo = match.to.line - 1;

        const idxColumnFrom = match.from.column - 1;
        const idxColumnTo = match.to.column + match.to.length - 1;

        let result = this.tokenStart;

        if (idxLineFrom === idxLineTo) {
            result += escape(fileLines[idxLineFrom].slice(idxColumnFrom, idxColumnTo)) + this.tokenEnd;
        } else {
            if (idxColumnFrom > 0) {
                result += escape(fileLines[idxLineFrom].slice(idxColumnFrom));
            }
            for (let j = idxLineFrom + (idxColumnFrom > 0 ? 1 : 0); j < idxLineTo; j++) {
                console.log('A "' + result + '"');
                if (result.trim() != this.tokenStart) {
                    console.log('A1 "' + result + '"');
                    result += '\n';
                }
                result += escape(fileLines[j]);
            }
            result += '\n' + escape(fileLines[idxLineTo].slice(0, idxColumnTo)) + this.tokenEnd;
        }

        // escape everything up until the next match (or the end of the string if there is no more match)
        if (match_index === matches.length - 1) {
            result += escape(fileLines[idxLineTo].slice(idxColumnTo));
            for (let j = idxLineTo + 1; j < fileLines.length; j++) {
                result += '\n' + escape(fileLines[j]);
            }
        } else if (matches[match_index + 1].from.line === match.to.line) {
            result += escape(fileLines[idxLineTo].slice(idxColumnTo, matches[match_index + 1].from.column - 1));
        } else {
            result += escape(fileLines[idxLineTo].slice(idxColumnTo)) + '\n';
            for (let j = idxLineTo + 1; j < matches[match_index + 1].from.line - 1; j++) {
                result += escape(fileLines[j]) + '\n';
            }
            result += escape(fileLines[matches[match_index + 1].from.line - 1].slice(0, matches[match_index + 1].from.column - 1));
        }

        return result;
    }
}
