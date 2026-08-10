import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { CourseTabRefreshService } from 'app/course/overview/services/course-tab-refresh.service';

describe('CourseTabRefreshService', () => {
    let service: CourseTabRefreshService;
    let events: Subject<NavigationEnd>;
    let currentNavigationId: number | undefined;

    /** A route whose own URL is /courses/1/lectures, regardless of which child is open. */
    const lecturesRoute = {
        pathFromRoot: [{ snapshot: { url: [] } }, { snapshot: { url: [{ path: 'courses' }, { path: '1' }] } }, { snapshot: { url: [{ path: 'lectures' }] } }],
    } as unknown as ActivatedRoute;

    function navigateTo(id: number, url: string): void {
        events.next(new NavigationEnd(id, url, url));
    }

    beforeEach(() => {
        events = new Subject<NavigationEnd>();
        currentNavigationId = 10;
        TestBed.configureTestingModule({
            providers: [
                {
                    provide: Router,
                    useValue: {
                        events: events.asObservable(),
                        currentNavigation: () => (currentNavigationId === undefined ? null : { id: currentNavigationId }),
                    },
                },
            ],
        });
        service = TestBed.inject(CourseTabRefreshService);
    });

    it('should emit when the user selects the tab they are already on', () => {
        const refreshed = vi.fn();
        service.reselections(lecturesRoute).subscribe(refreshed);

        navigateTo(11, '/courses/1/lectures');

        expect(refreshed).toHaveBeenCalledOnce();
    });

    it('should not emit for the navigation that opened the tab, which loads on its own', () => {
        const refreshed = vi.fn();
        service.reselections(lecturesRoute).subscribe(refreshed);

        navigateTo(10, '/courses/1/lectures');

        expect(refreshed).not.toHaveBeenCalled();
    });

    it('should not emit when a child route is opened, which is selecting a lecture rather than the tab', () => {
        const refreshed = vi.fn();
        service.reselections(lecturesRoute).subscribe(refreshed);

        navigateTo(11, '/courses/1/lectures/7');

        expect(refreshed).not.toHaveBeenCalled();
    });

    it('should emit after a deep link, where the tab was opened on a child URL', () => {
        // The tab URL comes from the route, not from the navigation that created the component: a student who opened
        // /courses/1/lectures/7 directly must still be able to refresh by selecting the tab
        currentNavigationId = 10;
        const refreshed = vi.fn();
        service.reselections(lecturesRoute).subscribe(refreshed);

        navigateTo(11, '/courses/1/lectures');

        expect(refreshed).toHaveBeenCalledOnce();
    });

    it('should ignore query parameters and the fragment when deciding it is the same tab', () => {
        const refreshed = vi.fn();
        service.reselections(lecturesRoute).subscribe(refreshed);

        navigateTo(11, '/courses/1/lectures?filter=upcoming');

        expect(refreshed).toHaveBeenCalledOnce();
    });

    it('should not emit for a different tab', () => {
        const refreshed = vi.fn();
        service.reselections(lecturesRoute).subscribe(refreshed);

        navigateTo(11, '/courses/1/exercises');

        expect(refreshed).not.toHaveBeenCalled();
    });
});
