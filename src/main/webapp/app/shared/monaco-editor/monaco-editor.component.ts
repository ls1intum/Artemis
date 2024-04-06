import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import * as monaco from 'monaco-editor';
import { Subscription } from 'rxjs';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { MonacoEditorLineWidget } from 'app/shared/monaco-editor/model/monaco-editor-inline-widget.model';
import { MonacoEditorBuildAnnotation, MonacoEditorBuildAnnotationType } from 'app/shared/monaco-editor/model/monaco-editor-build-annotation.model';

type EditorPosition = { row: number; column: number };
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
    lineWidgets: MonacoEditorLineWidget[] = [];
    editorBuildAnnotations: MonacoEditorBuildAnnotation[] = [];

    constructor(private themeService: ThemeService) {}

    @Input()
    textChangedEmitDelay?: number;

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

    private textChangedEmitTimeout?: NodeJS.Timeout;

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
        this.reset();
        this._editor.dispose();
        this.themeSubscription?.unsubscribe();
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

    // Workaround: The rest of the code expects { row, column } - we have { lineNumber, column }. Can be removed when Ace is removed.
    getPosition(): EditorPosition {
        const position = this._editor.getPosition() ?? new monaco.Position(0, 0);
        return { row: position.lineNumber, column: position.column };
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

    /**
     * Switches to another model (representing files) in the editor and optionally sets its content.
     * The editor's syntax highlighting will be set depending on the file extension.
     * All elements currently rendered in the editor will be disposed.
     * @param fileName The name of the file to switch to.
     * @param newFileContent The content of the file (will be retrieved from the model if left out).
     */
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
        this.disposeEditorElements();

        monaco.editor.setModelLanguage(model, model.getLanguageId());
        this._editor.setModel(model);
    }

    disposeModels() {
        this._editor.setModel(null);
        this.models.forEach((m) => m.dispose());
        this.models = [];
    }

    reset(): void {
        this.disposeEditorElements();
        this.disposeModels();
    }

    disposeEditorElements(): void {
        this.disposeAnnotations();
        this.disposeWidgets();
    }

    disposeWidgets() {
        this.lineWidgets.forEach((i) => {
            i.dispose();
        });
        this.lineWidgets = [];
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

    /**
     * Sets the build annotations to display in the editor. They are fixed to their respective lines and will be marked
     * as outdated.
     * @param annotations The annotations to render in the editor.
     * @param outdated Whether the specified annotations are already outdated and should be grayed out.
     */
    setAnnotations(annotations: Annotation[], outdated: boolean = false): void {
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
            editorBuildAnnotation.setOutdatedAndUpdate(outdated);
            this.editorBuildAnnotations.push(editorBuildAnnotation);
        }
    }

    /**
     * Renders a line widget after the specified line.
     * @param lineNumber The line after which the widget should be rendered.
     * @param id The ID to use for the widget.
     * @param domNode The content to display in the editor.
     */
    addLineWidget(lineNumber: number, id: string, domNode: HTMLElement) {
        const lineWidget = new MonacoEditorLineWidget(this._editor, id, domNode, lineNumber);
        lineWidget.addToEditor();
        this.lineWidgets.push(lineWidget);
    }
}
