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

    readonly panels = contentChildren(PanelDirective);
    readonly leftPanel = computed(() => this.panels()[0]);
    readonly rightPanels = computed(() => this.panels().slice(1));

    private readonly _activeRightIndex = signal(0);
    readonly activeRightIndex = this._activeRightIndex.asReadonly();

    private readonly _activeSingleIndex = signal(0);
    readonly activeSingleIndex = this._activeSingleIndex.asReadonly();

    private readonly _isNarrow = signal(false);
    readonly isNarrow = this._isNarrow.asReadonly();

    readonly splitPanes = viewChildren(SplitPaneDirective);
    private splitInstance?: Split.Instance;
    private resizeObserver?: ResizeObserver;

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

    setActiveSingle(value: string | number | undefined): void {
        if (value !== undefined) {
            this._activeSingleIndex.set(Number(value));
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
