import {
    AfterContentInit,
    AfterViewInit,
    ChangeDetectionStrategy,
    Component,
    ElementRef,
    Injector,
    OnDestroy,
    Signal,
    ViewContainerRef,
    afterNextRender,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
    viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { MonacoEditorMode } from 'app/editor/monaco-editor/model/monaco-editor.types';
import { EditorRange } from 'app/editor/monaco-editor/model/actions/monaco-editor.util';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
import { Tab, TabList, Tabs } from 'primeng/tabs';
import { Popover } from 'primeng/popover';
import { TieredMenu } from 'primeng/tieredmenu';
import { Tooltip } from 'primeng/tooltip';
import { MenuItem } from 'primeng/api';
import { TextEditorAction, TextStyleTextEditorAction } from 'app/editor/monaco-editor/model/actions/text-editor-action.model';
import { BoldAction } from 'app/editor/monaco-editor/model/actions/bold.action';
import { ItalicAction } from 'app/editor/monaco-editor/model/actions/italic.action';
import { UnderlineAction } from 'app/editor/monaco-editor/model/actions/underline.action';
import { QuoteAction } from 'app/editor/monaco-editor/model/actions/quote.action';
import { CodeAction } from 'app/editor/monaco-editor/model/actions/code.action';
import { CodeBlockAction } from 'app/editor/monaco-editor/model/actions/code-block.action';
import { UrlAction } from 'app/editor/monaco-editor/model/actions/url.action';
import { AttachmentAction } from 'app/editor/monaco-editor/model/actions/attachment.action';
import { BulletedListAction } from 'app/editor/monaco-editor/model/actions/bulleted-list.action';
import { StrikethroughAction } from 'app/editor/monaco-editor/model/actions/strikethrough.action';
import { OrderedListAction } from 'app/editor/monaco-editor/model/actions/ordered-list.action';
import { faAngleDown, faGripLines, faQuestionCircle, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { TextEditorActionGroup } from 'app/editor/monaco-editor/model/actions/text-editor-action-group.model';
import { HeadingAction } from 'app/editor/monaco-editor/model/actions/heading.action';
import { FullscreenAction } from 'app/editor/monaco-editor/model/actions/fullscreen.action';
import { ColorAction } from 'app/editor/monaco-editor/model/actions/color.action';
import { ColorSelectorComponent } from 'app/shared-ui/color-selector/color-selector.component';
import { CdkDrag, CdkDragMove, Point } from '@angular/cdk/drag-drop';
import { TextEditorDomainAction } from 'app/editor/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditorDomainActionWithOptions } from 'app/editor/monaco-editor/model/actions/text-editor-domain-action-with-options.model';
import { LectureAttachmentReferenceAction, LectureWithDetails } from 'app/editor/monaco-editor/model/actions/communication/lecture-attachment-reference.action';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { PostingEditType, ReferenceType } from 'app/communication/metis.util';
import { MonacoEditorOptionPreset } from 'app/editor/monaco-editor/model/monaco-editor-option-preset.model';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { parseMarkdownForDomainActions } from 'app/editor/markdown-editor/monaco/markdown-editor-parsing.helper';
import { COMMUNICATION_MARKDOWN_EDITOR_OPTIONS, DEFAULT_MARKDOWN_EDITOR_OPTIONS } from 'app/editor/monaco-editor/monaco-editor-option.helper';
import { MetisService } from 'app/communication/service/metis.service';
import { UPLOAD_MARKDOWN_FILE_EXTENSIONS } from 'app/foundation/constants/file-extensions.constants';
import { EmojiAction } from 'app/editor/monaco-editor/model/actions/emoji.action';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Tag } from 'primeng/tag';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisIntelligenceService } from 'app/editor/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { faPaperPlane } from '@fortawesome/free-regular-svg-icons';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { RedirectToIrisButtonComponent } from 'app/communication/shared/redirect-to-iris-button/redirect-to-iris-button.component';
import { Course } from 'app/course/shared/entities/course.model';
import { FileUploadResponse, FileUploaderService } from 'app/foundation/service/file-uploader.service';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { CommentThread, CommentThreadLocationType, ReviewThreadLocation } from 'app/exercise/shared/entities/review/comment-thread.model';
import { ReviewCommentWidgetManager } from 'app/exercise/review/review-comment-widget-manager';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { EditorSelectionWithPosition, InstructionSelectionPosition } from 'app/programming/manage/shared/problem-statement.utils';

/** Cached selection with Monaco-compatible data used by scroll re-positioning. */
type CachedSelectionWithText = InstructionSelectionPosition & { selectedText: string };

export enum MarkdownEditorHeight {
    INLINE = 125,
    SMALL = 300,
    MEDIUM = 500,
    LARGE = 1000,
    EXTRA_LARGE = 1500,
}

interface MarkdownActionsByGroup {
    style: TextStyleTextEditorAction[];
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

/** Identifiers for the editor tabs. Exposed as static fields on the component and as the active-tab signal value. */
const TAB_EDIT_ID = 'editor_edit';
const TAB_PREVIEW_ID = 'editor_preview';
const TAB_VISUAL_ID = 'editor_visual';

@Component({
    selector: 'jhi-markdown-editor-monaco',
    templateUrl: './markdown-editor-monaco.component.html',
    styleUrls: ['./markdown-editor-monaco.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        Tabs,
        TabList,
        Tab,
        TranslateDirective,
        NgClass,
        MonacoEditorComponent,
        FaIconComponent,
        Tooltip,
        NgTemplateOutlet,
        Popover,
        TieredMenu,
        ColorSelectorComponent,
        PostingButtonComponent,
        CdkDrag,
        ArtemisTranslatePipe,
        RedirectToIrisButtonComponent,
        Tag,
    ],
})
export class MarkdownEditorMonacoComponent implements AfterContentInit, AfterViewInit, OnDestroy {
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    // We inject the MetisService here to avoid a NullInjectorError in the FileUploaderService.
    private readonly metisService = inject(MetisService, { optional: true });
    private readonly fileUploaderService = inject(FileUploaderService);
    private readonly artemisMarkdown = inject(ArtemisMarkdownService);
    protected readonly artemisIntelligenceService = inject(ArtemisIntelligenceService); // used in template
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly exerciseReviewCommentService = inject(ExerciseReviewCommentService);
    private readonly injector = inject(Injector);

    readonly monacoEditor = viewChild(MonacoEditorComponent);
    readonly fullElement = viewChild.required<ElementRef<HTMLDivElement>>('fullElement');
    readonly wrapper = viewChild.required<ElementRef<HTMLDivElement>>('wrapper');
    readonly fileUploadFooter = viewChild<ElementRef<HTMLDivElement>>('fileUploadFooter');
    readonly fileUploadInput = viewChild<ElementRef<HTMLInputElement>>('fileUploadInput');
    readonly resizePlaceholder = viewChild<ElementRef<HTMLDivElement>>('resizePlaceholder');
    readonly actionPalette = viewChild<ElementRef<HTMLElement>>('actionPalette');
    readonly diffHeader = viewChild<ElementRef<HTMLDivElement>>('diffHeader');
    readonly colorSelector = viewChild(ColorSelectorComponent);

    /**
     * The incoming markdown content. Supports one-way `[markdown]` and two-way `[(markdown)]` bindings (the latter
     * pairs with the {@link markdownChange} output). Changes to this input are synced into the editor via an effect.
     * To read or imperatively change the live content, use {@link currentMarkdown} / {@link setMarkdown}.
     */
    readonly markdown = input<string>();
    /** Emitted whenever the user edits the content in the editor (not for programmatic/binding updates). */
    readonly markdownChange = output<string>();
    /**
     * The live markdown content. Updated by the {@link markdown} input binding, by user edits ({@link onTextChanged}),
     * and by imperative {@link setMarkdown} calls. Replaces the former private `_markdown` field.
     */
    readonly currentMarkdown = signal<string | undefined>(undefined);

    readonly enableFileUpload = input<boolean>(true);
    readonly enableResize = input<boolean>(true);
    readonly showPreviewButton = input<boolean>(true);
    readonly showVisualButton = input<boolean>(false);
    readonly showDefaultPreview = input<boolean>(true);
    readonly useDefaultMarkdownEditorOptions = input<boolean>(true);
    readonly showEditButton = input<boolean>(true);

    /**
     * If set to true, the editor will grow and shrink to fit its content. However, the height will still be constrained by {@link resizableMinHeight} and {@link resizableMaxHeight}.
     * In particular, an empty editor will have the height of {@link resizableMinHeight} upon initialization, no matter what value {@link initialEditorHeight} has.
     */
    readonly linkEditorHeightToContentHeight = input<boolean>(false);

    /**
     * The initial height the editor should have.
     */
    readonly initialEditorHeight = input<MarkdownEditorHeight>(MarkdownEditorHeight.SMALL);

    /**
     * If true, the editor height is managed externally by the parent container.
     * The editor will try to grow to fill the available space rather than managing its own height.
     * Use this when embedding the editor in a container that controls layout (e.g., code editor view).
     */
    readonly externalHeight = input<boolean>(false);

    readonly resizableMaxHeight = input<MarkdownEditorHeight>(MarkdownEditorHeight.LARGE);
    readonly resizableMinHeight = input<MarkdownEditorHeight>(MarkdownEditorHeight.SMALL);

    readonly defaultActions = input<TextEditorAction[]>([
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
    ]);

    readonly headerActions = input<TextEditorActionGroup<HeadingAction> | undefined>(
        new TextEditorActionGroup<HeadingAction>(
            'artemisApp.multipleChoiceQuestion.editor.style',
            [1, 2, 3].map((level) => new HeadingAction(level)),
            undefined,
        ),
    );

    readonly lectureReferenceAction = input<LectureAttachmentReferenceAction | undefined>(undefined);
    readonly colorAction = input<ColorAction | undefined>(new ColorAction());
    readonly domainActions = input<TextEditorDomainAction[]>([]);
    readonly artemisIntelligenceActions = input<TextEditorAction[]>([]);
    readonly metaActions = input<TextEditorAction[]>([new FullscreenAction()]);

    readonly enableExerciseReviewComments = input<boolean>(false);
    readonly showLocationWarning = input<boolean>(false);

    readonly isButtonLoading = input<boolean>(false);
    readonly isAiLoading = input<boolean>(false);
    readonly isFormGroupValid = input<boolean>(false);
    readonly isInCommunication = input<boolean>(false);
    readonly showMarkdownInfoText = input<boolean>(true);
    readonly editType = input<PostingEditType>();
    readonly course = input<Course>();

    readonly useCommunicationForFileUpload = input<boolean>(false);
    readonly fallbackConversationId = input<number>();

    readonly showCloseButton = input<boolean>(false);
    /** Whether the editor is read-only */
    readonly isReadOnly = input<boolean>(false);

    readonly mode = input<MonacoEditorMode>('normal');

    readonly renderSideBySide = input<boolean>(true);

    readonly closeEditor = output<void>();

    /** Emits diff line change information when in diff mode */
    readonly diffLineChange = output<{ ready: boolean; lineChange: LineChange }>();

    readonly onPreviewSelect = output<void>();
    readonly onEditSelect = output<void>();
    readonly onBlurEditor = output<void>();
    readonly textWithDomainActionsFound = output<TextWithDomainAction[]>();
    readonly onDefaultPreviewHtmlChanged = output<SafeHtml | undefined>();
    readonly onLeaveVisualTab = output<void>();

    readonly onAddReviewComment = output<{ lineNumber: number; fileName: string }>();
    readonly onNavigateToReviewCommentLocation = output<ReviewThreadLocation>();
    readonly onApplyInlineFix = output<{ threadId: number }>();

    /** Emits when user selects lines in the editor (includes selectedText, position, and column info for inline refinement) */
    readonly onSelectionChange = output<EditorSelectionWithPosition | undefined>();

    readonly defaultPreviewHtml = signal<SafeHtml | undefined>(undefined);
    /** The id of the currently active tab. */
    readonly activeTab = signal<string>(TAB_EDIT_ID);
    readonly inPreviewMode = signal<boolean>(false);
    readonly inVisualMode = signal<boolean>(false);
    readonly inEditMode = signal<boolean>(true);
    /** Tracks whether the visual/preview content has been activated at least once, mirroring ngbNav's lazy `destroyOnHide=false` behavior. */
    protected readonly visualTabActivated = signal<boolean>(false);
    protected readonly previewTabActivated = signal<boolean>(false);
    readonly uniqueMarkdownEditorId: string;
    resizeObserver?: ResizeObserver;
    /** Disposable for the selection change listener */
    private selectionChangeDisposable?: { dispose: () => void };
    /** Disposable for the scroll change listener used to hide the inline refinement button on editor scroll */
    private scrollChangeDisposable?: { dispose: () => void };
    /** Cached model selection used to re-compute screen position after scroll */
    private cachedSelection?: CachedSelectionWithText;
    /** Window scroll handler reference, stored so it can be removed in ngOnDestroy. */
    private windowScrollHandler?: () => void;
    /** Reactive translated label for the "Your Original" diff-pane header (updates on language change). */
    protected readonly diffOriginalLabel = toSignal(this.translateService.stream('artemisApp.programmingExercise.problemStatement.diffView.originalLabel'), { initialValue: '' });
    /** Reactive translated label for the "AI Suggestion" diff-pane header (updates on language change). */
    protected readonly diffSuggestionLabel = toSignal(this.translateService.stream('artemisApp.programmingExercise.problemStatement.diffView.suggestionLabel'), {
        initialValue: '',
    });
    /** Reactive translated hint text for the unified diff view (updates on language change). */
    protected readonly diffUnifiedHint = toSignal(this.translateService.stream('artemisApp.programmingExercise.problemStatement.diffView.unifiedHint'), { initialValue: '' });
    /** Pixel width of the original (left) pane in the diff editor, used to align header labels with the sash. */
    protected readonly diffOriginalPaneWidth = signal<number | undefined>(undefined);
    readonly targetWrapperHeight = signal<number | undefined>(undefined);
    readonly minWrapperHeight = signal<number | undefined>(undefined);
    readonly isResizing = signal<boolean>(false);
    /**
     * The actions to display in the editor, grouped by type. Snapshotted once in {@link ngAfterContentInit} from the
     * action inputs, intentionally kept in lock-step with the one-time action registration in {@link ngAfterViewInit}:
     * the toolbar only ever shows actions that are actually registered on the editor, so a button can never trigger
     * an unregistered action.
     */
    readonly displayedActions = signal<MarkdownActionsByGroup>({
        style: [],
        standard: [],
        header: [],
        color: undefined,
        domain: { withoutOptions: [], withOptions: [] },
        lecture: undefined,
        artemisIntelligence: [],
        meta: [],
    });

    /**
     * The PrimeNG TieredMenu model for the lecture attachment reference picker. Built on demand in
     * {@link openLectureMenu} (rather than as a computed) because the action's {@link LectureAttachmentReferenceAction.lecturesWithDetails}
     * are loaded asynchronously and are not a signal — rebuilding on open guarantees the freshest lectures/units/slides.
     */
    protected readonly lectureMenuModel = signal<MenuItem[]>([]);

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

    static readonly TAB_EDIT = TAB_EDIT_ID;
    static readonly TAB_PREVIEW = TAB_PREVIEW_ID;
    static readonly TAB_VISUAL = TAB_VISUAL_ID;
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

        // Keep the live content and the Monaco editor in sync with the markdown input. This mirrors the previous
        // `set markdown(...)` side effect: an incoming binding value updates the editor (where `setText` guards
        // against redundant updates and performs emoji conversion) WITHOUT emitting markdownChange. The editor is read
        // untracked so this only reacts to input changes, not to the editor first becoming available (initial content
        // is set in {@link ngAfterViewInit}).
        effect(() => {
            const value = this.markdown();
            this.currentMarkdown.set(value);
            untracked(() => this.applyMarkdownToEditor(value));
        });

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
                            if (this.monacoEditor()) {
                                this.adjustEditorDimensions();
                            }
                        },
                        { injector: this.injector },
                    );
                });
            }
            lastObservedMode = currentMode;
        });
    }

    /**
     * Renders review comment widgets inside the editor.
     */
    protected renderEditorWidgets() {
        // Bail out until the editor is ready
        if (!this.monacoEditor()) {
            return;
        }

        if (this.enableExerciseReviewComments()) {
            // Avoid tracking UI-only signals (e.g. showLocationWarning) as rerender dependencies.
            untracked(() => this.getReviewCommentManager()?.renderWidgets());
        } else {
            this.reviewCommentManager?.disposeAll();
            this.monacoEditor()!.clearLineDecorationsHoverButton();
        }
    }

    ngAfterContentInit(): void {
        // Affects the template - done in this method to avoid ExpressionChangedAfterItHasBeenCheckedErrors.
        this.targetWrapperHeight.set(!this.externalHeight() ? this.initialEditorHeight().valueOf() : undefined);
        this.minWrapperHeight.set(this.resizableMinHeight().valueOf());
        // Snapshot the displayed actions once, in lock-step with the registration performed in ngAfterViewInit.
        this.displayedActions.set(this.buildDisplayedActions());
    }

    filterDisplayedActions<T extends TextEditorAction>(actions: T[]): T[] {
        return actions.filter((action) => !action.hideInEditor);
    }

    filterDisplayedAction<T extends TextEditorAction>(action?: T): T | undefined {
        return action?.hideInEditor ? undefined : action;
    }

    /**
     * Groups the action inputs (filtering out actions hidden in the editor) into the structure consumed by the
     * toolbar template. Computed once at init so the displayed toolbar matches the registered action set.
     */
    private buildDisplayedActions(): MarkdownActionsByGroup {
        const domainActions = this.domainActions();
        return {
            style: this.filterDisplayedActions(this.defaultActions()).filter((action) => action instanceof TextStyleTextEditorAction) as TextStyleTextEditorAction[],
            standard: this.filterDisplayedActions(this.defaultActions()).filter((action) => !(action instanceof TextStyleTextEditorAction)),
            header: this.filterDisplayedActions(this.headerActions()?.actions ?? []),
            color: this.filterDisplayedAction(this.colorAction()),
            domain: {
                withoutOptions: this.filterDisplayedActions(domainActions.filter((action) => !(action instanceof TextEditorDomainActionWithOptions))),
                withOptions: this.filterDisplayedActions(
                    domainActions.filter((action) => action instanceof TextEditorDomainActionWithOptions),
                ) as TextEditorDomainActionWithOptions[],
            },
            lecture: this.filterDisplayedAction(this.lectureReferenceAction()),
            artemisIntelligence: this.filterDisplayedActions(this.artemisIntelligenceActions() ?? []),
            meta: this.filterDisplayedActions(this.metaActions()),
        };
    }

    /**
     * Rebuilds the lecture tiered-menu model from the action's currently-loaded lectures and opens the popup. Building
     * on open (instead of via a computed) ensures asynchronously-loaded lectures/units/slides are always reflected.
     * @param menu The PrimeNG tiered menu to open.
     * @param event The triggering mouse event.
     * @param lectureAction The lecture reference action whose lectures should populate the menu.
     */
    openLectureMenu(menu: TieredMenu, event: MouseEvent, lectureAction: LectureAttachmentReferenceAction): void {
        this.lectureMenuModel.set(this.buildLectureMenuModel(lectureAction));
        menu.toggle(event);
    }

    /**
     * Builds the full tiered-menu model for the lecture reference picker, or a single disabled "empty list" entry
     * when no lectures are available.
     */
    private buildLectureMenuModel(lectureAction: LectureAttachmentReferenceAction): MenuItem[] {
        const lectures = lectureAction.lecturesWithDetails ?? [];
        if (!lectures.length) {
            return [{ label: this.translateService.instant('global.generic.emptyList'), disabled: true }];
        }
        return lectures.map((lecture) => this.buildLectureMenuItem(lectureAction, lecture));
    }

    /**
     * Builds the PrimeNG menu item for a lecture. Lectures without referencable attachments insert a lecture
     * reference directly; otherwise the lecture exposes a submenu with its units, slides, and attachments.
     */
    private buildLectureMenuItem(lectureAction: LectureAttachmentReferenceAction, lecture: LectureWithDetails): MenuItem {
        if (!this.hasReferencableAttachments(lecture)) {
            return {
                label: lecture.title,
                command: () => lectureAction.executeInCurrentEditor({ reference: ReferenceType.LECTURE, lecture }),
            };
        }

        const items: MenuItem[] = [{ label: lecture.title, command: () => lectureAction.executeInCurrentEditor({ reference: ReferenceType.LECTURE, lecture }) }];

        for (const unit of lecture.attachmentVideoUnits ?? []) {
            // Only show attachment units with an attachment.
            if (unit.attachment && unit.attachment.link) {
                items.push(this.buildAttachmentVideoUnitMenuItem(lectureAction, lecture, unit));
            }
        }

        for (const attachment of lecture.attachments ?? []) {
            items.push({
                label: attachment.name,
                command: () => lectureAction.executeInCurrentEditor({ reference: ReferenceType.ATTACHMENT, lecture, attachment }),
            });
        }

        return { label: lecture.title, items };
    }

    /**
     * Builds the menu item for an attachment video unit. Units without slides insert a unit reference directly;
     * otherwise the unit exposes a submenu listing its individual slides.
     */
    private buildAttachmentVideoUnitMenuItem(
        lectureAction: LectureAttachmentReferenceAction,
        lecture: LectureWithDetails,
        unit: NonNullable<LectureWithDetails['attachmentVideoUnits']>[number],
    ): MenuItem {
        if (!unit.slides?.length) {
            return {
                label: unit.name,
                command: () => lectureAction.executeInCurrentEditor({ reference: ReferenceType.ATTACHMENT_UNITS, lecture, attachmentVideoUnit: unit }),
            };
        }

        const items: MenuItem[] = [
            { label: unit.name, command: () => lectureAction.executeInCurrentEditor({ reference: ReferenceType.ATTACHMENT_UNITS, lecture, attachmentVideoUnit: unit }) },
        ];
        unit.slides.forEach((slide, index) => {
            const slideNumber = index + 1;
            items.push({
                label: this.translateService.instant('artemisApp.markdownEditor.slideWithNumber', { number: slideNumber }),
                command: () => lectureAction.executeInCurrentEditor({ reference: ReferenceType.SLIDE, lecture, slide, attachmentVideoUnit: unit, slideIndex: slideNumber }),
            });
        });

        return { label: unit.name, items };
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
        this.monacoEditor()!.setWordWrap(true);
        // Use the live content (covers an imperative setMarkdown() made before the editor was ready) and fall back to the input.
        this.monacoEditor()!.changeModel('markdown-content.custom-md', this.currentMarkdown() ?? this.markdown() ?? '', 'custom-md');
        this.resizeObserver = new ResizeObserver(() => {
            this.adjustEditorDimensions();
        });
        this.resizeObserver.observe(this.wrapper().nativeElement);
        // Prevents the file upload footer from disappearing when switching between preview and editor.
        if (this.fileUploadFooter()?.nativeElement) {
            this.resizeObserver.observe(this.fileUploadFooter()!.nativeElement);
        }
        if (this.actionPalette()?.nativeElement) {
            this.resizeObserver.observe(this.actionPalette()!.nativeElement);
        }
        [
            this.defaultActions(),
            this.headerActions()?.actions ?? [],
            this.domainActions(),
            ...(this.colorAction() ? [this.colorAction()!] : []),
            ...(this.lectureReferenceAction() ? [this.lectureReferenceAction()!] : []),
            ...this.artemisIntelligenceActions(),
            this.metaActions(),
        ]
            .flat()
            .forEach((action) => {
                if (action instanceof FullscreenAction) {
                    // We include the full element if the initial height is set to 'external' so the editor is resized to fill the screen.
                    action.element = this.isHeightManagedExternally() ? this.fullElement().nativeElement : this.wrapper().nativeElement;
                } else if (this.enableFileUpload() && action instanceof AttachmentAction) {
                    action.setUploadCallback(this.embedFiles.bind(this));
                    action.setOpenFileDialogCallback(this.openFilePicker.bind(this));
                }
                this.monacoEditor()!.registerAction(action);
            });

        if (this.useDefaultMarkdownEditorOptions()) {
            this.monacoEditor()!.applyOptionPreset(DEFAULT_MARKDOWN_EDITOR_OPTIONS);
        }

        if (this.isInCommunication()) {
            this.showTextStyleActions.set(false);
        }

        // Set up selection change listener for inline comments/refinement and hiding/showing actions in communication mode
        this.selectionChangeDisposable = this.monacoEditor()!.onSelectionChange((selection) => {
            if (this.isInCommunication()) {
                this.updateEditorActionsVisibility(selection);
            }
            if (selection) {
                // Get selected text for inline refinement
                const model = this.monacoEditor()!.getModel();
                const selectedText = model ? model.getValueInRange(selection) : '';

                // Only emit if there's actual text selected (not just cursor movement)
                if (selectedText.trim().length === 0) {
                    this.cachedSelection = undefined;
                    this.onSelectionChange.emit(undefined);
                    return;
                }

                // Cache the selection so scroll events can re-compute position
                this.cachedSelection = {
                    startLine: selection.startLineNumber,
                    endLine: selection.endLineNumber,
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

        // Hide the inline refinement button whenever any scroll occurs (editor-internal or page).
        const hideOnScroll = () => {
            this.cachedSelection = undefined;
            this.onSelectionChange.emit(undefined);
        };
        this.scrollChangeDisposable = this.monacoEditor()!.onScrollChange(hideOnScroll);
        this.windowScrollHandler = hideOnScroll;
        window.addEventListener('scroll', hideOnScroll, { passive: true, capture: true });
    }

    /**
     * Computes the screen position from the cached selection and emits the selection event.
     * If the selection end is scrolled off-screen (coords are null), emits undefined to hide the button.
     */
    private emitSelectionWithScreenPosition(): void {
        if (!this.cachedSelection) {
            return;
        }

        const endPosition = { lineNumber: this.cachedSelection.endLine, column: this.cachedSelection.endColumn };
        const coords = this.monacoEditor()!.getScrolledVisiblePosition(endPosition);
        const editorDom = this.monacoEditor()!.getDomNode();

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
            startLine: this.cachedSelection.startLine,
            endLine: this.cachedSelection.endLine,
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
    constrainDragPosition = (pointerPosition: Point): Point => {
        // We do not want to drag past the minimum or maximum height.
        // x is not used, so we can ignore it.
        const minY = this.wrapper().nativeElement.getBoundingClientRect().top + this.resizableMinHeight();
        const maxY = this.wrapper().nativeElement.getBoundingClientRect().top + this.resizableMaxHeight();
        return {
            x: pointerPosition.x,
            y: Math.min(maxY, Math.max(minY, pointerPosition.y)),
        };
    };

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
        this.selectionChangeDisposable?.dispose();
        this.scrollChangeDisposable?.dispose();
        if (this.windowScrollHandler) {
            window.removeEventListener('scroll', this.windowScrollHandler, { capture: true });
            this.windowScrollHandler = undefined;
        }
        this.cachedSelection = undefined;
        this.reviewCommentManager?.disposeAll();
        this.monacoEditor()?.clearLineDecorationsHoverButton();
    }

    onTextChanged(event: { text: string; fileName: string }): void {
        // A user edit updates the live content, re-applies it to the editor (handling emoji conversion), and notifies
        // bound parents via markdownChange. Programmatic updates go through the input effect / setMarkdown and do NOT
        // emit, preserving the legacy distinction between binding updates and user edits.
        this.currentMarkdown.set(event.text);
        this.applyMarkdownToEditor(event.text);
        this.markdownChange.emit(event.text);
    }

    /**
     * Imperatively sets the markdown content (live value + editor), WITHOUT emitting markdownChange. This mirrors the
     * legacy `editor.markdown = value` input setter and is intended for consumers holding a reference to this editor.
     * @param value The markdown content to apply.
     */
    setMarkdown(value: string | undefined): void {
        this.currentMarkdown.set(value);
        this.applyMarkdownToEditor(value);
    }

    /**
     * Pushes the given content into the Monaco editor if it is available. setText guards against redundant updates
     * and performs emoji conversion.
     * @param value The content to apply.
     */
    private applyMarkdownToEditor(value: string | undefined): void {
        this.monacoEditor()?.setText(value ?? '');
    }

    readonly showTextStyleActions = signal<boolean>(true);
    readonly showNonTextStyleActions = signal<boolean>(true);

    /**
     * Hides actions that are not applicable in the given context, and shows actions that can be used.
     * @param selection Currently selected text
     */
    updateEditorActionsVisibility(selection: EditorRange | undefined): void {
        const isEmpty = !selection || (selection.startLineNumber == selection.endLineNumber && selection.startColumn == selection.endColumn);
        if (!isEmpty === this.showTextStyleActions() && isEmpty === this.showNonTextStyleActions()) {
            return;
        }
        this.showTextStyleActions.set(!isEmpty);
        this.showNonTextStyleActions.set(isEmpty);
    }

    /**
     * Called when the user moves the resize handle.
     * @param event The drag event caused by the user moving the resize handle.
     */
    onResizeMoved(event: CdkDragMove) {
        // This prevents the element from escaping its boundaries when being dragged. This is necessary because
        event.source.reset();
        // The editor's bottom edge becomes the top edge of the handle.
        this.targetWrapperHeight.set(
            event.pointerPosition.y - this.wrapper().nativeElement.getBoundingClientRect().top - this.getElementClientHeight(this.resizePlaceholder()) / 2,
        );
    }

    /**
     * Adjusts the height of the element when the content height changes.
     * @param newContentHeight The new height of the content in the editor.
     */
    onContentHeightChanged(newContentHeight: number | undefined): void {
        // Upon switching back from the preview tab, the file upload footer will briefly have a height of 0. We ignore this case to avoid an incorrect height.
        if (this.linkEditorHeightToContentHeight() && !(this.enableFileUpload() && this.getElementClientHeight(this.fileUploadFooter()) === 0)) {
            const totalHeight = (newContentHeight ?? 0) + this.getElementClientHeight(this.fileUploadFooter()) + this.getElementClientHeight(this.actionPalette());
            // Clamp the height so it is between the minimum and maximum height.
            this.targetWrapperHeight.set(Math.max(this.resizableMinHeight(), Math.min(this.resizableMaxHeight(), totalHeight)));
        }
    }

    /**
     * Computes the height of the editor based on the other elements displayed. We compute this directly to avoid layout issues with the Monaco editor.
     * The height of the editor is the height of the wrapper (or the full element, if the height is external) minus the height of the file upload footer and the action palette.
     */
    getEditorHeight(): number {
        // We always use the wrapper height, as it is correctly sized by the flexbox layout in all cases (external or internal height).
        const elementHeight = this.getElementClientHeight(this.wrapper());
        const fileUploadFooterHeight = this.getElementClientHeight(this.fileUploadFooter());
        const actionPaletteHeight = this.getElementClientHeight(this.actionPalette());
        const diffHeaderHeight = this.getElementClientHeight(this.diffHeader());
        return Math.max(0, elementHeight - fileUploadFooterHeight - actionPaletteHeight - diffHeaderHeight - BORDER_HEIGHT_OFFSET);
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
        return this.wrapper().nativeElement.clientWidth - BORDER_WIDTH_OFFSET;
    }

    /**
     * Adjust the dimensions of the editor to fit the available space.
     */
    adjustEditorDimensions(): void {
        this.onContentHeightChanged(this.monacoEditor()!.getContentHeight());
        const editorHeight = this.getEditorHeight();
        this.monacoEditor()!.layoutWithFixedSize(this.getEditorWidth(), editorHeight);
        // Prevents an issue with line wraps in the editor
        this.monacoEditor()!.layout();
    }

    /**
     * Called when a nav tab is shown. Adjusts editor dimensions and focuses the editor if the edit tab is active.
     */
    onTabShown(): void {
        if (this.inEditMode()) {
            this.adjustEditorDimensions();
            this.monacoEditor()!.focus();
        }
    }

    /**
     * Called when the user changes the active tab. Tracks the active tab and the resulting mode, emits the
     * appropriate selection events, parses the markdown when leaving the edit/visual tab, and re-lays out and
     * focuses the editor once the edit tab has been rendered.
     * @param nextId The id of the newly activated tab.
     */
    onTabChange(nextId: string | number | undefined): void {
        const previousId = this.activeTab();
        const newId = `${nextId}`;
        if (previousId === newId) {
            return;
        }
        this.activeTab.set(newId);
        this.inPreviewMode.set(newId === this.TAB_PREVIEW);
        this.inVisualMode.set(newId === this.TAB_VISUAL);
        this.inEditMode.set(newId === this.TAB_EDIT);
        if (newId === this.TAB_VISUAL) {
            this.visualTabActivated.set(true);
        }
        if (newId === this.TAB_PREVIEW) {
            this.previewTabActivated.set(true);
        }

        if (this.inEditMode()) {
            this.onEditSelect.emit();
        } else if (this.inPreviewMode()) {
            this.onPreviewSelect.emit();
        }
        this.updateReviewCommentButton();

        // Some components need to know when the user leaves the visual tab, as it might make changes to the underlying data.
        if (previousId === this.TAB_VISUAL) {
            this.onLeaveVisualTab.emit();
        }

        // Parse the markdown when switching away from the edit tab or from visual to preview mode, as the visual mode may make changes to the markdown.
        if (previousId === this.TAB_EDIT || (previousId === this.TAB_VISUAL && this.inPreviewMode())) {
            this.parseMarkdown();
        }

        // Mirror ngbNav's `(shown)` event: re-layout and focus the editor once the edit tab content is visible.
        if (this.inEditMode()) {
            afterNextRender(() => this.onTabShown(), { injector: this.injector });
        }
    }

    onDiffChanged(event: { ready: boolean; lineChange: LineChange }): void {
        this.diffLineChange.emit(event);
    }

    onDiffOriginalPaneLayoutChanged(originalWidth: number): void {
        this.diffOriginalPaneWidth.set(originalWidth);
    }

    parseMarkdown(domainActionsToCheck: TextEditorDomainAction[] = this.domainActions()): void {
        const markdown = this.currentMarkdown();
        if (this.showDefaultPreview()) {
            this.defaultPreviewHtml.set(this.artemisMarkdown.safeHtmlForMarkdown(markdown));
            this.onDefaultPreviewHtmlChanged.emit(this.defaultPreviewHtml());
        }
        if (domainActionsToCheck.length && markdown) {
            this.textWithDomainActionsFound.emit(parseMarkdownForDomainActions(markdown, domainActionsToCheck));
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
        this.fileUploadInput()?.nativeElement.click();
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
        if (!this.enableFileUpload()) {
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

        const attachmentAction: AttachmentAction | undefined = this.defaultActions().find((action) => action instanceof AttachmentAction) as AttachmentAction;
        const urlAction: UrlAction | undefined = this.defaultActions().find((action) => action instanceof UrlAction);
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
        this.colorSelector()!.openColorSelector(event, marginTop, height);
    }

    /**
     * Callback when a color is selected in the color picker.
     * @param color The hex code of the selected color.
     */
    onSelectColor(color: string): void {
        const colorName = this.colorToClassMap.get(color);
        if (colorName) {
            this.colorAction()?.executeInCurrentEditor({ color: colorName });
        }
    }

    /**
     * Check if the editor height is managed externally. This determines if the editor should grow to fill available space, rather than managing its own height.
     * @private
     */
    private isHeightManagedExternally(): boolean {
        return this.externalHeight();
    }

    /**
     * Enable the text field mode of the editor. This makes the editor look and behave like a normal text field.
     */
    enableTextFieldMode(): void {
        this.monacoEditor()!.applyOptionPreset(COMMUNICATION_MARKDOWN_EDITOR_OPTIONS);
    }

    /**
     * Applies the given option preset to the Monaco editor.
     * @param preset The preset to apply.
     */
    applyOptionPreset(preset: MonacoEditorOptionPreset): void {
        this.monacoEditor()!.applyOptionPreset(preset);
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
        if (!this.monacoEditor()) {
            return undefined;
        }
        if (!this.reviewCommentManager) {
            this.reviewCommentManager = new ReviewCommentWidgetManager(this.monacoEditor()!, this.viewContainerRef, {
                hoverButtonClass: REVIEW_COMMENT_HOVER_BUTTON_CLASS,
                shouldShowHoverButton: () => this.enableExerciseReviewComments() && this.inEditMode(),
                canSubmit: () => this.inEditMode() && !this.showLocationWarning(),
                getDraftFileName: () => PROBLEM_STATEMENT_FILE_PATH,
                getDraftContext: () => ({
                    targetType: CommentThreadLocationType.PROBLEM_STATEMENT,
                }),
                getThreads: () => this.exerciseReviewCommentService.threads(),
                filterThread: (thread) => this.isProblemStatementThread(thread),
                getThreadLine: (thread) => this.getProblemStatementThreadLine(thread),
                onAdd: (payload) => this.onAddReviewComment.emit(payload),
                onApplyInlineFix: ({ thread }) => this.onApplyInlineFix.emit({ threadId: thread.id }),
                onNavigateToLocation: (location) => this.onNavigateToReviewCommentLocation.emit(location),
                showLocationWarning: () => this.showLocationWarning(),
                showFeedbackAction: () => false,
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
        if (!this.monacoEditor()) {
            return undefined;
        }
        const sel = this.monacoEditor()!.getSelection();
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
        this.monacoEditor()?.applyDiffContent(content);
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
        this.monacoEditor()?.revertAll();
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
