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

    @Input()
    initialEditorHeight?: MarkdownEditorHeight;

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

    constructor(
        private alertService: AlertService,
        private fileUploaderService: FileUploaderService,
    ) {
        this.uniqueMarkdownEditorId = 'markdown-editor-' + uuid();
    }

    ngAfterContentInit(): void {
        // Affects the template - done in this method to avoid ExpressionChangedAfterItHasBeenCheckedErrors.
        this.targetWrapperHeight = this.initialEditorHeight?.valueOf();
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
        this.monacoEditor.changeModel('markdown-content.custom-md', this._markdown, 'custom-md');
        this.resizeObserver = new ResizeObserver(() => {
            this.adjustEditorDimensions();
        });
        this.resizeObserver.observe(this.wrapper.nativeElement);
        // Fixes a bug where the footer would disappear after switching to the preview tab
        if (this.fileUploadFooter?.nativeElement) {
            this.resizeObserver.observe(this.fileUploadFooter.nativeElement);
        }
        [this.defaultActions, this.headerActions.actions, this.domainActions, ...(this.colorAction ? [this.colorAction] : []), this.metaActions].flat().forEach((action) => {
            if (action instanceof MonacoFullscreenAction) {
                action.element = this.wrapper.nativeElement;
            }
            this.monacoEditor.registerAction(action);
        });

        if (this.resizeHandle && this.resizePlaceholder && this.enableResize) {
            const resizeHandleHeight = this.getElementClientHeight(this.resizeHandle);
            this.resizePlaceholder.nativeElement.style.height = resizeHandleHeight + 'px';
            this.resizeHandle.nativeElement.style.top =
                (this.resizePlaceholder?.nativeElement?.getBoundingClientRect()?.top ?? 0) - this.wrapper.nativeElement?.getBoundingClientRect()?.top + -resizeHandleHeight + 'px';
        }
    }

    /**
     * Constrain the drag position of the resize handle.
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

    onResizeMoved(event: CdkDragMove) {
        // The editor's bottom edge becomes the top edge of the handle.
        this.targetWrapperHeight =
            event.source.element.nativeElement.getBoundingClientRect().top -
            this.wrapper.nativeElement.getBoundingClientRect().top +
            this.getElementClientHeight(this.resizePlaceholder);
    }

    getEditorHeight(targetHeight?: number): number {
        const wrapperHeight = this.getElementClientHeight(this.wrapper);
        const fileUploadFooterHeight = this.getElementClientHeight(this.fileUploadFooter);
        const actionPaletteHeight = this.getElementClientHeight(this.actionPalette);
        const resizePlaceholderHeight = this.getElementClientHeight(this.resizePlaceholder);
        return (targetHeight ?? wrapperHeight) - fileUploadFooterHeight - actionPaletteHeight - resizePlaceholderHeight;
    }

    getElementClientHeight(element?: ElementRef): number {
        return element?.nativeElement?.clientHeight ?? 0;
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
                        actionData = { id: MonacoAttachmentAction.ID, payload: { text: file.name, url: response.path } };
                    } else {
                        // For PDFs, just link to the file
                        actionData = { id: MonacoUrlAction.ID, payload: { text: file.name, url: response.path } };
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
            this.triggerAction(MonacoColorAction.ID, colorName);
        }
    }
}
