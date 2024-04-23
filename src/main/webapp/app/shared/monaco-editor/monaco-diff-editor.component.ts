import { Component, ElementRef, OnDestroy, OnInit, Renderer2, ViewEncapsulation } from '@angular/core';
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
}
