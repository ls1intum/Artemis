import {
    AfterViewInit,
    ChangeDetectionStrategy,
    Component,
    ElementRef,
    OnDestroy,
    Renderer2,
    ViewChild,
    ViewEncapsulation,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
} from '@angular/core';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
import { MonacoEditorService } from 'app/shared/monaco-editor/service/monaco-editor.service';
import * as monaco from 'monaco-editor';
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
    imports: [NgbDropdown, NgbDropdownToggle, NgbDropdownMenu, TranslateDirective, FaIconComponent, NgbTooltip, NgTemplateOutlet, ArtemisTranslatePipe, ColorSelectorComponent],
})
export class MarkdownDiffEditorMonacoComponent implements AfterViewInit, OnDestroy {
    private _editor: monaco.editor.IStandaloneDiffEditor;
    monacoDiffEditorContainerElement: HTMLElement;

    @ViewChild('fullElement', { static: true }) fullElement!: ElementRef<HTMLDivElement>;
    @ViewChild('wrapper', { static: true }) wrapper!: ElementRef<HTMLDivElement>;
    @ViewChild(ColorSelectorComponent, { static: false }) colorSelector?: ColorSelectorComponent;

    allowSplitView = input<boolean>(true);
    onReadyForDisplayChange = output<{ ready: boolean; lineChange: LineChange }>();

    // Markdown editing actions - configurable via signals
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
            });
        });
    }

    ngAfterViewInit(): void {
        // Append the diff editor container below the toolbar
        this.renderer.appendChild(this.wrapper.nativeElement, this.monacoDiffEditorContainerElement);

        // Wire fullscreen actions so they fullscreen the whole component (toolbar + editor)
        this.metaActions()
            .filter((a) => a instanceof FullscreenAction)
            .forEach((fs) => {
                (fs as FullscreenAction).element = this.fullElement.nativeElement;
            });

        // Initial layout
        this._editor.layout({
            width: this.wrapper.nativeElement.clientWidth,
            height: this.wrapper.nativeElement.clientHeight,
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
            this.adjustContainerHeight(this.getMaximumContentHeight());

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
            const contentSizeListener = editor.onDidContentSizeChange((e: monaco.editor.IContentSizeChangedEvent) => {
                if (e.contentHeightChanged) {
                    this.adjustContainerHeight(this.getMaximumContentHeight());
                }
            });

            const hiddenAreaListener = editor.onDidChangeHiddenAreas(() => {
                this.adjustContainerHeight(this.getContentHeightOfEditor(editor));
            });

            this.listeners.push(contentSizeListener, hiddenAreaListener);
        });
    }

    /**
     * Adjusts the height of the editor's container to fit the new content height
     * and relayouts the diff editor.
     */
    adjustContainerHeight(newContentHeight: number) {
        // Let the container grow within the flex wrapper; scrolling is handled by CSS
        this.monacoDiffEditorContainerElement.style.height = `${newContentHeight}px`;

        this._editor.layout({
            width: this.wrapper.nativeElement.clientWidth,
            height: this.wrapper.nativeElement.clientHeight,
        });
    }

    setFileContents(original?: string, modified?: string, originalFileName?: string, modifiedFileName?: string): void {
        this.onReadyForDisplayChange.emit({ ready: false, lineChange: { addedLineCount: 0, removedLineCount: 0 } });

        const originalModelUri = monaco.Uri.parse(`inmemory://model/original-${this._editor.getId()}/${originalFileName ?? 'left'}`);
        const modifiedFileUri = monaco.Uri.parse(`inmemory://model/modified-${this._editor.getId()}/${modifiedFileName ?? 'right'}`);

        const originalModel = monaco.editor.getModel(originalModelUri) ?? monaco.editor.createModel(original ?? '', 'markdown', originalModelUri);
        const modifiedModel = monaco.editor.getModel(modifiedFileUri) ?? monaco.editor.createModel(modified ?? '', 'markdown', modifiedFileUri);

        originalModel.setValue(original ?? '');
        modifiedModel.setValue(modified ?? '');

        monaco.editor.setModelLanguage(originalModel, 'markdown');
        monaco.editor.setModelLanguage(modifiedModel, 'markdown');

        this._editor.setModel({ original: originalModel, modified: modifiedModel });

        // Ensure layout after new content
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
        return editor.getContentHeight();
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
        if (this.colorSelector) {
            this.colorSelector.openColorSelector(event, this.colorPickerMarginTop, this.colorPickerHeight);
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
