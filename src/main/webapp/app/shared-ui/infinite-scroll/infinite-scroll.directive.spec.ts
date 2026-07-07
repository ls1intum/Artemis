import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ApplicationRef, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InfiniteScrollDirective } from 'app/shared-ui/infinite-scroll/infinite-scroll.directive';

/** Controllable IntersectionObserver stand-in: captures the callback + options and lets tests fire entries. */
class MockIntersectionObserver {
    static instances: MockIntersectionObserver[] = [];
    private readonly callback: IntersectionObserverCallback;
    readonly options?: IntersectionObserverInit;

    constructor(callback: IntersectionObserverCallback, options?: IntersectionObserverInit) {
        this.callback = callback;
        this.options = options;
        MockIntersectionObserver.instances.push(this);
    }

    observe = vi.fn((_element: Element) => {});
    unobserve = vi.fn((_element: Element) => {});
    disconnect = vi.fn(() => {});

    trigger(entries: Array<{ target: Element; isIntersecting: boolean; boundingClientRect?: Partial<DOMRectReadOnly> }>): void {
        const normalized = entries.map(
            ({ target, isIntersecting, boundingClientRect }) =>
                // A laid-out 1px sentinel has a non-zero box by default; tests can pass a zero-area box to emulate a hidden container.
                ({ target, isIntersecting, boundingClientRect: { width: 100, height: 1, ...boundingClientRect } }) as IntersectionObserverEntry,
        );
        this.callback(normalized, this as unknown as IntersectionObserver);
    }
}

@Component({
    selector: 'jhi-infinite-scroll-test-host',
    imports: [InfiniteScrollDirective],
    template: `
        <div
            class="scroll-container"
            infinite-scroll
            [scrollWindow]="scrollWindow()"
            [infiniteScrollDisabled]="disabled()"
            (scrolledUp)="ups = ups + 1"
            (scrolled)="downs = downs + 1"
        >
            @for (item of items(); track item) {
                <div class="item">{{ item }}</div>
            }
        </div>
    `,
})
class TestHostComponent {
    readonly scrollWindow = signal(false);
    readonly disabled = signal(false);
    // The directive must keep its sentinels bracketing this list even as items are prepended/appended.
    readonly items = signal(['item']);
    ups = 0;
    downs = 0;
}

describe('InfiniteScrollDirective', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;
    let savedIntersectionObserver: typeof IntersectionObserver;

    beforeEach(() => {
        savedIntersectionObserver = global.IntersectionObserver;
        MockIntersectionObserver.instances = [];
        global.IntersectionObserver = MockIntersectionObserver as unknown as typeof IntersectionObserver;

        TestBed.configureTestingModule({ imports: [TestHostComponent] });
        fixture = TestBed.createComponent(TestHostComponent);
        host = fixture.componentInstance;
    });

    afterEach(() => {
        global.IntersectionObserver = savedIntersectionObserver;
    });

    /** Runs change detection and flushes the directive's afterNextRender setup. */
    function flush(): void {
        fixture.detectChanges();
        TestBed.inject(ApplicationRef).tick();
    }

    function container(): HTMLElement {
        return fixture.nativeElement.querySelector('.scroll-container');
    }

    function sentinel(position: 'top' | 'bottom'): Element {
        return container().querySelector(`[data-infinite-scroll-sentinel="${position}"]`)!;
    }

    function observer(): MockIntersectionObserver {
        return MockIntersectionObserver.instances.at(-1)!;
    }

    it('inserts an inert sentinel at the top and bottom of the content and observes both', () => {
        flush();

        expect(sentinel('top')).toBeTruthy();
        expect(sentinel('bottom')).toBeTruthy();
        // Sentinels must bracket the projected content so they stay at the scroll edges.
        expect(container().firstElementChild?.getAttribute('data-infinite-scroll-sentinel')).toBe('top');
        expect(container().lastElementChild?.getAttribute('data-infinite-scroll-sentinel')).toBe('bottom');
        expect(observer().observe).toHaveBeenCalledTimes(2);
    });

    it('keeps the sentinels at the content edges after items are prepended and appended', () => {
        flush();

        // Prepend older items (as loading a previous page does) and append a newer item.
        host.items.set(['older-2', 'older-1', 'item', 'newer-1']);
        fixture.detectChanges();

        // The directive inserts sentinels imperatively and never re-positions them, so this asserts the
        // @for block keeps inserting its items between (not around) the sentinels.
        expect(container().firstElementChild?.getAttribute('data-infinite-scroll-sentinel')).toBe('top');
        expect(container().lastElementChild?.getAttribute('data-infinite-scroll-sentinel')).toBe('bottom');
        // Sanity check that the list actually mutated between the sentinels.
        expect(container().querySelectorAll('.item')).toHaveLength(4);
    });

    it('observes the host element at the content edge (rootMargin 0)', () => {
        flush();

        expect(observer().options?.root).toBe(container());
        expect(observer().options?.rootMargin).toBe('0px');
    });

    it('observes the viewport (null root) when scrollWindow is true', () => {
        host.scrollWindow.set(true);
        flush();

        expect(observer().options?.root).toBeNull();
    });

    it('does not emit on the initial already-visible state (un-armed)', () => {
        flush();

        observer().trigger([{ target: sentinel('top'), isIntersecting: true }]);

        expect(host.ups).toBe(0);
    });

    it('emits scrolledUp exactly once each time the top sentinel leaves and re-enters', () => {
        flush();
        const top = sentinel('top');

        observer().trigger([{ target: top, isIntersecting: false }]); // leave -> arm
        observer().trigger([{ target: top, isIntersecting: true }]); // enter -> emit
        expect(host.ups).toBe(1);

        observer().trigger([{ target: top, isIntersecting: true }]); // still inside -> no re-emit
        expect(host.ups).toBe(1);

        observer().trigger([{ target: top, isIntersecting: false }]); // leave -> re-arm
        observer().trigger([{ target: top, isIntersecting: true }]); // enter -> emit
        expect(host.ups).toBe(2);
    });

    it('keeps paging chained when the container is hidden during the fetch: a hidden-box callback arms the next page', () => {
        flush();
        const top = sentinel('top');

        // First page: scroll to the top, fire once.
        observer().trigger([{ target: top, isIntersecting: false }]);
        observer().trigger([{ target: top, isIntersecting: true }]);
        expect(host.ups).toBe(1);

        // While loading, the consumer hides the list (`display: none`), collapsing the sentinel to a
        // zero-area box. This is the only callback the observer produces for that cycle, so it must arm
        // the sentinel — otherwise the next scroll back to the edge could not load the following page.
        observer().trigger([{ target: top, isIntersecting: false, boundingClientRect: { width: 0, height: 0 } }]);
        // The list is shown again at the nudged position and the user scrolls back to the edge -> next page.
        observer().trigger([{ target: top, isIntersecting: true }]);
        expect(host.ups).toBe(2);
    });

    it('never fires from a hidden (zero-area) box on its own', () => {
        flush();
        const top = sentinel('top');

        // A hidden box is reported as not intersecting, so it can only arm, never emit by itself.
        observer().trigger([{ target: top, isIntersecting: false, boundingClientRect: { width: 0, height: 0 } }]);
        expect(host.ups).toBe(0);
    });

    it('emits scrolled when the bottom sentinel re-enters', () => {
        flush();
        const bottom = sentinel('bottom');

        observer().trigger([{ target: bottom, isIntersecting: false }]);
        observer().trigger([{ target: bottom, isIntersecting: true }]);

        expect(host.downs).toBe(1);
        expect(host.ups).toBe(0);
    });

    it('suppresses events while infiniteScrollDisabled is true', () => {
        host.disabled.set(true);
        flush();
        const top = sentinel('top');

        observer().trigger([{ target: top, isIntersecting: false }]);
        observer().trigger([{ target: top, isIntersecting: true }]);

        expect(host.ups).toBe(0);
    });

    it('resumes emitting after infiniteScrollDisabled flips back to false', () => {
        host.disabled.set(true);
        flush();
        const top = sentinel('top');

        observer().trigger([{ target: top, isIntersecting: false }]);
        observer().trigger([{ target: top, isIntersecting: true }]);
        expect(host.ups).toBe(0);

        host.disabled.set(false);
        fixture.detectChanges();
        observer().trigger([{ target: top, isIntersecting: false }]);
        observer().trigger([{ target: top, isIntersecting: true }]);
        expect(host.ups).toBe(1);
    });

    it('routes top and bottom entries independently within a single callback batch', () => {
        flush();
        const top = sentinel('top');
        const bottom = sentinel('bottom');

        // Both leave the zone in one batch (arm both), then both re-enter in one batch (fire both once).
        observer().trigger([
            { target: top, isIntersecting: false },
            { target: bottom, isIntersecting: false },
        ]);
        observer().trigger([
            { target: top, isIntersecting: true },
            { target: bottom, isIntersecting: true },
        ]);

        expect(host.ups).toBe(1);
        expect(host.downs).toBe(1);
    });

    it('disconnects the observer and removes the sentinels on destroy', () => {
        flush();
        const target = container();
        const obs = observer();

        fixture.destroy();

        expect(obs.disconnect).toHaveBeenCalled();
        expect(target.querySelector('[data-infinite-scroll-sentinel="top"]')).toBeNull();
        expect(target.querySelector('[data-infinite-scroll-sentinel="bottom"]')).toBeNull();
    });
});
