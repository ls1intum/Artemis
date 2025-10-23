import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, Renderer2, ViewEncapsulation, effect, inject, input, output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MonacoTextEditorAdapter } from 'app/shared/monaco-editor/model/actions/adapter/monaco-text-editor.adapter';
import { Disposable, EditorPosition, EditorRange, MonacoEditorTextModel } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { MonacoEditorBuildAnnotation, MonacoEditorBuildAnnotationType } from 'app/shared/monaco-editor/model/monaco-editor-build-annotation.model';
import { MonacoEditorLineWidget } from 'app/shared/monaco-editor/model/monaco-editor-inline-widget.model';
import { MonacoEditorLineHighlight } from 'app/shared/monaco-editor/model/monaco-editor-line-highlight.model';
import { MonacoEditorOptionPreset } from 'app/shared/monaco-editor/model/monaco-editor-option-preset.model';
import { MonacoEditorService } from 'app/shared/monaco-editor/service/monaco-editor.service';
import { getOS } from 'app/shared/util/os-detector.util';
import Graphemer from 'graphemer';

import { EmojiConvertor } from 'emoji-js';
import * as monaco from 'monaco-editor';
import { MonacoEditorLineDecorationsHoverButton } from './model/monaco-editor-line-decorations-hover-button.model';
import { Annotation } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';

export const MAX_TAB_SIZE = 8;

@Component({
    selector: 'jhi-monaco-editor',
    template: '',
    styleUrls: ['monaco-editor.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
})
export class MonacoEditorComponent implements OnInit, OnDestroy {
    /**
     * The default width of the line decoration button in the editor. We use the ch unit to avoid fixed pixel sizes.
     * @private
     */
    private static readonly DEFAULT_LINE_DECORATION_BUTTON_WIDTH = '2.3ch';
    private static readonly SHRINK_TO_FIT_CLASS = 'monaco-shrink-to-fit';

    private readonly _editor: monaco.editor.IStandaloneCodeEditor;
    private readonly textEditorAdapter: MonacoTextEditorAdapter;
    private readonly monacoEditorContainerElement: HTMLElement;
    private readonly emojiConvertor = new EmojiConvertor();

    /*
     * Elements, models, and actions of the editor.
     */
    models: MonacoEditorTextModel[] = [];
    lineWidgets: MonacoEditorLineWidget[] = [];
    buildAnnotations: MonacoEditorBuildAnnotation[] = [];
    lineHighlights: MonacoEditorLineHighlight[] = [];
    actions: TextEditorAction[] = [];
    lineDecorationsHoverButton?: MonacoEditorLineDecorationsHoverButton;

    /*
     * Inputs and outputs.
     */
    textChangedEmitDelay = input<number | undefined>();
    shrinkToFit = input<boolean>(true);
    stickyScroll = input<boolean>(false);
    readOnly = input<boolean>(false);

    textChanged = output<{ text: string; fileName: string }>();
    contentHeightChanged = output<number>();
    onBlurEditor = output<void>();

    /*
     * Disposable listeners, subscriptions, and timeouts.
     */
    private contentHeightListener?: Disposable;
    private textChangedListener?: Disposable;
    private blurEditorWidgetListener?: Disposable;
    private textChangedEmitTimeouts = new Map<string, NodeJS.Timeout>();
    private customBackspaceCommandId: string | undefined;

    /*
     * Injected services and elements.
     */
    private readonly renderer = inject(Renderer2);
    private readonly translateService = inject(TranslateService);
    private readonly elementRef = inject(ElementRef);
    private readonly monacoEditorService = inject(MonacoEditorService);

    constructor() {
        /*
         * The constructor injects the editor along with its container into the empty template of this component.
         * This makes the editor available immediately (not just after ngOnInit), preventing errors when the methods
         * of this component are called.
         */
        this.monacoEditorContainerElement = this.renderer.createElement('div');
        this.renderer.addClass(this.monacoEditorContainerElement, 'monaco-editor-container');
        this.renderer.addClass(this.monacoEditorContainerElement, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
        this._editor = this.monacoEditorService.createStandaloneCodeEditor(this.monacoEditorContainerElement);
        this.textEditorAdapter = new MonacoTextEditorAdapter(this._editor);
        this.renderer.appendChild(this.elementRef.nativeElement, this.monacoEditorContainerElement);

        this.emojiConvertor.replace_mode = 'unified';
        this.emojiConvertor.allow_native = true;

        effect(() => {
            // TODO: The CSS class below allows the editor to shrink in the CodeEditorContainerComponent. We should eventually remove this class and handle the editor size differently in the code editor grid.
            if (this.shrinkToFit()) {
                this.renderer.addClass(this.monacoEditorContainerElement, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
            } else {
                this.renderer.removeClass(this.monacoEditorContainerElement, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
            }
        });

        effect(() => {
            this._editor.updateOptions({
                stickyScroll: { enabled: this.stickyScroll() },
                readOnly: this.readOnly(),
            });
        });
    }

    /**
     * Replaces emoticon-like text (e.g., ":)", ":D") with their corresponding emoji characters.
     * Only words that start with ":" are processed for conversion.
     *
     * @param text The raw input text to be scanned for emoji patterns.
     * @returns The transformed string with applicable emoticons replaced by emojis.
     */
    convertTextToEmoji(text: string): string {
        const words = text.split(' ');
        const convertedWords = words.map((word) => {
            return word.startsWith(':') ? this.emojiConvertor.replace_emoticons(word) : word;
        });

        return convertedWords.join(' ');
    }

    public onDidChangeModelContent(listener: (event: monaco.editor.IModelContentChangedEvent) => void): monaco.IDisposable {
        return this._editor.onDidChangeModelContent(listener);
    }

    public getModel() {
        return this._editor.getModel();
    }

    public getLineContent(lineNumber: number): string {
        const model = this._editor.getModel();
        return model ? model.getLineContent(lineNumber) : '';
    }

    ngOnInit(): void {
        const resizeObserver = new ResizeObserver(() => {
            this._editor.layout();
        });
        resizeObserver.observe(this.monacoEditorContainerElement);

        this.textChangedListener = this._editor.onDidChangeModelContent(() => {
            this.emitTextChangeEvent();
        }, this);

        this.contentHeightListener = this._editor.onDidContentSizeChange((event) => {
            if (event.contentHeightChanged) {
                this.contentHeightChanged.emit(event.contentHeight + this._editor.getOption(monaco.editor.EditorOption.lineHeight));
            }
        });

        this.blurEditorWidgetListener = this._editor.onDidBlurEditorWidget(() => {
            // On iOS, the editor does not lose focus when clicking outside of it. This listener ensures that the editor loses focus when the editor widget loses focus.
            // See https://github.com/microsoft/monaco-editor/issues/307
            if (getOS() === 'iOS' && document.activeElement && 'blur' in document.activeElement && typeof document.activeElement.blur === 'function') {
                document.activeElement.blur();
            }
            this.onBlurEditor.emit();
        });

        this._editor.onDidFocusEditorText(() => {
            this.registerCustomBackspaceAction(this._editor);
        });

        this.registerCustomBackspaceAction(this._editor);
    }

    ngOnDestroy() {
        this.reset();
        this._editor.dispose();
        this.textChangedListener?.dispose();
        this.contentHeightListener?.dispose();
        this.blurEditorWidgetListener?.dispose();

        // Clean up all per-model debounce timeouts
        this.textChangedEmitTimeouts.forEach((timeout) => clearTimeout(timeout));
        this.textChangedEmitTimeouts.clear();
    }

    private emitTextChangeEvent() {
        const newValue = this.getText();
        const delay = this.textChangedEmitDelay();
        const model = this.getModel();
        const fullFilePath = this.extractFilePathFromModel(model);

        if (!delay) {
            this.textChanged.emit({ text: newValue, fileName: fullFilePath });
            return;
        }
        const modelKey = model?.uri?.toString() ?? '';
        const existing = this.textChangedEmitTimeouts.get(modelKey);
        if (existing) {
            clearTimeout(existing);
        }
        const timeoutId = setTimeout(() => {
            this.textChanged.emit({ text: newValue, fileName: fullFilePath });
            this.textChangedEmitTimeouts.delete(modelKey);
        }, delay);
        this.textChangedEmitTimeouts.set(modelKey, timeoutId);
    }

    private extractFilePathFromModel(model: monaco.editor.ITextModel | null): string {
        const path = model?.uri?.path ?? '';
        if (!path) {
            return '';
        }
        // Path format: /model/<editorId>/<full/file/path>
        const parts = path.split('/').filter(Boolean);
        if (parts.length >= 3 && parts[0] === 'model') {
            return parts.slice(2).join('/');
        }
        // Fallback: best effort
        return parts.slice(1).join('/') || parts[parts.length - 1] || '';
    }

    getPosition(): EditorPosition {
        return this._editor.getPosition() ?? { column: 0, lineNumber: 0 };
    }

    setPosition(position: EditorPosition) {
        this._editor.setPosition(position);
    }

    getScrollTop(): number {
        return this._editor.getScrollTop();
    }

    setScrollTop(scrollTop: number) {
        this._editor.setScrollTop(scrollTop);
    }

    setSelection(range: EditorRange): void {
        this._editor.setSelection(range);
    }

    getText(): string {
        return this._editor.getValue();
    }

    getContentHeight(): number {
        return this._editor.getContentHeight() + this._editor.getOption(monaco.editor.EditorOption.lineHeight);
    }

    isConvertedToEmoji(originalText: string, convertedText: string): boolean {
        return originalText !== convertedText;
    }

    setText(text: string): void {
        const convertedText = this.convertTextToEmoji(text);
        if (this.isConvertedToEmoji(text, convertedText)) {
            this._editor.setValue(convertedText);
            this.setPosition({ column: this.getPosition().column + convertedText.length + text.length, lineNumber: this.getPosition().lineNumber });
        }
        if (this.getText() !== convertedText) {
            this._editor.setValue(convertedText);
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
     * @param languageId The language ID to use for syntax highlighting (will be inferred from the file extension if left out).
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

    /**
     * Updates the indentation size of the editor in the current model.
     * @param indentSize The size of the indentation in spaces.
     */
    updateModelIndentationSize(indentSize: number): void {
        this._editor.getModel()?.updateOptions({ indentSize });
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
        this.buildAnnotations.forEach((o) => {
            o.dispose();
        });
        this.buildAnnotations = [];
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
            this.buildAnnotations.push(editorBuildAnnotation);
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
    registerAction(action: TextEditorAction): void {
        action.register(this.textEditorAdapter, this.translateService);
        this.actions.push(action);
    }

    setWordWrap(value: boolean): void {
        this._editor.updateOptions({
            wordWrap: value ? 'on' : 'off',
        });
    }

    /**
     * Sets the line number from which the editor should start counting.
     * @param startLineNumber The line number to start counting from (starting at 1).
     */
    setStartLineNumber(startLineNumber: number): void {
        this._editor.updateOptions({
            lineNumbers: (number) => `${startLineNumber + number - 1}`,
        });
    }

    /**
     * Applies the given options to the editor.
     * @param options The options to apply.
     */
    applyOptionPreset(options: MonacoEditorOptionPreset): void {
        options.apply(this._editor);
    }

    public getCustomBackspaceCommandId(): string | undefined {
        return this.customBackspaceCommandId;
    }

    /**
     * Registers a custom backspace command that deletes the last grapheme cluster when pressing backspace.
     * @param editor The editor to register the command for.
     */
    private registerCustomBackspaceAction(editor: monaco.editor.IStandaloneCodeEditor) {
        this.customBackspaceCommandId =
            editor.addCommand(
                monaco.KeyCode.Backspace,
                () => {
                    const model = editor.getModel();
                    const selection = editor.getSelection();
                    if (!model || !selection) return;

                    if (!selection.isEmpty()) {
                        editor.trigger('keyboard', 'deleteLeft', null);
                        return;
                    }

                    const lineNumber = selection.startLineNumber;
                    const column = selection.startColumn;
                    const lineContent = model.getLineContent(lineNumber);

                    const textBeforeCursor = lineContent.substring(0, column - 1);
                    const splitter = new Graphemer();
                    const graphemes = splitter.splitGraphemes(textBeforeCursor);

                    if (textBeforeCursor.length === 0) {
                        editor.trigger('keyboard', 'deleteLeft', null);
                        return;
                    }

                    const lastGrapheme = graphemes.pop();
                    const deletedLength = lastGrapheme?.length ?? 1;
                    const newTextBeforeCursor = graphemes.join('');
                    const textAfterCursor = lineContent.substring(column - 1);

                    const newLineContent = newTextBeforeCursor + textAfterCursor;

                    model.pushEditOperations(
                        [],
                        [
                            {
                                range: new monaco.Range(lineNumber, 1, lineNumber, lineContent.length + 1),
                                text: newLineContent,
                            },
                        ],
                        () => null,
                    );
                    const newCursorPosition = column - deletedLength;
                    editor.setSelection(new monaco.Range(lineNumber, newCursorPosition, lineNumber, newCursorPosition));
                },
                'editorTextFocus && !findWidgetVisible',
            ) || undefined;
    }
}
