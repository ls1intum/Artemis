import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, RouterLink, provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { CourseCardHeaderComponent } from 'app/course/overview/course-card-header/course-card-header.component';
import { ImageComponent } from 'app/shared-ui/image/image.component';
import { CourseNotificationBubbleComponent } from 'app/notification/course-notification/course-notification-bubble/course-notification-bubble.component';

describe('CourseCardHeaderComponent', () => {
    let fixture: ComponentFixture<CourseCardHeaderComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [provideRouter([])],
        }).overrideComponent(CourseCardHeaderComponent, {
            remove: { imports: [ImageComponent, CourseNotificationBubbleComponent] },
            add: { imports: [MockComponent(ImageComponent), MockComponent(CourseNotificationBubbleComponent)] },
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseCardHeaderComponent);
        fixture.componentRef.setInput('courseId', 42);
        fixture.componentRef.setInput('courseTitle', 'Test Course');
        fixture.componentRef.setInput('courseIcon', '');
        fixture.componentRef.setInput('courseColor', '#123456');
    });

    afterEach(() => vi.restoreAllMocks());

    // Regression test for issue #12905: the header must expose exactly one navigation target into the course.
    // A second, redundant routerLink (on the card-header div in addition to the stretched-link anchor) made a single
    // click fire two navigations to the same URL. With onSameUrlNavigation: 'reload' the second navigation canceled
    // the first mid-flight, aborting and re-issuing the expensive courses/{id}/for-dashboard request.
    it('should expose exactly one router link into the course', () => {
        fixture.detectChanges();

        const routerLinks = fixture.debugElement.queryAll(By.directive(RouterLink));
        expect(routerLinks).toHaveLength(1);
        // The single link must be the accessible stretched-link anchor, not a clickable div.
        expect(routerLinks[0].nativeElement.tagName).toBe('A');
        expect(routerLinks[0].nativeElement.getAttribute('href')).toBe('/courses/42');
    });

    // Regression test for issue #12905: a single click on the card must trigger exactly one router navigation.
    // Previously the redundant div routerLink meant the click also bubbled to a second routerLink, firing two
    // navigations to the same URL; with onSameUrlNavigation: 'reload' this issued (and canceled) a duplicate
    // courses/{id}/for-dashboard request. Two navigations here would mean two guard fetches downstream.
    it('should trigger exactly one navigation when the card is clicked', () => {
        fixture.detectChanges();
        const router = TestBed.inject(Router);
        const navigateByUrlSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

        const link = fixture.nativeElement.querySelector('a.stretched-link') as HTMLElement;
        link.click();

        expect(navigateByUrlSpy).toHaveBeenCalledTimes(1);
    });
});
