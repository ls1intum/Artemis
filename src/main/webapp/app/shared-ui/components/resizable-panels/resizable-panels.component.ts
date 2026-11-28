import {
    AfterViewInit,
    Component,
    Directive,
    ElementRef,
    Injector,
    OnDestroy,
    TemplateRef,
    afterNextRender,
    computed,
    contentChildren,
    effect,
    inject,
    input,
    signal,
    untracked,
} from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgTemplateOutlet } from '@angular/common';
import { CdkScrollable } from '@angular/cdk/scrolling';
import { TumAetUiSplitterComponent } from '@tumaet/ui-angular/splitter';
import { TumAetUiTabComponent, TumAetUiTabListComponent, TumAetUiTabPanelComponent, TumAetUiTabPanelsComponent, TumAetUiTabsComponent } from '@tumaet/ui-angular/tabs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { parseJson } from 'app/foundation/util/json.util';

@Directive({ selector: 'ng-template[jhiPanel]' })
export class PanelDirective {
    readonly templateRef = inject(TemplateRef);

    readonly label = input.required<string>();
    readonly icon = input<IconProp>();
    readonly iconTemplate = input<TemplateRef<unknown>>();
    /**
     * Keeps the right panels collapsed while it is true on any of them, and opens them when it turns false. Despite the name it is a live
     * state, not only the initial one. It stops having an effect for a ResizablePanels instance as soon as the user has collapsed or
     * expanded the right panel there, by hand or by dragging, so a user choice always wins over it.
     */
    readonly startsCollapsed = input(false);
}

@Component({
    selector: 'jhi-resizable-panels',
    templateUrl: './resizable-panels.component.html',
    styleUrls: ['./resizable-panels.component.css'],
    imports: [
        CdkScrollable,
        FaIconComponent,
        NgTemplateOutlet,
        TumAetUiSplitterComponent,
        TumAetUiTabsComponent,
        TumAetUiTabListComponent,
        TumAetUiTabComponent,
        TumAetUiTabPanelsComponent,
        TumAetUiTabPanelComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class ResizablePanelsComponent implements AfterViewInit, OnDestroy {
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly document = inject(DOCUMENT);
    private readonly injector = inject(Injector);

    /** Width in px below which the split collapses into a single tabbed panel */
    readonly collapseBelowPx = input(768);
    /** Use the viewport width for the responsive breakpoint instead of this component's content width. */
    readonly useViewportWidthForCollapse = input(false);
    /**
     * Persists split sizes under this localStorage key across reloads. Without a key, sizes stay in memory.
     */
    readonly storageKey = input<string | undefined>(undefined);
    /**
     * Drag-to-collapse threshold (percent). When the right panel is dragged narrower than this, it snaps shut
     * (and the icon rail / collapse buttons reopen it). Restores the split.js `snapOffset` behaviour that the
     * migration dropped. Set to 0 to disable drag-to-collapse.
     */
    readonly collapseSnapPercent = input(12);
    /** Removes the standard content gutter around the primary panel for full-bleed editors or canvases. */
    readonly flushLeftPanel = input(false);
    /**
     * Smallest width (percent) the left panel keeps, so dragging the slider cannot squeeze it away. The right panel
     * needs no such bound, as dragging it narrow collapses it (see {@link collapseSnapPercent}). 0 leaves it unbounded.
     */
    readonly leftMinSizePercent = input(0);
    /**
     * Smallest width (px) the left panel keeps, for content that needs a fixed room whatever the size of the split. It is turned
     * into a share of the split's current width, and the larger of it and {@link leftMinSizePercent} applies. 0 leaves it unbounded.
     */
    readonly leftMinWidthPx = input(0);
    /** Width of the split, which {@link leftMinWidthPx} is a share of; only measured when that minimum is set. */
    private readonly hostWidth = signal(0);
    /**
     * The left minimum, capped so the right panel can still be dragged as wide again as {@link collapseSnapPercent}.
     * A larger minimum would leave every permitted drag below the threshold, so each drag would close the right panel.
     */
    protected readonly effectiveLeftMinSizePercent = computed(() => {
        const width = this.hostWidth();
        const minSize = Math.max(this.leftMinSizePercent(), width > 0 ? (this.leftMinWidthPx() / width) * 100 : 0);
        const snap = this.collapseSnapPercent();
        return snap > 0 ? Math.max(0, Math.min(minSize, 100 - 2 * snap)) : minSize;
    });

    /**
     * Gutter size (px) of the splitter: the grey divider between the two panels, so it matches `--spacing-divider`,
     * the width every other divider in the shells uses. The template binds `gutterSize` below, so the value the
     * splitter renders and the value the flex-basis arithmetic assumes cannot drift apart.
     */
    private static readonly GUTTER_SIZE = 6;
    protected readonly gutterSize = ResizablePanelsComponent.GUTTER_SIZE;
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
     * Updated on resizeEnd; fed to the splitter via {@link panelSizesForSplitter}.
     */
    readonly savedSizes = signal<number[] | undefined>(undefined);

    readonly panelSizesForSplitter = computed(() => this.savedSizes() ?? this.defaultSizes());

    private resizeObserver?: ResizeObserver;
    private hostWidthObserver?: ResizeObserver;
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

        // The splitter refuses every drag that leaves the left panel below its minimum, so a remembered split that is
        // already below it (e.g. saved before the minimum applied) would freeze the slider. Widen it to the minimum.
        effect(() => {
            this.effectiveLeftMinSizePercent();
            untracked(() => {
                const sizes = this.savedSizes();
                if (sizes) {
                    const clamped = this.withLeftMinSize(sizes);
                    if (clamped !== sizes) {
                        this.savedSizes.set(clamped);
                    }
                }
            });
        });

        // Follow the requested collapsed state whenever it changes, unless the user already
        // controlled the right panel in this component instance.
        effect(() => {
            const collapsedPanelIndex = this.rightPanels().findIndex((panel) => panel.startsCollapsed());
            untracked(() => {
                if (this.rightPanelCollapseChangedByUser) {
                    return;
                }
                const collapse = collapsedPanelIndex >= 0;
                if (collapse === this._isRightPanelCollapsed()) {
                    return;
                }
                // The change does not come from the user, so keep the focus from being lost with the element that had it,
                // as the manual collapse and expand do.
                const restoreFocusSelector = collapse
                    ? this.focusIsInRightPanel()
                        ? '[data-testid="collapsed-panel-tab"]'
                        : undefined
                    : this.focusIsInCollapsedRail()
                      ? '[role="tab"][aria-selected="true"]'
                      : undefined;
                this._isRightPanelCollapsed.set(collapse);
                if (restoreFocusSelector) {
                    this.focusAfterRender(restoreFocusSelector);
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
        if (this.leftMinWidthPx() > 0) {
            this.hostWidthObserver = new ResizeObserver((entries) => this.hostWidth.set(entries[0].contentRect.width));
            this.hostWidthObserver.observe(this.elementRef.nativeElement);
        }
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
        this.hostWidthObserver?.disconnect();
    }

    /** Restores compatible persisted sizes, ignoring malformed, unavailable or degenerate storage. */
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
            // Retains the existing JSON array format.
            const parsed = parseJson<number[]>(raw);
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
                this.savedSizes.set(this.withLeftMinSize(parsed));
            }
        } catch {
            // Malformed or unavailable storage (private mode / no window); the in-memory default applies.
        }
    }

    /** The split itself when its left panel keeps {@link leftMinSizePercent}, otherwise the split widened to it. */
    private withLeftMinSize(sizes: number[]): number[] {
        const leftMinSize = this.effectiveLeftMinSizePercent();
        return sizes[0] >= leftMinSize ? sizes : [leftMinSize, 100 - leftMinSize];
    }

    /** {@link DEFAULT_SIZES}, widened to {@link leftMinSizePercent}. */
    private defaultSizes(): number[] {
        return this.withLeftMinSize(ResizablePanelsComponent.DEFAULT_SIZES);
    }

    setActiveRight(value: string | number | undefined): void {
        if (value !== undefined) {
            this._activeRightIndex.set(Number(value));
        }
    }

    private focusIsInRightPanel(): boolean {
        const active = this.document.activeElement;
        return (
            !!this.elementRef.nativeElement.querySelector('[data-testid="right-panel-content"]')?.contains(active) ||
            active === this.elementRef.nativeElement.querySelector('[role="separator"]')
        );
    }

    private focusIsInCollapsedRail(): boolean {
        return !!this.elementRef.nativeElement.querySelector('.collapsed-right-panel')?.contains(this.document.activeElement);
    }

    expandRightPanel(index?: number): void {
        const restoreFocus = this.focusIsInCollapsedRail();
        if (index !== undefined) {
            this._activeRightIndex.set(index);
        }
        this.rightPanelCollapseChangedByUser = true;
        this._isRightPanelCollapsed.set(false);
        this.restoreUsableSplit();
        if (restoreFocus) {
            this.focusAfterRender('[role="tab"][aria-selected="true"]');
        }
    }

    private restoreUsableSplit(): void {
        const current = this.savedSizes();
        this.savedSizes.set(current && (current[1] ?? 0) > this.collapseSnapPercent() ? [...current] : [...this.defaultSizes()]);
    }

    collapseRightPanel(): void {
        const restoreFocus = this.focusIsInRightPanel();
        this.rightPanelCollapseChangedByUser = true;
        this._isRightPanelCollapsed.set(true);
        if (restoreFocus) {
            this.focusAfterRender('[data-testid="collapsed-panel-tab"]');
        }
    }

    private focusAfterRender(selector: string): void {
        afterNextRender(() => this.elementRef.nativeElement.querySelector<HTMLElement>(selector)?.focus(), { injector: this.injector });
    }

    /** Remembers usable sizes and snaps narrow secondary panels shut without losing their reopen width. */
    onResizeEnd(rawSizes: (number | string)[]): void {
        const sizes = rawSizes.map(Number);
        if (sizes.some((size) => !Number.isFinite(size))) {
            return;
        }
        const rightSize = sizes[1] ?? 0;
        if (this.collapseSnapPercent() > 0 && rightSize <= this.collapseSnapPercent()) {
            // A snap never replaces the usable reopen width.
            const remembered = this.savedSizes();
            const reopenSizes = remembered && (remembered[1] ?? 0) > this.collapseSnapPercent() ? [...remembered] : [...this.defaultSizes()];
            this.savedSizes.set(reopenSizes);
            this.persistSizes(reopenSizes);
            this.collapseRightPanel();
            return;
        }
        this.savedSizes.set([...sizes]);
        this.persistSizes(sizes);
    }

    /** Retains the existing localStorage format for previously saved layouts. */
    private persistSizes(sizes: number[]): void {
        const key = this.storageKey();
        if (!key) {
            return;
        }
        try {
            // Retains the existing JSON array format under the storage key.
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
