import { DestroyRef, Directive, ElementRef, Renderer2, afterNextRender, inject, input, output } from '@angular/core';

/**
 * In-house replacement for the `ngx-infinite-scroll` directive, implemented with a native
 * {@link IntersectionObserver} and two inert sentinel elements (one at the top, one at the bottom of
 * the host's scroll content). It keeps the original selector and input/output names so it is a
 * drop-in: place it on the scrollable element and listen to `(scrolledUp)` / `(scrolled)`.
 *
 * ```html
 * <div infinite-scroll [scrollWindow]="false" (scrolledUp)="fetchNextPage()">
 *     @for (message of messages(); track message.id) { ... }
 * </div>
 * ```
 *
 * Why IntersectionObserver instead of scroll listeners: it needs no throttling or scroll-position
 * math and is naturally edge-triggered. The observer reports a sentinel "entering" the viewport only
 * once per visit, so the directive fires the matching event a single time when the user reaches an
 * edge and re-arms only after they scroll away again. This prevents the page-skipping over-fetch that
 * a level-triggered scroll listener is prone to, and matches `ngx-infinite-scroll`'s default
 * `immediateCheck=false` behaviour (no event on the initial, already-at-the-top render).
 *
 * Configuration inputs (`scrollWindow`, `infiniteScrollUpDistance`, `infiniteScrollDistance`) are read
 * once when the observer is created; `infiniteScrollDisabled` is evaluated live on every callback.
 */
@Directive({
    selector: '[infiniteScroll], [infinite-scroll], [data-infinite-scroll]',
})
export class InfiniteScrollDirective {
    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly renderer = inject(Renderer2);
    private readonly destroyRef = inject(DestroyRef);

    /** When false, the host element itself is the scroll container; when true, the browser viewport is observed. */
    readonly scrollWindow = input(true);
    /** Top prefetch threshold; mapped to the observer's top `rootMargin` as `infiniteScrollUpDistance * 10`% of the root height. */
    readonly infiniteScrollUpDistance = input(1.5);
    /** Bottom prefetch threshold; mapped to the observer's bottom `rootMargin` as `infiniteScrollDistance * 10`% of the root height. */
    readonly infiniteScrollDistance = input(2);
    /** When true, edge events are suppressed (the observer keeps running, but nothing is emitted). */
    readonly infiniteScrollDisabled = input(false);

    /** Emitted when the user scrolls to (near) the top of the scroll content. */
    readonly scrolledUp = output<void>();
    /** Emitted when the user scrolls to (near) the bottom of the scroll content. Not consumed today, kept for reuse. */
    readonly scrolled = output<void>();

    private observer?: IntersectionObserver;
    private topSentinel?: HTMLElement;
    private bottomSentinel?: HTMLElement;
    // Both start un-armed so the initial render (already at the top) does not emit. A sentinel is
    // (re-)armed once it leaves the trigger zone and fires once when it next re-enters.
    private armedUp = false;
    private armedDown = false;

    constructor() {
        // Wait for the first render so the sentinels are inserted around the already-rendered content.
        afterNextRender(() => this.setup());
        this.destroyRef.onDestroy(() => this.teardown());
    }

    private setup(): void {
        const hostEl = this.host.nativeElement;
        this.topSentinel = this.createSentinel('top');
        this.bottomSentinel = this.createSentinel('bottom');
        // Keep the top sentinel as the first node and the bottom sentinel as the last node so they
        // stay at the content edges even as the consumer prepends/appends items via `@for`.
        this.renderer.insertBefore(hostEl, this.topSentinel, hostEl.firstChild);
        this.renderer.appendChild(hostEl, this.bottomSentinel);

        this.observer = new IntersectionObserver((entries) => this.onIntersect(entries), {
            root: this.scrollWindow() ? null : hostEl,
            rootMargin: `${this.infiniteScrollUpDistance() * 10}% 0px ${this.infiniteScrollDistance() * 10}% 0px`,
            threshold: 0,
        });
        this.observer.observe(this.topSentinel);
        this.observer.observe(this.bottomSentinel);
    }

    private onIntersect(entries: IntersectionObserverEntry[]): void {
        for (const entry of entries) {
            // Ignore callbacks fired because the scroll container is detached or hidden (e.g. a
            // `display: none` toggle while loading more, but also `visibility: hidden`, a collapsed
            // height, or a zero-width parent): the sentinel then has no usable layout box, so this is
            // not a real scroll movement and must not (re-)arm or fire the trigger. A shown sentinel is
            // always `width: 100%` / `height: 1px`, so neither dimension is ever legitimately zero —
            // hence `||` (any zero dimension means hidden) has no false positives and catches more
            // hidden states than a strict `&&` would.
            if (entry.boundingClientRect.width === 0 || entry.boundingClientRect.height === 0) {
                continue;
            }
            const isTop = entry.target === this.topSentinel;
            if (!entry.isIntersecting) {
                // Left the trigger zone: arm so the next entry fires exactly once.
                if (isTop) {
                    this.armedUp = true;
                } else {
                    this.armedDown = true;
                }
                continue;
            }
            const wasArmed = isTop ? this.armedUp : this.armedDown;
            if (isTop) {
                this.armedUp = false;
            } else {
                this.armedDown = false;
            }
            if (wasArmed && !this.infiniteScrollDisabled()) {
                (isTop ? this.scrolledUp : this.scrolled).emit();
            }
        }
    }

    private createSentinel(position: 'top' | 'bottom'): HTMLElement {
        const sentinel = this.renderer.createElement('div') as HTMLElement;
        this.renderer.setAttribute(sentinel, 'aria-hidden', 'true');
        this.renderer.setAttribute(sentinel, 'data-infinite-scroll-sentinel', position);
        // Inert, negligible 1px marker that does not affect layout, flex sizing or pointer interaction.
        this.renderer.setStyle(sentinel, 'width', '100%');
        this.renderer.setStyle(sentinel, 'height', '1px');
        this.renderer.setStyle(sentinel, 'flex-shrink', '0');
        this.renderer.setStyle(sentinel, 'pointer-events', 'none');
        return sentinel;
    }

    private teardown(): void {
        this.observer?.disconnect();
        this.observer = undefined;
        const hostEl = this.host.nativeElement;
        if (this.topSentinel) {
            this.renderer.removeChild(hostEl, this.topSentinel);
            this.topSentinel = undefined;
        }
        if (this.bottomSentinel) {
            this.renderer.removeChild(hostEl, this.bottomSentinel);
            this.bottomSentinel = undefined;
        }
    }
}
