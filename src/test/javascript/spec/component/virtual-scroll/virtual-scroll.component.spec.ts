import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { DebugElement, SimpleChange, SimpleChanges } from '@angular/core';
import { ArtemisTestModule } from '../../test.module';
import { VirtualScrollComponent } from 'app/shared/virtual-scroll/virtual-scroll.component';
import { metisCoursePosts, metisCoursePostsWithCourseWideContext } from '../../helpers/sample/metis-sample-data';
import { ReplaySubject } from 'rxjs';
import { NavigationStart, Router, RouterEvent } from '@angular/router';

const routerEventSubject = new ReplaySubject<RouterEvent>(1);

class MockRouter {
    public url = 'courses/1/discussion';
    public events = routerEventSubject.asObservable();
}

describe('VirtualScrollComponent', () => {
    let comp: VirtualScrollComponent;
    let fixture: ComponentFixture<VirtualScrollComponent>;
    let debugElement: DebugElement;

    let prepareDataItemsSpy: jest.SpyInstance;
    let forceReloadChangeSpy: jest.SpyInstance;
    let onEndOfOriginalItemsReachedSpy: jest.SpyInstance;

    const minPostItemHeight = 126.7;

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
                debugElement = fixture.debugElement;
                prepareDataItemsSpy = jest.spyOn(comp, 'prepareDataItems');
                forceReloadChangeSpy = jest.spyOn(comp.forceReloadChange, 'emit');
                onEndOfOriginalItemsReachedSpy = jest.spyOn(comp.onEndOfOriginalItemsReached, 'emit');
            });
    });

    it('should initialize correctly', fakeAsync(() => {
        fixture.detectChanges();

        expect(comp.prevOriginalItems).toBeEmpty();
        expect(comp.minItemHeight).not.toBeNull();
        expect(comp.focusInListener).not.toBeNull();
        expect(comp.scrollListener).not.toBeNull();
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
    }));

    it('should set forceReloadChange flag to true when user navigates to specific post', fakeAsync(() => {
        fixture.detectChanges();

        routerEventSubject.next(new NavigationStart(0, 'courses/1/discussion?searchText=%231'));

        expect(forceReloadChangeSpy).toHaveBeenCalledOnce();
        expect(forceReloadChangeSpy).toHaveBeenCalledWith(true);
    }));

    it('should update rendered DOM tree items correctly', fakeAsync(() => {
        comp.minItemHeight = minPostItemHeight;
        comp.endOfListReachedItemThreshold = 2;
        comp.originalItems = metisCoursePostsWithCourseWideContext;

        const changes = {} as SimpleChanges;
        changes.originalItems = new SimpleChange([], metisCoursePostsWithCourseWideContext, true);
        comp.ngOnChanges(changes);

        tick();
        fixture.detectChanges();

        // conditions to append new items to the end of originalItems
        comp.forceReload = false;
        comp.currentScroll = comp.minItemHeight * 2;

        const updatedTitle = 'Updated title';
        comp.prevOriginalItems[0].title = updatedTitle;

        changes.originalItems = new SimpleChange(comp.prevOriginalItems, comp.originalItems, false);
        comp.ngOnChanges(changes);

        tick();
        fixture.detectChanges();

        expect(comp.domTreeItems).toHaveLength(comp.originalItems.length);
        expect(comp.domTreeItems[0].title).toBe(updatedTitle);
    }));

    it('should not unintentionally scroll on clicking the text area of posting markdown editor component', fakeAsync(() => {
        comp.originalItems = metisCoursePostsWithCourseWideContext;

        const changes = {} as SimpleChanges;
        changes.originalItems = new SimpleChange([], metisCoursePostsWithCourseWideContext, true);
        comp.ngOnChanges(changes);

        tick();
        fixture.detectChanges();

        expect(fixture.nativeElement.scrollTop).toBe(0);

        comp.currentScroll = comp.minItemHeight * 2;
        debugElement.nativeElement.dispatchEvent(new Event('focusin'));

        expect(fixture.nativeElement.scrollTop).toBe(comp.currentScroll);
    }));

    it('should perform virtual scroll on scroll event and update the DOM tree', fakeAsync(() => {
        comp.minItemHeight = minPostItemHeight;
        comp.endOfListReachedItemThreshold = 2;
        comp.originalItems = metisCoursePosts;

        const changes = {} as SimpleChanges;
        changes.originalItems = new SimpleChange([], metisCoursePosts, true);
        comp.ngOnChanges(changes);

        tick();
        fixture.detectChanges();

        expect(fixture.nativeElement.scrollTop).toBe(0);
        expect(comp.domTreeItems[0].id).toBe(1);
        expect(comp.domTreeItems[1].id).toBe(2);
        expect(comp.domTreeItems[2].id).toBe(3);

        fixture.nativeElement.scrollTop = comp.minItemHeight * 2;
        debugElement.nativeElement.dispatchEvent(new Event('scroll'));

        tick();
        fixture.detectChanges();

        expect(comp.currentScroll).toBe(comp.minItemHeight * 2);
        expect(prepareDataItemsSpy).toHaveBeenCalledTimes(2);
        expect(comp.domTreeItems[0].id).toBe(3);
        expect(comp.domTreeItems[1].id).toBe(5);
        expect(comp.domTreeItems[2].id).toBe(6);
    }));
});
