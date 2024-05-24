import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { NgbNavChangeEvent } from '@ng-bootstrap/ng-bootstrap';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { MonacoBoldAction } from 'app/shared/monaco-editor/model/actions/monaco-bold.action';
import { MonacoItalicAction } from 'app/shared/monaco-editor/model/actions/monaco-italic.action';
import { MonacoUnderlineAction } from 'app/shared/monaco-editor/model/actions/monaco-underline.action';
import { MonacoQuoteAction } from 'app/shared/monaco-editor/model/actions/monaco-quote.action';
import { MonacoCodeAction } from 'app/shared/monaco-editor/model/actions/monaco-code.action';
import { MonacoCodeBlockAction } from 'app/shared/monaco-editor/model/actions/monaco-code-block.action';
import { MonacoUrlAction } from 'app/shared/monaco-editor/model/actions/monaco-url.action';
import { MonacoAttachmentAction } from 'app/shared/monaco-editor/model/actions/monaco-attachment.action';
import { MonacoUnorderedListAction } from 'app/shared/monaco-editor/model/actions/monaco-unordered-list.action';
import { MonacoOrderedListAction } from 'app/shared/monaco-editor/model/actions/monaco-ordered-list.action';
import { MultiOptionCommand } from 'app/shared/markdown-editor/commands/multiOptionCommand';

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

    @Input()
    defaultActions: MonacoEditorAction[] = [
        new MonacoBoldAction('Bold', 'todo'),
        new MonacoItalicAction('Italic', 'todo'),
        new MonacoUnderlineAction('Underline', 'todo'),
        new MonacoQuoteAction('Quote', 'todo'),
        new MonacoCodeAction('Code', 'todo'),
        new MonacoCodeBlockAction('Code block', 'todo'),
        new MonacoUrlAction('URL', 'todo'),
        new MonacoAttachmentAction('Attachment', 'todo'),
        new MonacoOrderedListAction('Ordered list', 'todo'),
        new MonacoUnorderedListAction('Unordered list', 'todo'),
    ];

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

    triggerCommand(id: string): void {
        this.monacoEditor.triggerCommand(id);
    }

    protected readonly MultiOptionCommand = MultiOptionCommand;
}
