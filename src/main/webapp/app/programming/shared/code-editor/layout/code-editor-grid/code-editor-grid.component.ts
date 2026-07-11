import { Component, ElementRef, HostListener, Renderer2, ViewEncapsulation, afterNextRender, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { InteractableEvent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { faGripLines, faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { CollapsableCodeEditorElement } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ResizeType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ResizableDirective, ResizableSizeEvent } from 'app/shared-ui/directives/resizable.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-code-editor-grid',
    templateUrl: './code-editor-grid.component.html',
    styleUrls: ['./code-editor-grid.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [FaIconComponent, ResizableDirective, ArtemisTranslatePipe],
})
export class CodeEditorGridComponent {
    private renderer = inject(Renderer2);

    readonly editorWrapperElement = viewChild<ElementRef<HTMLElement>>('editorWrapper');
    readonly buildOutputElement = viewChild.required<ElementRef>('buildOutput');
    readonly fileBrowserElement = viewChild.required<ElementRef>('fileBrowser');
    readonly instructionsElement = viewChild.required<ElementRef>('instructions');

    readonly isTutorAssessment = input(false);
    readonly showEditorNavbar = input(true);
    readonly showEditorSidebarRight = input(true);
    readonly onResize = output<ResizeType>();

    readonly fileBrowserIsCollapsed = signal(false);
    readonly rightPanelIsCollapsed = signal(false);
    readonly buildOutputIsCollapsed = signal(false);

    private readonly viewport = signal({ width: window.innerWidth, height: window.innerHeight });
    protected readonly resizableMinHeightMain = computed(() => Math.min(500, Math.max(192, this.viewport().height / 3)));
    protected readonly resizableMinWidthLeft = computed(() => Math.min(310, Math.max(128, this.viewport().width / 7)));
    protected readonly resizableMinWidthRight = computed(() => Math.min(500, Math.max(160, this.viewport().width / 6)));
    protected readonly resizableMinHeightBottom = computed(() => Math.min(200, Math.max(128, this.viewport().height / 6)));
    protected readonly panelSizes = signal({ heightMain: 500, heightBottom: 200, widthLeft: 310, widthRight: 500 });

    // Keep enough space for the editor and resize grips when a sidebar grows.
    private static readonly EDITOR_CENTER_MIN_WIDTH = 300;
    private static readonly VERTICAL_BUFFER_PX = 40;
    private static readonly HORIZONTAL_BUFFER_PX = 24;

    // Recomputed only after layout changes to avoid layout reads during editor change detection.
    protected readonly maxConstraints = signal({
        heightMain: 1200,
        heightBottom: 600,
        widthLeft: window.innerWidth / 2,
        widthRight: window.innerWidth / 1.3,
    });

    protected readonly ResizeType = ResizeType;

    // Icons
    faGripLines = faGripLines;
    faGripLinesVertical = faGripLinesVertical;

    constructor() {
        afterNextRender(() => this.recomputeMaxConstraints());
    }

    @HostListener('window:resize')
    onWindowResize(): void {
        this.viewport.set({ width: window.innerWidth, height: window.innerHeight });
        this.recomputeMaxConstraints();
    }

    /** Re-emit a panel resize and refresh the sum-aware maxima so the next drag accounts for the new sizes. */
    protected onPanelResized(type: ResizeType, size?: number): void {
        const element = this.elementForResizeType(type);
        const dimension = type === ResizeType.SIDEBAR_LEFT || type === ResizeType.SIDEBAR_RIGHT ? 'width' : 'height';
        const measuredSize = size ?? (dimension === 'width' ? element.offsetWidth : element.offsetHeight);
        this.updatePanelSize(type, measuredSize);
        this.recomputeMaxConstraints();
        this.onResize.emit(type);
    }

    protected onPanelResizeMove(type: ResizeType, size: ResizableSizeEvent): void {
        const horizontalResize = type === ResizeType.SIDEBAR_LEFT || type === ResizeType.SIDEBAR_RIGHT;
        this.updatePanelSize(type, horizontalResize ? size.width : size.height);
    }

    onResizeHandleKeydown(event: KeyboardEvent, type: ResizeType): void {
        const step = 20;
        const element = this.elementForResizeType(type);
        const horizontalResize = type === ResizeType.SIDEBAR_LEFT || type === ResizeType.SIDEBAR_RIGHT;
        const currentSize = horizontalResize ? element.offsetWidth : element.offsetHeight;
        const minimum = this.minimumForResizeType(type);
        const maximum = this.maximumForResizeType(type);
        let nextSize: number | undefined;
        switch (event.key) {
            case 'ArrowLeft':
                if (horizontalResize) {
                    nextSize = currentSize + (type === ResizeType.SIDEBAR_RIGHT ? step : -step);
                }
                break;
            case 'ArrowRight':
                if (horizontalResize) {
                    nextSize = currentSize + (type === ResizeType.SIDEBAR_RIGHT ? -step : step);
                }
                break;
            case 'ArrowUp':
                if (!horizontalResize) {
                    nextSize = currentSize + (type === ResizeType.BOTTOM ? step : -step);
                }
                break;
            case 'ArrowDown':
                if (!horizontalResize) {
                    nextSize = currentSize + (type === ResizeType.BOTTOM ? -step : step);
                }
                break;
            case 'Home':
                nextSize = minimum;
                break;
            case 'End':
                nextSize = maximum;
                break;
        }
        if (nextSize === undefined) {
            return;
        }

        event.preventDefault();
        const size = Math.round(Math.max(minimum, Math.min(maximum, nextSize)));
        this.renderer.setStyle(element, horizontalResize ? 'width' : 'height', `${size}px`);
        this.onPanelResized(type, size);
    }

    private elementForResizeType(type: ResizeType): HTMLElement {
        switch (type) {
            case ResizeType.SIDEBAR_LEFT:
                return this.fileBrowserElement().nativeElement;
            case ResizeType.SIDEBAR_RIGHT:
                return this.instructionsElement().nativeElement;
            case ResizeType.MAIN_BOTTOM:
                return this.editorWrapperElement()!.nativeElement.querySelector<HTMLElement>('.editor-main')!;
            case ResizeType.BOTTOM:
                return this.buildOutputElement().nativeElement;
        }
    }

    private minimumForResizeType(type: ResizeType): number {
        switch (type) {
            case ResizeType.SIDEBAR_LEFT:
                return this.resizableMinWidthLeft();
            case ResizeType.SIDEBAR_RIGHT:
                return this.resizableMinWidthRight();
            case ResizeType.MAIN_BOTTOM:
                return this.resizableMinHeightMain();
            case ResizeType.BOTTOM:
                return this.resizableMinHeightBottom();
        }
    }

    private maximumForResizeType(type: ResizeType): number {
        const constraints = this.maxConstraints();
        switch (type) {
            case ResizeType.SIDEBAR_LEFT:
                return constraints.widthLeft;
            case ResizeType.SIDEBAR_RIGHT:
                return constraints.widthRight;
            case ResizeType.MAIN_BOTTOM:
                return constraints.heightMain;
            case ResizeType.BOTTOM:
                return constraints.heightBottom;
        }
    }

    private updatePanelSize(type: ResizeType, size: number): void {
        this.panelSizes.update((sizes) => {
            switch (type) {
                case ResizeType.SIDEBAR_LEFT:
                    return { heightMain: sizes.heightMain, heightBottom: sizes.heightBottom, widthLeft: size, widthRight: sizes.widthRight };
                case ResizeType.SIDEBAR_RIGHT:
                    return { heightMain: sizes.heightMain, heightBottom: sizes.heightBottom, widthLeft: sizes.widthLeft, widthRight: size };
                case ResizeType.MAIN_BOTTOM:
                    return { heightMain: size, heightBottom: sizes.heightBottom, widthLeft: sizes.widthLeft, widthRight: sizes.widthRight };
                case ResizeType.BOTTOM:
                    return { heightMain: sizes.heightMain, heightBottom: size, widthLeft: sizes.widthLeft, widthRight: sizes.widthRight };
            }
        });
    }

    private recomputeMaxConstraints(): void {
        const wrapper = this.editorWrapperElement()?.nativeElement;
        if (!wrapper) {
            return;
        }
        const main = wrapper.querySelector<HTMLElement>('.editor-main');
        const bottom = wrapper.querySelector<HTMLElement>('.editor-bottom');
        const content = wrapper.querySelector<HTMLElement>('.editor-main__content');
        const left = wrapper.querySelector<HTMLElement>('.editor-sidebar-left');
        const right = wrapper.querySelector<HTMLElement>('.editor-sidebar-right');

        const mainTop = (main ?? wrapper).getBoundingClientRect().top;
        const availableHeight = Math.max(0, window.innerHeight - mainTop - CodeEditorGridComponent.VERTICAL_BUFFER_PX);
        const availableWidth = content?.clientWidth ?? window.innerWidth;
        const reservedWidth = CodeEditorGridComponent.EDITOR_CENTER_MIN_WIDTH + CodeEditorGridComponent.HORIZONTAL_BUFFER_PX;

        const constraints = {
            heightMain: Math.max(this.resizableMinHeightMain(), Math.min(1200, availableHeight - (bottom?.offsetHeight ?? this.resizableMinHeightBottom()))),
            heightBottom: Math.max(this.resizableMinHeightBottom(), Math.min(600, availableHeight - (main?.offsetHeight ?? this.resizableMinHeightMain()))),
            widthLeft: Math.max(this.resizableMinWidthLeft(), Math.min(this.viewport().width / 2, availableWidth - (right?.offsetWidth ?? 0) - reservedWidth)),
            widthRight: Math.max(this.resizableMinWidthRight(), Math.min(this.viewport().width / 1.3, availableWidth - (left?.offsetWidth ?? 0) - reservedWidth)),
        };
        this.maxConstraints.set(constraints);

        this.syncPanelSize(ResizeType.MAIN_BOTTOM, main, this.resizableMinHeightMain(), constraints.heightMain, false);
        this.syncPanelSize(ResizeType.BOTTOM, bottom, this.resizableMinHeightBottom(), constraints.heightBottom, this.buildOutputIsCollapsed());
        this.syncPanelSize(ResizeType.SIDEBAR_LEFT, left, this.resizableMinWidthLeft(), constraints.widthLeft, this.fileBrowserIsCollapsed());
        this.syncPanelSize(ResizeType.SIDEBAR_RIGHT, right, this.resizableMinWidthRight(), constraints.widthRight, this.rightPanelIsCollapsed());
    }

    private syncPanelSize(type: ResizeType, element: HTMLElement | null, minimum: number, maximum: number, collapsed: boolean): void {
        if (!element || collapsed) {
            return;
        }
        const horizontalResize = type === ResizeType.SIDEBAR_LEFT || type === ResizeType.SIDEBAR_RIGHT;
        const actualSize = horizontalResize ? element.offsetWidth : element.offsetHeight;
        if (!actualSize) {
            return;
        }
        const size = Math.round(Math.max(minimum, Math.min(maximum, actualSize)));
        if (size !== actualSize) {
            this.renderer.setStyle(element, horizontalResize ? 'width' : 'height', `${size}px`);
        }
        this.updatePanelSize(type, size);
    }

    private elementRefForCollapsableElement(collapsableElement: CollapsableCodeEditorElement): ElementRef {
        switch (collapsableElement) {
            case CollapsableCodeEditorElement.BuildOutput:
                return this.buildOutputElement();
            case CollapsableCodeEditorElement.FileBrowser:
                return this.fileBrowserElement();
            case CollapsableCodeEditorElement.Instructions:
                return this.instructionsElement();
        }
    }

    /**
     * Collapse parts of the editor (file browser, build output, or instructions)
     * @param interactableEvent {object} The custom event object with additional information
     * @param collapsableElement an enum to decide which card is collapsed
     */
    toggleCollapse(interactableEvent: InteractableEvent, collapsableElement: CollapsableCodeEditorElement) {
        const horizontal = interactableEvent.horizontal;
        const cardElement = this.elementRefForCollapsableElement(collapsableElement);

        const collapsed = `collapsed--${horizontal ? 'horizontal' : 'vertical'}`;

        if (cardElement.nativeElement.classList.contains(collapsed)) {
            this.renderer.removeClass(cardElement.nativeElement, collapsed);
        } else {
            this.renderer.addClass(cardElement.nativeElement, collapsed);
        }

        switch (collapsableElement) {
            case CollapsableCodeEditorElement.Instructions: {
                this.rightPanelIsCollapsed.update((value) => !value);
                break;
            }
            case CollapsableCodeEditorElement.FileBrowser: {
                this.fileBrowserIsCollapsed.update((value) => !value);
                break;
            }
            case CollapsableCodeEditorElement.BuildOutput: {
                this.buildOutputIsCollapsed.update((value) => !value);
                break;
            }
        }

        this.recomputeMaxConstraints();
    }

    /** Opens the shared bottom area without toggling an already-open panel closed. */
    expandBottomPanel(): void {
        if (!this.buildOutputIsCollapsed()) {
            return;
        }
        this.renderer.removeClass(this.buildOutputElement().nativeElement, 'collapsed--vertical');
        this.buildOutputIsCollapsed.set(false);
        this.recomputeMaxConstraints();
    }
}
