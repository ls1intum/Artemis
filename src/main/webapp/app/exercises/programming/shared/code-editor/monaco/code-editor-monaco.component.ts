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

export type FileSession = { [fileName: string]: { code: string; cursorPosition: EditorPosition; loadingError: boolean } };

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
    course?: Course;
    @Input()
    feedbacks: Feedback[] = [];
    @Input()
    selectedFile: string | undefined = undefined;
    @Input()
    sessionId: number | string;
    @Input()
    set buildAnnotations(buildAnnotations: Array<Annotation>) {
        this.setBuildAnnotations(buildAnnotations);
    }

    private annotationsArray: Array<Annotation> = [];

    @Output()
    onFileContentChange = new EventEmitter<{ file: string; fileContent: string }>();

    isLoading = false;

    private fileSession: FileSession = {};

    constructor(
        private repositoryFileService: CodeEditorRepositoryFileService,
        private fileService: CodeEditorFileService,
        protected localStorageService: LocalStorageService,
        private changeDetectorRef: ChangeDetectorRef,
    ) {}

    async ngOnChanges(changes: SimpleChanges): Promise<void> {
        if (changes.selectedFile) {
            await this.selectFileInEditor(changes.selectedFile.currentValue);
            this.setBuildAnnotations(this.annotationsArray);
            this.renderFeedbackWidgets();
        }
    }

    async selectFileInEditor(fileName: string): Promise<void> {
        if (!this.fileSession[fileName]) {
            this.isLoading = true;
            const fileContent = await firstValueFrom(this.repositoryFileService.getFile(fileName)).then((fileObj) => fileObj.fileContent);
            this.fileSession[fileName] = { code: fileContent, loadingError: false, cursorPosition: { column: 0, lineNumber: 0 } };
            this.isLoading = false;
        }

        if (this.selectedFile === fileName) {
            //this.editor.setText(this.fileSession[fileName].code);
            this.editor.changeModel(fileName, this.fileSession[fileName].code);
            this.editor.setPosition(this.fileSession[fileName].cursorPosition);
        }
    }

    onFileTextChanged(text: string): void {
        if (this.selectedFile && this.fileSession[this.selectedFile]) {
            const previousText = this.fileSession[this.selectedFile].code;
            if (previousText !== text) {
                this.fileSession[this.selectedFile] = { code: text, loadingError: false, cursorPosition: this.editor.getPosition() };
                this.onFileContentChange.emit({ file: this.selectedFile, fileContent: text });
            }
        }
    }

    protected renderFeedbackWidgets() {
        for (const feedback of this.filterFeedbackForFile([...this.feedbacks])) {
            this.addLineWidgetWithFeedback(feedback);
        }
    }

    private addLineWidgetWithFeedback(feedback: Feedback): void {
        const line = Feedback.getReferenceLine(feedback);
        if (!line) {
            throw new Error('No line found for feedback ' + feedback.id);
        }
        // TODO: Maybe there can be more than one feedback item per line.
        const feedbackNode = [...this.inlineFeedbackComponents].find((c) => c.codeLine === line)?.elementRef?.nativeElement;
        // TODO: The lines don't align with monaco (off by 1)
        this.editor.addViewZoneWithWidget(line + 1, 'foo-' + feedback.id, feedbackNode);
    }

    protected filterFeedbackForFile(feedbacks: Feedback[]): Feedback[] {
        if (!this.selectedFile) {
            return [];
        }
        return feedbacks.filter((feedback) => feedback.reference && Feedback.getReferenceFilePath(feedback) === this.selectedFile);
    }

    private loadBuildAnnotationsFromLocalStorage() {
        return JSON.parse(this.localStorageService.retrieve('annotations-' + this.sessionId) || '{}');
    }

    private setBuildAnnotations(buildAnnotations: Array<Annotation>): void {
        this.annotationsArray = buildAnnotations;
        // TODO: Load them from local storage here.
        this.editor.addGlyphDecorations(
            buildAnnotations
                .filter((a) => a.fileName === this.selectedFile)
                .map((a) => ({
                    lineNumber: a.row,
                    hoverMessage: { value: '**' + a.type + ': ' + a.text + '**' },
                    glyphMarginClassName: 'codicon-error monaco-error-glyph',
                })),
        );
    }

    protected readonly Feedback = Feedback;
}
