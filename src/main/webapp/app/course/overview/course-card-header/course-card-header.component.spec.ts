import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, RouterLink, provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { Logger, compile } from 'sass';
import { MockComponent, MockDirective } from 'ng-mocks';
import { CourseCardHeaderComponent } from 'app/course/overview/course-card-header/course-card-header.component';
import { ImageComponent } from 'app/shared-ui/image/image.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseNotificationBubbleComponent } from 'app/notification/course-notification/course-notification-bubble/course-notification-bubble.component';

describe('CourseCardHeaderComponent', () => {
    let fixture: ComponentFixture<CourseCardHeaderComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [provideRouter([])],
        }).overrideComponent(CourseCardHeaderComponent, {
            remove: { imports: [ImageComponent, CourseNotificationBubbleComponent, TranslateDirective] },
            add: { imports: [MockComponent(ImageComponent), MockComponent(CourseNotificationBubbleComponent), MockDirective(TranslateDirective)] },
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

    // Regression test for issue #12526: a long course title used to be cut off in the middle of its fourth line (the
    // title box had a fixed max-height without a line clamp) and a long unbreakable word overflowed the card, because
    // the flex item holding the title could not shrink below its content width.
    // Vitest does not apply component styles, so the component's SCSS is compiled and attached to the document here.
    describe('long course titles', () => {
        const longTitle = 'Funktionale Programmierung und Verifikation (Sommersemester 2023)';
        let styleElement: HTMLStyleElement;

        beforeEach(() => {
            const { css } = compile('src/main/webapp/app/course/overview/course-card-header/course-card-header.component.scss', { logger: Logger.silent });
            styleElement = document.createElement('style');
            // ::ng-deep is Angular-specific and not valid CSS for the DOM implementation of the test environment
            styleElement.textContent = css.replaceAll('::ng-deep', '');
            document.head.appendChild(styleElement);
        });

        afterEach(() => styleElement.remove());

        it('should keep the full title reachable as a tooltip', () => {
            fixture.componentRef.setInput('courseTitle', longTitle);
            fixture.detectChanges();

            const title = fixture.nativeElement.querySelector('.card-title') as HTMLElement;
            expect(title.textContent?.trim()).toBe(longTitle);
            expect(title.getAttribute('title')).toBe(longTitle);
        });

        it.each([
            ['regular card', false, false],
            ['archive card', true, false],
            ['archive card with management zone', true, true],
        ])('should let the title shrink, break long words and clamp its lines in a %s', (_name, archiveMode, canManageCourse) => {
            fixture.componentRef.setInput('courseTitle', longTitle);
            fixture.componentRef.setInput('archiveMode', archiveMode);
            fixture.componentRef.setInput('canManageCourse', canManageCourse);
            fixture.detectChanges();

            const container = fixture.nativeElement.querySelector('.course-title-container') as HTMLElement;
            const title = fixture.nativeElement.querySelector('.card-title') as HTMLElement;
            const titleStyle = getComputedStyle(title);
            // Without min-width: 0 a flex item never shrinks below its longest word and overflows the card.
            expect(getComputedStyle(container).minWidth).toBe('0px');
            // A word wider than the card has to break instead of overflowing it.
            expect(titleStyle.overflowWrap).toBe('anywhere');
            // The visible lines are limited, so that no line is cut in half by the fixed header height of 85px.
            expect(titleStyle.overflow).toBe('hidden');
            expect(titleStyle.getPropertyValue('-webkit-line-clamp')).toBe('3');
            expect(titleStyle.maxHeight).not.toBe('76px');
        });
    });
});
