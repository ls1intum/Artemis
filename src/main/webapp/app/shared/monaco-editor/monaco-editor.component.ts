import { ChangeDetectionStrategy, Component, ElementRef, NgZone, OnDestroy, OnInit, Renderer2, ViewEncapsulation, effect, inject, input, isDevMode, output } from '@angular/core';
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
    private static readonly OVERLAY_HIDDEN_CLASS = 'monaco-overlay-hidden';

    private readonly _editor: monaco.editor.IStandaloneCodeEditor;
    private textEditorAdapter: MonacoTextEditorAdapter;
    private readonly monacoEditorContainerElement: HTMLElement;
    private readonly emojiConvertor = new EmojiConvertor();

    /**
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
    textChangedEmitDelay = input<number | undefined>();
    shrinkToFit = input<boolean>(true);
    stickyScroll = input<boolean>(false);
    readOnly = input<boolean>(false);

    textChanged = output<{ text: string; fileName: string }>();
    contentHeightChanged = output<number>();
    onBlurEditor = output<void>();

    mode = input<MonacoEditorMode>('normal');
    renderSideBySide = input<boolean>(true);
    diffChanged = output<{ ready: boolean; lineChange: LineChange }>();

    /*
     * Disposable listeners, subscriptions, and timeouts.
     */
    private activeEditorListeners: Disposable[] = [];
    private textChangedEmitTimeouts = new Map<string, NodeJS.Timeout>();
    private customBackspaceCommandId: string | undefined;

    private resizeObserver?: ResizeObserver;
    private diffUpdateListener?: Disposable;

    /*
     * Injected services and elements.
     */
    private readonly renderer = inject(Renderer2);
    private readonly translateService = inject(TranslateService);
    private readonly elementRef = inject(ElementRef);
    private readonly monacoEditorService = inject(MonacoEditorService);
    private readonly ngZone = inject(NgZone);

    private _diffEditor?: monaco.editor.IStandaloneDiffEditor;
    private diffEditorContainer?: HTMLElement;
    private diffSnapshotModel?: monaco.editor.ITextModel;
    private useLiveSyncedDiff = false;
    private currentMode: MonacoEditorMode = 'normal';

    constructor() {
        this.monacoEditorContainerElement = this.createContainer('monaco-editor-container');
        this._editor = this.monacoEditorService.createStandaloneCodeEditor(this.monacoEditorContainerElement);
        this.textEditorAdapter = new MonacoTextEditorAdapter(this._editor);

        this.renderer.appendChild(this.elementRef.nativeElement, this.monacoEditorContainerElement);

        this.emojiConvertor.replace_mode = 'unified';
        this.emojiConvertor.allow_native = true;

        effect(() => this.applyShrinkToFitClass(this.monacoEditorContainerElement, this.shrinkToFit()));

        effect(() => this.applyEditorOptions());

        effect(() => {
            const newMode = this.mode();
            if (newMode !== this.currentMode) {
                this.switchToMode(newMode);
            }
        });
    }

    ngOnInit(): void {
        this.resizeObserver = new ResizeObserver(() => this.layout());
        this.resizeObserver.observe(this.elementRef.nativeElement);

        this.attachToEditor(this._editor);
    }

    ngOnDestroy(): void {
        this.reset();
        this.disposeActiveEditorListeners();
        this.diffUpdateListener?.dispose();

        // Dispose all selection change listeners
        this.selectionChangeListeners.forEach((entry) => entry.disposable?.dispose());
        this.selectionChangeListeners = [];

        // Clear all debounce timeouts
        this.textChangedEmitTimeouts.forEach((timeout) => clearTimeout(timeout));
        this.textChangedEmitTimeouts.clear();

        this.resizeObserver?.disconnect();
        this._diffEditor?.dispose();
        this._editor.dispose();
    }

    /**
     * Switches between normal and diff mode.
     * Uses display:none to toggle containers, with explicit sizing to prevent 0-height collapse.
     */
    private switchToMode(newMode: MonacoEditorMode): void {
        this.currentMode = newMode;

        if (newMode === 'diff') {
            this.ensureDiffEditor();
            this.setContainerVisibility(this.monacoEditorContainerElement, false);

            this.setContainerVisibility(this.diffEditorContainer!, true);
            this.textEditorAdapter = new MonacoTextEditorAdapter(this._diffEditor!.getModifiedEditor());
            this.attachToEditor(this._diffEditor!.getModifiedEditor());
            this.bindDiffUpdateListener();
        } else {
            this.setContainerVisibility(this.diffEditorContainer, false);
            this.setContainerVisibility(this.monacoEditorContainerElement, true);

            // If we had modified content in diff mode, sync it back (unless live-synced)
            if (this._diffEditor && !this.useLiveSyncedDiff) {
                const modified = this._diffEditor.getModifiedEditor().getValue();
                this._editor.setValue(modified);
            }

            // Clear diff model to stop background diff computation
            this._diffEditor?.setModel(null);
            this.diffUpdateListener?.dispose();
            this.diffUpdateListener = undefined;

            // Dispose snapshot model
            if (this.diffSnapshotModel) {
                const idx = this.models.indexOf(this.diffSnapshotModel);
                if (idx !== -1) this.models.splice(idx, 1);
                this.diffSnapshotModel.dispose();
                this.diffSnapshotModel = undefined;
            }

            this.useLiveSyncedDiff = false;
            this.textEditorAdapter = new MonacoTextEditorAdapter(this._editor);
            this.attachToEditor(this._editor);
            this._editor.layout();
            this.emitTextChangeEvent();
        }
    }

    /**
     * Lazily creates the diff editor on first use. Subsequent calls are no-ops.
     */
    private ensureDiffEditor(): void {
        if (this._diffEditor) {
            // Diff editor exists, just update the snapshot model
            this.updateDiffSnapshot();
            return;
        }

        // Create diff editor container (hidden initially)
        this.diffEditorContainer = this.createContainer('monaco-diff-editor-container');
        this.setContainerVisibility(this.diffEditorContainer, false);
        this.renderer.appendChild(this.elementRef.nativeElement, this.diffEditorContainer);

        // Create the diff editor
        this._diffEditor = this.monacoEditorService.createStandaloneDiffEditor(this.diffEditorContainer);
        this.applyEditorOptions();

        // Create and set up the diff model
        this.updateDiffSnapshot();

        // Observe diff container for resize
        if (this.resizeObserver) {
            this.resizeObserver.observe(this.diffEditorContainer);
        }
    }

    /**
     * Creates or updates the snapshot model for diff comparison.
     */
    private updateDiffSnapshot(): void {
        const currentModel = this._editor.getModel();
        const currentContent = currentModel?.getValue() ?? '';
        const currentLanguage = currentModel?.getLanguageId() ?? 'markdown';

        // Dispose old snapshot if it exists
        if (this.diffSnapshotModel) {
            const idx = this.models.indexOf(this.diffSnapshotModel);
            if (idx !== -1) this.models.splice(idx, 1);
            this.diffSnapshotModel.dispose();
        }

        // Create new snapshot model (left side)
        const snapshotUri = monaco.Uri.parse(`inmemory://model/snapshot-${this._editor.getId()}/${Date.now()}`);
        this.diffSnapshotModel = monaco.editor.createModel(currentContent, currentLanguage, snapshotUri);
        this.models.push(this.diffSnapshotModel);

        if (currentModel && this._diffEditor) {
            this._diffEditor.setModel({ original: this.diffSnapshotModel, modified: currentModel });
            this.useLiveSyncedDiff = true;
        }
    }

    /**
     * Sets visibility of a container using CSS overlay class.
     */
    private setContainerVisibility(container: HTMLElement | undefined, visible: boolean): void {
        if (!container) return;

        if (visible) {
            this.renderer.removeClass(container, MonacoEditorComponent.OVERLAY_HIDDEN_CLASS);
        } else {
            this.renderer.addClass(container, MonacoEditorComponent.OVERLAY_HIDDEN_CLASS);
        }
    }

    private bindDiffUpdateListener(): void {
        this.diffUpdateListener?.dispose();
        if (!this._diffEditor) return;

        this.ngZone.runOutsideAngular(() => {
            this.diffUpdateListener = this._diffEditor!.onDidUpdateDiff(() => {
                const changes = this._diffEditor!.getLineChanges() ?? [];
                const lineChange = this.convertMonacoLineChanges(changes);
                this.ngZone.run(() => this.diffChanged.emit({ ready: true, lineChange }));
            });
        });
    }

    private disposeActiveEditorListeners(): void {
        this.activeEditorListeners.forEach((l) => l.dispose());
        this.activeEditorListeners = [];
    }

    private attachToEditor(editor: monaco.editor.IStandaloneCodeEditor): void {
        this.disposeActiveEditorListeners();

        this.ngZone.runOutsideAngular(() => {
            this.activeEditorListeners.push(
                editor.onDidChangeModelContent(() => {
                    this.ngZone.run(() => this.emitTextChangeEvent());
                }),
            );

            this.activeEditorListeners.push(
                editor.onDidFocusEditorText(() => {
                    this.ngZone.run(() => this.registerCustomBackspaceAction(editor));
                }),
            );

            this.activeEditorListeners.push(
                editor.onDidContentSizeChange((event) => {
                    if (event.contentHeightChanged) {
                        this.ngZone.run(() => this.contentHeightChanged.emit(event.contentHeight + editor.getOption(monaco.editor.EditorOption.lineHeight)));
                    }
                }),
            );

            this.activeEditorListeners.push(
                editor.onDidBlurEditorWidget(() => {
                    // On iOS, the editor does not lose focus when clicking outside of it. This listener ensures that the editor loses focus when the editor widget loses focus.
                    // See https://github.com/microsoft/monaco-editor/issues/307
                    if (getOS() === 'iOS' && document.activeElement && 'blur' in document.activeElement && typeof (document.activeElement as any).blur === 'function') {
                        (document.activeElement as HTMLElement).blur();
                    }
                    this.ngZone.run(() => this.onBlurEditor.emit());
                }),
            );
        });

        this.registerCustomBackspaceAction(editor);

        // Re-register actions for this specific editor instance
        for (const action of this.actions) {
            try {
                action.dispose();
                action.register(this.textEditorAdapter, this.translateService);
            } catch (e) {
                if (isDevMode()) {
                    // eslint-disable-next-line no-undef
                    console.error('Failed to register action', action, e);
                }
            }
        }

        // Re-register selection listeners
        for (const entry of this.selectionChangeListeners) {
            entry.disposable?.dispose();
            entry.disposable = this.registerSelectionListenerOnEditor(editor, entry.listener);
        }
    }

    private applyEditorOptions(): void {
        const stickyScrollEnabled = this.stickyScroll();
        const isReadOnly = this.readOnly();
        const renderSideBySide = this.renderSideBySide();

        this._editor.updateOptions({
            stickyScroll: { enabled: stickyScrollEnabled },
            readOnly: isReadOnly,
        });

        if (this._diffEditor) {
            this._diffEditor.updateOptions({
                readOnly: isReadOnly,
                originalEditable: false,
                renderSideBySide,
                scrollbar: {
                    vertical: 'visible',
                    horizontal: 'auto',
                },
            });
        }
    }

    private createContainer(cssClass: string): HTMLElement {
        const el = this.renderer.createElement('div');
        this.renderer.addClass(el, cssClass);
        this.renderer.addClass(el, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
        return el;
    }

    private applyShrinkToFitClass(el: HTMLElement, enabled: boolean): void {
        // TODO: The CSS class below allows the editor to shrink in the CodeEditorContainerComponent. We should eventually remove this class and handle the editor size differently in the code editor grid.
        if (enabled) {
            this.renderer.addClass(el, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
        } else {
            this.renderer.removeClass(el, MonacoEditorComponent.SHRINK_TO_FIT_CLASS);
        }
    }

    layout(): void {
        this.getActiveEditor().layout();
    }

    /**
     * Layouts this editor with a fixed size. Should only be used for testing purposes or when the size
     * of the editor is clear; otherwise, there may be visual glitches!
     * @param width The new width of the editor
     * @param height The new height of the editor.
     */
    layoutWithFixedSize(width: number, height: number): void {
        if (this.diffEditorContainer) {
            this.renderer.setStyle(this.diffEditorContainer, 'width', `${width}px`);
            this.renderer.setStyle(this.diffEditorContainer, 'height', `${height}px`);
        }
        this.getActiveEditor().layout({ width, height });
    }

    applyDiffContent(newContent: string): void {
        if (this.currentMode !== 'diff' || !this._diffEditor) return;

        this.diffChanged.emit({ ready: false, lineChange: { addedLineCount: 0, removedLineCount: 0 } });
        this._diffEditor.getModifiedEditor().setValue(newContent);

        requestAnimationFrame(() => {
            this._diffEditor?.layout();
        });
    }

    revertAll(): void {
        if (this.currentMode !== 'diff' || !this._diffEditor || !this.diffSnapshotModel) return;
        this._diffEditor.getModifiedEditor().setValue(this.diffSnapshotModel.getValue());
    }

    getDiffText(): MonacoEditorDiffText | undefined {
        if (this.currentMode !== 'diff' || !this._diffEditor) {
            return undefined;
        }
        return {
            original: this._diffEditor.getOriginalEditor().getValue(),
            modified: this._diffEditor.getModifiedEditor().getValue(),
        };
    }

    getModifiedEditor(): monaco.editor.IStandaloneCodeEditor | undefined {
        if (this.currentMode !== 'diff' || !this._diffEditor) {
            return undefined;
        }
        return this._diffEditor.getModifiedEditor();
    }

    getActiveEditor(): monaco.editor.IStandaloneCodeEditor {
        return this.currentMode === 'diff' && this._diffEditor ? this._diffEditor.getModifiedEditor() : this._editor;
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
        const active = this.getActiveEditor();
        return active.getContentHeight() + active.getOption(monaco.editor.EditorOption.lineHeight);
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

    isConvertedToEmoji(originalText: string, convertedText: string): boolean {
        return originalText !== convertedText;
    }

    setText(text: string): void {
        const convertedText = this.convertTextToEmoji(text);
        const editor = this.getActiveEditor();

        if (this.isConvertedToEmoji(text, convertedText)) {
            editor.setValue(convertedText);
            this.setPosition({
                column: this.getPosition().column + convertedText.length + text.length,
                lineNumber: this.getPosition().lineNumber,
            });
            return;
        }

        if (editor.getValue() !== convertedText) {
            editor.setValue(convertedText);
        }
    }

    /**
     * Signals to the editor that the specified text was typed.
     * @param text The text to type.
     */
    triggerKeySequence(text: string): void {
        this.getActiveEditor().trigger('MonacoEditorComponent::triggerKeySequence', 'type', { text });
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

    disposeModels(): void {
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

    private registerSelectionListenerOnEditor(editor: monaco.editor.IStandaloneCodeEditor, listener: (selection: EditorRange | null) => void): Disposable {
        return this.ngZone.runOutsideAngular(() => {
            return editor.onDidChangeCursorSelection((e) => {
                const sel = e.selection;
                if (sel.isEmpty()) {
                    this.ngZone.run(() => listener(null));
                } else {
                    this.ngZone.run(() =>
                        listener({
                            startLineNumber: sel.startLineNumber,
                            endLineNumber: sel.endLineNumber,
                            startColumn: sel.startColumn,
                            endColumn: sel.endColumn,
                        }),
                    );
                }
            });
        });
    }

    onSelectionChange(listener: (selection: EditorRange | null) => void): Disposable {
        const disposable = this.registerSelectionListenerOnEditor(this.getActiveEditor(), listener);
        const entry = { listener, disposable };
        this.selectionChangeListeners.push(entry);

        return {
            dispose: () => {
                entry.disposable?.dispose();
                const idx = this.selectionChangeListeners.indexOf(entry);
                if (idx !== -1) {
                    this.selectionChangeListeners.splice(idx, 1);
                }
            },
        };
    }

    getSelection(): EditorRange | null {
        const selection = this.getActiveEditor().getSelection();
        if (!selection || selection.isEmpty()) return null;

        return {
            startLineNumber: selection.startLineNumber,
            endLineNumber: selection.endLineNumber,
            startColumn: selection.startColumn,
            endColumn: selection.endColumn,
        };
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
     * Applies the specified option preset to the editor.
     * @param options The option preset to apply.
     */
    applyOptionPreset(options: MonacoEditorOptionPreset): void {
        options.apply(this.getActiveEditor());
    }

    public getCustomBackspaceCommandId(): string | undefined {
        return this.customBackspaceCommandId;
    }

    private convertMonacoLineChanges(monacoLineChanges: monaco.editor.ILineChange[]): LineChange {
        const lineChange: LineChange = { addedLineCount: 0, removedLineCount: 0 };
        if (!monacoLineChanges) return lineChange;

        for (const change of monacoLineChanges) {
            const added = change.modifiedEndLineNumber >= change.modifiedStartLineNumber ? change.modifiedEndLineNumber - change.modifiedStartLineNumber + 1 : 0;
            const removed = change.originalEndLineNumber >= change.originalStartLineNumber ? change.originalEndLineNumber - change.originalStartLineNumber + 1 : 0;
            lineChange.addedLineCount += added;
            lineChange.removedLineCount += removed;
        }

        return lineChange;
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
                'editorTextFocus && !findWidgetVisible && !editorReadonly',
            ) || undefined;
    }
}
