import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';

@Component({
    selector: 'jhi-markdown-editor-monaco',
    templateUrl: './markdown-editor-monaco.component.html',
    styleUrl: './markdown-editor-monaco.component.scss',
})
export class MarkdownEditorMonacoComponent implements OnInit {
    @ViewChild(MonacoEditorComponent, { static: true }) monacoEditor: MonacoEditorComponent;

    @Input()
    set markdown(value: string | undefined) {
        this._markdown = value;
        this.monacoEditor.setText(value ?? '');
    }

    _markdown?: string;

    @Input()
    minHeightEditor: number = MarkdownEditorHeight.SMALL.valueOf();

    @Input()
    growToFit = false;

    @Output()
    markdownChange = new EventEmitter<string>();

    ngOnInit(): void {
        this.monacoEditor.changeModel('markdown-content.md', this._markdown);
        this.monacoEditor.layout();
    }

    onTextChanged(text: string): void {
        this.markdown = text;
        this.markdownChange.emit(text);
    }
}
