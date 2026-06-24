import { AfterViewInit, Component, Directive, ElementRef, OnDestroy, TemplateRef, computed, contentChildren, effect, inject, input, signal, untracked } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgTemplateOutlet } from '@angular/common';
import { SplitterModule } from 'primeng/splitter';
import { TabsModule } from 'primeng/tabs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Directive({ selector: 'ng-template[jhiPanel]' })
export class PanelDirective {
    readonly label = input.required<string>();
    readonly icon = input<IconProp>();
    readonly iconTemplate = input<TemplateRef<unknown>>();
    readonly startsCollapsed = input(false);
    readonly templateRef = inject(TemplateRef);
}

@Component({
    selector: 'jhi-resizable-panels',
    templateUrl: './resizable-panels.component.html',
    styleUrls: ['./resizable-panels.component.scss'],
    imports: [FaIconComponent, NgTemplateOutlet, SplitterModule, TabsModule, TranslateDirective, ArtemisTranslatePipe],
})
export class ResizablePanelsComponent implements AfterViewInit, OnDestroy {
    private readonly elementRef = inject(ElementRef);
    private readonly document = inject(DOCUMENT);

    /** Width in px below which the split collapses into a single tabbed panel */
    readonly collapseBelowPx = input(768);
    /** Use the viewport width for the responsive breakpoint instead of this component's content width. */
    readonly useViewportWidthForCollapse = input(false);
    /**
     * When set, the chosen split sizes are persisted under this key (localStorage, via the splitter's native
     * stateful mode) so they survive a reload. When omitted, sizes are only kept in memory for the session.
     */
    readonly storageKey = input<string | undefined>(undefined);
    /**
     * Drag-to-collapse threshold (percent). When the right panel is dragged narrower than this, it snaps shut
     * (and the icon rail / collapse buttons reopen it). Restores the split.js `snapOffset` behaviour that the
     * migration dropped. Set to 0 to disable drag-to-collapse.
     */
    readonly collapseSnapPercent = input(12);

    readonly panels = contentChildren(PanelDirective);
    readonly leftPanel = computed(() => this.panels()[0]);
    readonly rightPanels = computed(() => this.panels().slice(1));

    private readonly _activeRightIndex = signal(0);
    readonly activeRightIndex = this._activeRightIndex.asReadonly();

    private readonly _activeSingleIndex = signal(0);
    readonly activeSingleIndex = this._activeSingleIndex.asReadonly();

    private readonly _isNarrow = signal(false);
    readonly isNarrow = this._isNarrow.asReadonly();

    private readonly _isRightPanelCollapsed = signal(false);
    readonly isRightPanelCollapsed = this._isRightPanelCollapsed.asReadonly();

    /** In narrow (combined) mode the tab header is only useful when there is more than one panel to switch between. */
    readonly showCombinedTabHeader = computed(() => this.panels().length > 1);

    /**
     * Split sizes (in percent) of the two splitter panels, persisted across mode switches.
     * Bound to p-splitter via [panelSizes] and updated on (onResizeEnd).
     */
    readonly savedSizes = signal<number[] | undefined>(undefined);

    private resizeObserver?: ResizeObserver;
    private rightPanelCollapseChangedByUser = false;

    protected readonly faChevronRight = faChevronRight;

    constructor() {
        // Clamp _activeRightIndex when rightPanels shrinks.
        effect(() => {
            const len = this.rightPanels().length;
            untracked(() => {
                if (this._activeRightIndex() >= len) {
                    this._activeRightIndex.set(Math.max(0, len - 1));
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

        // Apply the requested collapsed start state once, unless the user already
        // controlled the right panel in this component instance.
        effect(() => {
            const collapsedPanelIndex = this.rightPanels().findIndex((panel) => panel.startsCollapsed());
            untracked(() => {
                if (this.rightPanelCollapseChangedByUser) {
                    return;
                }
                if (collapsedPanelIndex >= 0) {
                    this._isRightPanelCollapsed.set(true);
                } else {
                    this._isRightPanelCollapsed.set(false);
                }
            });
        });
    }

    ngAfterViewInit(): void {
        const observedElement = this.useViewportWidthForCollapse() ? this.document.documentElement : this.elementRef.nativeElement;
        this.resizeObserver = new ResizeObserver((entries) => {
            const width = entries[0].contentRect.width;
            this._isNarrow.set(width < this.collapseBelowPx());
        });
        this.resizeObserver.observe(observedElement);
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }

    setActiveRight(value: string | number | undefined): void {
        if (value !== undefined) {
            this._activeRightIndex.set(Number(value));
        }
    }

    expandRightPanel(index?: number): void {
        if (index !== undefined) {
            this._activeRightIndex.set(index);
        }
        this.rightPanelCollapseChangedByUser = true;
        this._isRightPanelCollapsed.set(false);
    }

    collapseRightPanel(): void {
        this.rightPanelCollapseChangedByUser = true;
        this._isRightPanelCollapsed.set(true);
    }

    /**
     * Persists the split sizes on drag end, and snaps the right panel shut when it is dragged narrower than
     * {@link collapseSnapPercent} (drag-to-collapse, the split.js `snapOffset` behaviour). When snapping closed,
     * the near-zero drag size is not kept as the remembered split, so reopening (or reloading) uses a usable width.
     *
     * The incoming `sizes` is the splitter's own internal array, which it mutates in place on the next drag, so it
     * must be copied before being stored. The splitter also persists that raw size to localStorage (stateStorage)
     * before this handler runs, so on a snap-collapse the stored size must be overwritten too.
     */
    onResizeEnd(sizes: number[]): void {
        const rightSize = sizes[1] ?? 0;
        if (this.collapseSnapPercent() > 0 && rightSize <= this.collapseSnapPercent()) {
            const reopenSizes = [...(this.savedSizes() ?? [65, 35])];
            this.savedSizes.set(reopenSizes);
            this.persistSizes(reopenSizes);
            this.collapseRightPanel();
            return;
        }
        this.savedSizes.set([...sizes]);
    }

    /**
     * Mirrors the chosen sizes into the splitter's own localStorage entry. The splitter persists its raw drag
     * sizes there (stateStorage="local") before {@link onResizeEnd} runs, so after a drag-to-collapse its storage
     * holds the near-zero size; overwriting it keeps a reload from restoring the collapsed sliver. No-op when no
     * storageKey is set (sizes are then only kept in memory anyway).
     */
    private persistSizes(sizes: number[]): void {
        const key = this.storageKey();
        if (!key) {
            return;
        }
        try {
            // Matches PrimeNG's Splitter stateStorage format: JSON.stringify(number[]) under the stateKey.
            this.document.defaultView?.localStorage.setItem(key, JSON.stringify(sizes));
        } catch {
            // localStorage may be unavailable (private mode / no window); the in-memory savedSizes still applies.
        }
    }

    setActiveSingle(value: string | number | undefined): void {
        if (value !== undefined) {
            this._activeSingleIndex.set(Number(value));
        }
    }
}
