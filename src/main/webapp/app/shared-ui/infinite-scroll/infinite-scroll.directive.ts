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
 * ## How it triggers
 *
 * The observer watches the sentinels against the true content edge (`rootMargin: 0`). A sentinel is
 * *armed* whenever it is reported as having left the viewport, and fires its event exactly once when
 * it next re-enters. Both sentinels start un-armed, so the initial render (already at the top) does
 * not emit — matching `ngx-infinite-scroll`'s default `immediateCheck=false`. Firing on re-entry
 * (rather than on every scroll tick) avoids the page-skipping over-fetch a level-triggered scroll
 * listener is prone to.
 *
 * ## Why the trigger sits at the content edge (and not a prefetch margin)
 *
 * The consumers load the next page in response to `scrolledUp`, then nudge the scroll position by a
 * small fixed amount (e.g. `scrollTop += 50`) so the user is no longer pinned to the edge. For chained
 * paging to keep working, that nudge must move the sentinel *across* the trigger boundary so the next
 * scroll back to the edge re-arms and re-fires. A prefetch `rootMargin` (a percentage of the root
 * height, typically larger than the nudge) keeps the sentinel inside the trigger zone after the nudge,
 * so no further `IntersectionObserver` callback is produced and paging stalls after the first page.
 * Anchoring the trigger at the content edge keeps each scroll-to-edge gesture loading exactly one page,
 * which is what `ngx-infinite-scroll` did via its scroll listener. `infiniteScrollUpDistance` /
 * `infiniteScrollDistance` are still accepted for drop-in compatibility but no longer widen the zone.
 *
 * ## Hidden container during a fetch
 *
 * While loading, the consumers hide the list (`display: none`), which collapses the sentinels to a
 * zero-area box. The observer reports that as "left the viewport", which *arms* the sentinel — and
 * crucially it must, because once the list is shown again at the nudged position the sentinel sits just
 * outside the viewport with no further threshold crossing, so this is the only callback that can arm it
 * for the next scroll. A hidden box is never reported as intersecting, so it can never *fire*; it only
 * ever arms. `infiniteScrollDisabled` is evaluated live on every callback.
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
    /** Accepted for `ngx-infinite-scroll` drop-in compatibility; no longer widens the trigger zone (see class docs). */
    readonly infiniteScrollUpDistance = input(1.5);
    /** Accepted for `ngx-infinite-scroll` drop-in compatibility; no longer widens the trigger zone (see class docs). */
    readonly infiniteScrollDistance = input(2);
    /** When true, edge events are suppressed (the observer keeps running, but nothing is emitted). */
    readonly infiniteScrollDisabled = input(false);

    /** Emitted when the user scrolls to the top of the scroll content. */
    readonly scrolledUp = output<void>();
    /** Emitted when the user scrolls to the bottom of the scroll content. Not consumed today, kept for reuse. */
    readonly scrolled = output<void>();

    private observer?: IntersectionObserver;
    private topSentinel?: HTMLElement;
    private bottomSentinel?: HTMLElement;
    // Both start un-armed so the initial render (already at the top) does not emit. A sentinel is
    // (re-)armed once it leaves the viewport (including when the container is hidden mid-fetch) and
    // fires once when it next re-enters.
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
            // Trigger at the true content edge so the consumer's post-load scroll nudge re-arms the
            // sentinel and chained paging keeps working (see class docs).
            rootMargin: '0px',
            threshold: 0,
        });
        this.observer.observe(this.topSentinel);
        this.observer.observe(this.bottomSentinel);
    }

    private onIntersect(entries: IntersectionObserverEntry[]): void {
        for (const entry of entries) {
            const isTop = entry.target === this.topSentinel;
            if (!entry.isIntersecting) {
                // Left the viewport (or the container was hidden, e.g. a `display: none` toggle while
                // loading more): arm so the next re-entry fires exactly once. A hidden box reports
                // `isIntersecting === false`, so it can only ever arm here, never fire below.
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
