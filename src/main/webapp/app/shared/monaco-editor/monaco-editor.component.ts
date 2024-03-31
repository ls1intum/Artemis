import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import * as monaco from 'monaco-editor';
import { Subscription } from 'rxjs';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { MonacoEditorInlineWidget } from 'app/shared/monaco-editor/model/monaco-editor-inline-widget.model';
import { MonacoEditorBuildAnnotation, MonacoEditorBuildAnnotationType } from 'app/shared/monaco-editor/model/monaco-editor-build-annotation.model';

export type EditorPosition = { row: number; column: number };
export type MarkdownString = monaco.IMarkdownString;
@Component({
    selector: 'jhi-monaco-editor',
    templateUrl: 'monaco-editor.component.html',
    styleUrls: ['monaco-editor.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class MonacoEditorComponent implements OnInit, OnDestroy {
    @ViewChild('monacoEditorContainer', { static: true }) private monacoEditorContainer: ElementRef;
    private _editor: monaco.editor.IStandaloneCodeEditor;
    themeSubscription?: Subscription;
    models: monaco.editor.IModel[] = [];
    inlineWidgets: MonacoEditorInlineWidget[] = [];
    editorBuildAnnotations: MonacoEditorBuildAnnotation[] = [];

    constructor(private themeService: ThemeService) {}

    @Input()
    textChangedEmitDelay: number | undefined;

    @Input()
    set readOnly(value: boolean) {
        this._readOnly = value;
        if (this._editor) {
            this._editor.updateOptions({
                readOnly: value,
            });
        }
    }

    private _readOnly: boolean = false;

    @Output()
    textChanged = new EventEmitter<string>();

    private textChangedEmitTimeout: NodeJS.Timeout | undefined = undefined;

    ngOnInit(): void {
        this._editor = monaco.editor.create(this.monacoEditorContainer.nativeElement, {
            value: '',
            glyphMargin: true,
            minimap: { enabled: false },
            readOnly: this._readOnly,
        });

        const resizeObserver = new ResizeObserver(() => {
            this._editor.layout();
        });
        resizeObserver.observe(this.monacoEditorContainer.nativeElement);

        this._editor.onDidChangeModelContent(() => {
            this.emitTextChangeEvent();
        }, this);

        this.themeSubscription = this.themeService.getCurrentThemeObservable().subscribe((theme) => this.changeTheme(theme));
    }

    ngOnDestroy() {
        this.disposeAnnotations();
        this.disposeWidgets();
        this._editor.dispose();
        this.themeSubscription?.unsubscribe();
        this.models.forEach((m) => m.dispose());
    }

    private emitTextChangeEvent() {
        const newValue = this.getText();
        if (!this.textChangedEmitDelay) {
            this.textChanged.emit(newValue);
        } else {
            if (this.textChangedEmitTimeout) {
                clearTimeout(this.textChangedEmitTimeout);
                this.textChangedEmitTimeout = undefined;
            }
            this.textChangedEmitTimeout = setTimeout(() => {
                this.textChanged.emit(newValue);
            }, this.textChangedEmitDelay);
        }
    }

    // The rest of the code uses { row, column }... Will have to refactor
    toEditorPosition(position: monaco.Position | null): EditorPosition {
        if (!position) return { row: 0, column: 0 };
        return { row: position.lineNumber, column: position.column };
    }

    getPosition(): EditorPosition {
        return this.toEditorPosition(this._editor.getPosition());
    }

    setPosition(position: EditorPosition) {
        this._editor.setPosition({ lineNumber: position.row, column: position.column });
    }

    getText(): string {
        return this._editor.getValue();
    }

    setText(text: string): void {
        this._editor.setValue(text);
    }

    /**
     * Signals to the editor that the specified text was typed.
     * @param text The text to type.
     */
    triggerKeySequence(text: string): void {
        this._editor.trigger('MonacoEditorComponent::triggerKeySequence', 'type', { text });
    }

    getNumberOfLines(): number {
        return this._editor.getModel()?.getLineCount() ?? 0;
    }

    isReadOnly(): boolean {
        return this._editor.getOption(monaco.editor.EditorOption.readOnly);
    }

    changeModel(fileName: string, newFileContent?: string) {
        const uri = monaco.Uri.parse(`inmemory://model/${this._editor.getId()}/${fileName}`);
        const model = monaco.editor.getModel(uri) ?? monaco.editor.createModel(newFileContent ?? '', undefined, uri);
        if (!this.models.includes(model)) {
            this.models.push(model);
        }
        if (newFileContent !== undefined) {
            model.setValue(newFileContent);
        }

        // Some elements remain when the model is changed - dispose of them.
        this.disposeWidgets();

        monaco.editor.setModelLanguage(model, model.getLanguageId());
        this._editor.setModel(model);
    }

    disposeWidgets() {
        this.disposeAnnotations();
        this.inlineWidgets.forEach((i) => {
            i.dispose();
        });
        this.inlineWidgets = [];
    }

    disposeAnnotations() {
        this.editorBuildAnnotations.forEach((o) => {
            o.dispose();
        });
        this.editorBuildAnnotations = [];
    }

    changeTheme(artemisTheme: Theme): void {
        this._editor.updateOptions({
            theme: artemisTheme === Theme.DARK ? 'vs-dark' : 'vs-light',
        });
    }

    setAnnotations(annotations: Annotation[], markAsOutdated: boolean = false): void {
        if (!this._editor) {
            return;
        }
        this.disposeAnnotations();
        for (const annotation of annotations) {
            const lineNumber = annotation.row + 1;
            const editorBuildAnnotation = new MonacoEditorBuildAnnotation(
                this._editor,
                `${annotation.fileName}:${lineNumber}:${annotation.text}`,
                lineNumber,
                annotation.text,
                annotation.type === 'error' ? MonacoEditorBuildAnnotationType.ERROR : MonacoEditorBuildAnnotationType.WARNING,
            );
            editorBuildAnnotation.addToEditor();
            editorBuildAnnotation.setOutdatedAndUpdate(markAsOutdated);
            this.editorBuildAnnotations.push(editorBuildAnnotation);
        }
    }

    addLineWidget(lineNumber: number, id: string, domNode: HTMLElement) {
        const lineWidget = new MonacoEditorInlineWidget(this._editor, id, domNode, lineNumber);
        lineWidget.addToEditor();
        this.inlineWidgets.push(lineWidget);
    }
}
