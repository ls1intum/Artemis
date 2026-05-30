import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { VirtualScrollComponent } from 'app/shared-ui/virtual-scroll/virtual-scroll.component';
import { metisCoursePosts, metisGeneralCourseWidePosts } from 'test/helpers/sample/metis-sample-data';
import { ReplaySubject } from 'rxjs';
import { NavigationStart, Router, RouterEvent } from '@angular/router';
import { Post } from 'app/communication/shared/entities/post.model';

const routerEventSubject = new ReplaySubject<RouterEvent>(1);

class MockRouter {
    public url = 'courses/1/discussion';
    public events = routerEventSubject.asObservable();
}

describe('VirtualScrollComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: VirtualScrollComponent<Post>;
    let fixture: ComponentFixture<VirtualScrollComponent<Post>>;

    let originalWindow: any;
    let windowScrollToSpy: ReturnType<typeof vi.spyOn>;
    let prepareDataItemsSpy: ReturnType<typeof vi.spyOn>;
    let forceReloadChangeSpy: ReturnType<typeof vi.spyOn>;
    let onEndOfOriginalItemsReachedSpy: ReturnType<typeof vi.spyOn>;

    const SCROLL_PADDING_TOP = 325;
    const MIN_ITEM_HEIGHT = 126.7;
    const END_OF_LIST_THRESHOLD = 2;

    beforeEach(() => {
        vi.useFakeTimers();
        return TestBed.configureTestingModule({
            providers: [{ provide: Router, useClass: MockRouter }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(VirtualScrollComponent);
                comp = fixture.componentInstance;

                // provide all required inputs up front so they can be read by the component logic
                fixture.componentRef.setInput('scrollPaddingTop', SCROLL_PADDING_TOP);
                fixture.componentRef.setInput('minItemHeight', MIN_ITEM_HEIGHT);
                fixture.componentRef.setInput('endOfListReachedItemThreshold', END_OF_LIST_THRESHOLD);
                fixture.componentRef.setInput('collapsableHtmlClassNames', []);
                fixture.componentRef.setInput('forceReload', false);

                windowScrollToSpy = vi.spyOn(window, 'scrollTo');
                prepareDataItemsSpy = vi.spyOn(comp, 'prepareDataItems');
                forceReloadChangeSpy = vi.spyOn(comp.forceReloadChange, 'emit');
                onEndOfOriginalItemsReachedSpy = vi.spyOn(comp.onEndOfOriginalItemsReached, 'emit');
                vi.spyOn(comp, 'numberItemsCanRender').mockReturnValue(2);

                // make a copy of type any to assign readonly variable scrollY and prevent circular dependency problem
                originalWindow = window;
            });
    });

    afterEach(() => {
        originalWindow.scrollY = 0;

        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    it('should initialize correctly', () => {
        fixture.detectChanges();

        expect(comp.prevOriginalItems).toHaveLength(0);
        expect(comp.minItemHeight()).not.toBeNull();
        expect(comp.focusInUnListener).not.toBeNull();
        expect(comp.scrollUnListener).not.toBeNull();
    });

    it('should set originalItems to empty array if undefined value is passed into the component', () => {
        fixture.detectChanges();

        comp.originalItems = undefined;
        comp.handleOriginalItemsChange();
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(comp.originalItems).toBeDefined();
        expect(comp.originalItems).toHaveLength(0);
    });

    it('should initialize DOM tree with items', () => {
        fixture.componentRef.setInput('forceReload', true);
        // bind the items input so the effect processes them once on the first change detection
        fixture.componentRef.setInput('items', metisCoursePosts);
        fixture.detectChanges();

        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(comp.previousItemsHeight).toHaveLength(metisCoursePosts.length);
        expect(prepareDataItemsSpy).toHaveBeenCalledOnce();
        expect(onEndOfOriginalItemsReachedSpy).not.toHaveBeenCalled();
    });

    // Regression test for the initial-render bug introduced during the signal migration:
    // the migrated `items` effect originally skipped its first run (a "skip-first" guard), so the
    // FIRST bound value was never processed and the component rendered nothing until a later change
    // or scroll. The original `ngOnChanges` had no isFirstChange() guard and DID process the first
    // bound value. This test drives the EFFECT path (binding the input + change detection) rather than
    // calling handleOriginalItemsChange() directly, so it would fail on the buggy skip-first code and
    // passes once the guard is removed.
    it('should render items on the first bound value via the items effect', () => {
        // bind the public `items` input so the effect fires on first change detection
        fixture.componentRef.setInput('items', metisCoursePosts);
        fixture.componentRef.setInput('forceReload', true);

        // run change detection so the effect executes its first run and processes the bound items
        fixture.detectChanges();

        // flush the setTimeout the handler defers its work into
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        // the effect must have processed the first bound value (skip-first guard would have prevented this)
        expect(prepareDataItemsSpy).toHaveBeenCalledOnce();
        expect(comp.originalItems).toBe(metisCoursePosts);
        expect(comp.previousItemsHeight).toHaveLength(metisCoursePosts.length);
        expect(comp.domTreeItems.length).toBeGreaterThan(0);
    });

    it('should set forceReloadChange flag to true when user navigates to specific post', () => {
        fixture.detectChanges();

        routerEventSubject.next(new NavigationStart(0, 'courses/1/discussion?searchText=%231'));

        expect(forceReloadChangeSpy).toHaveBeenCalledOnce();
        expect(forceReloadChangeSpy).toHaveBeenCalledWith(true);
    });

    it('should update rendered DOM tree items correctly', () => {
        prepareComponent();

        // conditions to append new items to the end of originalItems
        fixture.componentRef.setInput('forceReload', false);
        comp.currentScroll = comp.minItemHeight()! * 2;

        const updatedTitle = 'Updated title';
        comp.prevOriginalItems[0].title = updatedTitle;

        comp.handleOriginalItemsChange();

        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(comp.domTreeItems).toHaveLength(3);
        expect(comp.domTreeItems[0].title).toBe(updatedTitle);
        expect(onEndOfOriginalItemsReachedSpy).not.toHaveBeenCalled();
    });

    it('should not unintentionally scroll on clicking the text area of posting markdown editor component', () => {
        fixture.detectChanges();
        comp.originalItems = metisGeneralCourseWidePosts;

        comp.handleOriginalItemsChange();

        originalWindow.scrollY = 1500;
        global.window.dispatchEvent(new Event('scroll'));

        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        // simulate unintended scrolling that occurs on focus
        originalWindow.scrollY = 1000;
        global.window.dispatchEvent(new Event('focusin'));

        expect(windowScrollToSpy).toHaveBeenCalledOnce();
        expect(windowScrollToSpy).toHaveBeenCalledWith(0, comp.windowScrollTop);
    });

    it('should perform virtual scroll on scroll event and update the DOM tree', () => {
        prepareComponent();

        expect(window.scrollY).toBe(0);
        expect(comp.domTreeItems[0].id).toBe(1);
        expect(comp.domTreeItems[1].id).toBe(2);
        expect(comp.domTreeItems[2].id).toBe(3);

        originalWindow.scrollY = comp.minItemHeight()! * 7;
        global.window.dispatchEvent(new Event('scroll'));

        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(comp.windowScrollTop).toBe(comp.minItemHeight()! * 7);
        expect(prepareDataItemsSpy).toHaveBeenCalledTimes(2);
        expect(onEndOfOriginalItemsReachedSpy).not.toHaveBeenCalled();
        expect(comp.domTreeItems[0].id).toBe(2);
        expect(comp.domTreeItems[1].id).toBe(3);
        expect(comp.domTreeItems[2].id).toBe(5);

        originalWindow.scrollY = comp.minItemHeight()! * 10;
        global.window.dispatchEvent(new Event('scroll'));

        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(onEndOfOriginalItemsReachedSpy).toHaveBeenCalledOnce();
    });

    function prepareComponent() {
        fixture.componentRef.setInput('scrollPaddingTop', SCROLL_PADDING_TOP);
        fixture.componentRef.setInput('minItemHeight', MIN_ITEM_HEIGHT);
        fixture.componentRef.setInput('endOfListReachedItemThreshold', END_OF_LIST_THRESHOLD);
        // bind the items input so the effect processes them once; the first change detection also
        // resolves the required viewChild (itemsContainer) and registers the ngOnInit listeners
        fixture.componentRef.setInput('items', metisCoursePosts);
        fixture.detectChanges();

        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();
    }
});
