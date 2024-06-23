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
import { faGripLines, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { v4 as uuid } from 'uuid';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { MonacoEditorActionGroup } from 'app/shared/monaco-editor/model/actions/monaco-editor-action-group.model';
import { MonacoHeadingAction } from 'app/shared/monaco-editor/model/actions/monaco-heading.action';
import { MonacoFormulaAction } from 'app/shared/monaco-editor/model/actions/monaco-formula.action';
import { MonacoFullscreenAction } from 'app/shared/monaco-editor/model/actions/monaco-fullscreen.action';
import { MonacoColorAction } from 'app/shared/monaco-editor/model/actions/monaco-color.action';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { CdkDragMove, Point } from '@angular/cdk/drag-drop';
import { MonacoEditorDomainAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action.model';
import { MonacoEditorDomainActionWithOptions } from 'app/shared/monaco-editor/model/actions/monaco-editor-domain-action-with-options.model';

interface MarkdownActionsByGroup {
    standard: MonacoEditorAction[];
    header: MonacoHeadingAction[];
    color?: MonacoColorAction;
    domain: {
        withoutOptions: MonacoEditorDomainAction[];
        withOptions: MonacoEditorDomainActionWithOptions[];
    };
    meta: MonacoEditorAction[];
}

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
    @ViewChild('resizablePlaceholder', { static: false }) resizePlaceholder?: ElementRef<HTMLDivElement>;
    @ViewChild('resizeHandle', { static: false }) resizeHandle?: ElementRef<HTMLDivElement>;
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

    /**
     * The initial height the editor should have. If set to 'external', the editor will try to grow to the available space.
     */
    @Input({ required: true })
    initialEditorHeight: MarkdownEditorHeight | 'external';

    @Input()
    resizableMaxHeight = MarkdownEditorHeight.LARGE;

    @Input()
    resizableMinHeight = MarkdownEditorHeight.SMALL;

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
    domainActions: MonacoEditorDomainAction[] = [];

    @Input()
    metaActions: MonacoEditorAction[] = [new MonacoFullscreenAction()];

    @Output()
    markdownChange = new EventEmitter<string>();

    @Output()
    onPreviewSelect = new EventEmitter();

    inPreviewMode = false;
    uniqueMarkdownEditorId: string;
    faQuestionCircle = faQuestionCircle;
    faGripLines = faGripLines;
    resizeObserver?: ResizeObserver;
    targetWrapperHeight?: number;
    constrainDragPositionFn?: (pointerPosition: Point) => Point;
    isResizing = false;
    displayedActions: MarkdownActionsByGroup = {
        standard: [],
        header: [],
        color: undefined,
        domain: { withoutOptions: [], withOptions: [] },
        meta: [],
    };

    readonly markdownColors = ['#ca2024', '#3ea119', '#ffffff', '#000000', '#fffa5c', '#0d3cc2', '#b05db8', '#d86b1f'];
    readonly markdownColorNames = ['red', 'green', 'white', 'black', 'yellow', 'blue', 'lila', 'orange'];
    readonly colorPickerMarginTop = 35;
    readonly colorPickerHeight = 110;

    constructor(
        private alertService: AlertService,
        private fileUploaderService: FileUploaderService,
    ) {
        this.uniqueMarkdownEditorId = 'markdown-editor-' + uuid();
    }

    ngAfterContentInit(): void {
        // Affects the template - done in this method to avoid ExpressionChangedAfterItHasBeenCheckedErrors.
        this.targetWrapperHeight = this.initialEditorHeight !== 'external' ? this.initialEditorHeight.valueOf() : undefined;
        this.constrainDragPositionFn = this.constrainDragPosition.bind(this);
        this.displayedActions = {
            standard: this.defaultActions,
            header: this.headerActions.actions,
            color: this.colorAction,
            domain: {
                withoutOptions: this.domainActions.filter((action) => !(action instanceof MonacoEditorDomainActionWithOptions)),
                withOptions: <MonacoEditorDomainActionWithOptions[]>this.domainActions.filter((action) => action instanceof MonacoEditorDomainActionWithOptions),
            },
            meta: this.metaActions,
        };
    }

    ngAfterViewInit(): void {
        this.adjustEditorDimensions();
        this.monacoEditor.setWordWrap(true);
        this.monacoEditor.changeModel('markdown-content.custom-md', this._markdown, 'custom-md');
        this.resizeObserver = new ResizeObserver(() => {
            this.adjustEditorDimensions();
        });
        this.resizeObserver.observe(this.wrapper.nativeElement);
        // Prevents the file upload footer from disappearing when switching between preview and editor.
        if (this.fileUploadFooter?.nativeElement) {
            this.resizeObserver.observe(this.fileUploadFooter.nativeElement);
        }
        [this.defaultActions, this.headerActions.actions, this.domainActions, ...(this.colorAction ? [this.colorAction] : []), this.metaActions].flat().forEach((action) => {
            if (action instanceof MonacoFullscreenAction) {
                // Include the entire wrapper to allow using actions in fullscreen mode.
                action.element = this.wrapper.nativeElement;
            }
            this.monacoEditor.registerAction(action);
        });

        if (this.resizeHandle && this.resizePlaceholder && this.enableResize) {
            // The resize handle is positioned absolutely. We move it to its place in the placeholder, from where it can be dragged.
            const resizeHandleHeight = this.getElementClientHeight(this.resizeHandle);
            this.resizePlaceholder.nativeElement.style.height = resizeHandleHeight + 'px';
            this.resizeHandle.nativeElement.style.top =
                (this.resizePlaceholder?.nativeElement?.getBoundingClientRect()?.top ?? 0) - this.wrapper.nativeElement?.getBoundingClientRect()?.top + -resizeHandleHeight + 'px';
        }
    }

    /**
     * Constrains the drag position of the resize handle.
     * @param pointerPosition The current mouse position while dragging.
     */
    constrainDragPosition(pointerPosition: Point): Point {
        // We do not want to drag past the minimum or maximum height.
        // x is not used, so we can ignore it.
        const minY = this.wrapper.nativeElement.getBoundingClientRect().top + this.resizableMinHeight;
        const maxY = this.wrapper.nativeElement.getBoundingClientRect().top + this.resizableMaxHeight;
        return {
            x: pointerPosition.x,
            y: Math.min(maxY, Math.max(minY, pointerPosition.y)),
        };
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }

    onTextChanged(text: string): void {
        this.markdown = text;
        this.markdownChange.emit(text);
    }

    /**
     * Called when the user moves the resize handle.
     * @param event The drag event caused by the user moving the resize handle.
     */
    onResizeMoved(event: CdkDragMove) {
        // The editor's bottom edge becomes the top edge of the handle.
        this.targetWrapperHeight =
            event.source.element.nativeElement.getBoundingClientRect().top -
            this.wrapper.nativeElement.getBoundingClientRect().top +
            this.getElementClientHeight(this.resizePlaceholder);
    }

    /**
     * Computes the height of the editor based on the other elements displayed. We compute this directly to avoid layout issues with the Monaco editor.
     * The height of the editor is the height of the wrapper minus the height of the file upload footer, action palette, and resize placeholder.
     */
    getEditorHeight(): number {
        const wrapperHeight = this.getElementClientHeight(this.wrapper);
        const fileUploadFooterHeight = this.getElementClientHeight(this.fileUploadFooter);
        const actionPaletteHeight = this.getElementClientHeight(this.actionPalette);
        const resizePlaceholderHeight = this.getElementClientHeight(this.resizePlaceholder);
        return wrapperHeight - fileUploadFooterHeight - actionPaletteHeight - resizePlaceholderHeight;
    }

    /**
     * Get the client height of the given element. If no element is provided, 0 is returned.
     * @param element The element to get the client height of.
     */
    getElementClientHeight(element?: ElementRef): number {
        return element?.nativeElement?.clientHeight ?? 0;
    }

    /**
     * Computes the width the editor can take up.
     */
    getEditorWidth(): number {
        return this.wrapper?.nativeElement?.clientWidth ?? 0;
    }

    /**
     * Adjust the dimensions of the editor to fit the available space.
     */
    adjustEditorDimensions(): void {
        this.monacoEditor.layoutWithFixedSize(this.getEditorWidth(), this.getEditorHeight());
    }

    /**
     * Called when the user changes the active tab. If the preview tab is selected, emits the onPreviewSelect event.
     * @param event The event that contains the new active tab.
     */
    onNavChanged(event: NgbNavChangeEvent) {
        this.inPreviewMode = event.nextId === 'editor_preview';
        if (!this.inPreviewMode) {
            this.adjustEditorDimensions();
            this.monacoEditor.focus();
        } else {
            this.onPreviewSelect.emit();
        }
    }

    /**
     * Called when the user selects a file to upload.
     * @param event The event that contains the files to upload (typed as any to avoid compilation errors).
     */
    onFileUpload(event: any): void {
        if (event.target.files.length >= 1) {
            this.embedFiles(Array.from(event.target.files));
        }
    }

    /**
     * Called when the user drops files into the editor.
     * @param event The drag event that contains the files.
     */
    onFileDrop(event: DragEvent): void {
        event.preventDefault();
        if (event.dataTransfer?.files.length) {
            this.embedFiles(Array.from(event.dataTransfer.files));
        }
    }

    /**
     * Embed the given files into the editor by uploading them and inserting the appropriate markdown.
     * For PDFs, a link to the file is inserted. For other files, the file is embedded as an image.
     * @param files
     */
    embedFiles(files: File[]): void {
        files.forEach((file) => {
            this.fileUploaderService.uploadMarkdownFile(file).then(
                (response) => {
                    const extension = file.name.split('.').last()?.toLocaleLowerCase();

                    const attachmentAction: MonacoAttachmentAction | undefined = this.defaultActions.find((action) => action instanceof MonacoAttachmentAction);
                    const urlAction: MonacoUrlAction | undefined = this.defaultActions.find((action) => action instanceof MonacoUrlAction);
                    if (!attachmentAction || !urlAction || !response.path) {
                        throw new Error('Cannot process file upload.');
                    }
                    const payload = { text: file.name, url: response.path };
                    if (extension !== 'pdf') {
                        // Embedded image
                        attachmentAction?.executeInCurrentEditor(payload);
                    } else {
                        // For PDFs, just link to the file
                        urlAction?.executeInCurrentEditor(payload);
                    }
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

    /**
     * Open the color selector at the current cursor position.
     * @param event The mouse event that triggered the color selector.
     */
    openColorSelector(event: MouseEvent): void {
        const marginTop = this.colorPickerMarginTop;
        const height = this.colorPickerHeight;
        this.colorSelector.openColorSelector(event, marginTop, height);
    }

    /**
     * Callback when a color is selected in the color picker.
     * @param color The hex code of the selected color.
     */
    onSelectColor(color: string): void {
        // Map the hex code to the color name.
        const index = this.markdownColors.indexOf(color);
        const colorName = this.markdownColorNames[index];
        if (colorName) {
            this.colorAction?.executeInCurrentEditor({ color: colorName });
        }
    }
}
