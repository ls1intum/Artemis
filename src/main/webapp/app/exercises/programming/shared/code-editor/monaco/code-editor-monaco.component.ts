import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, Output, QueryList, SimpleChanges, ViewChild, ViewChildren, ViewEncapsulation } from '@angular/core';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { LocalStorageService } from 'ngx-webstorage';
import { EditorPosition, MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { firstValueFrom } from 'rxjs';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { Feedback } from 'app/entities/feedback.model';
import { Course } from 'app/entities/course.model';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { CommitState, CreateFileChange, DeleteFileChange, FileChange, RenameFileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { fromPairs, pickBy } from 'lodash-es';

export type FileSession = { [fileName: string]: { code: string; cursor: EditorPosition; loadingError: boolean } };

@Component({
    selector: 'jhi-code-editor-monaco',
    templateUrl: './code-editor-monaco.component.html',
    styleUrls: ['./code-editor-monaco.component.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [RepositoryFileService],
})
export class CodeEditorMonacoComponent implements OnChanges {
    @ViewChild('editor', { static: true })
    editor: MonacoEditorComponent;
    @ViewChildren(CodeEditorTutorAssessmentInlineFeedbackComponent)
    inlineFeedbackComponents: QueryList<CodeEditorTutorAssessmentInlineFeedbackComponent>;
    @Input()
    commitState: CommitState;
    @Input()
    course?: Course;
    @Input()
    feedbacks: Feedback[] = [];
    @Input()
    isTutorAssessment: boolean = false;
    @Input()
    selectedFile: string | undefined = undefined;
    @Input()
    sessionId: number | string;
    @Input()
    set buildAnnotations(buildAnnotations: Array<Annotation>) {
        this.setBuildAnnotations(buildAnnotations);
    }

    annotationsArray: Array<Annotation> = [];

    @Output()
    onFileContentChange: EventEmitter<{ file: string; fileContent: string }> = new EventEmitter<{ file: string; fileContent: string }>();

    isLoading = false;

    fileSession: FileSession = {};

    constructor(
        private repositoryFileService: CodeEditorRepositoryFileService,
        private fileService: CodeEditorFileService,
        protected localStorageService: LocalStorageService,
        private changeDetectorRef: ChangeDetectorRef,
    ) {}

    async ngOnChanges(changes: SimpleChanges): Promise<void> {
        if (changes.selectedFile && this.selectedFile) {
            await this.selectFileInEditor(changes.selectedFile.currentValue);
            this.setBuildAnnotations(this.annotationsArray);
            this.renderFeedbackWidgets();
        }
    }

    async selectFileInEditor(fileName: string): Promise<void> {
        if (!this.fileSession[fileName]) {
            this.isLoading = true;
            const fileContent = await firstValueFrom(this.repositoryFileService.getFile(fileName)).then((fileObj) => fileObj.fileContent);
            this.fileSession[fileName] = { code: fileContent, loadingError: false, cursor: { column: 0, row: 0 } };
            this.isLoading = false;
        }

        if (this.selectedFile === fileName) {
            //this.editor.setText(this.fileSession[fileName].code);
            this.editor.changeModel(fileName, this.fileSession[fileName].code);
            this.editor.setPosition(this.fileSession[fileName].cursor);
        }
    }

    onFileTextChanged(text: string): void {
        if (this.selectedFile && this.fileSession[this.selectedFile]) {
            const previousText = this.fileSession[this.selectedFile].code;
            if (previousText !== text) {
                this.fileSession[this.selectedFile] = { code: text, loadingError: false, cursor: this.editor.getPosition() };
                this.onFileContentChange.emit({ file: this.selectedFile, fileContent: text });
            }
        }
    }

    protected renderFeedbackWidgets() {
        for (const feedback of this.filterFeedbackForFile([...this.feedbacks])) {
            this.addLineWidgetWithFeedback(feedback);
        }
    }

    getInlineFeedbackNode(line: number) {
        return [...this.inlineFeedbackComponents].find((c) => c.codeLine === line)?.elementRef?.nativeElement;
    }

    private addLineWidgetWithFeedback(feedback: Feedback): void {
        const line = Feedback.getReferenceLine(feedback);
        if (!line) {
            throw new Error('No line found for feedback ' + feedback.id);
        }
        // TODO: Maybe there can be more than one feedback item per line.
        const feedbackNode = this.getInlineFeedbackNode(line);
        // TODO: The lines don't align with monaco (off by 1)
        this.editor.addLineWidget(line + 1, 'feedback-' + feedback.id, feedbackNode);
    }

    filterFeedbackForFile(feedbacks: Feedback[]): Feedback[] {
        if (!this.selectedFile) {
            return [];
        }
        return feedbacks.filter((feedback) => feedback.reference && Feedback.getReferenceFilePath(feedback) === this.selectedFile);
    }

    async onFileChange(fileChange: FileChange) {
        if (fileChange instanceof RenameFileChange) {
            this.fileSession = this.fileService.updateFileReferences(this.fileSession, fileChange);
            for (const annotation of this.annotationsArray) {
                if (annotation.fileName === fileChange.oldFileName) {
                    annotation.fileName = fileChange.newFileName;
                }
            }
            this.storeAnnotations([fileChange.newFileName]);
        } else if (fileChange instanceof DeleteFileChange) {
            this.fileSession = this.fileService.updateFileReferences(this.fileSession, fileChange);
            this.storeAnnotations([fileChange.fileName]);
        } else if (fileChange instanceof CreateFileChange && this.selectedFile === fileChange.fileName) {
            this.fileSession = { ...this.fileSession, [fileChange.fileName]: { code: '', cursor: { row: 0, column: 0 }, loadingError: false } };
        }
        this.setBuildAnnotations(this.annotationsArray);
    }

    /*
     * Taken from code-editor-ace.component.ts
     */
    /**
     * Saves the updated annotations to local storage
     * @param savedFiles
     */
    storeAnnotations(savedFiles: Array<string>) {
        const toUpdate = fromPairs(this.annotationsArray.filter((a) => savedFiles.includes(a.fileName)).map((a) => [a.hash, a]));
        const toKeep = pickBy(this.loadAnnotations(), (a) => !savedFiles.includes(a.fileName));

        this.localStorageService.store(
            'annotations-' + this.sessionId,
            JSON.stringify({
                ...toKeep,
                ...toUpdate,
            }),
        );
    }

    /**
     * Loads annotations from local storage
     */
    loadAnnotations() {
        return JSON.parse(this.localStorageService.retrieve('annotations-' + this.sessionId) || '{}');
    }

    setBuildAnnotations(buildAnnotations: Array<Annotation>): void {
        if (buildAnnotations.length > 0 && this.selectedFile) {
            const sessionAnnotations = this.loadAnnotations();
            this.annotationsArray = buildAnnotations.map((a) => {
                const hash = a.fileName + a.row + a.column + a.text;
                if (sessionAnnotations[hash] == undefined || sessionAnnotations[hash].timestamp < a.timestamp) {
                    return { ...a, hash };
                } else {
                    return sessionAnnotations[hash];
                }
            });
        } else {
            this.annotationsArray = buildAnnotations;
        }
        this.editor.setAnnotations(
            buildAnnotations.filter((buildAnnotation) => buildAnnotation.fileName === this.selectedFile),
            this.commitState === CommitState.UNCOMMITTED_CHANGES,
        );
    }

    protected readonly Feedback = Feedback;
}
