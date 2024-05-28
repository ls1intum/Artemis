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
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { v4 as uuid } from 'uuid';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { MonacoEditorActionGroup } from 'app/shared/monaco-editor/model/actions/monaco-editor-action-group.model';
import { MonacoHeadingAction } from 'app/shared/monaco-editor/model/actions/monaco-heading.action';
import { MonacoFormulaAction } from 'app/shared/monaco-editor/model/actions/monaco-formula.action';
import { MonacoFullscreenAction } from 'app/shared/monaco-editor/model/actions/monaco-fullscreen.action';

// TODO: Once the old markdown editor is gone, remove the style url.
@Component({
    selector: 'jhi-markdown-editor-monaco',
    templateUrl: './markdown-editor-monaco.component.html',
    styleUrls: ['./markdown-editor-monaco.component.scss', '../markdown-editor.component.scss'],
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
    enableFileUpload = true;

    @Input()
    growToFit = false;

    // TODO: Register these actions with the editor!
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
        new MonacoFormulaAction('Formula', 'artemisApp.markdownEditor.commands.katex'),
        new MonacoFullscreenAction('Fullscreen', 'todo'),
    ];

    @Input()
    headerActions: MonacoEditorActionGroup<MonacoHeadingAction> = new MonacoEditorActionGroup<MonacoHeadingAction>(
        'artemisApp.multipleChoiceQuestion.editor.style',
        [1, 2, 3].map((level) => new MonacoHeadingAction(`Heading ${level}`, `todo`, level)),
        undefined,
    );

    @Input()
    domainActions: MonacoEditorAction[] = [];

    @Output()
    markdownChange = new EventEmitter<string>();

    @Output()
    onPreviewSelect = new EventEmitter();

    inPreviewMode = false;
    uniqueMarkdownEditorId: string;
    faQuestionCircle = faQuestionCircle;
    resizeObserver?: ResizeObserver;

    constructor(
        private alertService: AlertService,
        private fileUploaderService: FileUploaderService,
    ) {
        this.uniqueMarkdownEditorId = 'markdown-editor-' + uuid();
    }

    ngAfterViewInit(): void {
        this.monacoEditor.changeModel('markdown-content.custom-md', this._markdown, 'custom-md');
        this.monacoEditor.layoutWithFixedSize(100, 400);
        this.resizeObserver = new ResizeObserver(() => {
            this.monacoEditor.layout();
        });
        this.resizeObserver.observe(this.wrapper.nativeElement);
        [this.defaultActions, this.headerActions.actions, this.domainActions].flat().forEach((action) => {
            this.monacoEditor.registerAction(action);
        });
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

    triggerAction(id: string, args?: unknown): void {
        this.monacoEditor.triggerAction(id, args);
    }

    onFileUpload(event: any): void {
        if (event.target.files.length >= 1) {
            this.embedFiles(Array.from(event.target.files));
        }
    }

    onFileDrop(event: DragEvent): void {
        event.preventDefault();
        if (event.dataTransfer?.files.length) {
            this.embedFiles(Array.from(event.dataTransfer.files));
        }
    }

    embedFiles(files: File[]): void {
        files.forEach((file) => {
            this.fileUploaderService.uploadMarkdownFile(file).then(
                (response) => {
                    const extension = file.name.split('.').last()?.toLocaleLowerCase();

                    let actionData: { id: string; payload: unknown };
                    if (extension !== 'pdf') {
                        // Mode: embedded image
                        actionData = { id: 'monaco-attachment.action', payload: { text: file.name, url: response.path } };
                    } else {
                        // For PDFs, just link to the file
                        actionData = { id: 'monaco-url.action', payload: { text: file.name, url: response.path } };
                    }
                    this.triggerAction(actionData.id, actionData.payload);
                },
                (error) => {
                    this.alertService.addAlert({
                        type: AlertType.DANGER,
                        message: error.message,
                        disableTranslation: true,
                    });
                },
            );
        });
    }

    protected readonly MultiOptionCommand = MultiOptionCommand;
}
