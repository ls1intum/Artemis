import { ChangeDetectionStrategy, Component, ElementRef, NgZone, OnDestroy, OnInit, Renderer2, ViewEncapsulation, effect, inject, input, output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MonacoTextEditorAdapter } from 'app/shared/monaco-editor/model/actions/adapter/monaco-text-editor.adapter';
import { Disposable, EditorPosition, EditorRange, MonacoEditorTextModel } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
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

export type MonacoEditorMode = 'normal' | 'diff';
export type MonacoEditorDiffText = { original: string; modified: string };

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
    private _diffEditor?: monaco.editor.IStandaloneDiffEditor;
    private diffEditorContainer?: HTMLElement;
    private textEditorAdapter: MonacoTextEditorAdapter;
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
    private selectionChangeListeners: { listener: (selection: EditorRange | null) => void; disposable?: Disposable }[] = [];

    /*
     * Inputs and outputs.
     */
    mode = input<MonacoEditorMode>('normal');
    textChangedEmitDelay = input<number | undefined>();
    shrinkToFit = input<boolean>(true);
    stickyScroll = input<boolean>(false);
    readOnly = input<boolean>(false);

    textChanged = output<{ text: string; fileName: string }>();
    contentHeightChanged = output<number>();
    onBlurEditor = output<void>();
    diffChanged = output<{ ready: boolean; lineChange: LineChange }>();

    /*
     * Disposable listeners, subscriptions, and timeouts.
     */
    private contentHeightListener?: Disposable;
    private textChangedListener?: Disposable;
    private blurEditorWidgetListener?: Disposable;
    private focusEditorTextListener?: Disposable;
    private diffUpdateListener?: Disposable;
    private resizeObserver?: ResizeObserver;
    private textChangedEmitTimeouts = new Map<string, NodeJS.Timeout>();
    private customBackspaceCommandId: string | undefined;
    private diffEditorFocusListener?: Disposable;

    /*
     * Injected services and elements.
     */
    private readonly renderer = inject(Renderer2);
    private readonly translateService = inject(TranslateService);
    private readonly elementRef = inject(ElementRef);
    private readonly monacoEditorService = inject(MonacoEditorService);
    private readonly ngZone = inject(NgZone);

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

            // Also update diff editor if it exists
            if (this._diffEditor) {
                this._diffEditor.updateOptions({
                    readOnly: this.readOnly(),
                    originalEditable: false,
                });
            }
        });

        // Handle mode switching between normal and diff
        effect(() => {
            const currentMode = this.mode();
            if (currentMode === 'diff' && !this._diffEditor) {
                this.initializeDiffEditor();
            } else if (currentMode === 'normal' && this._diffEditor) {
                this.disposeDiffEditor();
            }
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

    /**
     * Initializes the diff editor and hides the normal editor.
     * @private
     */
    private initializeDiffEditor(): void {
        if (this._diffEditor) {
            return;
        }

        // Hide the normal editor
        this.renderer.setStyle(this.monacoEditorContainerElement, 'display', 'none');

        // Create diff editor container the same way as normal editor container
        const diffEditorContainer = this.renderer.createElement('div');
        this.renderer.addClass(diffEditorContainer, 'monaco-diff-editor-container');
        this.renderer.addClass(diffEditorContainer, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
        this.renderer.appendChild(this.elementRef.nativeElement, diffEditorContainer);
        this.diffEditorContainer = diffEditorContainer;

        this._diffEditor = this.monacoEditorService.createStandaloneDiffEditor(diffEditorContainer);
        this._diffEditor.updateOptions({
            readOnly: this.readOnly(),
            // Enable editing on the modified (right) side of the diff editor
            // The original (left) side should always be read-only
            originalEditable: false,
            renderSideBySide: true,
        });

        // Update the text editor adapter to point to the modified (editable) side of the diff editor
        // This is crucial for toolbar actions to work correctly in diff mode
        this.textEditorAdapter = new MonacoTextEditorAdapter(this._diffEditor.getModifiedEditor());

        // Initial layout after diff editor creation
        // The ResizeObserver will handle subsequent layouts
        this._diffEditor.layout();

        // Observe parent element (not diff container) so we can update diff container size on resize
        if (this.resizeObserver) {
            this.resizeObserver.observe(diffEditorContainer);
        }

        // Set up diff update listener
        this.ngZone.runOutsideAngular(() => {
            this.diffUpdateListener = this._diffEditor!.onDidUpdateDiff(() => {
                const monacoLineChanges = this._diffEditor!.getLineChanges() ?? [];
                const lineChange = this.convertMonacoLineChanges(monacoLineChanges);
                this.ngZone.run(() => {
                    this.diffChanged.emit({ ready: true, lineChange });
                });
            });
        });

        // Set up text change listener for the modified editor
        this.ngZone.runOutsideAngular(() => {
            this.textChangedListener?.dispose();
            this.textChangedListener = this._diffEditor!.getModifiedEditor().onDidChangeModelContent(() => {
                this.ngZone.run(() => this.emitTextChangeEvent());
            });
        });

        // Set up focus listener for custom backspace in diff editor
        this.ngZone.runOutsideAngular(() => {
            this.diffEditorFocusListener = this._diffEditor!.getModifiedEditor().onDidFocusEditorText(() => {
                this.ngZone.run(() => this.registerCustomBackspaceAction(this._diffEditor!.getModifiedEditor()));
            });
        });
        this.registerCustomBackspaceAction(this._diffEditor.getModifiedEditor());

        // Re-register all actions with the new adapter for diff mode
        this.reRegisterActions();
        this.reRegisterSelectionListeners();
    }

    /**
     * Disposes the diff editor and shows the normal editor.
     * @private
     */
    private disposeDiffEditor(): void {
        if (!this._diffEditor) {
            return;
        }

        // Clean up diff editor listeners
        this.diffUpdateListener?.dispose();
        this.diffUpdateListener = undefined;
        this.diffEditorFocusListener?.dispose();
        this.diffEditorFocusListener = undefined;

        if (this.resizeObserver && this.diffEditorContainer) {
            this.resizeObserver.unobserve(this.diffEditorContainer);
        }

        // Show the normal editor FIRST to prevent parent collapse
        // The dimensions are already set on the container from layoutWithFixedSize
        this.renderer.setStyle(this.monacoEditorContainerElement, 'display', 'block');

        // Restore the text editor adapter
        this.textEditorAdapter = new MonacoTextEditorAdapter(this._editor);

        // Layout the normal editor with its current container dimensions
        this._editor.layout();

        // Remove the diff editor
        const diffEditorContainer = this._diffEditor.getContainerDomNode();
        this._diffEditor.dispose();
        this._diffEditor = undefined;
        this.renderer.removeChild(this.elementRef.nativeElement, diffEditorContainer);
        this.diffEditorContainer = undefined;

        // Restore text change listener for normal editor
        this.ngZone.runOutsideAngular(() => {
            this.textChangedListener?.dispose();
            this.textChangedListener = this._editor.onDidChangeModelContent(() => {
                this.ngZone.run(() => this.emitTextChangeEvent());
            });
        });

        // Re-register all actions with the restored adapter for normal mode
        this.reRegisterActions();
        this.reRegisterSelectionListeners();

        // Emit text change to notify parent of mode switch
        this.emitTextChangeEvent();
    }

    /**
     * Converts Monaco line changes to a LineChange object.
     * @private
     */
    private convertMonacoLineChanges(monacoLineChanges: monaco.editor.ILineChange[]): LineChange {
        const lineChange: LineChange = { addedLineCount: 0, removedLineCount: 0 };
        if (!monacoLineChanges) {
            return lineChange;
        }

        for (const change of monacoLineChanges) {
            const addedLines = change.modifiedEndLineNumber >= change.modifiedStartLineNumber ? change.modifiedEndLineNumber - change.modifiedStartLineNumber + 1 : 0;
            const removedLines = change.originalEndLineNumber >= change.originalStartLineNumber ? change.originalEndLineNumber - change.originalStartLineNumber + 1 : 0;
            lineChange.addedLineCount += addedLines;
            lineChange.removedLineCount += removedLines;
        }

        return lineChange;
    }

    /**
     * Sets the contents for diff comparison.
     * Only works when mode is 'diff'.
     */
    setDiffContents(original: string, modified: string, originalFileName: string = 'original', modifiedFileName: string = 'modified'): void {
        if (this.mode() !== 'diff' || !this._diffEditor) {
            return;
        }

        // Emit not ready state while diff is being computed
        this.diffChanged.emit({ ready: false, lineChange: { addedLineCount: 0, removedLineCount: 0 } });

        const originalModelUri = monaco.Uri.parse(`inmemory://model/original-${this._diffEditor.getId()}/${originalFileName}`);
        const modifiedFileUri = monaco.Uri.parse(`inmemory://model/modified-${this._diffEditor.getId()}/${modifiedFileName}`);
        const originalModel = monaco.editor.getModel(originalModelUri) ?? monaco.editor.createModel(original, 'markdown', originalModelUri);
        const modifiedModel = monaco.editor.getModel(modifiedFileUri) ?? monaco.editor.createModel(modified, 'markdown', modifiedFileUri);

        originalModel.setValue(original);
        modifiedModel.setValue(modified);

        this._diffEditor.setModel({
            original: originalModel,
            modified: modifiedModel,
        });

        // Layout after setting content to ensure proper sizing
        const parentElement = this.elementRef.nativeElement as HTMLElement;
        const width = parentElement.clientWidth || parentElement.offsetWidth;
        const height = parentElement.clientHeight || parentElement.offsetHeight;
        if (width > 0 && height > 0) {
            this._diffEditor.layout({ width, height });
        }
    }

    /**
     * Gets the text from both editors in diff mode.
     * Returns undefined if not in diff mode.
     */
    getDiffText(): MonacoEditorDiffText | undefined {
        if (this.mode() !== 'diff' || !this._diffEditor) {
            return undefined;
        }

        return {
            original: this._diffEditor.getOriginalEditor().getValue(),
            modified: this._diffEditor.getModifiedEditor().getValue(),
        };
    }

    /**
     * Gets the modified (right) editor instance when in diff mode.
     * Returns undefined if not in diff mode.
     */
    getModifiedEditor(): monaco.editor.IStandaloneCodeEditor | undefined {
        if (this.mode() !== 'diff' || !this._diffEditor) {
            return undefined;
        }
        return this._diffEditor.getModifiedEditor();
    }

    /**
     * Gets the active editor: the normal editor in normal mode, or the modified editor in diff mode.
     */
    getActiveEditor(): monaco.editor.IStandaloneCodeEditor {
        return this.mode() === 'diff' && this._diffEditor ? this._diffEditor.getModifiedEditor() : this._editor;
    }

    public onDidChangeModelContent(listener: (event: monaco.editor.IModelContentChangedEvent) => void): monaco.IDisposable {
        return this.getActiveEditor().onDidChangeModelContent(listener);
    }

    public getModel() {
        return this.getActiveEditor().getModel();
    }

    public getLineContent(lineNumber: number): string {
        const model = this.getActiveEditor().getModel();
        return model ? model.getLineContent(lineNumber) : '';
    }

    ngOnInit(): void {
        this.resizeObserver = new ResizeObserver(() => {
            // Layout the active editor (normal or diff)
            if (this._diffEditor) {
                this._diffEditor.layout();
            } else {
                this._editor.layout();
            }
        });
        // Observe both the normal editor container AND the parent element
        // Parent element is needed for diff mode to detect when jhi-monaco-editor resizes
        this.resizeObserver.observe(this.monacoEditorContainerElement);
        this.resizeObserver.observe(this.elementRef.nativeElement);

        this.ngZone.runOutsideAngular(() => {
            this.textChangedListener = this._editor.onDidChangeModelContent(() => {
                this.ngZone.run(() => this.emitTextChangeEvent());
            }, this);

            this.contentHeightListener = this._editor.onDidContentSizeChange((event) => {
                if (event.contentHeightChanged) {
                    this.ngZone.run(() => this.contentHeightChanged.emit(event.contentHeight + this._editor.getOption(monaco.editor.EditorOption.lineHeight)));
                }
            });

            this.blurEditorWidgetListener = this._editor.onDidBlurEditorWidget(() => {
                // On iOS, the editor does not lose focus when clicking outside of it. This listener ensures that the editor loses focus when the editor widget loses focus.
                // See https://github.com/microsoft/monaco-editor/issues/307
                if (getOS() === 'iOS' && document.activeElement && 'blur' in document.activeElement && typeof document.activeElement.blur === 'function') {
                    (document.activeElement as HTMLElement).blur();
                }
                this.ngZone.run(() => this.onBlurEditor.emit());
            });

            this.focusEditorTextListener = this._editor.onDidFocusEditorText(() => {
                this.ngZone.run(() => this.registerCustomBackspaceAction(this._editor));
            });
        });

        this.registerCustomBackspaceAction(this._editor);
    }

    ngOnDestroy() {
        this.reset();
        this._editor.dispose();
        this._diffEditor?.dispose();
        this.textChangedListener?.dispose();
        this.contentHeightListener?.dispose();
        this.blurEditorWidgetListener?.dispose();
        this.focusEditorTextListener?.dispose();
        this.diffUpdateListener?.dispose();
        this.diffEditorFocusListener?.dispose();
        this.resizeObserver?.disconnect();

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
        const timeoutId = this.ngZone.runOutsideAngular(() =>
            setTimeout(() => {
                this.ngZone.run(() => {
                    this.textChanged.emit({ text: newValue, fileName: fullFilePath });
                    this.textChangedEmitTimeouts.delete(modelKey);
                });
            }, delay),
        );
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

    getScrolledVisiblePosition(position: EditorPosition): { top: number; left: number; height: number } | null {
        return this.getActiveEditor().getScrolledVisiblePosition(position);
    }

    getDomNode(): HTMLElement | null {
        return this.getActiveEditor().getDomNode();
    }

    getPosition(): EditorPosition {
        return this.getActiveEditor().getPosition() ?? { column: 0, lineNumber: 0 };
    }

    revealLine(lineNumber: number, scrollType: monaco.editor.ScrollType): void {
        this.getActiveEditor().revealLineNearTop(lineNumber, scrollType);
    }

    setPosition(position: EditorPosition) {
        this.getActiveEditor().setPosition(position);
    }

    getScrollTop(): number {
        return this.getActiveEditor().getScrollTop();
    }

    setScrollTop(scrollTop: number) {
        this.getActiveEditor().setScrollTop(scrollTop);
    }

    setSelection(range: EditorRange): void {
        this.getActiveEditor().setSelection(range);
    }

    getText(): string {
        return this.getActiveEditor().getValue();
    }

    getContentHeight(): number {
        const activeEditor = this.getActiveEditor();
        return activeEditor.getContentHeight() + activeEditor.getOption(monaco.editor.EditorOption.lineHeight);
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
        this.getActiveEditor().trigger('MonacoEditorComponent::triggerKeySequence', 'type', { text });
    }

    focus(): void {
        this.getActiveEditor().focus();
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

    /**
     * Registers a keydown listener on the underlying Monaco editor and returns a disposable to remove it.
     * Use this to intercept keys before the editor handles them (e.g., in read-only mode).
     * @param listener The callback to invoke on keydown events while the editor is focused.
     */
    onKeyDown(listener: (event: monaco.IKeyboardEvent) => void): Disposable {
        return this._editor.onKeyDown(listener);
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
        if (this.mode() === 'diff' && this._diffEditor) {
            this._diffEditor.layout();
        } else {
            // When layout is triggered for normal editor (e.g. by resize), clear any fixed dimensions
            // so that it returns to being 100% of the parent (responsive)
            this.renderer.removeStyle(this.monacoEditorContainerElement, 'width');
            this.renderer.removeStyle(this.monacoEditorContainerElement, 'height');
            this.renderer.setStyle(this.monacoEditorContainerElement, 'width', '100%');
            this.renderer.setStyle(this.monacoEditorContainerElement, 'height', '100%');

            this._editor.layout();
        }
    }

    /**
     * Layouts this editor with a fixed size. Should only be used for testing purposes or when the size
     * of the editor is clear; otherwise, there may be visual glitches!
     * @param width The new width of the editor
     * @param height The new height of the editor.
     */
    layoutWithFixedSize(width: number, height: number): void {
        // Always set dimensions on both containers so the normal editor is ready when switching back
        this.renderer.setStyle(this.monacoEditorContainerElement, 'width', `${width}px`);
        this.renderer.setStyle(this.monacoEditorContainerElement, 'height', `${height}px`);

        if (this.mode() === 'diff' && this._diffEditor) {
            if (this.diffEditorContainer) {
                this.renderer.setStyle(this.diffEditorContainer, 'width', `${width}px`);
                this.renderer.setStyle(this.diffEditorContainer, 'height', `${height}px`);
            }
            this._diffEditor.layout({ width, height });
        } else {
            this._editor.layout({ width, height });
        }
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

    /**
     * Re-registers all actions with the current text editor adapter.
     * This is needed when switching between normal and diff mode to ensure
     * toolbar actions work correctly with the active editor.
     * @private
     */
    private reRegisterActions(): void {
        for (const action of this.actions) {
            action.dispose();
            action.register(this.textEditorAdapter, this.translateService);
        }
    }

    /**
     * Re-registers all selection listeners with the current active editor.
     * This is needed when switching between normal and diff mode.
     * @private
     */
    private reRegisterSelectionListeners(): void {
        for (const listenerEntry of this.selectionChangeListeners) {
            listenerEntry.disposable?.dispose();
            listenerEntry.disposable = this.registerSelectionListenerOnEditor(this.getActiveEditor(), listenerEntry.listener);
        }
    }

    /**
     * Helper to register a selection listener on a specific editor instance.
     * @param editor The editor to register the listener on.
     * @param listener The callback to invoke when selection changes.
     */
    private registerSelectionListenerOnEditor(editor: monaco.editor.IStandaloneCodeEditor, listener: (selection: EditorRange | null) => void): Disposable {
        return this.ngZone.runOutsideAngular(() => {
            return editor.onDidChangeCursorSelection((e) => {
                const selection = e.selection;
                if (selection.isEmpty()) {
                    this.ngZone.run(() => listener(null));
                } else {
                    this.ngZone.run(() =>
                        listener({
                            startLineNumber: selection.startLineNumber,
                            endLineNumber: selection.endLineNumber,
                            startColumn: selection.startColumn,
                            endColumn: selection.endColumn,
                        }),
                    );
                }
            });
        });
    }

    /**
     * Registers a listener for selection changes in the editor.
     * @param listener The callback to invoke when the selection changes.
     * @returns A disposable to remove the listener.
     */
    onSelectionChange(listener: (selection: EditorRange | null) => void): Disposable {
        const disposable = this.registerSelectionListenerOnEditor(this.getActiveEditor(), listener);
        const listenerEntry = { listener, disposable };
        this.selectionChangeListeners.push(listenerEntry);

        return {
            dispose: () => {
                listenerEntry.disposable?.dispose();
                const index = this.selectionChangeListeners.indexOf(listenerEntry);
                if (index !== -1) {
                    this.selectionChangeListeners.splice(index, 1);
                }
            },
        };
    }

    /**
     * Gets the current selection in the editor.
     * @returns The current selection or null if no selection.
     */
    getSelection(): EditorRange | null {
        const selection = this.getActiveEditor().getSelection();
        if (!selection || selection.isEmpty()) {
            return null;
        }
        return {
            startLineNumber: selection.startLineNumber,
            endLineNumber: selection.endLineNumber,
            startColumn: selection.startColumn,
            endColumn: selection.endColumn,
        };
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
