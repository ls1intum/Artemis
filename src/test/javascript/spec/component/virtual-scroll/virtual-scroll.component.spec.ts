import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SimpleChange, SimpleChanges } from '@angular/core';
import { ArtemisTestModule } from '../../test.module';
import { VirtualScrollComponent } from 'app/shared/virtual-scroll/virtual-scroll.component';
import { metisCoursePosts, metisGeneralCourseWidePosts } from '../../helpers/sample/metis-sample-data';
import { ReplaySubject } from 'rxjs';
import { NavigationStart, Router, RouterEvent } from '@angular/router';
import { Post } from 'app/entities/metis/post.model';

const routerEventSubject = new ReplaySubject<RouterEvent>(1);

class MockRouter {
    public url = 'courses/1/discussion';
    public events = routerEventSubject.asObservable();
}

describe('VirtualScrollComponent', () => {
    let comp: VirtualScrollComponent<Post>;
    let fixture: ComponentFixture<VirtualScrollComponent<Post>>;

    let originalWindow: any;
    let windowSpy: jest.SpyInstance;
    let windowScrollToSpy: jest.SpyInstance;
    let prepareDataItemsSpy: jest.SpyInstance;
    let forceReloadChangeSpy: jest.SpyInstance;
    let onEndOfOriginalItemsReachedSpy: jest.SpyInstance;

    const SCROLL_PADDING_TOP = 325;
    const MIN_ITEM_HEIGHT = 126.7;
    const END_OF_LIST_THRESHOLD = 2;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [VirtualScrollComponent],
            providers: [{ provide: Router, useClass: MockRouter }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(VirtualScrollComponent);
                comp = fixture.componentInstance;

                windowSpy = jest.spyOn(global, 'window', 'get');
                windowScrollToSpy = jest.spyOn(window, 'scrollTo');
                prepareDataItemsSpy = jest.spyOn(comp, 'prepareDataItems');
                forceReloadChangeSpy = jest.spyOn(comp.forceReloadChange, 'emit');
                onEndOfOriginalItemsReachedSpy = jest.spyOn(comp.onEndOfOriginalItemsReached, 'emit');
                jest.spyOn(comp, 'numberItemsCanRender').mockReturnValue(2);

                // make a copy of type any to assign readonly variable scrollY and prevent circular dependency problem
                originalWindow = window;
            });
    });

    afterEach(() => {
        originalWindow.scrollY = 0;
        windowSpy.mockReturnValue(originalWindow);

        jest.restoreAllMocks();
    });

    it('should initialize correctly', fakeAsync(() => {
        fixture.detectChanges();

        expect(comp.prevOriginalItems).toBeEmpty();
        expect(comp.minItemHeight).not.toBeNull();
        expect(comp.focusInUnListener).not.toBeNull();
        expect(comp.scrollUnListener).not.toBeNull();
    }));

    it('should set originalItems to empty array if undefined value is passed into the component', fakeAsync(() => {
        fixture.detectChanges();

        const changes = {} as SimpleChanges;
        changes.originalItems = new SimpleChange([], undefined, true);
        comp.originalItems = undefined;
        comp.ngOnChanges(changes);
        tick();
        fixture.detectChanges();

        expect(comp.originalItems).toBeDefined();
        expect(comp.originalItems).toBeEmpty();
    }));

    it('should initialize DOM tree with items', fakeAsync(() => {
        fixture.detectChanges();
        comp.forceReload = true;
        comp.originalItems = metisCoursePosts;

        const changes = {} as SimpleChanges;
        changes.originalItems = new SimpleChange([], metisCoursePosts, true);
        comp.ngOnChanges(changes);

        tick();
        fixture.detectChanges();

        expect(comp.previousItemsHeight).toHaveLength(metisCoursePosts.length);
        expect(prepareDataItemsSpy).toHaveBeenCalledOnce();
        expect(onEndOfOriginalItemsReachedSpy).not.toHaveBeenCalled();
    }));

    it('should set forceReloadChange flag to true when user navigates to specific post', fakeAsync(() => {
        fixture.detectChanges();

        routerEventSubject.next(new NavigationStart(0, 'courses/1/discussion?searchText=%231'));

        expect(forceReloadChangeSpy).toHaveBeenCalledOnce();
        expect(forceReloadChangeSpy).toHaveBeenCalledWith(true);
    }));

    it('should update rendered DOM tree items correctly', fakeAsync(() => {
        prepareComponent();

        // conditions to append new items to the end of originalItems
        comp.forceReload = false;
        comp.currentScroll = comp.minItemHeight * 2;

        const updatedTitle = 'Updated title';
        comp.prevOriginalItems[0].title = updatedTitle;

        const changes = {} as SimpleChanges;
        changes.originalItems = new SimpleChange(comp.prevOriginalItems, comp.originalItems, false);
        comp.ngOnChanges(changes);

        tick();
        fixture.detectChanges();

        expect(comp.domTreeItems).toHaveLength(3);
        expect(comp.domTreeItems[0].title).toBe(updatedTitle);
        expect(onEndOfOriginalItemsReachedSpy).not.toHaveBeenCalled();
    }));

    it('should not unintentionally scroll on clicking the text area of posting markdown editor component', fakeAsync(() => {
        comp.originalItems = metisGeneralCourseWidePosts;

        const changes = {} as SimpleChanges;
        changes.originalItems = new SimpleChange([], metisGeneralCourseWidePosts, true);
        comp.ngOnChanges(changes);

        originalWindow.scrollY = 1500;
        windowSpy.mockReturnValue(originalWindow);
        global.window.dispatchEvent(new Event('scroll'));

        tick();
        fixture.detectChanges();

        // simulate unintended scrolling that occurs on focus
        originalWindow.scrollY = 1000;
        windowSpy.mockReturnValue(originalWindow);
        global.window.dispatchEvent(new Event('focusin'));

        expect(windowScrollToSpy).toHaveBeenCalledOnce();
        expect(windowScrollToSpy).toHaveBeenCalledWith(0, comp.windowScrollTop);
    }));

    it('should perform virtual scroll on scroll event and update the DOM tree', fakeAsync(() => {
        prepareComponent();

        expect(window.scrollY).toBe(0);
        expect(comp.domTreeItems[0].id).toBe(1);
        expect(comp.domTreeItems[1].id).toBe(2);
        expect(comp.domTreeItems[2].id).toBe(3);

        originalWindow.scrollY = comp.minItemHeight * 7;
        windowSpy.mockReturnValue(originalWindow);
        global.window.dispatchEvent(new Event('scroll'));

        tick();
        fixture.detectChanges();

        expect(comp.windowScrollTop).toBe(comp.minItemHeight * 7);
        expect(prepareDataItemsSpy).toHaveBeenCalledTimes(2);
        expect(onEndOfOriginalItemsReachedSpy).not.toHaveBeenCalled();
        expect(comp.domTreeItems[0].id).toBe(2);
        expect(comp.domTreeItems[1].id).toBe(3);
        expect(comp.domTreeItems[2].id).toBe(5);

        originalWindow.scrollY = comp.minItemHeight * 10;
        windowSpy.mockReturnValue(originalWindow);
        global.window.dispatchEvent(new Event('scroll'));

        tick();
        fixture.detectChanges();

        expect(onEndOfOriginalItemsReachedSpy).toHaveBeenCalledOnce();
    }));

    function prepareComponent() {
        comp.scrollPaddingTop = SCROLL_PADDING_TOP;
        comp.minItemHeight = MIN_ITEM_HEIGHT;
        comp.endOfListReachedItemThreshold = END_OF_LIST_THRESHOLD;
        comp.originalItems = metisCoursePosts;

        const changes = {} as SimpleChanges;
        changes.originalItems = new SimpleChange([], metisCoursePosts, true);
        comp.ngOnChanges(changes);

        tick();
        fixture.detectChanges();
    }
});
