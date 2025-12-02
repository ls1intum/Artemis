import {
    AfterViewInit,
    ChangeDetectionStrategy,
    Component,
    ElementRef,
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
import { faGripLines, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
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
    resizePlaceholder = viewChild<ElementRef<HTMLDivElement>>('resizePlaceholder');
    colorSelector = viewChild<ColorSelectorComponent>(ColorSelectorComponent);

    // Inputs
    allowSplitView = input<boolean>(true);
    enableResize = input<boolean>(true);
    enableFileUpload = input<boolean>(true);
    initialEditorHeight = input<number>(300);
    resizableMinHeight = input<number>(200);
    resizableMaxHeight = input<number>(800);
    allowedFileExtensions = input<string>('.png, .jpg, .jpeg, .gif, .svg, .pdf');
    maxFileSize = input<number>(5); // MB

    // Outputs
    onReadyForDisplayChange = output<{ ready: boolean; lineChange: LineChange }>();
    onFileUpload = output<FileList>();
    onFileDrop = output<DragEvent>();

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

    // Icons
    readonly faGripLines = faGripLines;
    readonly faQuestionCircle = faQuestionCircle;

    /*
     * Subscriptions and listeners that need to be disposed of when this component is destroyed.
     */
    listeners: Disposable[] = [];

    /*
     * Injected services and elements.
     */
    private readonly renderer = inject(Renderer2);
    private readonly monacoEditorService = inject(MonacoEditorService);

    constructor() {
        // Create editor container, real attachment happens in ngAfterViewInit
        this.monacoDiffEditorContainerElement = this.renderer.createElement('div');
        this._editor = this.monacoEditorService.createStandaloneDiffEditor(this.monacoDiffEditorContainerElement);
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
        });
    }

    ngAfterViewInit(): void {
        this.renderer.appendChild(this.wrapper().nativeElement, this.monacoDiffEditorContainerElement);

        this.metaActions()
            .filter((a) => a instanceof FullscreenAction)
            .forEach((fs) => {
                (fs as FullscreenAction).element = this.fullElement().nativeElement;
            });

        this.targetWrapperHeight = this.initialEditorHeight();
        this.minWrapperHeight = this.resizableMinHeight();
        this.constrainDragPositionFn = this.constrainDragPosition.bind(this);

        this._editor.layout({
            width: this.wrapper().nativeElement.clientWidth,
            height: this.wrapper().nativeElement.clientHeight,
        });
    }

    ngOnDestroy(): void {
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
            this.onReadyForDisplayChange.emit({
                ready: true,
                lineChange: this.convertMonacoLineChanges(monacoLineChanges),
            });
        });

        this.listeners.push(diffListener);
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

        this._editor.layout({
            width: this.wrapper().nativeElement.clientWidth,
            height: this.wrapper().nativeElement.clientHeight,
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

        this.adjustContainerHeight(this.getMaximumContentHeight());
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
     */
    onResizeMoved(event: CdkDragMove) {
        const wrapperTop = this.wrapper().nativeElement.getBoundingClientRect().top;
        const dragElemHeight = this.getElementClientHeight(event.source.element.nativeElement);
        const newHeight = event.pointerPosition.y - wrapperTop - dragElemHeight / 2;

        if (newHeight >= this.resizableMinHeight() && newHeight <= this.resizableMaxHeight()) {
            this.targetWrapperHeight = newHeight;
            this._editor.layout();
        }

        event.source.reset();
    }

    getElementClientHeight(element: HTMLElement): number {
        return element.clientHeight;
    }

    handleFileUpload(event: Event): void {
        const inputElem = event.target as HTMLInputElement;
        if (inputElem.files && inputElem.files.length > 0) {
            this.onFileUpload.emit(inputElem.files);
        }
    }

    handleFileDrop(event: DragEvent): void {
        event.preventDefault();
        this.onFileDrop.emit(event);
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
