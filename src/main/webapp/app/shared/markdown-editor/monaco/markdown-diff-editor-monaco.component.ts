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
import { CdkDrag, CdkDragMove, Point } from '@angular/cdk/drag-drop';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
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
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { BoldAction } from 'app/shared/monaco-editor/model/actions/bold.action';
import { ItalicAction } from 'app/shared/monaco-editor/model/actions/italic.action';
import { UnderlineAction } from 'app/shared/monaco-editor/model/actions/underline.action';
import { StrikethroughAction } from 'app/shared/monaco-editor/model/actions/strikethrough.action';
import { QuoteAction } from 'app/shared/monaco-editor/model/actions/quote.action';
import { CodeAction } from 'app/shared/monaco-editor/model/actions/code.action';
import { CodeBlockAction } from 'app/shared/monaco-editor/model/actions/code-block.action';
import { UrlAction } from 'app/shared/monaco-editor/model/actions/url.action';
import { AttachmentAction } from 'app/shared/monaco-editor/model/actions/attachment.action';
import { OrderedListAction } from 'app/shared/monaco-editor/model/actions/ordered-list.action';
import { BulletedListAction } from 'app/shared/monaco-editor/model/actions/bulleted-list.action';
import { MonacoEditorService } from 'app/shared/monaco-editor/service/monaco-editor.service';
import * as monaco from 'monaco-editor';

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
    /**
     * Color mapping from hex codes to CSS class names.
     */
    private readonly colorToClassMap = new Map<string, string>([
        ['#ca2024', 'red'],
        ['#3ea119', 'green'],
        ['#ffffff', 'white'],
        ['#000000', 'black'],
        ['#fffa5c', 'yellow'],
        ['#0d3cc2', 'blue'],
        ['#b05db8', 'lila'],
        ['#d86b1f', 'orange'],
    ]);

    // Injected services
    private readonly renderer = inject(Renderer2);
    private readonly monacoEditorService = inject(MonacoEditorService);

    // Monaco diff editor instance - created directly in this component
    private _diffEditor!: monaco.editor.IStandaloneDiffEditor;
    private diffEditorContainerElement!: HTMLElement;

    fullElement = viewChild.required<ElementRef<HTMLDivElement>>('fullElement');
    wrapper = viewChild.required<ElementRef<HTMLDivElement>>('wrapper');
    diffEditorHost = viewChild.required<ElementRef<HTMLDivElement>>('diffEditorHost');
    resizePlaceholder = viewChild<ElementRef<HTMLDivElement>>('resizePlaceholder');
    colorSelector = viewChild<ColorSelectorComponent>(ColorSelectorComponent);

    // Inputs
    allowSplitView = input<boolean>(true);
    enableResize = input<boolean>(true);
    fillHeight = input<boolean>(false);
    initialEditorHeight = input<number>(MarkdownEditorHeight.MEDIUM);
    resizableMinHeight = input<number>(MarkdownEditorHeight.SMALL);
    resizableMaxHeight = input<number>(MarkdownEditorHeight.LARGE);
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

    // Create default actions inline
    defaultActions = input<TextEditorAction[]>(this.createDefaultActions());
    lectureReferenceAction = input<LectureAttachmentReferenceAction | undefined>(undefined);
    colorAction = input<ColorAction | undefined>(new ColorAction());
    domainActions = input<TextEditorDomainAction[]>([]);
    metaActions = input<TextEditorAction[]>([new FullscreenAction()]);

    // Domain actions split by type – used by the template
    domainActionsWithoutOptions = computed(() => this.domainActions().filter((action) => !(action instanceof TextEditorDomainActionWithOptions)));
    domainActionsWithOptions = computed(() => this.domainActions().filter((action) => action instanceof TextEditorDomainActionWithOptions) as TextEditorDomainActionWithOptions[]);

    // Colors for color picker
    colorSignal = signal<string[]>([...this.colorToClassMap.keys()]);
    readonly colorPickerMarginTop = 35;
    readonly colorPickerHeight = 110;

    targetWrapperHeight = MarkdownEditorHeight.MEDIUM;
    minWrapperHeight = MarkdownEditorHeight.SMALL;
    constrainDragPositionFn?: (pointerPosition: Point) => Point;
    isResizing = false;
    resizeObserver?: ResizeObserver;
    private resizeAnimationFrame?: number;

    // Icons
    readonly faGripLines = faGripLines;

    // Subscriptions and listeners
    listeners: Disposable[] = [];
    private fullscreenHandler = this.onFullscreenChange.bind(this);

    constructor() {
        effect(() => {
            // React to allowSplitView and readOnly input changes
            if (this._diffEditor) {
                this._diffEditor.updateOptions({
                    renderSideBySide: this.allowSplitView(),
                });
                this._diffEditor.getModifiedEditor().updateOptions({ readOnly: this.readOnly() });
            }
        });
    }

    ngAfterViewInit(): void {
        // Create the diff editor container and inject it into the host element
        this.diffEditorContainerElement = this.renderer.createElement('div');
        this.renderer.addClass(this.diffEditorContainerElement, 'diff-editor-container');
        this.renderer.appendChild(this.diffEditorHost().nativeElement, this.diffEditorContainerElement);

        // Create the Monaco diff editor directly
        this._diffEditor = this.monacoEditorService.createStandaloneDiffEditor(this.diffEditorContainerElement);

        // Set up diff update listener
        this.setupDiffListener();

        // Apply initial options
        this._diffEditor.updateOptions({
            renderSideBySide: this.allowSplitView(),
        });
        this._diffEditor.getModifiedEditor().updateOptions({ readOnly: this.readOnly() });

        // Set up fullscreen actions
        this.metaActions()
            .filter((a) => a instanceof FullscreenAction)
            .forEach((fs) => {
                (fs as FullscreenAction).element = this.fullElement().nativeElement;
            });

        this.targetWrapperHeight = this.initialEditorHeight();
        this.minWrapperHeight = this.resizableMinHeight();
        this.constrainDragPositionFn = this.constrainDragPosition.bind(this);

        // Initialize the diff editor to fill its container
        setTimeout(() => this.fillContainer(), 0);

        // Set up resize observer to trigger layout on container size changes
        this.resizeObserver = new ResizeObserver(() => {
            if (this.resizeAnimationFrame) {
                window.cancelAnimationFrame(this.resizeAnimationFrame);
            }
            this.resizeAnimationFrame = requestAnimationFrame(() => {
                this.layout();
            });
        });
        this.resizeObserver.observe(this.wrapper().nativeElement);

        // Listen for fullscreen changes to trigger layout recalculation
        this.fullElement().nativeElement.addEventListener('fullscreenchange', this.fullscreenHandler);
    }

    /**
     * Sets up a listener that responds to changes in the diff.
     */
    private setupDiffListener(): void {
        const diffListener = this._diffEditor.onDidUpdateDiff(() => {
            const monacoLineChanges = this._diffEditor.getLineChanges() ?? [];
            this.onReadyForDisplayChange.emit({ ready: true, lineChange: this.convertMonacoLineChanges(monacoLineChanges) });
        });

        this.listeners.push(diffListener);
    }

    // Note: We intentionally do NOT auto-size the container to content height.
    // This allows the Monaco editor to handle scrolling internally within its fixed container.

    /**
     * Converts Monaco line changes to a LineChange object.
     */
    private convertMonacoLineChanges(monacoLineChanges: monaco.editor.ILineChange[]): LineChange {
        const lineChange: LineChange = { addedLineCount: 0, removedLineCount: 0 };
        if (!monacoLineChanges) {
            return lineChange;
        }

        for (const change of monacoLineChanges) {
            const addedLines = change.modifiedEndLineNumber >= change.modifiedStartLineNumber ? change.modifiedEndLineNumber - change.modifiedStartLineNumber + 1 : 0;
            const removedLines = change.originalEndLineNumber >= change.originalStartLineNumber ? change.originalEndLineNumber - change.originalStartLineNumber + 1 : 0;
            lineChange.addedLineCount += addedLines;
            lineChange.removedLineCount += removedLines;
        }

        return lineChange;
    }

    /**
     * Handles fullscreen change events to recalculate editor layout.
     */
    private onFullscreenChange(): void {
        setTimeout(() => this.layout(), 100);
    }

    ngOnDestroy(): void {
        if (this.resizeAnimationFrame) {
            window.cancelAnimationFrame(this.resizeAnimationFrame);
        }
        this.resizeObserver?.disconnect();
        this.fullElement().nativeElement.removeEventListener('fullscreenchange', this.fullscreenHandler);
        this.listeners.forEach((listener) => listener.dispose());
        this._diffEditor?.dispose();
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
     */
    onResizeMoved(event: CdkDragMove) {
        const wrapperTop = this.wrapper().nativeElement.getBoundingClientRect().top;
        const dragElemHeight = this.getElementClientHeight(event.source.element.nativeElement);
        const newHeight = event.pointerPosition.y - wrapperTop - dragElemHeight / 2;

        if (newHeight >= this.resizableMinHeight() && newHeight <= this.resizableMaxHeight()) {
            this.targetWrapperHeight = newHeight;
        }

        event.source.reset();
    }

    getElementClientHeight(element: HTMLElement): number {
        return element.clientHeight;
    }

    /**
     * Manually triggers a layout recalculation.
     */
    layout(): void {
        const container = this.diffEditorHost()?.nativeElement;
        if (container && container.clientWidth > 0 && container.clientHeight > 0) {
            this._diffEditor.layout({ width: container.clientWidth, height: container.clientHeight });
        }
    }

    /**
     * Sets up the editor to fill its container using CSS.
     */
    fillContainer(): void {
        this.diffEditorContainerElement.style.width = '100%';
        this.diffEditorContainerElement.style.height = '100%';
        this.layout();
    }

    /**
     * Sets the file contents for diff comparison.
     */
    setFileContents(original?: string, modified?: string, originalFileName?: string, modifiedFileName?: string): void {
        // Reset ready state when loading new content
        this.onReadyForDisplayChange.emit({ ready: false, lineChange: { addedLineCount: 0, removedLineCount: 0 } });

        const originalModelUri = monaco.Uri.parse(`inmemory://model/original-${this._diffEditor.getId()}/${originalFileName ?? 'left'}`);
        const modifiedFileUri = monaco.Uri.parse(`inmemory://model/modified-${this._diffEditor.getId()}/${modifiedFileName ?? 'right'}`);
        const originalModel = monaco.editor.getModel(originalModelUri) ?? monaco.editor.createModel(original ?? '', undefined, originalModelUri);
        const modifiedModel = monaco.editor.getModel(modifiedFileUri) ?? monaco.editor.createModel(modified ?? '', undefined, modifiedFileUri);

        originalModel.setValue(original ?? '');
        modifiedModel.setValue(modified ?? '');

        monaco.editor.setModelLanguage(originalModel, originalModel.getLanguageId());
        monaco.editor.setModelLanguage(modifiedModel, modifiedModel.getLanguageId());

        const newModel = {
            original: originalModel,
            modified: modifiedModel,
        };

        this._diffEditor.setModel(newModel);
    }

    /**
     * Returns the current text from both editors.
     */
    getText(): MonacoEditorDiffText {
        const original = this._diffEditor.getOriginalEditor().getValue();
        const modified = this._diffEditor.getModifiedEditor().getValue();
        return { original, modified };
    }

    /**
     * Returns the modified (right) editor instance.
     */
    getModifiedEditor(): monaco.editor.IStandaloneCodeEditor {
        return this._diffEditor.getModifiedEditor();
    }

    /**
     * Executes a toolbar action on the modified editor.
     */
    executeAction(action: TextEditorAction): void {
        const modifiedEditor = this._diffEditor.getModifiedEditor();
        const adapter = new MonacoTextEditorAdapter(modifiedEditor);
        if (action instanceof FullscreenAction) {
            action.element = this.fullElement().nativeElement;
        }
        action.run(adapter);
    }

    /**
     * Executes a domain action with options.
     */
    executeDomainAction(action: TextEditorDomainActionWithOptions, value: { value: string; id: string }): void {
        const modifiedEditor = this._diffEditor.getModifiedEditor();
        const adapter = new MonacoTextEditorAdapter(modifiedEditor);
        action.run(adapter, { selectedItem: value });
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
            const colorName = this.colorToClassMap.get(selectedColor);
            if (colorName) {
                const modifiedEditor = this._diffEditor.getModifiedEditor();
                const adapter = new MonacoTextEditorAdapter(modifiedEditor);
                colorAction.run(adapter, { color: colorName });
            }
        }
    }

    /**
     * Creates a new array of default markdown actions.
     */
    private createDefaultActions(): TextEditorAction[] {
        return [
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
        ];
    }
}
