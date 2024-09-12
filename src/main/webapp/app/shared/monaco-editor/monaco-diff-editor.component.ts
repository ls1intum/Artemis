import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, Renderer2, ViewEncapsulation } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { Theme, ThemeService } from 'app/core/theme/theme.service';

import * as monaco from 'monaco-editor';
import { Subscription } from 'rxjs';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';

export type MonacoEditorDiffText = { original: string; modified: string };
@Component({
    selector: 'jhi-monaco-diff-editor',
    template: '',
    styleUrls: ['monaco-diff-editor.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class MonacoDiffEditorComponent implements OnInit, OnDestroy {
    private _editor: monaco.editor.IStandaloneDiffEditor;
    monacoDiffEditorContainerElement: HTMLElement;
    themeSubscription?: Subscription;
    listeners: Disposable[] = [];
    resizeObserver?: ResizeObserver;

    @Input()
    set allowSplitView(value: boolean) {
        this._editor.updateOptions({
            renderSideBySide: value,
        });
    }

    @Output()
    onReadyForDisplayChange = new EventEmitter<boolean>();

    constructor(
        private themeService: ThemeService,
        elementRef: ElementRef,
        renderer: Renderer2,
    ) {
        /*
         * The constructor injects the editor along with its container into the empty template of this component.
         * This makes the editor available immediately (not just after ngOnInit), preventing errors when the methods
         * of this component are called.
         */
        this.monacoDiffEditorContainerElement = renderer.createElement('div');
        this._editor = monaco.editor.createDiffEditor(this.monacoDiffEditorContainerElement, {
            glyphMargin: true,
            minimap: { enabled: false },
            readOnly: true,
            renderSideBySide: true,
            scrollBeyondLastLine: false,
            stickyScroll: {
                enabled: false,
            },
            renderOverviewRuler: false,
            scrollbar: {
                vertical: 'hidden',
                handleMouseWheel: true,
                alwaysConsumeMouseWheel: false,
            },
            hideUnchangedRegions: {
                enabled: true,
            },
            fontSize: 12,
        });
        renderer.appendChild(elementRef.nativeElement, this.monacoDiffEditorContainerElement);
        this.setupDiffListener();
        this.setupContentHeightListeners();
        this.themeSubscription = toObservable(this.themeService.currentTheme).subscribe((theme) => this.changeTheme(theme));
    }

    ngOnInit(): void {
        this.resizeObserver = new ResizeObserver(() => {
            this.layout();
        });
        this.resizeObserver.observe(this.monacoDiffEditorContainerElement);
    }

    ngOnDestroy(): void {
        this.themeSubscription?.unsubscribe();
        this.resizeObserver?.disconnect();
        this.listeners.forEach((listener) => {
            listener.dispose();
        });
        this._editor.dispose();
    }

    /**
     * Sets up a listener that responds to changes in the diff. It will signal via {@link onReadyForDisplayChange} that
     * the component is ready to display the diff.
     */
    setupDiffListener(): void {
        const diffListener = this._editor.onDidUpdateDiff(() => {
            this.adjustHeightAndLayout(this.getMaximumContentHeight());
            this.onReadyForDisplayChange.emit(true);
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
                    this.adjustHeightAndLayout(this.getMaximumContentHeight());
                }
            });

            // Called when the user reveals or collapses a hidden region.
            const hiddenAreaListener = editor.onDidChangeHiddenAreas(() => {
                this.adjustHeightAndLayout(this.getContentHeightOfEditor(editor));
            });

            this.listeners.push(contentSizeListener, hiddenAreaListener);
        });
    }

    /**
     * Adjusts the height of the editor to fit the new content height.
     * @param newContentHeight The new content height of the editor.
     */
    adjustHeightAndLayout(newContentHeight: number) {
        this.monacoDiffEditorContainerElement.style.height = newContentHeight + 'px';
        this.layout();
    }

    /**
     * Adjusts this editor to fit its container.
     */
    layout(): void {
        const width = this.monacoDiffEditorContainerElement.clientWidth;
        const height = this.monacoDiffEditorContainerElement.clientHeight;
        this._editor.layout({ width, height });
    }

    /**
     * Sets the theme of all Monaco editors according to the Artemis theme.
     * As of now, it is not possible to have two editors with different themes.
     * @param artemisTheme The active Artemis theme.
     */
    changeTheme(artemisTheme: Theme): void {
        monaco.editor.setTheme(artemisTheme === Theme.DARK ? 'vs-dark' : 'vs-light');
    }

    /**
     * Updates the files displayed in this editor. When this happens, {@link onReadyForDisplayChange} will signal that the editor is not
     * ready to display the diff (as it must be computed first). This will later be change by the appropriate listener.
     * @param original The content of the original file, if available.
     * @param originalFileName The name of the original file, if available. The name is used to determine the syntax highlighting of the left editor.
     * @param modified The content of the modified file, if available.
     * @param modifiedFileName The name of the modified file, if available. The name is used to determine the syntax highlighting of the right editor.
     */
    setFileContents(original?: string, originalFileName?: string, modified?: string, modifiedFileName?: string): void {
        this.onReadyForDisplayChange.emit(false);
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
