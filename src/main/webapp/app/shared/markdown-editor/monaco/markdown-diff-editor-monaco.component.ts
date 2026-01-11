import {
    AfterViewInit,
    ChangeDetectionStrategy,
    Component,
    ElementRef,
    HostBinding,
    OnDestroy,
    Renderer2,
    ViewEncapsulation,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    viewChild,
} from '@angular/core';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
import { MonacoEditorService } from 'app/shared/monaco-editor/service/monaco-editor.service';
import * as monaco from 'monaco-editor';
import { CdkDrag, CdkDragMove, Point } from '@angular/cdk/drag-drop';
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
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faGripLines } from '@fortawesome/free-solid-svg-icons';
import { NgTemplateOutlet } from '@angular/common';
import { MonacoTextEditorAdapter } from 'app/shared/monaco-editor/model/actions/adapter/monaco-text-editor.adapter';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ColorAction } from 'app/shared/monaco-editor/model/actions/color.action';
import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditorDomainActionWithOptions } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action-with-options.model';
import { FullscreenAction } from 'app/shared/monaco-editor/model/actions/fullscreen.action';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { LectureAttachmentReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/lecture-attachment-reference.action';

export type MonacoEditorDiffText = { original: string; modified: string };

@Component({
    selector: 'jhi-markdown-diff-editor-monaco',
    standalone: true,
    templateUrl: './markdown-diff-editor-monaco.component.html',
    styleUrls: ['./markdown-diff-editor-monaco.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    imports: [
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        TranslateDirective,
        FaIconComponent,
        NgbTooltip,
        NgTemplateOutlet,
        ArtemisTranslatePipe,
        ColorSelectorComponent,
        CdkDrag,
    ],
})
export class MarkdownDiffEditorMonacoComponent implements AfterViewInit, OnDestroy {
    private _editor: monaco.editor.IStandaloneDiffEditor;
    monacoDiffEditorContainerElement: HTMLElement;

    fullElement = viewChild.required<ElementRef<HTMLDivElement>>('fullElement');
    wrapper = viewChild.required<ElementRef<HTMLDivElement>>('wrapper');
    editorContainer = viewChild.required<ElementRef<HTMLDivElement>>('editorContainer');
    resizePlaceholder = viewChild<ElementRef<HTMLDivElement>>('resizePlaceholder');
    colorSelector = viewChild<ColorSelectorComponent>(ColorSelectorComponent);

    // Inputs
    allowSplitView = input<boolean>(true);
    enableResize = input<boolean>(true);
    fillHeight = input<boolean>(false);
    initialEditorHeight = input<number>(300);
    resizableMinHeight = input<number>(200);
    resizableMaxHeight = input<number>(800);
    /** Whether the modified (right) editor should be read-only */
    readOnly = input<boolean>(false);

    // Host bindings for fill-height mode
    @HostBinding('style.display') get hostDisplay() {
        return this.fillHeight() ? 'flex' : null;
    }
    @HostBinding('style.flexDirection') get hostFlexDirection() {
        return this.fillHeight() ? 'column' : null;
    }
    @HostBinding('style.height') get hostHeight() {
        return this.fillHeight() ? '100%' : null;
    }

    // Outputs
    onReadyForDisplayChange = output<{ ready: boolean; lineChange: LineChange }>();

    defaultActions = input<TextEditorAction[]>([
        new BoldAction(),
        new ItalicAction(),
        new UnderlineAction(),
        new StrikethroughAction(),
        new QuoteAction(),
        new CodeAction(),
        new CodeBlockAction('markdown'),
        new UrlAction(),
        new AttachmentAction(),
        new OrderedListAction(),
        new BulletedListAction(),
    ]);

    lectureReferenceAction = input<LectureAttachmentReferenceAction | undefined>(undefined);
    colorAction = input<ColorAction | undefined>(new ColorAction());
    domainActions = input<TextEditorDomainAction[]>([]);
    metaActions = input<TextEditorAction[]>([new FullscreenAction()]);

    // domain actions split by type – used by the template
    domainActionsWithoutOptions = computed(() => this.domainActions().filter((action) => !(action instanceof TextEditorDomainActionWithOptions)));

    domainActionsWithOptions = computed(() => this.domainActions().filter((action) => action instanceof TextEditorDomainActionWithOptions) as TextEditorDomainActionWithOptions[]);

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

    colorSignal = signal<string[]>([...this.colorToClassMap.keys()]);
    readonly colorPickerMarginTop = 35;
    readonly colorPickerHeight = 110;

    targetWrapperHeight = 300; // Default, will be updated from input in ngAfterViewInit
    minWrapperHeight = 200; // Default, will be updated from input in ngAfterViewInit
    constrainDragPositionFn?: (pointerPosition: Point) => Point;
    isResizing = false;
    resizeObserver?: ResizeObserver;
    private resizeAnimationFrame?: number;

    // Icons
    readonly faGripLines = faGripLines;

    /*
     * Subscriptions and listeners that need to be disposed of when this component is destroyed.
     */
    listeners: Disposable[] = [];

    /*
     * Decorations collection for revert buttons in the glyph margin.
     */
    private revertDecorations: monaco.editor.IEditorDecorationsCollection | undefined;

    /*
     * Store line changes for revert functionality.
     */
    private currentLineChanges: monaco.editor.ILineChange[] = [];

    /*
     * Hover widget for showing revert options.
     */
    private hoverWidget: HTMLDivElement | undefined;
    private currentHoverLineNumber: number | undefined;

    /*
     * Injected services and elements.
     */
    private readonly renderer = inject(Renderer2);
    private readonly monacoEditorService = inject(MonacoEditorService);

    constructor() {
        // Create editor container, real attachment happens in ngAfterViewInit
        this.monacoDiffEditorContainerElement = this.renderer.createElement('div');
        this._editor = this.monacoEditorService.createStandaloneDiffEditor(this.monacoDiffEditorContainerElement, false);
        this.renderer.addClass(this.monacoDiffEditorContainerElement, 'markdown-diff-editor-container');

        this.setupDiffListener();
        this.setupContentHeightListeners();

        effect(() => {
            this._editor.updateOptions({
                renderSideBySide: this.allowSplitView(),
                scrollbar: {
                    vertical: 'auto',
                    horizontal: 'auto',
                    handleMouseWheel: true,
                    alwaysConsumeMouseWheel: false,
                },
            });
            // Apply readOnly to the modified (right) editor
            const modifiedEditor = this._editor.getModifiedEditor();
            if (modifiedEditor) {
                modifiedEditor.updateOptions({ readOnly: this.readOnly() });
            }
        });

        this.setupRevertButtonClickHandler();
    }

    ngAfterViewInit(): void {
        // Append Monaco diff editor to the dedicated container
        this.renderer.appendChild(this.editorContainer().nativeElement, this.monacoDiffEditorContainerElement);

        this.metaActions()
            .filter((a) => a instanceof FullscreenAction)
            .forEach((fs) => {
                (fs as FullscreenAction).element = this.fullElement().nativeElement;
            });

        this.targetWrapperHeight = this.initialEditorHeight();
        this.minWrapperHeight = this.resizableMinHeight();
        this.constrainDragPositionFn = this.constrainDragPosition.bind(this);

        // Use ResizeObserver on the editor container to handle layout updates
        // Debounce with requestAnimationFrame to prevent excessive repaints during resize
        this.resizeObserver = new ResizeObserver(() => {
            if (this.resizeAnimationFrame) {
                window.cancelAnimationFrame(this.resizeAnimationFrame);
            }
            this.resizeAnimationFrame = requestAnimationFrame(() => {
                const container = this.editorContainer().nativeElement;
                this._editor.layout({
                    width: container.clientWidth,
                    height: container.clientHeight,
                });
            });
        });
        this.resizeObserver.observe(this.editorContainer().nativeElement);

        // Initial layout
        const container = this.editorContainer().nativeElement;
        this._editor.layout({
            width: container.clientWidth,
            height: container.clientHeight,
        });
    }

    ngOnDestroy(): void {
        if (this.resizeAnimationFrame) {
            window.cancelAnimationFrame(this.resizeAnimationFrame);
        }
        this.resizeObserver?.disconnect();
        this.listeners.forEach((listener) => listener.dispose());
        this._editor.dispose();
    }

    /**
     * Sets up a listener that responds to changes in the diff.
     */
    setupDiffListener(): void {
        const diffListener = this._editor.onDidUpdateDiff(() => {
            // Only auto-adjust height if resize is disabled
            if (!this.enableResize()) {
                this.adjustContainerHeight(this.getMaximumContentHeight());
            }

            const monacoLineChanges = this._editor.getLineChanges() ?? [];
            this.currentLineChanges = monacoLineChanges;
            this.onReadyForDisplayChange.emit({
                ready: true,
                lineChange: this.convertMonacoLineChanges(monacoLineChanges),
            });
        });

        this.listeners.push(diffListener);
    }

    /**
     * Sets up hover and click handlers for revert buttons.
     * Shows the glyph icon only when hovering over a changed line (like Monaco's lightbulb).
     */
    private setupRevertButtonClickHandler(): void {
        const modifiedEditor = this._editor.getModifiedEditor();

        // Create hover widget element for the 3-option menu
        this.hoverWidget = document.createElement('div');
        this.hoverWidget.className = 'diff-revert-hover-widget';
        this.hoverWidget.style.display = 'none';
        this.hoverWidget.style.position = 'absolute';
        this.hoverWidget.style.zIndex = '100';
        this.monacoDiffEditorContainerElement.appendChild(this.hoverWidget);

        // Hide widget
        const hideWidget = () => {
            if (this.hoverWidget) {
                this.hoverWidget.style.display = 'none';
            }
        };

        // Track which line we're hovering on to show the glyph icon only there
        const mouseMoveListener = modifiedEditor.onMouseMove((e) => {
            const lineNumber = e.target.position?.lineNumber;

            // Check if hovering on a changed line
            if (lineNumber && this.isLineInChange(lineNumber)) {
                if (lineNumber !== this.currentHoverLineNumber) {
                    this.currentHoverLineNumber = lineNumber;
                    this.updateHoverDecoration(lineNumber);
                }
            } else {
                // Not on a changed line, clear the hover decoration
                if (this.currentHoverLineNumber !== undefined) {
                    this.currentHoverLineNumber = undefined;
                    this.clearHoverDecoration();
                }
            }
        });

        // On click of the glyph, show the widget
        const mouseDownListener = modifiedEditor.onMouseDown((e) => {
            // If clicking in glyph margin on a line that has a hover decoration, show the widget
            if (e.target.type === monaco.editor.MouseTargetType.GUTTER_GLYPH_MARGIN && this.currentHoverLineNumber !== undefined) {
                e.event.preventDefault();
                e.event.stopPropagation();
                this.showHoverWidget(this.currentHoverLineNumber, e.event.posx, e.event.posy);
            } else if (!this.hoverWidget?.contains(e.event.target as Node)) {
                hideWidget();
            }
        });

        // Hide on escape
        const keyDownListener = modifiedEditor.onKeyDown((e) => {
            if (e.keyCode === monaco.KeyCode.Escape) {
                hideWidget();
            }
        });

        // Hide when clicking outside the widget (with a slight delay to not conflict with editor click)
        const documentClickHandler = (e: MouseEvent) => {
            if (this.hoverWidget && this.hoverWidget.style.display !== 'none' && !this.hoverWidget.contains(e.target as Node)) {
                hideWidget();
            }
        };
        // Use setTimeout to add listener after current click event
        setTimeout(() => {
            document.addEventListener('mousedown', documentClickHandler);
        }, 0);

        // Clear decoration when mouse leaves editor
        const mouseLeaveListener = modifiedEditor.onMouseLeave(() => {
            this.currentHoverLineNumber = undefined;
            this.clearHoverDecoration();
        });

        this.listeners.push(mouseMoveListener, mouseDownListener, keyDownListener, mouseLeaveListener);
    }

    /**
     * Checks if a line number is within any change block.
     */
    private isLineInChange(lineNumber: number): boolean {
        return this.currentLineChanges.some((c) => lineNumber >= c.modifiedStartLineNumber && lineNumber <= (c.modifiedEndLineNumber || c.modifiedStartLineNumber));
    }

    /**
     * Updates the hover decoration to show the revert glyph on a specific line.
     */
    private updateHoverDecoration(lineNumber: number): void {
        const modifiedEditor = this._editor.getModifiedEditor();

        if (!this.revertDecorations) {
            this.revertDecorations = modifiedEditor.createDecorationsCollection();
        }

        this.revertDecorations.set([
            {
                range: new monaco.Range(lineNumber, 1, lineNumber, 1),
                options: {
                    glyphMarginClassName: 'diff-revert-glyph',
                },
            },
        ]);
    }

    /**
     * Clears the hover decoration (hides the glyph icon).
     */
    private clearHoverDecoration(): void {
        if (this.revertDecorations) {
            this.revertDecorations.set([]);
        }
    }

    /**
     * Shows the hover widget with 3 options at the specified position.
     */
    private showHoverWidget(lineNumber: number, x: number, y: number): void {
        if (!this.hoverWidget || this.readOnly()) return;

        const change = this.currentLineChanges.find((c) => lineNumber >= c.modifiedStartLineNumber && lineNumber <= (c.modifiedEndLineNumber || c.modifiedStartLineNumber));

        if (!change) return;

        this.currentHoverLineNumber = lineNumber;
        const linesCount = change.modifiedEndLineNumber - change.modifiedStartLineNumber + 1;
        const isMultipleLines = linesCount > 1;

        // Build the menu HTML
        this.hoverWidget.innerHTML = `
            <div class="diff-revert-menu">
                <button class="diff-revert-menu-item" data-action="copy-all">
                    <span class="codicon codicon-copy"></span>
                    Copy changed line${isMultipleLines ? 's' : ''}
                </button>
                <button class="diff-revert-menu-item" data-action="copy-line">
                    <span class="codicon codicon-copy"></span>
                    Copy changed line (${lineNumber})
                </button>
                <button class="diff-revert-menu-item" data-action="revert">
                    <span class="codicon codicon-discard"></span>
                    Revert this change
                </button>
            </div>
        `;

        // Position the widget using fixed positioning at the click location
        // This mirrors how Monaco positions its popups
        this.hoverWidget.style.position = 'fixed';
        this.hoverWidget.style.left = `${x}px`;
        this.hoverWidget.style.top = `${y}px`;
        this.hoverWidget.style.display = 'block';

        // Add click handlers
        this.hoverWidget.querySelectorAll('.diff-revert-menu-item').forEach((btn) => {
            btn.addEventListener('click', (e) => {
                const action = (e.currentTarget as HTMLElement).dataset['action'];
                if (action === 'copy-all') {
                    this.copyChangedLines(change);
                } else if (action === 'copy-line') {
                    this.copyLineContent(lineNumber);
                } else if (action === 'revert') {
                    this.revertChangeAtLine(lineNumber);
                }
                this.hoverWidget!.style.display = 'none';
                this.currentHoverLineNumber = undefined;
            });
        });
    }

    /**
     * Copies all changed lines in the block to clipboard.
     */
    private copyChangedLines(change: monaco.editor.ILineChange): void {
        const modifiedModel = this._editor.getModifiedEditor().getModel();
        if (!modifiedModel) return;

        const content = modifiedModel.getValueInRange(
            new monaco.Range(change.modifiedStartLineNumber, 1, change.modifiedEndLineNumber, modifiedModel.getLineMaxColumn(change.modifiedEndLineNumber)),
        );
        navigator.clipboard.writeText(content);
    }

    /**
     * Copies a single line's content to clipboard.
     */
    private copyLineContent(lineNumber: number): void {
        const modifiedModel = this._editor.getModifiedEditor().getModel();
        if (!modifiedModel) return;

        const content = modifiedModel.getLineContent(lineNumber);
        navigator.clipboard.writeText(content);
    }

    /**
     * Reverts the change at the specified line number in the modified editor.
     * Reverts the entire block that contains the line.
     */
    private revertChangeAtLine(lineNumber: number): void {
        if (this.readOnly()) return;

        // Find the change that contains this line
        const change = this.currentLineChanges.find((c) => lineNumber >= c.modifiedStartLineNumber && lineNumber <= (c.modifiedEndLineNumber || c.modifiedStartLineNumber));

        if (!change) return;

        const originalModel = this._editor.getOriginalEditor().getModel();
        const modifiedModel = this._editor.getModifiedEditor().getModel();

        if (!originalModel || !modifiedModel) return;

        // Get the original content for this change range
        let originalContent = '';
        if (change.originalStartLineNumber <= change.originalEndLineNumber) {
            originalContent = originalModel.getValueInRange(
                new monaco.Range(change.originalStartLineNumber, 1, change.originalEndLineNumber, originalModel.getLineMaxColumn(change.originalEndLineNumber)),
            );
        }

        // Calculate the range in the modified model to replace
        const modifiedEndLineNumber = change.modifiedEndLineNumber || change.modifiedStartLineNumber;
        const modifiedRange =
            change.modifiedStartLineNumber <= modifiedEndLineNumber
                ? new monaco.Range(change.modifiedStartLineNumber, 1, modifiedEndLineNumber, modifiedModel.getLineMaxColumn(modifiedEndLineNumber))
                : new monaco.Range(change.modifiedStartLineNumber, 1, change.modifiedStartLineNumber, 1);

        // Apply the edit to revert the change
        modifiedModel.pushEditOperations(
            [],
            [
                {
                    range: modifiedRange,
                    text: originalContent,
                },
            ],
            () => null,
        );
    }

    /**
     * Sets up listeners that adjust the height of the editor to the height of its current content.
     */
    setupContentHeightListeners(): void {
        const editors = [this._editor.getOriginalEditor(), this._editor.getModifiedEditor()];
        editors.forEach((editor) => {
            const contentSizeListener = editor.onDidContentSizeChange(() => {
                if (!this.enableResize()) {
                    this.adjustContainerHeight(this.getMaximumContentHeight());
                }
            });

            const hiddenAreaListener = editor.onDidChangeHiddenAreas(() => {
                if (!this.enableResize()) {
                    this.adjustContainerHeight(this.getContentHeightOfEditor(editor));
                }
            });

            this.listeners.push(contentSizeListener, hiddenAreaListener);
        });
    }

    /**
     * Adjusts the height of the editor's container to fit the new content height
     * and relayouts the diff editor.
     */
    adjustContainerHeight(newContentHeight: number) {
        this.monacoDiffEditorContainerElement.style.height = `${newContentHeight}px`;

        const container = this.editorContainer().nativeElement;
        this._editor.layout({
            width: container.clientWidth,
            height: container.clientHeight,
        });
    }

    setFileContents(original?: string, modified?: string, originalFileName?: string, modifiedFileName?: string): void {
        this.onReadyForDisplayChange.emit({ ready: false, lineChange: { addedLineCount: 0, removedLineCount: 0 } });

        const originalModelUri = monaco.Uri.parse(`inmemory://model/original-${this._editor.getId()}/${originalFileName ?? 'left'}`);
        const modifiedModelUri = monaco.Uri.parse(`inmemory://model/modified-${this._editor.getId()}/${modifiedFileName ?? 'right'}`);

        const originalModel = monaco.editor.getModel(originalModelUri) ?? monaco.editor.createModel(original ?? '', 'markdown', originalModelUri);
        const modifiedModel = monaco.editor.getModel(modifiedModelUri) ?? monaco.editor.createModel(modified ?? '', 'markdown', modifiedModelUri);

        originalModel.setValue(original ?? '');
        modifiedModel.setValue(modified ?? '');

        monaco.editor.setModelLanguage(originalModel, 'markdown');
        monaco.editor.setModelLanguage(modifiedModel, 'markdown');

        this._editor.setModel({ original: originalModel, modified: modifiedModel });

        // Only auto-adjust height when resize is disabled
        // When resize is enabled, the user controls the height via targetWrapperHeight
        if (!this.enableResize()) {
            this.adjustContainerHeight(this.getMaximumContentHeight());
        }
    }

    convertMonacoLineChanges(monacoLineChanges: monaco.editor.ILineChange[]): LineChange {
        const lineChange: LineChange = { addedLineCount: 0, removedLineCount: 0 };

        for (const change of monacoLineChanges ?? []) {
            const addedLines = change.modifiedEndLineNumber >= change.modifiedStartLineNumber ? change.modifiedEndLineNumber - change.modifiedStartLineNumber + 1 : 0;

            const removedLines = change.originalEndLineNumber >= change.originalStartLineNumber ? change.originalEndLineNumber - change.originalStartLineNumber + 1 : 0;

            lineChange.addedLineCount += addedLines;
            lineChange.removedLineCount += removedLines;
        }

        return lineChange;
    }

    getMaximumContentHeight(): number {
        return Math.max(this.getContentHeightOfEditor(this._editor.getOriginalEditor()), this.getContentHeightOfEditor(this._editor.getModifiedEditor()));
    }

    getContentHeightOfEditor(editor: monaco.editor.IStandaloneCodeEditor): number {
        return editor.getScrollHeight();
    }

    /**
     * Constrains the drag position of the resize handle.
     */
    constrainDragPosition(pointerPosition: Point): Point {
        const wrapperTop = this.wrapper().nativeElement.getBoundingClientRect().top;
        const minY = wrapperTop + this.resizableMinHeight();
        const maxY = wrapperTop + this.resizableMaxHeight();
        return {
            x: pointerPosition.x,
            y: Math.max(minY, Math.min(maxY, pointerPosition.y)),
        };
    }

    /**
     * Called when the user moves the resize handle.
     * Only updates targetWrapperHeight - the ResizeObserver handles the layout call.
     */
    onResizeMoved(event: CdkDragMove) {
        const wrapperTop = this.wrapper().nativeElement.getBoundingClientRect().top;
        const dragElemHeight = this.getElementClientHeight(event.source.element.nativeElement);
        const newHeight = event.pointerPosition.y - wrapperTop - dragElemHeight / 2;

        if (newHeight >= this.resizableMinHeight() && newHeight <= this.resizableMaxHeight()) {
            this.targetWrapperHeight = newHeight;
            // ResizeObserver will handle the layout call when the wrapper height changes
        }

        event.source.reset();
    }

    getElementClientHeight(element: HTMLElement): number {
        return element.clientHeight;
    }

    getText(): MonacoEditorDiffText {
        const original = this._editor.getOriginalEditor().getValue();
        const modified = this._editor.getModifiedEditor().getValue();
        return { original, modified };
    }

    executeAction(action: TextEditorAction): void {
        const modifiedEditor = this._editor.getModifiedEditor();
        const textEditor = new MonacoTextEditorAdapter(modifiedEditor);
        action.run(textEditor);
    }

    executeDomainAction(action: TextEditorDomainActionWithOptions, value: any): void {
        const modifiedEditor = this._editor.getModifiedEditor();
        const textEditor = new MonacoTextEditorAdapter(modifiedEditor);
        action.run(textEditor, { selectedItem: value });
    }

    openColorSelector(event: MouseEvent): void {
        const selector = this.colorSelector();
        if (selector) {
            selector.openColorSelector(event, this.colorPickerMarginTop, this.colorPickerHeight);
        }
    }

    onSelectColor(selectedColor: string): void {
        const colorAction = this.colorAction();
        if (colorAction) {
            const modifiedEditor = this._editor.getModifiedEditor();
            const textEditor = new MonacoTextEditorAdapter(modifiedEditor);
            colorAction.run(textEditor, { color: selectedColor });
        }
    }
}
