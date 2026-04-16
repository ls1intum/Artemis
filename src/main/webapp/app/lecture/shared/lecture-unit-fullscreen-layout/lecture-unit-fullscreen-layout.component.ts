import {
    Component,
    ElementRef,
    HostListener,
    Injector,
    NgZone,
    OnDestroy,
    ViewEncapsulation,
    afterNextRender,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
    viewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import Split from 'split.js';

@Component({
    selector: 'jhi-lecture-unit-fullscreen-layout',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './lecture-unit-fullscreen-layout.component.html',
    styleUrl: './lecture-unit-fullscreen-layout.component.scss',
    encapsulation: ViewEncapsulation.None,
})
export class LectureUnitFullscreenLayoutComponent implements OnDestroy {
    private readonly hostElement = inject(ElementRef<HTMLElement>);
    private readonly injector = inject(Injector);
    private readonly ngZone = inject(NgZone);

    readonly isCollapsed = input<boolean>(true);
    readonly showSidebar = input<boolean>(false);
    readonly dialogAriaLabel = input<string | undefined>(undefined);
    readonly sidebarAriaLabel = input<string | undefined>(undefined);
    readonly splitSizes = input<[number, number]>([50, 50]);
    readonly minSplitSizes = input<[number, number]>([120, 120]);
    readonly hasNestedFullscreen = input<boolean>(false);
    readonly preventEscapeClose = input<boolean>(false);
    readonly defaultVerticalSplitSizes = input<[number, number]>([50, 50]);
    readonly defaultHorizontalSplitSizes = input<[number, number]>([50, 50]);
    readonly hasHorizontalSplit = input<boolean>(false);
    readonly horizontalSplitSizes = input<[number, number]>([50, 50]);
    readonly minHorizontalSplitSizes = input<[number, number]>([80, 80]);
    readonly horizontalSplitTopElement = input<ElementRef | undefined>(undefined);
    readonly horizontalSplitBottomElement = input<ElementRef | undefined>(undefined);

    readonly backdropClick = output<void>();
    readonly splitSizesChange = output<[number, number]>();
    readonly fullscreenChange = output<boolean>();
    readonly horizontalSplitSizesChange = output<[number, number]>();

    private readonly _isFullscreen = signal<boolean>(false);
    readonly isFullscreen = this._isFullscreen.asReadonly();

    readonly contentContainer = viewChild<ElementRef<HTMLElement>>('contentContainer');
    readonly mainContentElement = viewChild<ElementRef<HTMLElement>>('mainContent');
    readonly sidebarElement = viewChild<ElementRef<HTMLElement>>('sidebar');

    readonly contentContainerClasses = computed(() => ({
        'content-container--hidden': this.isCollapsed() && !this.isFullscreen(),
        'content-container--fullscreen': this.isFullscreen(),
        'content-container--with-sidebar': this.isFullscreen() && this.showSidebar(),
    }));

    private splitInstance?: Split.Instance;
    private horizontalSplitInstance?: Split.Instance;
    private _focusTrapHandler?: (event: KeyboardEvent) => void;
    private _focusTrapContainer?: HTMLElement;
    private _inertElements = new Map<HTMLElement, { hadInert: boolean; previousAriaHidden: string | null }>();
    private _previouslyFocusedElement: HTMLElement | null = null;

    private readonly fullscreenBodyClass = 'lecture-combined-view-fullscreen-active';
    private readonly focusableSelector = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

    constructor() {
        // Vertical splitter lifecycle (main content | sidebar)
        effect(() => {
            const needsSplitter = this.isFullscreen() && this.showSidebar();
            const mainEl = this.mainContentElement()?.nativeElement;
            const sidebarEl = this.sidebarElement()?.nativeElement;

            untracked(() => {
                this.destroySplitter();
                if (needsSplitter && mainEl && sidebarEl) {
                    this.ngZone.runOutsideAngular(() => {
                        this.initSplitter([mainEl, sidebarEl]);
                    });
                }
            });
        });

        // Horizontal splitter lifecycle (top | bottom)
        effect(() => {
            const needsSplitter = this.isFullscreen() && this.hasHorizontalSplit();
            const topEl = this.horizontalSplitTopElement()?.nativeElement;
            const bottomEl = this.horizontalSplitBottomElement()?.nativeElement;

            untracked(() => {
                this.destroyHorizontalSplitter();
                if (needsSplitter && topEl && bottomEl) {
                    this.ngZone.runOutsideAngular(() => {
                        this.initHorizontalSplitter([topEl, bottomEl]);
                    });
                }
            });
        });

        effect(() => {
            const fullscreen = this.isFullscreen();
            untracked(() => {
                if (fullscreen) {
                    this.updateFullscreenTopOffset();
                    this.setGlobalFullscreenState(true);
                    afterNextRender(
                        () => {
                            this.focusFullscreenContainer();
                        },
                        { injector: this.injector },
                    );
                } else {
                    this.clearFullscreenTopOffset();
                    this.setGlobalFullscreenState(false);
                    this.cleanupFullscreenAccessibility();
                }
            });
        });
    }

    ngOnDestroy(): void {
        this.destroySplitter();
        this.destroyHorizontalSplitter();
        this.clearFullscreenTopOffset();
        this.setGlobalFullscreenState(false);
        this.cleanupFullscreenAccessibility();
    }

    @HostListener('window:resize')
    onResize(): void {
        if (this.isFullscreen()) {
            this.updateFullscreenTopOffset();
        }
    }

    @HostListener('document:keydown.escape', ['$event'])
    onEscapePressed(event: Event): void {
        if (!this.isFullscreen() || event.defaultPrevented || this.preventEscapeClose() || this.hasNestedFullscreen()) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        this.close();
    }

    /**
     * Opens fullscreen and captures the currently focused element for restoration on close.
     */
    open(): void {
        if (this._isFullscreen()) {
            return;
        }
        this._captureFocusedElement();
        this.resetSplitSizesToDefaults();
        this._isFullscreen.set(true);
        this.fullscreenChange.emit(true);
    }

    /**
     * Closes fullscreen and restores focus to the previously focused element.
     */
    close(): void {
        if (!this._isFullscreen() || this.hasNestedFullscreen()) {
            return;
        }
        this._isFullscreen.set(false);
        this._restoreFocusedElement();
        this.fullscreenChange.emit(false);
    }

    protected onBackdropClick(): void {
        this.backdropClick.emit();
    }

    private initSplitter(elements: HTMLElement[]): void {
        this.splitInstance = Split(elements, {
            sizes: this.splitSizes(),
            minSize: this.minSplitSizes(),
            gutterSize: 12,
            cursor: 'col-resize',
            direction: 'horizontal',
            onDragEnd: (sizes) => {
                this.ngZone.run(() => {
                    this.splitSizesChange.emit([sizes[0], sizes[1]]);
                });
            },
            gutter: (_index, direction) => this.createSplitGutter(direction),
        });
    }

    private createSplitGutter(direction: string): HTMLElement {
        const gutter = document.createElement('div');
        gutter.className = `gutter gutter-${direction}`;

        const handle = document.createElement('div');
        handle.className = 'split-gutter-handle';
        gutter.appendChild(handle);

        return gutter;
    }

    private destroySplitter(): void {
        this.splitInstance?.destroy();
        this.splitInstance = undefined;
    }

    private initHorizontalSplitter(elements: HTMLElement[]): void {
        this.horizontalSplitInstance = Split(elements, {
            sizes: this.horizontalSplitSizes(),
            minSize: this.minHorizontalSplitSizes(),
            gutterSize: 12,
            cursor: 'row-resize',
            direction: 'vertical',
            onDragEnd: (sizes) => {
                this.ngZone.run(() => {
                    this.horizontalSplitSizesChange.emit([sizes[0], sizes[1]]);
                });
            },
            gutter: (_index, direction) => this.createSplitGutter(direction),
        });
    }

    private destroyHorizontalSplitter(): void {
        this.horizontalSplitInstance?.destroy();
        this.horizontalSplitInstance = undefined;
    }

    private resetSplitSizesToDefaults(): void {
        this.splitSizesChange.emit(this.defaultVerticalSplitSizes());
        this.horizontalSplitSizesChange.emit(this.defaultHorizontalSplitSizes());
    }

    private _captureFocusedElement(): void {
        const activeEl = document.activeElement;
        if (activeEl instanceof HTMLElement) {
            this._previouslyFocusedElement = activeEl;
        }
    }

    private _restoreFocusedElement(): void {
        const elementToRestore = this._previouslyFocusedElement && document.contains(this._previouslyFocusedElement) ? this._previouslyFocusedElement : undefined;
        this._previouslyFocusedElement = null;

        if (elementToRestore) {
            afterNextRender(
                () => {
                    elementToRestore.focus();
                },
                { injector: this.injector },
            );
        }
    }

    private updateFullscreenTopOffset(): void {
        const topOffset = this.getNavbarHeight();
        this.hostElement.nativeElement.style.setProperty('--lecture-combined-view-top', `${topOffset}px`);
    }

    private clearFullscreenTopOffset(): void {
        this.hostElement.nativeElement.style.removeProperty('--lecture-combined-view-top');
    }

    private getNavbarHeight(): number {
        const navbar = document.querySelector('nav.navbar.jh-navbar');
        return navbar instanceof HTMLElement ? navbar.getBoundingClientRect().height : 0;
    }

    private setGlobalFullscreenState(isFullscreen: boolean): void {
        document.body.classList.toggle(this.fullscreenBodyClass, isFullscreen);
    }

    private focusFullscreenContainer(): void {
        const container = this.contentContainer()?.nativeElement;
        if (!container || !this.isFullscreen()) {
            return;
        }

        this.setupFocusTrap(container);
        this.setBackgroundInert(true);
        container.focus();
    }

    private getFocusableElements(container: HTMLElement): HTMLElement[] {
        return Array.from(container.querySelectorAll<HTMLElement>(this.focusableSelector)).filter(
            (el) => el.offsetParent !== null && !el.hasAttribute('disabled') && el.tabIndex >= 0,
        );
    }

    private setupFocusTrap(container: HTMLElement): void {
        // Clean up any existing handler first
        this.cleanupFocusTrap();

        this._focusTrapContainer = container;
        this._focusTrapHandler = (event: KeyboardEvent) => {
            if (event.key !== 'Tab') {
                return;
            }

            const elements = this.getFocusableElements(container);
            if (elements.length === 0) {
                event.preventDefault();
                return;
            }

            const active = document.activeElement;
            const isAtBoundary = event.shiftKey ? active === elements[0] || active === container : active === elements[elements.length - 1];

            if (isAtBoundary) {
                event.preventDefault();
                (event.shiftKey ? elements[elements.length - 1] : elements[0]).focus();
            }
        };

        // Listener is removed in cleanupFocusTrap() which is called from ngOnDestroy()
        // eslint-disable-next-line localRules/enforce-cleanup-on-destroy
        container.addEventListener('keydown', this._focusTrapHandler);
    }

    private cleanupFocusTrap(): void {
        if (this._focusTrapContainer && this._focusTrapHandler) {
            this._focusTrapContainer.removeEventListener('keydown', this._focusTrapHandler);
            this._focusTrapHandler = undefined;
            this._focusTrapContainer = undefined;
        }
    }

    private shouldSkipBackgroundInert(element: HTMLElement): boolean {
        return element.classList.contains('sticky-top-navbar') || element.matches('jhi-navbar') || !!element.querySelector('jhi-navbar');
    }

    private setBackgroundInert(inert: boolean): void {
        if (inert) {
            this.setBackgroundElementsInert();
            return;
        }

        this.restoreBackgroundElementsState();
    }

    private setBackgroundElementsInert(): void {
        let current: HTMLElement | null = this.hostElement.nativeElement;
        while (current && current !== document.body) {
            const parent = current.parentElement;
            if (!parent) {
                break;
            }
            this.markSiblingElementsAsInert(parent, current);
            current = parent;
        }
    }

    private markSiblingElementsAsInert(parent: HTMLElement, currentElement: HTMLElement): void {
        Array.from(parent.children).forEach((sibling) => {
            if (!(sibling instanceof HTMLElement) || sibling === currentElement || this._inertElements.has(sibling) || this.shouldSkipBackgroundInert(sibling)) {
                return;
            }
            this._inertElements.set(sibling, {
                hadInert: sibling.hasAttribute('inert'),
                previousAriaHidden: sibling.getAttribute('aria-hidden'),
            });
            sibling.setAttribute('inert', '');
            sibling.setAttribute('aria-hidden', 'true');
        });
    }

    private restoreBackgroundElementsState(): void {
        this._inertElements.forEach((state, element) => {
            if (!state.hadInert) {
                element.removeAttribute('inert');
            }
            if (state.previousAriaHidden === null) {
                element.removeAttribute('aria-hidden');
            } else {
                element.setAttribute('aria-hidden', state.previousAriaHidden);
            }
        });
        this._inertElements.clear();
    }

    private cleanupFullscreenAccessibility(): void {
        this.cleanupFocusTrap();
        this.setBackgroundInert(false);
    }
}
