import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, Renderer2, ViewEncapsulation, effect, inject, input, output } from '@angular/core';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
import { MonacoEditorService } from 'app/shared/monaco-editor/service/monaco-editor.service';
import * as monaco from 'monaco-editor';

export type MonacoEditorDiffText = { original: string; modified: string };

@Component({
    selector: 'jhi-monaco-diff-editor',
    template: '',
    styleUrls: ['monaco-diff-editor.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
})
export class MonacoDiffEditorComponent implements OnDestroy {
    private _editor: monaco.editor.IStandaloneDiffEditor;
    monacoDiffEditorContainerElement: HTMLElement;

    allowSplitView = input<boolean>(true);
    onReadyForDisplayChange = output<{ ready: boolean; lineChange: LineChange }>();

    /*
     * Subscriptions and listeners that need to be disposed of when this component is destroyed.
     */
    listeners: Disposable[] = [];

    /*
     * Injected services and elements.
     */
    private readonly elementRef = inject(ElementRef);
    private readonly renderer = inject(Renderer2);
    private readonly monacoEditorService = inject(MonacoEditorService);

    constructor() {
        /*
         * The constructor injects the editor along with its container into the empty template of this component.
         * This makes the editor available immediately (not just after ngOnInit), preventing errors when the methods
         * of this component are called.
         */
        this.monacoDiffEditorContainerElement = this.renderer.createElement('div');
        this._editor = this.monacoEditorService.createStandaloneDiffEditor(this.monacoDiffEditorContainerElement);
        this.renderer.appendChild(this.elementRef.nativeElement, this.monacoDiffEditorContainerElement);
        this.renderer.addClass(this.monacoDiffEditorContainerElement, 'diff-editor-container');
        this.setupDiffListener();
        this.setupContentHeightListeners();

        effect(() => {
            this._editor.updateOptions({
                renderSideBySide: this.allowSplitView(),
            });
        });
    }

    ngOnDestroy(): void {
        this.listeners.forEach((listener) => {
            listener.dispose();
        });
        this._editor.dispose();
    }

    /**
     * Sets up a listener that responds to changes in the diff. It will signal via {@link onReadyForDisplayChange} that
     * the component is ready to display the diff, including the current line changes.
     */
    setupDiffListener(): void {
        const diffListener = this._editor.onDidUpdateDiff(() => {
            this.adjustContainerHeight(this.getMaximumContentHeight());

            // Get line changes when diff is updated
            const monacoLineChanges = this._editor.getLineChanges() ?? [];

            // Signal that the diff is ready for display with line changes summary
            this.onReadyForDisplayChange.emit({ ready: true, lineChange: this.convertMonacoLineChanges(monacoLineChanges) });
        });

        this.listeners.push(diffListener);
    }

    /**
     * Sets up listeners that adjust the height of the editor to the height of its current content.
     */
    setupContentHeightListeners(): void {
        const editors = [this._editor.getOriginalEditor(), this._editor.getModifiedEditor()];

        editors.forEach((editor) => {
            // Called e.g. when the content of the editor changes.
            const contentSizeListener = editor.onDidContentSizeChange((e: monaco.editor.IContentSizeChangedEvent) => {
                if (e.contentHeightChanged) {
                    // Using the content height of the larger editor here ensures that neither of the editors break out of the container.
                    this.adjustContainerHeight(this.getMaximumContentHeight());
                }
            });

            // Called when the user reveals or collapses a hidden region.
            const hiddenAreaListener = editor.onDidChangeHiddenAreas(() => {
                this.adjustContainerHeight(this.getContentHeightOfEditor(editor));
            });

            this.listeners.push(contentSizeListener, hiddenAreaListener);
        });
    }

    /**
     * Adjusts the height of the editor's container to fit the new content height.
     * @param newContentHeight The new content height of the editor.
     */
    adjustContainerHeight(newContentHeight: number) {
        this.monacoDiffEditorContainerElement.style.height = newContentHeight + 'px';
    }

    /**
     * Updates the files displayed in this editor. When this happens, {@link onReadyForDisplayChange} will signal that the editor is not
     * ready to display the diff (as it must be computed first). This will later be change by the appropriate listener.
     * @param original The content of the original file, if available.
     * @param modified The content of the modified file, if available.
     * @param originalFileName The name of the original file, if available.
     * @param modifiedFileName The name of the modified file, if available.
     */
    setFileContents(original?: string, modified?: string, originalFileName?: string, modifiedFileName?: string): void {
        // Reset ready state and clear line changes when loading new content
        this.onReadyForDisplayChange.emit({ ready: false, lineChange: { addedLineCount: 0, removedLineCount: 0 } });

        const originalModelUri = monaco.Uri.parse(`inmemory://model/original-${this._editor.getId()}/${originalFileName ?? 'left'}`);
        const modifiedFileUri = monaco.Uri.parse(`inmemory://model/modified-${this._editor.getId()}/${modifiedFileName ?? 'right'}`);
        const originalModel = monaco.editor.getModel(originalModelUri) ?? monaco.editor.createModel(original ?? '', undefined, originalModelUri);
        const modifiedModel = monaco.editor.getModel(modifiedFileUri) ?? monaco.editor.createModel(modified ?? '', undefined, modifiedFileUri);

        originalModel.setValue(original ?? '');
        modifiedModel.setValue(modified ?? '');

        monaco.editor.setModelLanguage(originalModel, originalModel.getLanguageId());
        monaco.editor.setModelLanguage(modifiedModel, modifiedModel.getLanguageId());

        const newModel = {
            original: originalModel,
            modified: modifiedModel,
        };

        this._editor.setModel(newModel);
    }

    /**
     * Converts Monaco line changes to a LineChange object
     * @param monacoLineChanges The Monaco line changes to convert
     * @returns The converted LineChange object
     */
    convertMonacoLineChanges(monacoLineChanges: monaco.editor.ILineChange[]): LineChange {
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
     * Returns the content height of the larger of the two editors in this view.
     */
    getMaximumContentHeight(): number {
        return Math.max(this.getContentHeightOfEditor(this._editor.getOriginalEditor()), this.getContentHeightOfEditor(this._editor.getModifiedEditor()));
    }

    /**
     * Returns the content height of the provided editor.
     * @param editor The editor whose content height should be retrieved.
     */
    getContentHeightOfEditor(editor: monaco.editor.IStandaloneCodeEditor): number {
        return editor.getContentHeight();
    }

    /**
     * Returns the text (original and modified) currently stored in the editor.
     */
    getText(): MonacoEditorDiffText {
        const original = this._editor.getOriginalEditor().getValue();
        const modified = this._editor.getModifiedEditor().getValue();
        return { original, modified };
    }
}
