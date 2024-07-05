import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, Renderer2, ViewEncapsulation } from '@angular/core';
import * as monaco from 'monaco-editor';
import { Subscription } from 'rxjs';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { MonacoEditorLineWidget } from 'app/shared/monaco-editor/model/monaco-editor-inline-widget.model';
import { MonacoEditorBuildAnnotation, MonacoEditorBuildAnnotationType } from 'app/shared/monaco-editor/model/monaco-editor-build-annotation.model';
import { MonacoEditorLineHighlight } from 'app/shared/monaco-editor/model/monaco-editor-line-highlight.model';
import { Annotation } from 'app/exercises/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { MonacoEditorLineDecorationsHoverButton } from './model/monaco-editor-line-decorations-hover-button.model';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { TranslateService } from '@ngx-translate/core';

type EditorPosition = { row: number; column: number };
@Component({
    selector: 'jhi-monaco-editor',
    template: '',
    styleUrls: ['monaco-editor.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class MonacoEditorComponent implements OnInit, OnDestroy {
    private _editor: monaco.editor.IStandaloneCodeEditor;
    private monacoEditorContainerElement: HTMLElement;
    themeSubscription?: Subscription;
    models: monaco.editor.IModel[] = [];
    lineWidgets: MonacoEditorLineWidget[] = [];
    editorBuildAnnotations: MonacoEditorBuildAnnotation[] = [];
    lineHighlights: MonacoEditorLineHighlight[] = [];
    actions: MonacoEditorAction[] = [];
    lineDecorationsHoverButton?: MonacoEditorLineDecorationsHoverButton;

    /**
     * The default width of the line decoration button in the editor. We use the ch unit to avoid fixed pixel sizes.
     * @private
     */
    private static readonly DEFAULT_LINE_DECORATION_BUTTON_WIDTH = '2.3ch';

    constructor(
        private readonly themeService: ThemeService,
        elementRef: ElementRef,
        private readonly renderer: Renderer2,
        private readonly translateService: TranslateService,
    ) {
        /*
         * The constructor injects the editor along with its container into the empty template of this component.
         * This makes the editor available immediately (not just after ngOnInit), preventing errors when the methods
         * of this component are called.
         */
        this.monacoEditorContainerElement = renderer.createElement('div');
        renderer.addClass(this.monacoEditorContainerElement, 'monaco-editor-container');
        renderer.addClass(this.monacoEditorContainerElement, 'monaco-shrink-to-fit');
        this._editor = monaco.editor.create(this.monacoEditorContainerElement, {
            value: '',
            glyphMargin: true,
            minimap: { enabled: false },
            readOnly: this._readOnly,
            lineNumbersMinChars: 4,
            scrollBeyondLastLine: false,
            scrollbar: {
                alwaysConsumeMouseWheel: false, // Prevents the editor from consuming the mouse wheel event, allowing the parent element to scroll.
            },
        });
        this._editor.getModel()?.setEOL(monaco.editor.EndOfLineSequence.LF);
        renderer.appendChild(elementRef.nativeElement, this.monacoEditorContainerElement);
    }

    @Input()
    textChangedEmitDelay?: number;

    // TODO: The CSS class below allows the editor to shrink in the CodeEditorContainerComponent. We should eventually remove this class and handle the editor size differently in the code editor grid.
    @Input()
    set shrinkToFit(value: boolean) {
        if (value) {
            this.renderer.addClass(this.monacoEditorContainerElement, 'monaco-shrink-to-fit');
        } else {
            this.renderer.removeClass(this.monacoEditorContainerElement, 'monaco-shrink-to-fit');
        }
    }

    @Input()
    set stickyScroll(value: boolean) {
        this._editor.updateOptions({
            stickyScroll: { enabled: value },
        });
    }

    @Input()
    set readOnly(value: boolean) {
        this._readOnly = value;
        this._editor.updateOptions({
            readOnly: value,
        });
    }

    private _readOnly: boolean = false;

    @Output()
    textChanged = new EventEmitter<string>();

    private textChangedEmitTimeout?: NodeJS.Timeout;

    ngOnInit(): void {
        const resizeObserver = new ResizeObserver(() => {
            this._editor.layout();
        });
        resizeObserver.observe(this.monacoEditorContainerElement);

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

    setSelection(range: monaco.IRange): void {
        this._editor.setSelection(range);
    }

    getText(): string {
        return this._editor.getValue();
    }

    setText(text: string): void {
        if (this.getText() !== text) {
            this._editor.setValue(text);
        }
    }

    /**
     * Signals to the editor that the specified text was typed.
     * @param text The text to type.
     */
    triggerKeySequence(text: string): void {
        this._editor.trigger('MonacoEditorComponent::triggerKeySequence', 'type', { text });
    }

    focus(): void {
        this._editor.focus();
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
    changeModel(fileName: string, newFileContent?: string, languageId?: string) {
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

        monaco.editor.setModelLanguage(model, languageId !== undefined ? languageId : model.getLanguageId());
        model.setEOL(monaco.editor.EndOfLineSequence.LF);
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
        this.disposeLineHighlights();
        this.disposeActions();
        this.lineDecorationsHoverButton?.dispose();
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

    disposeLineHighlights(): void {
        this.lineHighlights.forEach((o) => {
            o.dispose();
        });
        this.lineHighlights = [];
    }

    disposeActions(): void {
        this.actions.forEach((a) => {
            a.dispose();
        });
        this.actions = [];
    }

    changeTheme(artemisTheme: Theme): void {
        this._editor.updateOptions({
            theme: artemisTheme === Theme.DARK ? 'vs-dark' : 'vs-light',
        });
    }

    layout(): void {
        this._editor.layout();
    }

    /**
     * Layouts this editor with a fixed size. Should only be used for testing purposes or when the size
     * of the editor is clear; otherwise, there may be visual glitches!
     * @param width The new width of the editor
     * @param height The new height of the editor.
     */
    layoutWithFixedSize(width: number, height: number): void {
        this._editor.layout({ width, height });
    }

    /**
     * Sets the build annotations to display in the editor. They are fixed to their respective lines and will be marked
     * as outdated.
     * @param annotations The annotations to render in the editor.
     * @param outdated Whether the specified annotations are already outdated and should be grayed out.
     */
    setAnnotations(annotations: Annotation[], outdated: boolean = false): void {
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

    /**
     * Adds a hover button to the line decorations of the editor. The button is only visible when hovering over the line.
     * This will disable folding and set the line decorations width to make room for the button.
     * @param className The CSS class to use for the button. This class must uniquely identify the button. To render content, use the CSS content attribute.
     * @param clickCallback The callback to invoke when the button is clicked. The line number is passed as an argument.
     */
    setLineDecorationsHoverButton(className: string, clickCallback: (lineNumber: number) => void): void {
        this.lineDecorationsHoverButton?.dispose();
        this.lineDecorationsHoverButton = new MonacoEditorLineDecorationsHoverButton(
            this._editor,
            `line-decorations-hover-button-${this._editor.getId()}`,
            className,
            clickCallback,
        );
        // Make room for the hover button in the line decorations.
        this._editor.updateOptions({
            folding: false,
            lineDecorationsWidth: MonacoEditorComponent.DEFAULT_LINE_DECORATION_BUTTON_WIDTH,
        });
    }

    /**
     * Highlights a range of lines in the editor using the specified class names.
     * @param startLine The number of the first line to highlight.
     * @param endLine The number of the last line to highlight.
     * @param className The CSS class to use for highlighting the line itself, or undefined if none should be used.
     * @param marginClassName The CSS class to use for highlighting the margin, or undefined if none should be used.
     */
    highlightLines(startLine: number, endLine: number, className?: string, marginClassName?: string) {
        const highlight = new MonacoEditorLineHighlight(this._editor, 'line-highlight', startLine, endLine, className, marginClassName);
        highlight.addToEditor();
        this.lineHighlights.push(highlight);
    }

    getLineHighlights(): MonacoEditorLineHighlight[] {
        return this.lineHighlights;
    }

    /**
     * Registers an action to be available in the editor. The action will be disposed when the editor is disposed.
     * @param action The action to register.
     */
    registerAction(action: MonacoEditorAction): void {
        action.register(this._editor, this.translateService);
        this.actions.push(action);
    }

    setWordWrap(value: boolean): void {
        this._editor.updateOptions({
            wordWrap: value ? 'on' : 'off',
        });
    }
}
