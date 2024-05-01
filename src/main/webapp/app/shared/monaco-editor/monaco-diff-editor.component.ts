import { Component, ElementRef, EventEmitter, OnDestroy, OnInit, Output, Renderer2, ViewEncapsulation } from '@angular/core';
import { Theme, ThemeService } from 'app/core/theme/theme.service';

import * as monaco from 'monaco-editor';
import { Subscription } from 'rxjs';

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
    listeners: monaco.IDisposable[] = [];
    resizeObserver?: ResizeObserver;

    private original: string | undefined;
    private modified: string | undefined;

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
        renderer.addClass(this.monacoDiffEditorContainerElement, 'monaco-diff-editor-container');
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
            fontSize: 12,
        });
        renderer.appendChild(elementRef.nativeElement, this.monacoDiffEditorContainerElement);

        this._editor.onDidChangeModel(() => {
            this.adjustHeightAndLayout(this.getMaximumContentHeight());
            if (this.original === undefined) {
                this.replaceEditorWithPlaceholder('create', this._editor.getOriginalEditor());
            }
            if (this.modified === undefined) {
                this.replaceEditorWithPlaceholder('delete', this._editor.getModifiedEditor());
            }
        });

        this._editor.onDidUpdateDiff(() => {
            // called when the editor is truly ready
            this.monacoDiffEditorContainerElement.style.height = this.getMaximumContentHeight() + 'px';
            this.onReadyForDisplayChange.emit(true);
        });
        this.setupContentHeightListeners();
    }

    ngOnInit(): void {
        this.resizeObserver = new ResizeObserver(() => {
            this.layout();
        });
        this.resizeObserver.observe(this.monacoDiffEditorContainerElement);
        this.themeSubscription = this.themeService.getCurrentThemeObservable().subscribe((theme) => this.changeTheme(theme));
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
     * @param newContentHeight
     */
    adjustHeightAndLayout(newContentHeight: number) {
        this.monacoDiffEditorContainerElement.style.height = newContentHeight + 'px';
        this.layout();
    }

    /**
     * Adjusts this editor to fit its container.
     */
    layout(): void {
        this._editor.layout();
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
     * Sets the theme of all Monaco editors according to the Artemis theme.
     * As of now, it is not possible to have two editors with different themes.
     * @param artemisTheme The active Artemis theme.
     */
    changeTheme(artemisTheme: Theme): void {
        monaco.editor.setTheme(artemisTheme === Theme.DARK ? 'vs-dark' : 'vs-light');
    }

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

        this.original = original;
        this.modified = modified;
        const newModel = {
            original: originalModel,
            modified: modifiedModel,
        };

        this._editor.setModel(newModel);
    }

    setUnchangedRegionHidingOptions(enabled: boolean, revealLineCount = 5, contextLineCount = 3, minimumLineCount = 5): void {
        this._editor.updateOptions({
            hideUnchangedRegions: {
                enabled,
                revealLineCount,
                contextLineCount,
                minimumLineCount,
            },
        });
    }

    private replaceEditorWithPlaceholder(action: 'create' | 'delete', editorToReplace: monaco.editor.IStandaloneCodeEditor): void {
        // TODO remove this hack
        const container: HTMLElement = editorToReplace.getContainerDomNode();
        const placeholder = document.createElement('div');
        placeholder.innerHTML = action === 'create' ? 'This file was created.' : 'This file was deleted.';
        placeholder.style.position = 'absolute';
        placeholder.style.top = '50%';
        placeholder.style.left = '50%';
        placeholder.style.transform = 'translate(-50%, -50%)';
        container.style.backgroundColor = 'rgb(30,30,30)';
        container.children[0]['hidden'] = true;
        container.appendChild(placeholder);
    }

    /**
     * Returns the content height of the larger of the two editors in this view.
     * @private
     */
    getMaximumContentHeight(): number {
        return Math.max(this.getContentHeightOfEditor(this._editor.getOriginalEditor()), this.getContentHeightOfEditor(this._editor.getModifiedEditor()));
    }

    /**
     * Returns the content height of the provided editor.
     * @param editor The editor whose content height should be retrieved.
     * @private
     */
    private getContentHeightOfEditor(editor: monaco.editor.ICodeEditor): number {
        return editor.getContentHeight();
    }
}
