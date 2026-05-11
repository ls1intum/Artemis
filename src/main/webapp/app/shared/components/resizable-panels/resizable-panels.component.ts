import {
    AfterViewInit,
    Component,
    Directive,
    ElementRef,
    NgZone,
    OnDestroy,
    TemplateRef,
    computed,
    contentChildren,
    effect,
    inject,
    input,
    signal,
    untracked,
    viewChildren,
} from '@angular/core';
import Split from 'split.js';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgTemplateOutlet } from '@angular/common';
import { TabsModule } from 'primeng/tabs';
import { TranslateDirective } from 'app/shared/language/translate.directive';

type PanelSide = 'left' | 'right';

interface PanelLayout {
    left: number[];
    right: number[];
}

@Directive({ selector: 'ng-template[jhiPanel]' })
export class PanelDirective {
    readonly label = input.required<string>();
    readonly icon = input<IconProp>();
    readonly iconTemplate = input<TemplateRef<unknown>>();
    readonly templateRef = inject(TemplateRef);
}

@Directive({ selector: '[jhiSplitPane]' })
export class SplitPaneDirective {
    readonly elementRef = inject(ElementRef);
}

@Component({
    selector: 'jhi-resizable-panels',
    templateUrl: './resizable-panels.component.html',
    styleUrls: ['./resizable-panels.component.scss'],
    imports: [FaIconComponent, NgTemplateOutlet, SplitPaneDirective, TabsModule, TranslateDirective],
})
export class ResizablePanelsComponent implements AfterViewInit, OnDestroy {
    private readonly ngZone = inject(NgZone);
    private readonly elementRef = inject(ElementRef);

    /** Width in px below which the split collapses into a single tabbed panel */
    readonly collapseBelowPx = input(768);
    readonly splitOnWide = input(true);

    readonly panels = contentChildren(PanelDirective);
    private readonly _layout = signal<PanelLayout>({ left: [], right: [] });
    readonly leftPanels = computed(() =>
        this._layout()
            .left.map((index) => this.panels()[index])
            .filter((panel): panel is PanelDirective => !!panel),
    );
    readonly rightPanels = computed(() =>
        this._layout()
            .right.map((index) => this.panels()[index])
            .filter((panel): panel is PanelDirective => !!panel),
    );
    readonly hasSplitLayout = computed(() => this.leftPanels().length > 0 && this.rightPanels().length > 0);
    readonly renderSplit = computed(() => !this.isNarrow() && this.hasSplitLayout() && (this.splitOnWide() || this._userCreatedSplit()));

    private readonly _activeLeftIndex = signal(0);
    private readonly _activeRightIndex = signal(0);
    readonly activeLeftIndex = this._activeLeftIndex.asReadonly();
    readonly activeRightIndex = this._activeRightIndex.asReadonly();

    private readonly _activeSingleIndex = signal(0);
    readonly activeSingleIndex = this._activeSingleIndex.asReadonly();

    private readonly _isNarrow = signal(false);
    readonly isNarrow = this._isNarrow.asReadonly();
    readonly isDraggingPanel = computed(() => this._draggedPanelIndex() !== undefined);

    readonly splitPanes = viewChildren(SplitPaneDirective);
    private splitInstance?: Split.Instance;
    private resizeObserver?: ResizeObserver;
    private lastPanelSignature = '';
    private readonly _draggedPanelIndex = signal<number | undefined>(undefined);
    private readonly _userCreatedSplit = signal(false);

    constructor() {
        effect(() => {
            const panels = this.panels();
            const signature = `${panels.length}:${panels.map((panel) => panel.label()).join('|')}`;
            untracked(() => {
                if (signature !== this.lastPanelSignature) {
                    this.lastPanelSignature = signature;
                    this.resetLayout(panels.length);
                }
            });
        });

        // Clamp active indices when panels move between sides or disappear.
        effect(() => {
            const leftLen = this.leftPanels().length;
            const rightLen = this.rightPanels().length;
            untracked(() => {
                if (this._activeLeftIndex() >= leftLen) {
                    this._activeLeftIndex.set(Math.max(0, leftLen - 1));
                }
                if (this._activeRightIndex() >= rightLen) {
                    this._activeRightIndex.set(Math.max(0, rightLen - 1));
                }
            });
        });

        // Clamp _activeSingleIndex when panels shrinks.
        effect(() => {
            const len = this.panels().length;
            untracked(() => {
                if (this._activeSingleIndex() >= len) {
                    this._activeSingleIndex.set(Math.max(0, len - 1));
                }
            });
        });

        // (Re-)initialize split.js whenever panes enter or leave the DOM,
        // i.e. when switching between wide and narrow mode.
        effect(() => {
            const panes = this.splitPanes();
            untracked(() => {
                this.splitInstance?.destroy();
                this.splitInstance = undefined;
                if (panes.length >= 2) {
                    this.ngZone.runOutsideAngular(() => this.initSplit(panes.map((p) => p.elementRef.nativeElement)));
                }
            });
        });
    }

    ngAfterViewInit(): void {
        this.resizeObserver = new ResizeObserver((entries) => {
            const width = entries[0].contentRect.width;
            this.ngZone.run(() => this._isNarrow.set(width < this.collapseBelowPx()));
        });
        this.resizeObserver.observe(this.elementRef.nativeElement);
    }

    ngOnDestroy(): void {
        this.splitInstance?.destroy();
        this.resizeObserver?.disconnect();
    }

    setActiveRight(value: string | number | undefined): void {
        if (value !== undefined) {
            this._activeRightIndex.set(Number(value));
        }
    }

    setActiveLeft(value: string | number | undefined): void {
        if (value !== undefined) {
            this._activeLeftIndex.set(Number(value));
        }
    }

    setActiveSingle(value: string | number | undefined): void {
        if (value !== undefined) {
            this._activeSingleIndex.set(Number(value));
        }
    }

    onPanelDragStart(event: DragEvent, panelIndex: number): void {
        this._draggedPanelIndex.set(panelIndex);
        event.dataTransfer?.setData('text/plain', panelIndex.toString());
        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = 'move';
        }
    }

    onPanelDragEnd(): void {
        this._draggedPanelIndex.set(undefined);
    }

    allowPanelDrop(event: DragEvent): void {
        if (this._draggedPanelIndex() !== undefined || event.dataTransfer?.getData('text/plain')) {
            event.preventDefault();
        }
    }

    dropPanelOnSide(event: DragEvent, side: PanelSide): void {
        event.preventDefault();
        const panelIndex = this.getDraggedPanelIndex(event);
        if (panelIndex === undefined) {
            return;
        }
        this.movePanelToSide(panelIndex, side);
        this._draggedPanelIndex.set(undefined);
    }

    dropPanelToCreateSplit(event: DragEvent, side: PanelSide): void {
        event.preventDefault();
        const panelIndex = this.getDraggedPanelIndex(event);
        const panels = this.panels();
        if (panelIndex === undefined || panels.length < 2 || this.renderSplit()) {
            return;
        }

        const remainingPanelIndexes = panels.map((_panel, index) => index).filter((index) => index !== panelIndex);
        this._layout.set(side === 'left' ? { left: [panelIndex], right: remainingPanelIndexes } : { left: remainingPanelIndexes, right: [panelIndex] });
        this._userCreatedSplit.set(true);
        this._activeLeftIndex.set(0);
        this._activeRightIndex.set(0);
        this._draggedPanelIndex.set(undefined);
    }

    private resetLayout(panelCount: number): void {
        this._layout.set({
            left: panelCount > 0 ? [0] : [],
            right: Array.from({ length: Math.max(0, panelCount - 1) }, (_value, index) => index + 1),
        });
        this._activeLeftIndex.set(0);
        this._activeRightIndex.set(0);
        this._activeSingleIndex.set(0);
        this._userCreatedSplit.set(false);
    }

    private getDraggedPanelIndex(event: DragEvent): number | undefined {
        const indexFromSignal = this._draggedPanelIndex();
        if (indexFromSignal !== undefined) {
            return indexFromSignal;
        }
        const indexFromEvent = event.dataTransfer?.getData('text/plain');
        if (!indexFromEvent) {
            return undefined;
        }
        const panelIndex = Number(indexFromEvent);
        return Number.isInteger(panelIndex) ? panelIndex : undefined;
    }

    private movePanelToSide(panelIndex: number, side: PanelSide): void {
        const layout = this._layout();
        const targetIndexes = layout[side];
        if (targetIndexes.includes(panelIndex)) {
            return;
        }

        const nextLayout = {
            left: layout.left.filter((index) => index !== panelIndex),
            right: layout.right.filter((index) => index !== panelIndex),
        };
        nextLayout[side] = [...nextLayout[side], panelIndex];
        this._layout.set(nextLayout);
        if (side === 'left') {
            this._activeLeftIndex.set(nextLayout.left.length - 1);
        } else {
            this._activeRightIndex.set(nextLayout.right.length - 1);
        }
    }

    private initSplit(elements: HTMLElement[]): void {
        this.splitInstance = Split(elements, {
            sizes: [65, 35],
            minSize: 0,
            snapOffset: 150,
            gutterSize: 12,
            cursor: 'col-resize',
            gutter: (_index, direction) => {
                const gutter = document.createElement('div');
                gutter.className = `gutter gutter-${direction}`;
                const handle = document.createElement('div');
                handle.className = 'split-gutter-handle';
                gutter.appendChild(handle);
                return gutter;
            },
        });
    }
}
