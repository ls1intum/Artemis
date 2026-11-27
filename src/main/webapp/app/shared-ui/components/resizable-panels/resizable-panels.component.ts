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
import { faChevronRight } from '@fortawesome/free-solid-svg-icons/faChevronRight';
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

    readonly panelSizesForSplitter = computed(() => this.savedSizes() ?? ResizablePanelsComponent.DEFAULT_SIZES);

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
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
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
                this.savedSizes.set(parsed);
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
        this.savedSizes.set(current && (current[1] ?? 0) > this.collapseSnapPercent() ? [...current] : [...ResizablePanelsComponent.DEFAULT_SIZES]);
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
            const reopenSizes = remembered && (remembered[1] ?? 0) > this.collapseSnapPercent() ? [...remembered] : [...ResizablePanelsComponent.DEFAULT_SIZES];
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
