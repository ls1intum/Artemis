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
import { parseJson } from 'app/foundation/util/json.util';

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

    /** Gutter size (px) of the splitter; kept in sync with the template's [gutterSize]. */
    private static readonly GUTTER_SIZE = 12;
    /** Split (percent) used when reopening without a usable saved size. */
    private static readonly DEFAULT_SIZES = [65, 35];

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
     * Updated on (onResizeEnd); fed to the splitter via {@link panelSizesForSplitter}.
     */
    readonly savedSizes = signal<number[] | undefined>(undefined);

    /**
     * The array actually bound to the splitter's [panelSizes]. It MUST be a copy of {@link savedSizes}: PrimeNG's
     * `set panelSizes` adopts the bound array by reference and then mutates it in place on every drag
     * (primeng-splitter.mjs). Binding `savedSizes()` directly would alias the signal's array to the splitter's
     * internal buffer, so a drag would silently corrupt the remembered split (a near-zero snap size would leak into
     * savedSizes + localStorage and reopen/reload jammed). Spreading here hands the splitter a throwaway copy.
     */
    readonly panelSizesForSplitter = computed(() => [...(this.savedSizes() ?? ResizablePanelsComponent.DEFAULT_SIZES)]);

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
        this.seedSavedSizesFromStorage();
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

    /**
     * Seeds {@link savedSizes} from the splitter's persisted localStorage entry on init. The splitter restores the
     * panel widths itself (stateStorage="local"), but it never feeds them back to this component, so savedSizes
     * stays undefined after a reload. Without this, the first collapse/expand (or a drag-to-collapse) would fall
     * back to {@link DEFAULT_SIZES} and silently discard the user's persisted custom split. No-op when no
     * storageKey is set or the stored value is not a usable two-number split.
     */
    private seedSavedSizesFromStorage(): void {
        const key = this.storageKey();
        if (!key) {
            return;
        }
        try {
            const raw = this.document.defaultView?.localStorage.getItem(key);
            if (!raw) {
                return;
            }
            // Matches PrimeNG's Splitter stateStorage format: JSON.stringify(number[]).
            const parsed = parseJson(raw);
            // Only seed a usable split: two finite numbers, a positive left, and a right above the collapse
            // threshold. A near-zero/degenerate stored value (external tampering, legacy data) is ignored so the
            // initial render never restores a jammed sliver (restoreUsableSplit only runs on expand, not on seed).
            if (
                Array.isArray(parsed) &&
                parsed.length === 2 &&
                parsed.every((size) => typeof size === 'number' && Number.isFinite(size)) &&
                parsed[0] > 0 &&
                parsed[1] > this.collapseSnapPercent()
            ) {
                this.savedSizes.set(parsed as number[]);
            }
        } catch {
            // Malformed or unavailable storage (private mode / no window); the in-memory default applies.
        }
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
        this.restoreUsableSplit();
    }

    /**
     * Reopens the right panel with a visible split. After a drag-to-collapse the splitter keeps the near-zero
     * position the slider was dragged to, and only re-applies its panel sizes when the `panelSizes` input changes,
     * not when the collapse state toggles. So expanding would otherwise show the panel jammed against the edge,
     * invisible until the user drags the slider back (a reviewer finding). Re-apply a usable split: refresh
     * savedSizes (a new reference, so the splitter re-applies it and the persisted size matches) and also write the
     * panel flex-basis directly as a safeguard, in case the splitter does not pick up the change on expand.
     */
    private restoreUsableSplit(): void {
        const current = this.savedSizes();
        const usable = current && (current[1] ?? 0) >= this.collapseSnapPercent() ? [...current] : [...ResizablePanelsComponent.DEFAULT_SIZES];
        this.savedSizes.set(usable);

        // Direct-child combinator: PrimeNG applies the root class to the <p-splitter> host and renders the panel
        // divs as its direct children (it selects them the same way: el.children.filter(data-pc-section==="panel")).
        // A descendant selector would also match panels of a nested PrimeNG component projected into a panel.
        const panels = this.elementRef.nativeElement.querySelectorAll('.resizable-panels-splitter > [data-pc-section="panel"]') as NodeListOf<HTMLElement>;
        if (panels.length >= 2) {
            // Mirrors PrimeNG's own flexBasis formula: calc(size% - (panels-1) * gutterSize px).
            panels[0].style.flexBasis = `calc(${usable[0]}% - ${ResizablePanelsComponent.GUTTER_SIZE}px)`;
            panels[1].style.flexBasis = `calc(${usable[1]}% - ${ResizablePanelsComponent.GUTTER_SIZE}px)`;
        }
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
            // Only reuse the remembered split if it is itself usable. Guards against a degenerate value (and, as a
            // belt-and-suspenders to panelSizesForSplitter's copy, against any near-zero size that aliasing might
            // have left in savedSizes), so reopening/reloading never restores a jammed sliver.
            const remembered = this.savedSizes();
            const reopenSizes = remembered && (remembered[1] ?? 0) > this.collapseSnapPercent() ? [...remembered] : [...ResizablePanelsComponent.DEFAULT_SIZES];
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
