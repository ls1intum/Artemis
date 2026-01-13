import {
    AfterViewInit,
    ChangeDetectionStrategy,
    Component,
    ElementRef,
    HostBinding,
    OnDestroy,
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
import { MonacoDiffEditorComponent, MonacoEditorDiffText } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';
import { MarkdownEditorToolbarService } from 'app/shared/markdown-editor/service/markdown-editor-toolbar.service';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

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
        MonacoDiffEditorComponent,
    ],
})
export class MarkdownDiffEditorMonacoComponent implements AfterViewInit, OnDestroy {
    private readonly toolbarService = inject(MarkdownEditorToolbarService);

    fullElement = viewChild.required<ElementRef<HTMLDivElement>>('fullElement');
    wrapper = viewChild.required<ElementRef<HTMLDivElement>>('wrapper');
    resizePlaceholder = viewChild<ElementRef<HTMLDivElement>>('resizePlaceholder');
    colorSelector = viewChild<ColorSelectorComponent>(ColorSelectorComponent);
    diffEditorComponent = viewChild<MonacoDiffEditorComponent>('diffEditor');

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

    // Use service to create default actions
    defaultActions = input<TextEditorAction[]>(this.toolbarService.createDefaultActions());
    lectureReferenceAction = input<LectureAttachmentReferenceAction | undefined>(undefined);
    colorAction = input<ColorAction | undefined>(new ColorAction());
    domainActions = input<TextEditorDomainAction[]>([]);
    metaActions = input<TextEditorAction[]>(this.toolbarService.createMetaActions());

    // Domain actions split by type – used by the template
    domainActionsWithoutOptions = computed(() => this.domainActions().filter((action) => !(action instanceof TextEditorDomainActionWithOptions)));
    domainActionsWithOptions = computed(() => this.domainActions().filter((action) => action instanceof TextEditorDomainActionWithOptions) as TextEditorDomainActionWithOptions[]);

    // Use service for color map
    colorSignal = signal<string[]>(this.toolbarService.getColors());
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
            const diffEditor = this.diffEditorComponent();
            if (diffEditor) {
                // Configure the diff editor via the embedded component
                // The MonacoDiffEditorComponent handles allowSplitView via its own input
            }
        });
    }

    ngAfterViewInit(): void {
        this.metaActions()
            .filter((a) => a instanceof FullscreenAction)
            .forEach((fs) => {
                (fs as FullscreenAction).element = this.fullElement().nativeElement;
            });

        this.targetWrapperHeight = this.initialEditorHeight();
        this.minWrapperHeight = this.resizableMinHeight();
        this.constrainDragPositionFn = this.constrainDragPosition.bind(this);

        // Initialize the embedded diff editor to fill its container
        const diffEditor = this.diffEditorComponent();
        if (diffEditor) {
            // Use setTimeout to ensure DOM is rendered
            setTimeout(() => diffEditor.fillContainer(), 0);
        }

        // Set up resize observer to trigger layout on container size changes
        this.resizeObserver = new ResizeObserver(() => {
            if (this.resizeAnimationFrame) {
                window.cancelAnimationFrame(this.resizeAnimationFrame);
            }
            this.resizeAnimationFrame = requestAnimationFrame(() => {
                const editor = this.diffEditorComponent();
                if (editor) {
                    editor.layout();
                }
            });
        });
        this.resizeObserver.observe(this.wrapper().nativeElement);

        // Listen for fullscreen changes to trigger layout recalculation
        this.fullElement().nativeElement.addEventListener('fullscreenchange', this.fullscreenHandler);
    }

    /**
     * Handles fullscreen change events to recalculate editor layout.
     */
    private onFullscreenChange(): void {
        const diffEditor = this.diffEditorComponent();
        if (diffEditor) {
            // Delay layout to allow fullscreen transition to complete
            setTimeout(() => diffEditor.layout(), 100);
        }
    }

    ngOnDestroy(): void {
        if (this.resizeAnimationFrame) {
            window.cancelAnimationFrame(this.resizeAnimationFrame);
        }
        this.resizeObserver?.disconnect();
        this.fullElement().nativeElement.removeEventListener('fullscreenchange', this.fullscreenHandler);
        this.listeners.forEach((listener) => listener.dispose());
    }

    /**
     * Handles the ready event from the embedded diff editor component.
     */
    onDiffEditorReady(event: { ready: boolean; lineChange: LineChange }): void {
        this.onReadyForDisplayChange.emit(event);
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
     * Sets the file contents for diff comparison.
     * Delegates to the embedded MonacoDiffEditorComponent.
     */
    setFileContents(original?: string, modified?: string, originalFileName?: string, modifiedFileName?: string): void {
        const diffEditor = this.diffEditorComponent();
        if (diffEditor) {
            diffEditor.setFileContents(original, modified, originalFileName, modifiedFileName);
        }
    }

    /**
     * Returns the current text from both editors.
     */
    getText(): MonacoEditorDiffText {
        const diffEditor = this.diffEditorComponent();
        if (diffEditor) {
            return diffEditor.getText();
        }
        return { original: '', modified: '' };
    }

    /**
     * Executes a toolbar action on the modified editor.
     */
    executeAction(action: TextEditorAction): void {
        const diffEditor = this.diffEditorComponent();
        if (diffEditor) {
            const modifiedEditor = diffEditor.getModifiedEditor();
            const adapter = new MonacoTextEditorAdapter(modifiedEditor);
            // Handle fullscreen action specially since it needs the fullElement
            if (action instanceof FullscreenAction) {
                action.element = this.fullElement().nativeElement;
            }
            action.run(adapter);
        }
    }

    /**
     * Executes a domain action with options.
     */
    executeDomainAction(action: TextEditorDomainActionWithOptions, value: { value: string; id: string }): void {
        const diffEditor = this.diffEditorComponent();
        if (diffEditor) {
            const modifiedEditor = diffEditor.getModifiedEditor();
            const adapter = new MonacoTextEditorAdapter(modifiedEditor);
            action.run(adapter, { selectedItem: value });
        }
    }

    openColorSelector(event: MouseEvent): void {
        const selector = this.colorSelector();
        if (selector) {
            selector.openColorSelector(event, this.colorPickerMarginTop, this.colorPickerHeight);
        }
    }

    onSelectColor(selectedColor: string): void {
        const colorAction = this.colorAction();
        const diffEditor = this.diffEditorComponent();
        if (colorAction && diffEditor) {
            const colorName = this.toolbarService.getColorClass(selectedColor);
            if (colorName) {
                const modifiedEditor = diffEditor.getModifiedEditor();
                const adapter = new MonacoTextEditorAdapter(modifiedEditor);
                colorAction.run(adapter, { color: colorName });
            }
        }
    }
}
