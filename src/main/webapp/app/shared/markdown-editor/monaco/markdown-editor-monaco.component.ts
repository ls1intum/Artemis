import {
    AfterContentInit,
    AfterViewInit,
    ChangeDetectionStrategy,
    Component,
    ElementRef,
    EventEmitter,
    Injector,
    Input,
    OnDestroy,
    Output,
    Signal,
    ViewChild,
    ViewContainerRef,
    afterNextRender,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
} from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MonacoEditorMode } from 'app/shared/monaco-editor/model/monaco-editor.types';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
import {
    NgbDropdown,
    NgbDropdownMenu,
    NgbDropdownToggle,
    NgbNav,
    NgbNavChangeEvent,
    NgbNavContent,
    NgbNavItem,
    NgbNavLink,
    NgbNavLinkBase,
    NgbNavOutlet,
    NgbTooltip,
} from '@ng-bootstrap/ng-bootstrap';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { BoldAction } from 'app/shared/monaco-editor/model/actions/bold.action';
import { ItalicAction } from 'app/shared/monaco-editor/model/actions/italic.action';
import { UnderlineAction } from 'app/shared/monaco-editor/model/actions/underline.action';
import { QuoteAction } from 'app/shared/monaco-editor/model/actions/quote.action';
import { CodeAction } from 'app/shared/monaco-editor/model/actions/code.action';
import { CodeBlockAction } from 'app/shared/monaco-editor/model/actions/code-block.action';
import { UrlAction } from 'app/shared/monaco-editor/model/actions/url.action';
import { AttachmentAction } from 'app/shared/monaco-editor/model/actions/attachment.action';
import { BulletedListAction } from 'app/shared/monaco-editor/model/actions/bulleted-list.action';
import { StrikethroughAction } from 'app/shared/monaco-editor/model/actions/strikethrough.action';
import { OrderedListAction } from 'app/shared/monaco-editor/model/actions/ordered-list.action';
import { faAngleDown, faGripLines, faQuestionCircle, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { TextEditorActionGroup } from 'app/shared/monaco-editor/model/actions/text-editor-action-group.model';
import { HeadingAction } from 'app/shared/monaco-editor/model/actions/heading.action';
import { FullscreenAction } from 'app/shared/monaco-editor/model/actions/fullscreen.action';
import { ColorAction } from 'app/shared/monaco-editor/model/actions/color.action';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { CdkDrag, CdkDragMove, Point } from '@angular/cdk/drag-drop';
import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditorDomainActionWithOptions } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action-with-options.model';
import { LectureAttachmentReferenceAction, LectureWithDetails } from 'app/shared/monaco-editor/model/actions/communication/lecture-attachment-reference.action';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { PostingEditType, ReferenceType } from 'app/communication/metis.util';
import { MonacoEditorOptionPreset } from 'app/shared/monaco-editor/model/monaco-editor-option-preset.model';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { parseMarkdownForDomainActions } from 'app/shared/markdown-editor/monaco/markdown-editor-parsing.helper';
import { COMMUNICATION_MARKDOWN_EDITOR_OPTIONS, DEFAULT_MARKDOWN_EDITOR_OPTIONS } from 'app/shared/monaco-editor/monaco-editor-option.helper';
import { MetisService } from 'app/communication/service/metis.service';
import { UPLOAD_MARKDOWN_FILE_EXTENSIONS } from 'app/shared/constants/file-extensions.constants';
import { EmojiAction } from 'app/shared/monaco-editor/model/actions/emoji.action';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatButton } from '@angular/material/button';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { faPaperPlane } from '@fortawesome/free-regular-svg-icons';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { RedirectToIrisButtonComponent } from 'app/communication/shared/redirect-to-iris-button/redirect-to-iris-button.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { FileUploadResponse, FileUploaderService } from 'app/shared/service/file-uploader.service';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { addCommentBoxes } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/consistency-check';
import { TranslateService } from '@ngx-translate/core';
import { CommentThread, CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { ReviewCommentWidgetManager } from 'app/exercise/review/review-comment-widget-manager';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { EditorSelectionWithPosition } from 'app/programming/manage/shared/problem-statement.utils';

export enum MarkdownEditorHeight {
    INLINE = 125,
    SMALL = 300,
    MEDIUM = 500,
    LARGE = 1000,
    EXTRA_LARGE = 1500,
}

interface MarkdownActionsByGroup {
    standard: TextEditorAction[];
    header: HeadingAction[];
    color?: ColorAction;
    domain: {
        withoutOptions: TextEditorDomainAction[];
        withOptions: TextEditorDomainActionWithOptions[];
    };
    // Special case due to the complex structure of lectures, attachments, and their slides
    lecture?: LectureAttachmentReferenceAction;
    // AI assistance in the editor
    artemisIntelligence: TextEditorAction[];
    meta: TextEditorAction[];
}

export type TextWithDomainAction = { text: string; action?: TextEditorDomainAction };

/**
 * The offset (in px) that is subtracted from the editor's width to prevent it from obscuring the border.
 * This consists of the width of the borders on each side (1px each) and one extra pixel to prevent the editor
 * from covering the border while shrinking.
 */
const BORDER_WIDTH_OFFSET = 3;
const BORDER_HEIGHT_OFFSET = 2;
const PROBLEM_STATEMENT_FILE_PATH = 'problem_statement.md';
const REVIEW_COMMENT_HOVER_BUTTON_CLASS = 'monaco-add-review-comment-button';

/** Vertical offset (in px) applied below the selection end position for the floating action button. */
const FLOATING_BUTTON_VERTICAL_OFFSET = 5;

@Component({
    selector: 'jhi-markdown-editor-monaco',
    templateUrl: './markdown-editor-monaco.component.html',
    styleUrls: ['./markdown-editor-monaco.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        NgbNav,
        NgbNavItem,
        NgbNavLink,
        NgbNavLinkBase,
        TranslateDirective,
        NgbNavContent,
        NgClass,
        MonacoEditorComponent,
        FaIconComponent,
        NgbTooltip,
        NgTemplateOutlet,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        ColorSelectorComponent,
        PostingButtonComponent,
        MatMenuTrigger,
        MatMenu,
        MatMenuItem,
        NgbNavOutlet,
        CdkDrag,
        MatButton,
        ArtemisTranslatePipe,
        RedirectToIrisButtonComponent,
    ],
})
export class MarkdownEditorMonacoComponent implements AfterContentInit, AfterViewInit, OnDestroy {
    private readonly alertService = inject(AlertService);
    // We inject the MetisService here to avoid a NullInjectorError in the FileUploaderService.
    private readonly metisService = inject(MetisService, { optional: true });
    private readonly fileUploaderService = inject(FileUploaderService);
    private readonly artemisMarkdown = inject(ArtemisMarkdownService);
    private readonly translateService = inject(TranslateService);
    protected readonly artemisIntelligenceService = inject(ArtemisIntelligenceService); // used in template
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly exerciseReviewCommentService = inject(ExerciseReviewCommentService);

    @ViewChild(MonacoEditorComponent, { static: false }) monacoEditor: MonacoEditorComponent;
    @ViewChild('fullElement', { static: true }) fullElement: ElementRef<HTMLDivElement>;
    @ViewChild('wrapper', { static: true }) wrapper: ElementRef<HTMLDivElement>;
    @ViewChild('fileUploadFooter', { static: false }) fileUploadFooter?: ElementRef<HTMLDivElement>;
    @ViewChild('fileUploadInput', { static: false }) fileUploadInput?: ElementRef<HTMLInputElement>;
    @ViewChild('resizePlaceholder', { static: false }) resizePlaceholder?: ElementRef<HTMLDivElement>;
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
    showPreviewButton = true;

    @Input()
    showVisualButton = false;

    @Input()
    showDefaultPreview = true;

    @Input()
    useDefaultMarkdownEditorOptions = true;

    @Input()
    showEditButton = true;

    /**
     * If set to true, the editor will grow and shrink to fit its content. However, the height will still be constrained by {@link resizableMinHeight} and {@link resizableMaxHeight}.
     * In particular, an empty editor will have the height of {@link resizableMinHeight} upon initialization, no matter what value {@link initialEditorHeight} has.
     */
    @Input()
    linkEditorHeightToContentHeight = false;

    /**
     * The initial height the editor should have.
     */
    @Input()
    initialEditorHeight: MarkdownEditorHeight = MarkdownEditorHeight.SMALL;

    /**
     * If true, the editor height is managed externally by the parent container.
     * The editor will try to grow to fill the available space rather than managing its own height.
     * Use this when embedding the editor in a container that controls layout (e.g., code editor view).
     */
    @Input()
    externalHeight = false;

    @Input()
    resizableMaxHeight = MarkdownEditorHeight.LARGE;

    @Input()
    resizableMinHeight = MarkdownEditorHeight.SMALL;

    @Input()
    defaultActions: TextEditorAction[] = [
        new BoldAction(),
        new ItalicAction(),
        new UnderlineAction(),
        new StrikethroughAction(),
        new QuoteAction(),
        new CodeAction(),
        new CodeBlockAction('java'),
        new UrlAction(),
        new AttachmentAction(),
        new OrderedListAction(),
        new BulletedListAction(),
    ];

    @Input()
    headerActions?: TextEditorActionGroup<HeadingAction> = new TextEditorActionGroup<HeadingAction>(
        'artemisApp.multipleChoiceQuestion.editor.style',
        [1, 2, 3].map((level) => new HeadingAction(level)),
        undefined,
    );

    @Input()
    lectureReferenceAction?: LectureAttachmentReferenceAction = undefined;

    @Input()
    colorAction?: ColorAction = new ColorAction();

    @Input()
    domainActions: TextEditorDomainAction[] = [];

    @Input()
    artemisIntelligenceActions: TextEditorAction[] = [];

    @Input()
    metaActions: TextEditorAction[] = [new FullscreenAction()];

    readonly consistencyIssues = input<ConsistencyIssue[]>([]);
    readonly enableExerciseReviewComments = input<boolean>(false);
    readonly showLocationWarning = input<boolean>(false);

    isButtonLoading = input<boolean>(false);
    readonly isAiLoading = input<boolean>(false);
    isFormGroupValid = input<boolean>(false);
    isInCommunication = input<boolean>(false);
    showMarkdownInfoText = input<boolean>(true);
    editType = input<PostingEditType>();
    course = input<Course>();

    readonly useCommunicationForFileUpload = input<boolean>(false);
    readonly fallbackConversationId = input<number>();

    showCloseButton = input<boolean>(false);
    /** Whether the editor is read-only */
    isReadOnly = input<boolean>(false);

    mode = input<MonacoEditorMode>('normal');

    renderSideBySide = input<boolean>(true);

    closeEditor = output<void>();

    /** Emits diff line change information when in diff mode */
    diffLineChange = output<{ ready: boolean; lineChange: LineChange }>();

    @Output()
    markdownChange = new EventEmitter<string>();

    @Output()
    onPreviewSelect = new EventEmitter<void>();

    @Output()
    onEditSelect = new EventEmitter<void>();

    @Output()
    onBlurEditor = new EventEmitter<void>();

    @Output()
    textWithDomainActionsFound = new EventEmitter<TextWithDomainAction[]>();

    @Output()
    onDefaultPreviewHtmlChanged = new EventEmitter<SafeHtml | undefined>();

    @Output()
    onLeaveVisualTab = new EventEmitter<void>();

    readonly onAddReviewComment = output<{ lineNumber: number; fileName: string }>();

    /** Emits when user selects lines in the editor (includes selectedText, position, and column info for inline refinement) */
    readonly onSelectionChange = output<EditorSelectionWithPosition | undefined>();

    defaultPreviewHtml: SafeHtml | undefined;
    inPreviewMode = false;
    inVisualMode = false;
    inEditMode = true;
    uniqueMarkdownEditorId: string;
    resizeObserver?: ResizeObserver;
    /** Disposable for the selection change listener */
    private selectionChangeDisposable?: { dispose: () => void };
    /** Disposable for the scroll change listener used to reposition the inline refinement button */
    private scrollChangeDisposable?: { dispose: () => void };
    /** Cached model selection used to re-compute screen position after scroll */
    private cachedSelection?: { startLineNumber: number; endLineNumber: number; startColumn: number; endColumn: number; selectedText: string };
    targetWrapperHeight?: number;
    minWrapperHeight?: number;
    constrainDragPositionFn?: (pointerPosition: Point) => Point;
    isResizing = false;
    displayedActions: MarkdownActionsByGroup = {
        standard: [],
        header: [],
        color: undefined,
        domain: { withoutOptions: [], withOptions: [] },
        lecture: undefined,
        artemisIntelligence: [],
        meta: [],
    };

    /**
     * Color mapping from hex codes to CSS class names.
     */
    readonly colorToClassMap = new Map<string, string>([
        ['#ca2024', 'red'],
        ['#3ea119', 'green'],
        ['#ffffff', 'white'],
        ['#000000', 'black'],
        ['#fffa5c', 'yellow'],
        ['#0d3cc2', 'blue'],
        ['#b05db8', 'lila'],
        ['#d86b1f', 'orange'],
    ]);

    colorSignal: Signal<string[]> = computed(() => [...this.colorToClassMap.keys()]);
    allowedFileExtensions = signal<string>(UPLOAD_MARKDOWN_FILE_EXTENSIONS.map((ext) => `.${ext}`).join(', ')).asReadonly();

    static readonly TAB_EDIT = 'editor_edit';
    static readonly TAB_PREVIEW = 'editor_preview';
    static readonly TAB_VISUAL = 'editor_visual';
    readonly colorPickerMarginTop = 35;
    readonly colorPickerHeight = 110;
    // Icons
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faSpinner = faSpinner;
    protected readonly faTimes = faTimes;
    protected readonly faGripLines = faGripLines;
    protected readonly faAngleDown = faAngleDown;
    protected readonly facArtemisIntelligence = facArtemisIntelligence;
    protected readonly faPaperPlane = faPaperPlane;
    // Types and values exposed to the template
    protected readonly LectureUnitType = LectureUnitType;
    protected readonly ReferenceType = ReferenceType;
    // We cannot reference these static fields in the template, so we expose them here.
    protected readonly TAB_EDIT = MarkdownEditorMonacoComponent.TAB_EDIT;
    protected readonly TAB_PREVIEW = MarkdownEditorMonacoComponent.TAB_PREVIEW;
    protected readonly TAB_VISUAL = MarkdownEditorMonacoComponent.TAB_VISUAL;

    readonly EditType = PostingEditType;
    private reviewCommentManager?: ReviewCommentWidgetManager;

    constructor() {
        this.uniqueMarkdownEditorId = 'markdown-editor-' + window.crypto.randomUUID().toString();
        const injector = inject(Injector);

        effect(() => {
            this.enableExerciseReviewComments();
            this.exerciseReviewCommentService.threads();
            this.renderEditorWidgets();
            this.updateReviewCommentButton();
        });

        effect(() => {
            this.showLocationWarning();
            const threads = this.exerciseReviewCommentService.threads();
            this.reviewCommentManager?.updateDraftInputs();
            this.reviewCommentManager?.tryUpdateThreadInputs(threads);
        });

        // Adjust editor dimensions when mode changes (e.g. entering/leaving diff mode).
        // Skip the initial run: the editor is already laid out by ngAfterViewInit, and an
        // extra layout() call in tests triggers Monaco's monospace-font-assumption scheduler.
        let lastObservedMode: MonacoEditorMode | undefined;
        effect(() => {
            const currentMode = this.mode();
            if (lastObservedMode !== undefined && lastObservedMode !== currentMode) {
                untracked(() => {
                    afterNextRender(
                        () => {
                            if (this.monacoEditor) {
                                this.adjustEditorDimensions();
                            }
                        },
                        { injector },
                    );
                });
            }
            lastObservedMode = currentMode;
        });
    }

    /**
     * Renders consistency issues inside the editor.
     */
    protected renderEditorWidgets() {
        const issues = this.consistencyIssues();

        // Bail out until the editor is ready
        if (!this.monacoEditor) {
            return;
        }

        // Keep review comment widgets stable while refreshing consistency issue widgets.
        this.monacoEditor.disposeWidgetsByPrefix('comment-');
        addCommentBoxes(this.monacoEditor, issues, PROBLEM_STATEMENT_FILE_PATH, CommentThreadLocationType.PROBLEM_STATEMENT, this.translateService);
        if (this.enableExerciseReviewComments()) {
            // Avoid tracking UI-only signals (e.g. showLocationWarning) as rerender dependencies.
            untracked(() => this.getReviewCommentManager()?.renderWidgets());
        } else {
            this.reviewCommentManager?.disposeAll();
            this.monacoEditor.clearLineDecorationsHoverButton();
        }
    }

    ngAfterContentInit(): void {
        // Affects the template - done in this method to avoid ExpressionChangedAfterItHasBeenCheckedErrors.
        this.targetWrapperHeight = !this.externalHeight ? this.initialEditorHeight.valueOf() : undefined;
        this.minWrapperHeight = this.resizableMinHeight.valueOf();
        this.constrainDragPositionFn = this.constrainDragPosition.bind(this);
        this.displayedActions = {
            standard: this.filterDisplayedActions(this.defaultActions),
            header: this.filterDisplayedActions(this.headerActions?.actions ?? []),
            color: this.filterDisplayedAction(this.colorAction),
            domain: {
                withoutOptions: this.filterDisplayedActions(this.domainActions.filter((action) => !(action instanceof TextEditorDomainActionWithOptions))),
                withOptions: this.filterDisplayedActions(
                    this.domainActions.filter((action) => action instanceof TextEditorDomainActionWithOptions),
                ) as TextEditorDomainActionWithOptions[],
            },
            lecture: this.filterDisplayedAction(this.lectureReferenceAction),
            artemisIntelligence: this.filterDisplayedActions(this.artemisIntelligenceActions ?? []),
            meta: this.filterDisplayedActions(this.metaActions),
        };
    }

    filterDisplayedActions<T extends TextEditorAction>(actions: T[]): T[] {
        return actions.filter((action) => !action.hideInEditor);
    }

    filterDisplayedAction<T extends TextEditorAction>(action?: T): T | undefined {
        return action?.hideInEditor ? undefined : action;
    }

    /**
     * Handles the click event on a text editor action button (e.g., bold, italic, emoji).
     * If the action is of type EmojiAction, it sets the cursor location for emoji placement.
     * Then it executes the action in the currently focused Monaco editor instance.
     *
     * @param event The mouse event triggered by the button click
     * @param action The editor action to be executed
     */
    handleActionClick(event: MouseEvent, action: TextEditorAction): void {
        const x = event.clientX;
        const y = event.clientY;
        if (action instanceof EmojiAction) {
            action.setPoint({ x, y });
        }

        action.executeInCurrentEditor();
    }

    ngAfterViewInit(): void {
        this.adjustEditorDimensions();
        this.monacoEditor.setWordWrap(true);
        this.monacoEditor.changeModel('markdown-content.custom-md', this._markdown ?? '', 'custom-md');
        this.resizeObserver = new ResizeObserver(() => {
            this.adjustEditorDimensions();
        });
        this.resizeObserver.observe(this.wrapper.nativeElement);
        // Prevents the file upload footer from disappearing when switching between preview and editor.
        if (this.fileUploadFooter?.nativeElement) {
            this.resizeObserver.observe(this.fileUploadFooter.nativeElement);
        }
        if (this.actionPalette?.nativeElement) {
            this.resizeObserver.observe(this.actionPalette.nativeElement);
        }
        [
            this.defaultActions,
            this.headerActions?.actions ?? [],
            this.domainActions,
            ...(this.colorAction ? [this.colorAction] : []),
            ...(this.lectureReferenceAction ? [this.lectureReferenceAction] : []),
            ...this.artemisIntelligenceActions,
            this.metaActions,
        ]
            .flat()
            .forEach((action) => {
                if (action instanceof FullscreenAction) {
                    // We include the full element if the initial height is set to 'external' so the editor is resized to fill the screen.
                    action.element = this.isHeightManagedExternally() ? this.fullElement.nativeElement : this.wrapper.nativeElement;
                } else if (this.enableFileUpload && action instanceof AttachmentAction) {
                    action.setUploadCallback(this.embedFiles.bind(this));
                    action.setOpenFileDialogCallback(this.openFilePicker.bind(this));
                }
                this.monacoEditor.registerAction(action);
            });

        if (this.useDefaultMarkdownEditorOptions) {
            this.monacoEditor.applyOptionPreset(DEFAULT_MARKDOWN_EDITOR_OPTIONS);
        }
        // Set up selection change listener for inline comments/refinement
        this.selectionChangeDisposable = this.monacoEditor.onSelectionChange((selection) => {
            if (selection) {
                // Get selected text for inline refinement
                const model = this.monacoEditor.getModel();
                const selectedText = model ? model.getValueInRange(selection) : '';

                // Only emit if there's actual text selected (not just cursor movement)
                if (selectedText.trim().length === 0) {
                    this.cachedSelection = undefined;
                    this.onSelectionChange.emit(undefined);
                    return;
                }

                // Cache the selection so scroll events can re-compute position
                this.cachedSelection = {
                    startLineNumber: selection.startLineNumber,
                    endLineNumber: selection.endLineNumber,
                    startColumn: selection.startColumn,
                    endColumn: selection.endColumn,
                    selectedText,
                };

                this.emitSelectionWithScreenPosition();
            } else {
                this.cachedSelection = undefined;
                this.onSelectionChange.emit(undefined);
            }
        });

        // Subscribe to scroll changes to reposition the inline refinement button
        // when the selection scrolls back into view.
        this.scrollChangeDisposable = this.monacoEditor.getEditor().onDidScrollChange(() => {
            if (this.cachedSelection) {
                this.emitSelectionWithScreenPosition();
            }
        });
    }

    /**
     * Computes the screen position from the cached selection and emits the selection event.
     * If the selection end is scrolled off-screen (coords are null), emits undefined to hide the button.
     */
    private emitSelectionWithScreenPosition(): void {
        if (!this.cachedSelection) {
            return;
        }

        const endPosition = { lineNumber: this.cachedSelection.endLineNumber, column: this.cachedSelection.endColumn };
        const coords = this.monacoEditor.getScrolledVisiblePosition(endPosition);
        const editorDom = this.monacoEditor.getDomNode();

        if (!coords || !editorDom) {
            this.onSelectionChange.emit(undefined);
            return;
        }

        const editorRect = editorDom.getBoundingClientRect();
        const screenPosition = {
            top: editorRect.top + coords.top + coords.height + FLOATING_BUTTON_VERTICAL_OFFSET,
            left: editorRect.left + coords.left,
        };

        this.onSelectionChange.emit({
            startLine: this.cachedSelection.startLineNumber,
            endLine: this.cachedSelection.endLineNumber,
            startColumn: this.cachedSelection.startColumn,
            endColumn: this.cachedSelection.endColumn,
            selectedText: this.cachedSelection.selectedText,
            screenPosition,
        });
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
        this.selectionChangeDisposable?.dispose();
        this.scrollChangeDisposable?.dispose();
        this.reviewCommentManager?.disposeAll();
        this.monacoEditor?.clearLineDecorationsHoverButton();
    }

    onTextChanged(event: { text: string; fileName: string }): void {
        this.markdown = event.text;
        this.markdownChange.emit(event.text);
    }

    /**
     * Called when the user moves the resize handle.
     * @param event The drag event caused by the user moving the resize handle.
     */
    onResizeMoved(event: CdkDragMove) {
        // This prevents the element from escaping its boundaries when being dragged. This is necessary because
        event.source.reset();
        // The editor's bottom edge becomes the top edge of the handle.
        this.targetWrapperHeight = event.pointerPosition.y - this.wrapper.nativeElement.getBoundingClientRect().top - this.getElementClientHeight(this.resizePlaceholder) / 2;
    }

    /**
     * Adjusts the height of the element when the content height changes.
     * @param newContentHeight The new height of the content in the editor.
     */
    onContentHeightChanged(newContentHeight: number | undefined): void {
        // Upon switching back from the preview tab, the file upload footer will briefly have a height of 0. We ignore this case to avoid an incorrect height.
        if (this.linkEditorHeightToContentHeight && !(this.enableFileUpload && this.getElementClientHeight(this.fileUploadFooter) === 0)) {
            const totalHeight = (newContentHeight ?? 0) + this.getElementClientHeight(this.fileUploadFooter) + this.getElementClientHeight(this.actionPalette);
            // Clamp the height so it is between the minimum and maximum height.
            this.targetWrapperHeight = Math.max(this.resizableMinHeight, Math.min(this.resizableMaxHeight, totalHeight));
        }
    }

    /**
     * Computes the height of the editor based on the other elements displayed. We compute this directly to avoid layout issues with the Monaco editor.
     * The height of the editor is the height of the wrapper (or the full element, if the height is external) minus the height of the file upload footer and the action palette.
     */
    getEditorHeight(): number {
        // We always use the wrapper height, as it is correctly sized by the flexbox layout in all cases (external or internal height).
        const elementHeight = this.getElementClientHeight(this.wrapper);
        const fileUploadFooterHeight = this.getElementClientHeight(this.fileUploadFooter);
        const actionPaletteHeight = this.getElementClientHeight(this.actionPalette);
        return Math.max(0, elementHeight - fileUploadFooterHeight - actionPaletteHeight - BORDER_HEIGHT_OFFSET);
    }

    /**
     * Get the client height of the given element. If no element is provided, 0 is returned.
     * @param element The element to get the client height of.
     */
    getElementClientHeight(element?: ElementRef): number {
        return element?.nativeElement?.clientHeight ?? 0;
    }

    /**
     * Computes the width the editor can take up. To prevent the editor from obscuring the border, we subtract a fixed offset.
     */
    getEditorWidth(): number {
        return this.wrapper.nativeElement.clientWidth - BORDER_WIDTH_OFFSET;
    }

    /**
     * Adjust the dimensions of the editor to fit the available space.
     */
    adjustEditorDimensions(): void {
        this.onContentHeightChanged(this.monacoEditor.getContentHeight());
        const editorHeight = this.getEditorHeight();
        this.monacoEditor.layoutWithFixedSize(this.getEditorWidth(), editorHeight);
        // Prevents an issue with line wraps in the editor
        this.monacoEditor.layout();
    }

    /**
     * Called when the user changes the active tab. If the preview tab is selected, emits the onPreviewSelect event.
     * @param event The event that contains the new active tab.
     */
    onNavChanged(event: NgbNavChangeEvent) {
        this.inPreviewMode = event.nextId === this.TAB_PREVIEW;
        this.inVisualMode = event.nextId === this.TAB_VISUAL;
        this.inEditMode = event.nextId === this.TAB_EDIT;
        if (this.inEditMode) {
            this.adjustEditorDimensions();
            this.monacoEditor.focus();
            this.onEditSelect.emit();
        } else if (this.inPreviewMode) {
            this.onPreviewSelect.emit();
        }
        this.updateReviewCommentButton();

        // Some components need to know when the user leaves the visual tab, as it might make changes to the underlying data.
        if (event.activeId === this.TAB_VISUAL) {
            this.onLeaveVisualTab.emit();
        }

        // Parse the markdown when switching away from the edit tab or from visual to preview mode, as the visual mode may make changes to the markdown.
        if (event.activeId === this.TAB_EDIT || (event.activeId === this.TAB_VISUAL && this.inPreviewMode)) {
            this.parseMarkdown();
        }
    }

    onDiffChanged(event: { ready: boolean; lineChange: LineChange }): void {
        this.diffLineChange.emit(event);
    }

    parseMarkdown(domainActionsToCheck: TextEditorDomainAction[] = this.domainActions): void {
        if (this.showDefaultPreview) {
            this.defaultPreviewHtml = this.artemisMarkdown.safeHtmlForMarkdown(this._markdown);
            this.onDefaultPreviewHtmlChanged.emit(this.defaultPreviewHtml);
        }
        if (domainActionsToCheck.length && this._markdown) {
            this.textWithDomainActionsFound.emit(parseMarkdownForDomainActions(this._markdown, domainActionsToCheck));
        }
    }

    /**
     * Called when the user selects a file to upload.
     * @param event The event that contains the files to upload (typed as any to avoid compilation errors).
     */
    onFileUpload(event: any): void {
        if (event.target.files.length >= 1) {
            this.embedFiles(Array.from(event.target.files), event.target);
        }
    }

    /**
     * Opens the file picker dialog to allow the user to select files for upload.
     */
    openFilePicker(): void {
        this.fileUploadInput?.nativeElement.click();
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
     * @param files The files to embed.
     * @param inputElement The input element that contains the files. If provided, the input element will be reset.
     */
    embedFiles(files: File[], inputElement?: HTMLInputElement): void {
        if (!this.enableFileUpload) {
            return;
        }
        files.forEach((file) => {
            (this.useCommunicationForFileUpload()
                ? this.fileUploaderService.uploadMarkdownFileInCurrentMetisConversation(
                      file,
                      this.metisService?.getCourse()?.id,
                      this.metisService?.getCurrentConversation()?.id ?? this.fallbackConversationId(),
                  )
                : this.fileUploaderService.uploadMarkdownFile(file)
            )
                .then(
                    (response) => this.processFileUploadResponse(response, file),
                    (error) => {
                        this.alertService.addAlert({
                            type: AlertType.DANGER,
                            message: error.message,
                            disableTranslation: true,
                        });
                    },
                )
                .then(() => this.resetInputElement(inputElement));
        });
    }

    private processFileUploadResponse(response: FileUploadResponse, file: File): void {
        const extension = file.name.split('.').last()?.toLocaleLowerCase();

        const attachmentAction: AttachmentAction | undefined = this.defaultActions.find((action) => action instanceof AttachmentAction) as AttachmentAction;
        const urlAction: UrlAction | undefined = this.defaultActions.find((action) => action instanceof UrlAction);
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
    }

    private resetInputElement(inputElement?: HTMLInputElement): void {
        if (inputElement) {
            inputElement.value = '';
        }
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
        const colorName = this.colorToClassMap.get(color);
        if (colorName) {
            this.colorAction?.executeInCurrentEditor({ color: colorName });
        }
    }

    /**
     * Check if the editor height is managed externally. This determines if the editor should grow to fill available space, rather than managing its own height.
     * @private
     */
    private isHeightManagedExternally(): boolean {
        return this.externalHeight;
    }

    /**
     * Enable the text field mode of the editor. This makes the editor look and behave like a normal text field.
     */
    enableTextFieldMode(): void {
        this.monacoEditor.applyOptionPreset(COMMUNICATION_MARKDOWN_EDITOR_OPTIONS);
    }

    /**
     * Applies the given option preset to the Monaco editor.
     * @param preset The preset to apply.
     */
    applyOptionPreset(preset: MonacoEditorOptionPreset): void {
        this.monacoEditor.applyOptionPreset(preset);
    }

    /**
     * Emits the closeEditor event when the close button is clicked
     */
    onCloseButtonClick(): void {
        this.closeEditor.emit();
    }

    private updateReviewCommentButton(): void {
        if (!this.enableExerciseReviewComments() && !this.reviewCommentManager) {
            return;
        }
        this.getReviewCommentManager()?.updateHoverButton();
    }

    clearReviewCommentDrafts(): void {
        this.reviewCommentManager?.clearDrafts();
    }

    private getReviewCommentManager(): ReviewCommentWidgetManager | undefined {
        if (!this.monacoEditor) {
            return undefined;
        }
        if (!this.reviewCommentManager) {
            this.reviewCommentManager = new ReviewCommentWidgetManager(this.monacoEditor, this.viewContainerRef, {
                hoverButtonClass: REVIEW_COMMENT_HOVER_BUTTON_CLASS,
                shouldShowHoverButton: () => this.enableExerciseReviewComments() && this.inEditMode,
                canSubmit: () => this.inEditMode && !this.showLocationWarning(),
                getDraftFileName: () => PROBLEM_STATEMENT_FILE_PATH,
                getDraftContext: () => ({
                    targetType: CommentThreadLocationType.PROBLEM_STATEMENT,
                }),
                getThreads: () => this.exerciseReviewCommentService.threads(),
                filterThread: (thread) => this.isProblemStatementThread(thread),
                getThreadLine: (thread) => this.getProblemStatementThreadLine(thread),
                onAdd: (payload) => this.onAddReviewComment.emit(payload),
                showLocationWarning: () => this.showLocationWarning(),
            });
        }
        return this.reviewCommentManager;
    }

    private isProblemStatementThread(thread: CommentThread): boolean {
        return thread.targetType === CommentThreadLocationType.PROBLEM_STATEMENT;
    }

    private getProblemStatementThreadLine(thread: CommentThread): number {
        return (thread.lineNumber ?? thread.initialLineNumber ?? 1) - 1;
    }

    /**
     * Gets the current selection in the editor.
     * @returns The current selection or undefined.
     */
    getSelection(): { startLine: number; endLine: number; startColumn: number; endColumn: number } | undefined {
        if (!this.monacoEditor) {
            return undefined;
        }
        const sel = this.monacoEditor.getSelection();
        if (!sel) {
            return undefined;
        }
        return {
            startLine: sel.startLineNumber,
            endLine: sel.endLineNumber,
            startColumn: sel.startColumn,
            endColumn: sel.endColumn,
        };
    }

    /**
     * Applies new content to the right (modified) side of the diff editor.
     * In live-synced mode, changes sync immediately as the model is shared.
     * @param content The new content to apply.
     */
    applyDiffContent(content: string): void {
        this.monacoEditor?.applyDiffContent(content);
    }

    /**
     * Applies the refined content to the editor in diff mode.
     * Alias for applyDiffContent for semantic clarity in refinement workflows.
     * @param refined The new content to show in the modified editor.
     */
    applyRefinedContent(refined: string): void {
        this.applyDiffContent(refined);
    }

    /**
     * Reverts all changes in the diff editor
     * by restoring the snapshot taken when diff mode was entered.
     */
    revertAll(): void {
        this.monacoEditor?.revertAll();
    }

    /**
     * Checks whether the given lecture has any content/attachments that can explicitly be linked
     * @param lecture The lecture to check
     */
    hasReferencableAttachments(lecture: LectureWithDetails): boolean {
        const hasAttachments = !!lecture.attachments?.length;
        const hasReferencableAttachmentVideoUnits =
            lecture.attachmentVideoUnits?.some((unit) => {
                return unit.attachment && unit.attachment.link;
            }) === true;
        return hasAttachments || hasReferencableAttachmentVideoUnits;
    }
}
