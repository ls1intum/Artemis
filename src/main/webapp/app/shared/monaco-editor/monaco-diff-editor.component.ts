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
    private monacoDiffEditorContainerElement: HTMLElement;
    private themeSubscription: Subscription;

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

        // called once diff has been computed.
        // TODO explain
        this._editor.onDidChangeModel(() => {
            this.monacoDiffEditorContainerElement.style.height = this.getContentHeight() + 'px';
            this._editor.layout();
            if (this.original === undefined) {
                this.replaceEditorWithPlaceholder('create', this._editor.getOriginalEditor());
            }
            if (this.modified === undefined) {
                this.replaceEditorWithPlaceholder('delete', this._editor.getModifiedEditor());
            }
            this.onReadyForDisplayChange.emit(true);
        });
    }

    ngOnInit(): void {
        const resizeObserver = new ResizeObserver(() => {
            this._editor.layout();
        });
        resizeObserver.observe(this.monacoDiffEditorContainerElement);
        this.themeSubscription = this.themeService.getCurrentThemeObservable().subscribe((theme) => this.changeTheme(theme));
    }

    ngOnDestroy(): void {
        this.themeSubscription?.unsubscribe();
        this._editor.dispose();
    }

    changeTheme(artemisTheme: Theme): void {
        // TODO explain
        monaco.editor.setTheme(artemisTheme === Theme.DARK ? 'vs-dark' : 'vs-light');
    }

    setFileContents(original?: string, modified?: string): void {
        this.onReadyForDisplayChange.emit(false);
        this.original = original;
        this.modified = modified;
        const newModel = {
            original: monaco.editor.createModel(original ?? '', 'java'),
            modified: monaco.editor.createModel(modified ?? '', 'java'),
        };

        this._editor.setModel(newModel);
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

    private getContentHeight(): number {
        return Math.max(this._editor.getOriginalEditor().getContentHeight(), this._editor.getModifiedEditor().getContentHeight());
    }
}
