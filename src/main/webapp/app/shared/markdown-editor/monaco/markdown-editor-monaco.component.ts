import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { NgbNavChangeEvent } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-markdown-editor-monaco',
    templateUrl: './markdown-editor-monaco.component.html',
    styleUrl: './markdown-editor-monaco.component.scss',
})
export class MarkdownEditorMonacoComponent implements AfterViewInit, OnDestroy {
    @ViewChild(MonacoEditorComponent, { static: false }) monacoEditor: MonacoEditorComponent;
    @ViewChild('wrapper', { static: true }) wrapper: ElementRef<HTMLDivElement>;

    @Input()
    set markdown(value: string | undefined) {
        this._markdown = value;
        this.monacoEditor?.setText(value ?? '');
    }

    _markdown?: string;

    @Input()
    minHeightEditor: number = MarkdownEditorHeight.SMALL.valueOf();

    @Input()
    growToFit = false;

    @Output()
    markdownChange = new EventEmitter<string>();

    @Output()
    onPreviewSelect = new EventEmitter();

    inPreviewMode = false;
    resizeObserver?: ResizeObserver;

    ngAfterViewInit(): void {
        this.monacoEditor.changeModel('markdown-content.custom-md', this._markdown, 'custom-md');
        this.monacoEditor.layoutWithFixedSize(100, 400);
        this.resizeObserver = new ResizeObserver(() => {
            this.monacoEditor.layout();
        });
        this.resizeObserver.observe(this.wrapper.nativeElement);
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }

    onTextChanged(text: string): void {
        this.markdown = text;
        this.markdownChange.emit(text);
    }

    onNavChanged(event: NgbNavChangeEvent) {
        this.inPreviewMode = event.nextId === 'editor_preview';
        if (!this.inPreviewMode) {
            this.monacoEditor.layoutWithFixedSize(100, 400);
            this.monacoEditor.focus();
        } else {
            this.onPreviewSelect.emit();
        }
    }
}
