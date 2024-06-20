import { AfterContentInit, AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild } from '@angular/core';
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
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { v4 as uuid } from 'uuid';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { MonacoEditorActionGroup } from 'app/shared/monaco-editor/model/actions/monaco-editor-action-group.model';
import { MonacoHeadingAction } from 'app/shared/monaco-editor/model/actions/monaco-heading.action';
import { MonacoFormulaAction } from 'app/shared/monaco-editor/model/actions/monaco-formula.action';
import { MonacoFullscreenAction } from 'app/shared/monaco-editor/model/actions/monaco-fullscreen.action';
import { MonacoColorAction } from 'app/shared/monaco-editor/model/actions/monaco-color.action';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';

// TODO: Once the old markdown editor is gone, remove the style url.
@Component({
    selector: 'jhi-markdown-editor-monaco',
    templateUrl: './markdown-editor-monaco.component.html',
    styleUrls: ['./markdown-editor-monaco.component.scss', '../markdown-editor.component.scss'],
})
export class MarkdownEditorMonacoComponent implements AfterContentInit, AfterViewInit, OnDestroy {
    @ViewChild(MonacoEditorComponent, { static: false }) monacoEditor: MonacoEditorComponent;
    @ViewChild('wrapper', { static: true }) wrapper: ElementRef<HTMLDivElement>;
    @ViewChild('fileUploadFooter', { static: false }) fileUploadFooter?: ElementRef<HTMLDivElement>;
    @ViewChild('actionPalette', { static: false }) actionPalette?: ElementRef<HTMLElement>;
    @ViewChild(ColorSelectorComponent, { static: false }) colorSelector: ColorSelectorComponent;

    @Input()
    set markdown(value: string | undefined) {
        this._markdown = value;
        this.monacoEditor?.setText(value ?? '');
    }

    _markdown?: string;

    @Input()
    enableFileUpload = true;

    @Input()
    enableResize = true;

    @Input()
    initialEditorHeight?: MarkdownEditorHeight;

    @Input()
    defaultActions: MonacoEditorAction[] = [
        new MonacoBoldAction(),
        new MonacoItalicAction(),
        new MonacoUnderlineAction(),
        new MonacoQuoteAction(),
        new MonacoCodeAction(),
        new MonacoCodeBlockAction(),
        new MonacoUrlAction(),
        new MonacoAttachmentAction(),
        new MonacoOrderedListAction(),
        new MonacoUnorderedListAction(),
        new MonacoFormulaAction(),
        new MonacoFullscreenAction(),
    ];

    @Input()
    headerActions: MonacoEditorActionGroup<MonacoHeadingAction> = new MonacoEditorActionGroup<MonacoHeadingAction>(
        'artemisApp.multipleChoiceQuestion.editor.style',
        [1, 2, 3].map((level) => new MonacoHeadingAction(level)),
        undefined,
    );

    @Input()
    colorAction?: MonacoColorAction = new MonacoColorAction();

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
    targetWrapperHeight?: number;

    readonly markdownColors = ['#ca2024', '#3ea119', '#ffffff', '#000000', '#fffa5c', '#0d3cc2', '#b05db8', '#d86b1f'];
    readonly markdownColorNames = ['red', 'green', 'white', 'black', 'yellow', 'blue', 'lila', 'orange'];

    constructor(
        private alertService: AlertService,
        private fileUploaderService: FileUploaderService,
    ) {
        this.uniqueMarkdownEditorId = 'markdown-editor-' + uuid();
    }

    ngAfterContentInit(): void {
        // Setting the desired height is done here to avoid an ExpressionChangedAfterItHasBeenCheckedError.
        this.targetWrapperHeight = this.initialEditorHeight?.valueOf();
    }

    ngAfterViewInit(): void {
        this.adjustEditorDimensions();
        this.monacoEditor.changeModel('markdown-content.custom-md', this._markdown, 'custom-md');
        this.resizeObserver = new ResizeObserver(() => {
            this.adjustEditorDimensions();
        });
        this.resizeObserver.observe(this.wrapper.nativeElement);
        // Fixes a bug where the footer would disappear after switching to the preview tab
        if (this.fileUploadFooter?.nativeElement) {
            this.resizeObserver.observe(this.fileUploadFooter.nativeElement);
        }
        [this.defaultActions, this.headerActions.actions, this.domainActions, ...(this.colorAction ? [this.colorAction] : [])].flat().forEach((action) => {
            if (action.id === MonacoFullscreenAction.ID) {
                (<MonacoFullscreenAction>action).element = this.wrapper.nativeElement;
            }
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

    getEditorHeight(targetHeight?: number): number {
        const wrapperHeight = this.wrapper.nativeElement.clientHeight;
        const fileUploadFooterHeight = this.fileUploadFooter?.nativeElement?.clientHeight ?? 0;
        const actionPaletteHeight = this.actionPalette?.nativeElement?.clientHeight ?? 0;
        return (targetHeight ?? wrapperHeight) - fileUploadFooterHeight - actionPaletteHeight;
    }

    getEditorWidth(): number {
        return this.wrapper?.nativeElement?.clientWidth ?? 0;
    }

    adjustEditorDimensions(t?: number): void {
        this.monacoEditor.layoutWithFixedSize(this.getEditorWidth(), this.getEditorHeight(t));
    }

    onNavChanged(event: NgbNavChangeEvent) {
        this.inPreviewMode = event.nextId === 'editor_preview';
        if (!this.inPreviewMode) {
            this.adjustEditorDimensions();
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

    openColorSelector(event: MouseEvent) {
        const marginTop = 35;
        const height = 110;
        this.colorSelector.openColorSelector(event, marginTop, height);
    }

    onSelectedColor(color: string) {
        // Map the hex code to the color name.
        const index = this.markdownColors.indexOf(color);
        const colorName = this.markdownColorNames[index];
        if (colorName) {
            this.triggerAction('monaco-color.action', colorName);
        }
    }
}
