import { ChangeDetectionStrategy, Component, ElementRef, NgZone, OnDestroy, OnInit, Renderer2, ViewEncapsulation, effect, inject, input, isDevMode, output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MonacoTextEditorAdapter } from 'app/shared/monaco-editor/model/actions/adapter/monaco-text-editor.adapter';
import { Disposable, EditorPosition, EditorRange, MonacoEditorDiffText, MonacoEditorTextModel } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
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
import { LineChange, convertMonacoLineChanges } from 'app/programming/shared/utils/diff.utils';

export const MAX_TAB_SIZE = 8;

export type MonacoEditorMode = 'normal' | 'diff';

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

    private _editor: monaco.editor.IStandaloneCodeEditor;
    private _diffEditor?: monaco.editor.IStandaloneDiffEditor;

    private textEditorAdapter: MonacoTextEditorAdapter;
    private monacoEditorContainerElement: HTMLElement;
    private diffEditorContainerElement: HTMLElement;

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
     * Diff/editor switching state.
     */

    private diffSnapshotModel?: monaco.editor.ITextModel;
    private diffListenersAttached = false;

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

    mode = input<MonacoEditorMode>('normal');
    private lastMode: MonacoEditorMode | undefined = 'normal';
    renderSideBySide = input<boolean>(true);
    diffChanged = output<{ ready: boolean; lineChange: LineChange }>();

    /*
     * Disposable listeners, subscriptions, and timeouts.
     */
    private contentHeightListener?: Disposable;
    private textChangedListener?: Disposable;
    private blurEditorWidgetListener?: Disposable;
    private focusEditorTextListener?: Disposable;
    private lastEditableEditor?: monaco.editor.IStandaloneCodeEditor;

    private textChangedEmitTimeouts = new Map<string, NodeJS.Timeout>();
    private customBackspaceCommandId: string | undefined;

    private diffUpdateListener?: Disposable;

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
        this.initializeMonacoEditor();
        /*
         * Diff editor: create container once in constructor (hidden by default) but init editor lazily.
         */
        this.initializeDiffEditorContainer();

        this.emojiConvertor.replace_mode = 'unified';
        this.emojiConvertor.allow_native = true;

        effect(() => {
            // TODO: The CSS class below allows the editor to shrink in the CodeEditorContainerComponent. We should eventually remove this class and handle the editor size differently in the code editor grid.
            const enabled = this.shrinkToFit();
            if (enabled) {
                this.renderer.addClass(this.monacoEditorContainerElement, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
                this.renderer.addClass(this.diffEditorContainerElement, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
            } else {
                this.renderer.removeClass(this.monacoEditorContainerElement, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
                this.renderer.removeClass(this.diffEditorContainerElement, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
            }
        });

        effect(() => {
            const stickyScrollEnabled = this.stickyScroll();
            const isReadOnly = this.readOnly();
            const renderSideBySide = this.renderSideBySide();

            this._editor.updateOptions({
                stickyScroll: { enabled: stickyScrollEnabled },
                readOnly: isReadOnly,
            });

            this._diffEditor?.updateOptions({
                readOnly: isReadOnly,
                originalEditable: false,
                renderSideBySide,
            });
        });

        // Mode switching.
        effect(() => {
            const nextMode = this.mode();
            if (this.lastMode === nextMode) {
                return;
            }
            const prevMode = this.lastMode;
            this.lastMode = nextMode;
            if (nextMode === 'diff') {
                // only do expensive work if we're actually switching into diff
                this.enterDiffMode(prevMode);
            } else {
                this.leaveDiffMode(prevMode);
            }
        });
    }

    /**
     * Initializes the normal Monaco Editor and its container.
     */
    private initializeMonacoEditor(): void {
        this.monacoEditorContainerElement = this.renderer.createElement('div');
        this.renderer.addClass(this.monacoEditorContainerElement, 'monaco-editor-container');
        this.renderer.addClass(this.monacoEditorContainerElement, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
        this._editor = this.monacoEditorService.createStandaloneCodeEditor(this.monacoEditorContainerElement);
        this.renderer.appendChild(this.elementRef.nativeElement, this.monacoEditorContainerElement);
    }

    /**
     * Initializes the Diff Editor container (hidden by default).
     */
    private initializeDiffEditorContainer(): void {
        this.diffEditorContainerElement = this.renderer.createElement('div');
        this.renderer.addClass(this.diffEditorContainerElement, 'monaco-diff-editor-container');
        this.renderer.addClass(this.diffEditorContainerElement, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
        this.renderer.setStyle(this.diffEditorContainerElement, 'display', 'none');
        // Dimensions are set in enterDiffMode() when the normal editor container is laid out.
        this.renderer.appendChild(this.elementRef.nativeElement, this.diffEditorContainerElement);
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
        return this.getActiveEditor().onDidChangeModelContent(listener);
    }

    public getModel() {
        return this.getActiveEditor().getModel();
    }

    public getEditor() {
        return this._editor;
    }

    public getLineContent(lineNumber: number): string {
        const model = this.getActiveEditor().getModel();
        return model ? model.getLineContent(lineNumber) : '';
    }

    ngOnInit(): void {
        const resizeObserver = new ResizeObserver(() => {
            if (this.mode() === 'diff' && this._diffEditor) {
                this._diffEditor.layout();
            } else {
                this._editor.layout();
            }
        });
        resizeObserver.observe(this.monacoEditorContainerElement);
        resizeObserver.observe(this.diffEditorContainerElement);

        this.ngZone.runOutsideAngular(() => {
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
        });

        // Wire listeners that depend on the "editable" editor (normal or diff modified).
        this.setActiveEditorContext();
        // Ensure diff-only listeners are attached once.
        this.ensureDiffListeners();
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

        // Dispose snapshot model if present
        this.disposeDiffSnapshotModel();

        // Clean up all per-model debounce timeouts
        this.textChangedEmitTimeouts.forEach((timeout) => {
            clearTimeout(timeout);
        });
        this.textChangedEmitTimeouts.clear();
    }

    private enterDiffMode(prevMode: MonacoEditorMode | undefined): void {
        const rect = this.monacoEditorContainerElement.getBoundingClientRect();
        const width = `${rect.width}px`;
        const height = `${rect.height}px`;

        if (!this._diffEditor) {
            this._diffEditor = this.monacoEditorService.createStandaloneDiffEditor(this.diffEditorContainerElement, this.readOnly());
            this._diffEditor.updateOptions({
                originalEditable: false,
                renderSideBySide: this.renderSideBySide(),
            });
        }

        this.renderer.setStyle(this.diffEditorContainerElement, 'width', width);
        this.renderer.setStyle(this.diffEditorContainerElement, 'height', height);
        this.setContainersVisibility('diff');

        if (prevMode !== 'diff') {
            this.ensureDiffModelWired();
        }
        this.setActiveEditorContext();
    }

    private leaveDiffMode(prevMode: MonacoEditorMode | undefined): void {
        if (prevMode !== 'diff') {
            this.setContainersVisibility('normal');
            this.setActiveEditorContext();
            return;
        }

        if (this._diffEditor) {
            // Clear cached editor before disposing to prevent race conditions
            // (e.g., ResizeObserver callback reading mode() as 'diff' during transition)
            this.lastEditableEditor = undefined;
            this._diffEditor.dispose();
            this._diffEditor = undefined;
        }

        this.disposeDiffSnapshotModel();
        this.diffListenersAttached = false;
        this.diffUpdateListener?.dispose();
        this.diffUpdateListener = undefined;

        this.setContainersVisibility('normal');
        this.setActiveEditorContext();

        this.emitTextChangeEvent();
    }

    private setContainersVisibility(mode: MonacoEditorMode): void {
        this.renderer.setStyle(this.monacoEditorContainerElement, 'display', mode === 'normal' ? 'block' : 'none');
        this.renderer.setStyle(this.diffEditorContainerElement, 'display', mode === 'diff' ? 'block' : 'none');
    }

    private ensureDiffModelWired(): void {
        if (!this._diffEditor) return;

        const currentModel = this._editor.getModel();
        const currentContent = currentModel?.getValue() ?? '';
        const currentLanguage = currentModel?.getLanguageId() ?? 'markdown';

        // Recreate snapshot on every diff entry
        this.disposeDiffSnapshotModel();

        const snapshotUri = monaco.Uri.parse(`inmemory://model/snapshot-${this._editor.getId()}/${Date.now()}`);
        this.diffSnapshotModel = monaco.editor.createModel(currentContent, currentLanguage, snapshotUri);

        if (currentModel) {
            this._diffEditor.setModel({
                original: this.diffSnapshotModel,
                modified: currentModel,
            });
        }

        this._diffEditor.layout();
        this.ensureDiffListeners();
    }

    private disposeDiffSnapshotModel(): void {
        if (!this.diffSnapshotModel) return;

        this.diffSnapshotModel.dispose();
        this.diffSnapshotModel = undefined;
    }

    private ensureDiffListeners(): void {
        if (this.diffListenersAttached || !this._diffEditor) return;
        this.diffListenersAttached = true;

        this.ngZone.runOutsideAngular(() => {
            this.diffUpdateListener = this._diffEditor!.onDidUpdateDiff(() => {
                const monacoLineChanges = this._diffEditor!.getLineChanges() ?? [];
                const lineChange = convertMonacoLineChanges(monacoLineChanges);
                this.ngZone.run(() => {
                    this.diffChanged.emit({ ready: true, lineChange });
                });
            });
        });
    }

    private setActiveEditorContext(): void {
        const editor = this.getEditableEditor();
        if (editor === this.lastEditableEditor) {
            return;
        }
        this.lastEditableEditor = editor;
        this.textEditorAdapter = new MonacoTextEditorAdapter(editor);
        this.attachEditableEditorListeners(editor);
        this.reRegisterActions();
    }

    private getEditableEditor(): monaco.editor.IStandaloneCodeEditor {
        // If we are in diff mode but _diffEditor is not initialized for some reason, fallback to _editor.
        // This prevents crashes, though in practice enterDiffMode should have created it.
        return this.mode() === 'diff' && this._diffEditor ? this._diffEditor.getModifiedEditor() : this._editor;
    }

    private attachEditableEditorListeners(editor: monaco.editor.IStandaloneCodeEditor): void {
        this.ngZone.runOutsideAngular(() => {
            this.textChangedListener?.dispose();
            this.textChangedListener = editor.onDidChangeModelContent(() => {
                this.ngZone.run(() => this.emitTextChangeEvent());
            });

            this.focusEditorTextListener?.dispose();
            this.focusEditorTextListener = editor.onDidFocusEditorText(() => {
                this.ngZone.run(() => this.registerCustomBackspaceAction(editor));
            });
        });

        this.registerCustomBackspaceAction(editor);
    }

    applyDiffContent(newContent: string): void {
        if (!this.isDiffMode()) {
            return;
        }

        // Emit not ready state while diff is being computed
        this.diffChanged.emit({ ready: false, lineChange: { addedLineCount: 0, removedLineCount: 0 } });

        this._diffEditor!.getModifiedEditor().setValue(newContent);
        this._diffEditor!.layout();
    }

    revertAll(): void {
        if (!this.isDiffMode() || !this.diffSnapshotModel) {
            return;
        }
        const snapshotContent = this.diffSnapshotModel.getValue();
        this._diffEditor!.getModifiedEditor().setValue(snapshotContent);
    }

    getDiffText(): MonacoEditorDiffText | undefined {
        if (!this.isDiffMode()) {
            return undefined;
        }

        return {
            original: this._diffEditor!.getOriginalEditor().getValue(),
            modified: this._diffEditor!.getModifiedEditor().getValue(),
        };
    }

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
        return this.isDiffMode() ? this._diffEditor!.getModifiedEditor() : this._editor;
    }

    /**
     * Checks if the editor is currently in diff mode with an active diff editor.
     */
    private isDiffMode(): boolean {
        return this.mode() === 'diff' && !!this._diffEditor;
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
        return this.getActiveEditor().getContentHeight() + this.getActiveEditor().getOption(monaco.editor.EditorOption.lineHeight);
    }

    isConvertedToEmoji(originalText: string, convertedText: string): boolean {
        return originalText !== convertedText;
    }

    setText(text: string): void {
        const convertedText = this.convertTextToEmoji(text);
        const activeEditor = this.getActiveEditor();
        if (this.isConvertedToEmoji(text, convertedText)) {
            activeEditor.setValue(convertedText);
            this.setPosition({
                column: this.getPosition().column + convertedText.length + text.length,
                lineNumber: this.getPosition().lineNumber,
            });
        }
        if (this.getText() !== convertedText) {
            activeEditor.setValue(convertedText);
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
        return this.getActiveEditor().getModel()?.getLineCount() ?? 0;
    }

    isReadOnly(): boolean {
        return this.getActiveEditor().getOption(monaco.editor.EditorOption.readOnly);
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
        this.getActiveEditor().getModel()?.updateOptions({ indentSize });
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
        return this.getActiveEditor().onKeyDown(listener);
    }

    private disposeAndClear<T extends Disposable>(items: T[]): void {
        items.forEach((item) => {
            item.dispose();
        });
        items.length = 0;
    }

    disposeWidgets(): void {
        this.disposeAndClear(this.lineWidgets);
    }

    disposeAnnotations(): void {
        this.disposeAndClear(this.buildAnnotations);
    }

    disposeLineHighlights(): void {
        this.disposeAndClear(this.lineHighlights);
    }

    disposeActions(): void {
        this.disposeAndClear(this.actions);
    }

    layout(): void {
        if (this.isDiffMode()) {
            this._diffEditor!.layout();
        } else {
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
        if (this.isDiffMode()) {
            // Explicitly set the container style. The Diff Editor requires its container to match the layout dimensions
            // exactly to render its internal split-view correctly.
            this.renderer.setStyle(this.diffEditorContainerElement, 'width', `${width}px`);
            this.renderer.setStyle(this.diffEditorContainerElement, 'height', `${height}px`);
            this._diffEditor!.layout({ width, height });
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
                this.getActiveEditor(),
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
        const lineWidget = new MonacoEditorLineWidget(this.getActiveEditor(), id, domNode, lineNumber);
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
            this.getActiveEditor(),
            `line-decorations-hover-button-${this.getActiveEditor().getId()}`,
            className,
            clickCallback,
        );
        // Make room for the hover button in the line decorations.
        this.getActiveEditor().updateOptions({
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
        const highlight = new MonacoEditorLineHighlight(this.getActiveEditor(), 'line-highlight', startLine, endLine, className, marginClassName);
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
     * Re-registers all actions in the editor. This is necessary when the editor is disposed and re-created.
     * @private
     */
    private reRegisterActions(): void {
        for (const action of this.actions) {
            try {
                action.dispose();
                action.register(this.textEditorAdapter, this.translateService);
            } catch (error) {
                // Expected: Some actions may fail if no model is attached yet.
                if (isDevMode()) {
                    // eslint-disable-next-line no-undef
                    console.error('Failed to register action', action, error);
                }
            }
        }
    }

    setWordWrap(value: boolean): void {
        this.getActiveEditor().updateOptions({
            wordWrap: value ? 'on' : 'off',
        });
    }

    /**
     * Sets the line number from which the editor should start counting.
     * @param startLineNumber The line number to start counting from (starting at 1).
     */
    setStartLineNumber(startLineNumber: number): void {
        this.getActiveEditor().updateOptions({
            lineNumbers: (number) => `${startLineNumber + number - 1}`,
        });
    }

    /**
     * Applies the given options to the editor.
     * @param options The options to apply.
     */
    applyOptionPreset(options: MonacoEditorOptionPreset): void {
        options.apply(this.getActiveEditor());
    }

    public getCustomBackspaceCommandId(): string | undefined {
        return this.customBackspaceCommandId;
    }

    /**
     * Registers a custom backspace command that deletes the last grapheme cluster when pressing backspace.
     * Uses a targeted delete range (only the grapheme) instead of replacing the entire line,
     * so that collaborative editing (y-monaco) sees a minimal edit and cursor positions stay correct.
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
                    const deleteStartColumn = column - deletedLength;

                    model.pushEditOperations(
                        [],
                        [
                            {
                                range: new monaco.Range(lineNumber, deleteStartColumn, lineNumber, column),
                                text: '',
                            },
                        ],
                        () => [new monaco.Selection(lineNumber, deleteStartColumn, lineNumber, deleteStartColumn)],
                    );
                },
                'editorTextFocus && !findWidgetVisible && !editorReadonly',
            ) || undefined;
    }
}
