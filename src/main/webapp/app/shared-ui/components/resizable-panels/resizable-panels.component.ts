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

    setActiveSingle(value: string | number | undefined): void {
        if (value !== undefined) {
            this._activeSingleIndex.set(Number(value));
        }
    }
}
