import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VirtualScrollComponent } from 'app/shared-ui/virtual-scroll/virtual-scroll.component';
import { metisCoursePosts, metisGeneralCourseWidePosts } from 'test/helpers/sample/metis-sample-data';
import { ReplaySubject } from 'rxjs';
import { NavigationStart, Router, RouterEvent } from '@angular/router';
import { Post } from 'app/communication/shared/entities/post.model';
import { vi } from 'vitest';

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

                windowScrollToSpy = vi.spyOn(window, 'scrollTo');
                prepareDataItemsSpy = vi.spyOn(comp, 'prepareDataItems');
                forceReloadChangeSpy = vi.spyOn(comp.forceReloadChange, 'emit');
                onEndOfOriginalItemsReachedSpy = vi.spyOn(comp.onEndOfOriginalItemsReached, 'emit');
                vi.spyOn(comp, 'numberItemsCanRender').mockReturnValue(2);

                // make a copy of type any to assign readonly variable scrollY and prevent circular dependency problem
                originalWindow = window;
            });
    });

    const setInput = (name: string, value: unknown): void => {
        fixture.componentRef.setInput(name, value);
        fixture.detectChanges();
    };

    afterEach(() => {
        originalWindow.scrollY = 0;

        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    it('should initialize correctly', () => {
        fixture.detectChanges();

        expect(comp.prevOriginalItems).toEqual([]);
        expect(comp.minItemHeight()).not.toBeNull();
        expect(comp.focusInUnListener).not.toBeNull();
        expect(comp.scrollUnListener).not.toBeNull();
    });

    it('should set originalItems to empty array if undefined value is passed into the component', () => {
        setInput('items', undefined);
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(comp.domTreeItems).toEqual([]);
    });

    it('should initialize DOM tree with items', () => {
        setInput('forceReload', true);
        setInput('items', metisCoursePosts);

        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(comp.previousItemsHeight).toHaveLength(metisCoursePosts.length);
        expect(prepareDataItemsSpy).toHaveBeenCalled();
        expect(onEndOfOriginalItemsReachedSpy).not.toHaveBeenCalled();
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
        setInput('forceReload', false);
        comp.currentScroll = comp.minItemHeight() * 2;

        const updatedTitle = 'Updated title';
        comp.prevOriginalItems[0].title = updatedTitle;

        setInput('items', [...comp.prevOriginalItems]);

        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(comp.domTreeItems).toHaveLength(3);
        expect(comp.domTreeItems[0].title).toBe(updatedTitle);
        expect(onEndOfOriginalItemsReachedSpy).not.toHaveBeenCalled();
    });

    it('should not unintentionally scroll on clicking the text area of posting markdown editor component', () => {
        setInput('items', metisGeneralCourseWidePosts);

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

        originalWindow.scrollY = comp.minItemHeight() * 7;
        global.window.dispatchEvent(new Event('scroll'));

        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(comp.windowScrollTop).toBe(comp.minItemHeight() * 7);
        expect(prepareDataItemsSpy).toHaveBeenCalledOnce();
        expect(onEndOfOriginalItemsReachedSpy).not.toHaveBeenCalled();
        expect(comp.domTreeItems[0].id).toBe(2);
        expect(comp.domTreeItems[1].id).toBe(3);
        expect(comp.domTreeItems[2].id).toBe(5);

        originalWindow.scrollY = comp.minItemHeight() * 10;
        global.window.dispatchEvent(new Event('scroll'));

        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(onEndOfOriginalItemsReachedSpy).toHaveBeenCalledOnce();
    });

    function prepareComponent() {
        setInput('scrollPaddingTop', SCROLL_PADDING_TOP);
        setInput('minItemHeight', MIN_ITEM_HEIGHT);
        setInput('endOfListReachedItemThreshold', END_OF_LIST_THRESHOLD);
        setInput('items', metisCoursePosts);

        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();
        prepareDataItemsSpy.mockClear();
        onEndOfOriginalItemsReachedSpy.mockClear();
    }
});
